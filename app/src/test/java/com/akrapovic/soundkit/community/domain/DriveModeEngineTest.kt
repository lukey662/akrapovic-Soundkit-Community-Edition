package com.akrapovic.soundkit.community.domain

import com.akrapovic.soundkit.community.car.CarPresence
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import com.akrapovic.soundkit.community.test.FakeBleRepository
import com.akrapovic.soundkit.community.test.FakeCarPresenceSource
import com.akrapovic.soundkit.community.test.FakeSettingsStore
import com.akrapovic.soundkit.community.test.MainDispatcherRule
import com.akrapovic.soundkit.community.test.testDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveModeEngineTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val clock = com.akrapovic.soundkit.community.test.FixedClock()
    private val carPresence = FakeCarPresenceSource()

    @Test
    fun skipsWhenDriveModeDisabled() = runTest(mainDispatcherRule.dispatcher) {
        val log = InMemoryLog()
        val engine = engine(
            settings = FakeSettingsStore(SoundKitSettings(driveModeEnabled = false)),
            log = log,
        )

        engine.onConnectReady(1L, this)

        assertTrue(log.snapshot().isEmpty())
    }

    @Test
    fun appliesPreferredOpenOutsideQuietWindow() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(device)
        ble.valveState.value = ValveState.Closed
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))
        val log = InMemoryLog()
        val engine = engine(ble = ble, log = log)

        engine.onConnectReady(1L, this)
        runCurrent()

        assertEquals(1, ble.openValveCount)
        assertEquals(RuleExecutionOutcome.Success, log.snapshot().last().outcome)
        assertEquals("Open", log.snapshot().last().action)
    }

    @Test
    fun quietWindowHoldsClosedThenOpens() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(device)
        ble.valveState.value = ValveState.Open
        ble.closeResult = CompletableDeferred(CommandResult.Success(ValveState.Closed))
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))

        val quiet = QuietStartSettings(
            enabled = true,
            daysOfWeek = setOf(0, 1, 2, 3, 4, 5, 6),
            windowStartMinute = 0,
            windowEndMinute = 24 * 60 - 1,
            holdClosedMinutes = 1,
        )
        val log = InMemoryLog()
        val engine = engine(
            ble = ble,
            settings = FakeSettingsStore(SoundKitSettings(quietStart = quiet)),
            log = log,
        )

        engine.onConnectReady(1L, this)
        runCurrent()
        assertEquals(1, ble.closeValveCount)

        advanceTimeBy(61_000)
        runCurrent()

        assertEquals(1, ble.openValveCount)
        assertEquals(2, log.snapshot().size)
    }

    @Test
    fun manualAdjustmentSkipsDelayedPreferredApply() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(device)
        ble.valveState.value = ValveState.Open
        ble.closeResult = CompletableDeferred(CommandResult.Success(ValveState.Closed))
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))

        val quiet = QuietStartSettings(
            enabled = true,
            daysOfWeek = setOf(0, 1, 2, 3, 4, 5, 6),
            windowStartMinute = 0,
            windowEndMinute = 24 * 60 - 1,
            holdClosedMinutes = 1,
        )
        val log = InMemoryLog()
        val engine = engine(
            ble = ble,
            settings = FakeSettingsStore(SoundKitSettings(quietStart = quiet)),
            log = log,
        )

        engine.onConnectReady(1L, this)
        runCurrent()
        engine.onUserValveAdjustment()
        advanceTimeBy(61_000)
        runCurrent()

        assertEquals(0, ble.openValveCount)
    }

    @Test
    fun duplicateConnectReadySessionIsNoOp() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(device)
        ble.valveState.value = ValveState.Open
        ble.closeResult = CompletableDeferred(CommandResult.Success(ValveState.Closed))
        val quiet = QuietStartSettings(
            enabled = true,
            daysOfWeek = setOf(0, 1, 2, 3, 4, 5, 6),
            windowStartMinute = 0,
            windowEndMinute = 24 * 60 - 1,
            holdClosedMinutes = 5,
        )
        val log = InMemoryLog()
        val engine = engine(
            ble = ble,
            settings = FakeSettingsStore(SoundKitSettings(quietStart = quiet)),
            log = log,
        )

        engine.onConnectReady(1L, this)
        runCurrent()
        engine.onConnectReady(1L, this)
        runCurrent()

        assertEquals(1, ble.closeValveCount)
    }

    @Test
    fun quietHoldPublishesAnEndTimeAndClearsItWhenTheHoldEnds() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(device)
        ble.valveState.value = ValveState.Open
        ble.closeResult = CompletableDeferred(CommandResult.Success(ValveState.Closed))
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))
        val settings = FakeSettingsStore(SoundKitSettings(quietStart = allDayQuiet(holdMinutes = 1)))
        val engine = engine(ble = ble, settings = settings)

        engine.onConnectReady(1L, this)
        runCurrent()

        assertEquals(clock.now + 60_000L, engine.quietHoldEndsAt.value)
        assertEquals(clock.now + 60_000L, settings.settings.value.quietHoldEndsAtMillis)

        advanceTimeBy(61_000)
        runCurrent()

        assertEquals(null, engine.quietHoldEndsAt.value)
        assertEquals(0L, settings.settings.value.quietHoldEndsAtMillis)
        assertEquals(1, ble.openValveCount)
    }

    @Test
    fun manualAdjustmentClearsTheQuietHoldEnd() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(device)
        ble.valveState.value = ValveState.Open
        ble.closeResult = CompletableDeferred(CommandResult.Success(ValveState.Closed))
        val engine = engine(
            ble = ble,
            settings = FakeSettingsStore(SoundKitSettings(quietStart = allDayQuiet(holdMinutes = 5))),
        )

        engine.onConnectReady(1L, this)
        runCurrent()
        engine.onUserValveAdjustment()

        assertEquals(null, engine.quietHoldEndsAt.value)
    }

    @Test
    fun persistedQuietHoldResumesTheRemainderWithoutClosingAgain() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(device)
        ble.valveState.value = ValveState.Closed
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))
        val settings = FakeSettingsStore(
            SoundKitSettings(
                quietStart = allDayQuiet(holdMinutes = 5),
                quietHoldEndsAtMillis = clock.now + 30_000L,
            ),
        )
        val engine = engine(ble = ble, settings = settings)

        engine.onConnectReady(1L, this)
        runCurrent()
        assertEquals(0, ble.closeValveCount)
        assertEquals(clock.now + 30_000L, engine.quietHoldEndsAt.value)

        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(1, ble.openValveCount)
        assertEquals(null, engine.quietHoldEndsAt.value)
    }

    @Test
    fun reconnectInTheSameDriveKeepsTheManualClose() = runTest(mainDispatcherRule.dispatcher) {
        val ble = openedOnConnect()
        val engine = engine(ble = ble)
        engine.onConnectReady(1L, this)
        runCurrent()
        assertEquals(1, ble.openValveCount)

        engine.onUserValveAdjustment()
        ble.valveState.value = ValveState.Closed
        engine.onDisconnect()
        clock.now += 30_000
        engine.onConnectReady(2L, this)
        runCurrent()

        assertEquals(1, ble.openValveCount)
    }

    @Test
    fun reconnectInTheSameDriveDoesNotReapplyDriveMode() = runTest(mainDispatcherRule.dispatcher) {
        val ble = openedOnConnect()
        val engine = engine(ble = ble)
        engine.onConnectReady(1L, this)
        runCurrent()

        ble.valveState.value = ValveState.Closed
        engine.onDisconnect()
        clock.now += 30_000
        engine.onConnectReady(2L, this)
        runCurrent()

        assertEquals(1, ble.openValveCount)
    }

    @Test
    fun leavingTheCarStartsANewDrive() = runTest(mainDispatcherRule.dispatcher) {
        carPresence.presence.value = CarPresence(bluetoothConnected = true)
        val ble = openedOnConnect()
        val engine = engine(ble = ble)
        runCurrent()
        engine.onConnectReady(1L, this)
        runCurrent()
        engine.onUserValveAdjustment()
        ble.valveState.value = ValveState.Closed
        engine.onDisconnect()

        carPresence.presence.value = CarPresence()
        runCurrent()
        carPresence.presence.value = CarPresence(bluetoothConnected = true)
        runCurrent()
        engine.onConnectReady(2L, this)
        runCurrent()

        assertEquals(2, ble.openValveCount)
    }

    @Test
    fun aLongDisconnectStartsANewDrive() = runTest(mainDispatcherRule.dispatcher) {
        val ble = openedOnConnect()
        val engine = engine(ble = ble)
        engine.onConnectReady(1L, this)
        runCurrent()
        engine.onUserValveAdjustment()
        ble.valveState.value = ValveState.Closed
        engine.onDisconnect()

        clock.now += DriveModeEngine.SAME_DRIVE_GAP_MS + 1
        engine.onConnectReady(2L, this)
        runCurrent()

        assertEquals(2, ble.openValveCount)
    }

    @Test
    fun reconnectDuringAQuietHoldResumesTheRemainder() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(device)
        ble.valveState.value = ValveState.Open
        ble.closeResult = CompletableDeferred(CommandResult.Success(ValveState.Closed))
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))
        val engine = engine(
            ble = ble,
            settings = FakeSettingsStore(SoundKitSettings(quietStart = allDayQuiet(holdMinutes = 5))),
        )
        engine.onConnectReady(1L, this)
        runCurrent()
        val endsAt = engine.quietHoldEndsAt.value
        assertEquals(1, ble.closeValveCount)

        engine.onDisconnect()
        clock.now += 60_000
        engine.onConnectReady(2L, this)
        runCurrent()

        assertEquals(1, ble.closeValveCount)
        assertEquals(endsAt, engine.quietHoldEndsAt.value)
        assertEquals(0, ble.openValveCount)
    }

    private fun openedOnConnect(): FakeBleRepository {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Closed
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))
        return ble
    }

    private fun allDayQuiet(holdMinutes: Int) = QuietStartSettings(
        enabled = true,
        daysOfWeek = setOf(0, 1, 2, 3, 4, 5, 6),
        windowStartMinute = 0,
        windowEndMinute = 24 * 60 - 1,
        holdClosedMinutes = holdMinutes,
    )

    private fun engine(
        ble: FakeBleRepository = FakeBleRepository(),
        settings: FakeSettingsStore = FakeSettingsStore(),
        log: InMemoryLog = InMemoryLog(),
    ): DriveModeEngine {
        return DriveModeEngine(
            bleRepository = ble,
            settingsStore = settings,
            executionLog = log,
            diagnosticsRepository = DiagnosticsRepository(),
            valveCommandCoordinator = ValveCommandCoordinator(ble),
            clock = clock,
            carPresence = carPresence,
        )
    }

    private class InMemoryLog : RuleExecutionLogStore {
        private val stored = MutableStateFlow<List<RuleExecutionEntry>>(emptyList())
        override val entries: Flow<List<RuleExecutionEntry>> = stored
        override val lastExecution = MutableStateFlow<RuleExecutionEntry?>(null)

        fun snapshot(): List<RuleExecutionEntry> = stored.value

        override suspend fun append(entry: RuleExecutionEntry) {
            stored.value = stored.value + entry
            lastExecution.value = entry
        }

        override suspend fun clear() {
            stored.value = emptyList()
            lastExecution.value = null
        }
    }
}
