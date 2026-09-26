package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.ble.RetryPolicy
import com.akrapovic.soundkit.community.car.CarSessionTracker
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ConnectionYieldState
import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.domain.AwayReason
import com.akrapovic.soundkit.community.car.CarPresence
import com.akrapovic.soundkit.community.test.FakeBleConnectionGateway
import com.akrapovic.soundkit.community.test.FakeBleScannerGateway
import com.akrapovic.soundkit.community.test.FakeCarPresenceSource
import com.akrapovic.soundkit.community.test.FakeSettingsStore
import com.akrapovic.soundkit.community.test.FixedClock
import com.akrapovic.soundkit.community.test.MainDispatcherRule
import com.akrapovic.soundkit.community.test.testDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class BleRepositoryImplTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val scanner = FakeBleScannerGateway()
    private val connection = FakeBleConnectionGateway()
    private val settings = FakeSettingsStore()
    private val diagnostics = DiagnosticsRepository()
    private val retryPolicy = RetryPolicy(initialDelayMs = 1_000, maxDelayMs = 5_000, maxAttempts = 8)
    private val carPresence = FakeCarPresenceSource()
    private val clock = FixedClock()

    private fun repository(
        carSessionTracker: CarSessionTracker = CarSessionTracker(),
        settingsRepository: FakeSettingsStore = settings,
        retry: RetryPolicy = retryPolicy,
    ) = BleRepositoryImpl(
        scanner = scanner,
        connectionManager = connection,
        settingsRepository = settingsRepository,
        diagnosticsRepository = diagnostics,
        retryPolicy = retry,
        carSessionTracker = carSessionTracker,
        carPresence = carPresence,
        clock = clock,
    )

    @Test
    fun scanStartsStopsAndSortsLikelyDevicesFirst() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val unrelated = testDevice(
            name = "Kitchen Light",
            address = "AA:AA:AA:AA:AA:AA",
            rssi = -20,
            isLikelySoundKit = false,
        )
        val soundKit = testDevice(rssi = -80, isLikelySoundKit = true)

        repository.startScan()
        runCurrent()
        scanner.emissions.emit(listOf(unrelated, soundKit))
        runCurrent()

        assertEquals(1, scanner.scanCollectionCount)
        assertTrue(repository.isScanning.value)
        assertEquals(soundKit, repository.discoveredDevices.value.first())

        repository.stopScan()

        assertEquals(false, repository.isScanning.value)
    }

    @Test
    fun connectRemembersSelectedReceiverAndStopsScan() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val device = testDevice()
        repository.startScan()
        runCurrent()

        repository.connect(device)
        runCurrent()

        assertEquals(listOf(device), settings.rememberedDevices)
        assertEquals(listOf(device), connection.connectedDevices)
        assertEquals(false, repository.isScanning.value)
    }

    @Test
    fun connectToAlreadyConnectedReceiverDoesNotReconnectGatt() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val device = testDevice()

        repository.connect(device)
        connection.connectionState.value = ConnectionState.Connected(device)
        runCurrent()
        repository.connect(device)
        runCurrent()

        assertEquals(listOf(device), connection.connectedDevices)
        assertTrue(diagnostics.entries.value.any { it.message.contains("Already connected") })
    }

    @Test
    fun deliberateConnectionReplacementDoesNotScheduleAutoReconnect() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val firstDevice = testDevice(address = "00:11:22:33:44:55")
        val secondDevice = testDevice(address = "66:77:88:99:AA:BB")

        repository.connect(firstDevice)
        connection.connectionState.value = ConnectionState.Connected(firstDevice)
        runCurrent()
        repository.connect(secondDevice)
        connection.connectionState.value = ConnectionState.Disconnected
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(connection.reconnectMarks.isEmpty())
    }

    @Test
    fun disconnectCancelsPendingReconnect() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val device = testDevice()
        connection.connectResults = mutableListOf(Result.failure(IllegalStateException("radio busy")))

        repository.connect(device)
        repository.disconnect()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(1, connection.connectedDevices.size)
        assertEquals(1, connection.disconnectCount)
        assertTrue(connection.reconnectMarks.isEmpty())
    }

    @Test
    fun initialRecoverableConnectionFailureSchedulesReconnect() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val device = testDevice()
        connection.connectResults = mutableListOf(
            Result.failure(IllegalStateException("radio busy")),
            Result.success(Unit),
        )

        repository.connect(device)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(2, connection.connectedDevices.size)
        assertEquals(1, connection.reconnectMarks.size)
    }

    @Test
    fun autoReconnectDisabledSkipsInitialConnectionFailureRetry() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository(
            settingsRepository = FakeSettingsStore(settings.settings.value.copy(autoReconnect = false)),
        )
        runCurrent()
        val device = testDevice()
        connection.connectResults = mutableListOf(Result.failure(IllegalStateException("radio busy")))

        repository.connect(device)
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(connection.reconnectMarks.isEmpty())
    }

    @Test
    fun autoReconnectDisabledPreventsReconnectAfterStableConnectionDrops() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository(
            settingsRepository = FakeSettingsStore(settings.settings.value.copy(autoReconnect = false)),
        )
        val device = testDevice()

        repository.connect(device)
        connection.connectionState.value = ConnectionState.Connected(device)
        runCurrent()
        connection.connectionState.value = ConnectionState.Error("link loss", recoverable = true)
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(connection.reconnectMarks.isEmpty())
    }

    @Test
    fun receiverNotReadyWhileConnectedDoesNotScheduleReconnect() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val device = testDevice()

        repository.connect(device)
        connection.connectionState.value = ConnectionState.Connected(device)
        connection.receiverStatusMessage.value = "Receiver isn't ready"
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(connection.reconnectMarks.isEmpty())
    }

    @Test
    fun openValveFailureIsReturnedAndLogged() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        connection.writeResult = CommandResult.Failure("protocol not verified", recoverable = false)

        val result = repository.openValve()

        assertEquals(listOf(ValveCommand.Open), connection.writtenCommands)
        assertTrue(result is CommandResult.Failure)
        assertTrue(diagnostics.entries.value.any { it.message.contains("OPEN command failed") })
    }

    @Test
    fun autoReconnectStopsAfterMaxAttempts() = runTest(mainDispatcherRule.dispatcher) {
        val cappedPolicy = RetryPolicy(initialDelayMs = 100, maxDelayMs = 100, maxAttempts = 3)
        val repository = repository(retry = cappedPolicy)
        val device = testDevice()
        connection.connectResults = MutableList(10) { Result.failure(IllegalStateException("offline")) }

        repository.connect(device)
        advanceTimeBy(1_000)
        runCurrent()

        val away = connection.connectionState.value
        assertTrue(away is ConnectionState.Away)
        assertEquals(AwayReason.LeftCar, (away as ConnectionState.Away).reason)
        assertEquals(clock.now, away.sinceMillis)
        assertEquals(1, connection.awayMarks.size)
        assertEquals(2, connection.reconnectMarks.size)
    }

    @Test
    fun leavingCarMidReconnectBecomesAwayInsteadOfStayingReconnecting() = runTest(mainDispatcherRule.dispatcher) {
        val tracker = CarSessionTracker()
        tracker.beginSession()
        val repository = repository(
            carSessionTracker = tracker,
            retry = RetryPolicy(initialDelayMs = 100, maxDelayMs = 100, maxAttempts = 8),
        )
        val device = testDevice()

        repository.connect(device, userInitiated = false)
        connection.connectionState.value = ConnectionState.Connected(device)
        runCurrent()
        connection.connectResults = mutableListOf(Result.failure(IllegalStateException("offline")))
        connection.connectionState.value = ConnectionState.Disconnected
        advanceTimeBy(100)
        runCurrent()
        assertTrue(connection.connectionState.value is ConnectionState.Reconnecting)

        tracker.endSession()
        advanceTimeBy(100)
        runCurrent()

        val away = connection.connectionState.value
        assertTrue(away is ConnectionState.Away)
        assertEquals(AwayReason.LeftCar, (away as ConnectionState.Away).reason)
    }

    @Test
    fun linkLossOutsideTheCarBecomesAwayWithoutAReconnectBurst() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val device = testDevice()
        connection.connectResults = mutableListOf(Result.failure(IllegalStateException("out of range")))

        repository.connect(device, userInitiated = false)
        runCurrent()

        val away = connection.connectionState.value
        assertTrue(away is ConnectionState.Away)
        assertEquals(AwayReason.LeftCar, (away as ConnectionState.Away).reason)
        assertTrue(connection.reconnectMarks.isEmpty())
    }

    @Test
    fun leavingCarDuringReconnectDelayBecomesAwayImmediately() = runTest(mainDispatcherRule.dispatcher) {
        carPresence.presence.value = CarPresence(bluetoothConnected = true)
        val repository = repository(
            retry = RetryPolicy(initialDelayMs = 5_000, maxDelayMs = 5_000, maxAttempts = 8),
        )
        val device = testDevice()
        repository.connect(device, userInitiated = false)
        connection.connectionState.value = ConnectionState.Connected(device)
        runCurrent()
        connection.connectResults = mutableListOf(Result.failure(IllegalStateException("offline")))
        connection.connectionState.value = ConnectionState.Disconnected
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(connection.connectionState.value is ConnectionState.Reconnecting)

        carPresence.presence.value = CarPresence()
        runCurrent()

        val away = connection.connectionState.value
        assertTrue(away is ConnectionState.Away)
        assertEquals(AwayReason.LeftCar, (away as ConnectionState.Away).reason)
    }

    @Test
    fun leavingDuringReconnectBackoffBecomesAwayBeforeTheDelayEnds() = runTest(mainDispatcherRule.dispatcher) {
        val tracker = CarSessionTracker()
        tracker.beginSession()
        val repository = repository(
            carSessionTracker = tracker,
            retry = RetryPolicy(initialDelayMs = 5_000, maxDelayMs = 5_000, maxAttempts = 8),
        )
        val device = testDevice()
        repository.connect(device, userInitiated = false)
        connection.connectionState.value = ConnectionState.Connected(device)
        runCurrent()
        connection.connectResults = mutableListOf(Result.failure(IllegalStateException("offline")))
        connection.connectionState.value = ConnectionState.Disconnected
        runCurrent()
        assertTrue(connection.connectionState.value is ConnectionState.Reconnecting)

        tracker.endSession()
        runCurrent()

        val away = connection.connectionState.value
        assertTrue(away is ConnectionState.Away)
        assertEquals(AwayReason.LeftCar, (away as ConnectionState.Away).reason)
    }

    @Test
    fun endingCarSessionDuringReconnectBecomesAwayImmediately() = runTest(mainDispatcherRule.dispatcher) {
        val tracker = CarSessionTracker()
        tracker.beginSession()
        val repository = repository(
            carSessionTracker = tracker,
            retry = RetryPolicy(initialDelayMs = 5_000, maxDelayMs = 5_000, maxAttempts = 8),
        )
        val device = testDevice()
        repository.connect(device, userInitiated = false)
        connection.connectionState.value = ConnectionState.Connected(device)
        runCurrent()
        connection.connectResults = mutableListOf(Result.failure(IllegalStateException("offline")))
        connection.connectionState.value = ConnectionState.Disconnected
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(connection.connectionState.value is ConnectionState.Reconnecting)

        tracker.endSession()
        runCurrent()

        val away = connection.connectionState.value
        assertTrue(away is ConnectionState.Away)
        assertEquals(AwayReason.LeftCar, (away as ConnectionState.Away).reason)
    }

    @Test
    fun leavingCarWhileConnectAttemptIsInFlightBecomesAway() = runTest(mainDispatcherRule.dispatcher) {
        carPresence.presence.value = CarPresence(bluetoothConnected = true)
        val repository = repository(
            retry = RetryPolicy(initialDelayMs = 100, maxDelayMs = 100, maxAttempts = 8),
        )
        val device = testDevice()
        repository.connect(device, userInitiated = false)
        connection.connectionState.value = ConnectionState.Connected(device)
        runCurrent()
        connection.connectResults = mutableListOf(Result.success(Unit))
        connection.connectionState.value = ConnectionState.Disconnected
        advanceTimeBy(100)
        runCurrent()
        assertTrue(connection.connectionState.value is ConnectionState.Connecting)

        carPresence.presence.value = CarPresence()
        runCurrent()

        val away = connection.connectionState.value
        assertTrue(away is ConnectionState.Away)
        assertEquals(AwayReason.LeftCar, (away as ConnectionState.Away).reason)
    }

    @Test
    fun stoppingPeriodicScanClearsScanningFlag() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        settings.settings.value = settings.settings.value.copy(
            savedReceivers = listOf(
                com.akrapovic.soundkit.community.domain.SavedReceiver(device.address, device.name, isDefault = true),
            ),
            periodicScanWhenAway = true,
            periodicScanPromptAnswered = true,
        )
        val repository = repository(
            retry = RetryPolicy(initialDelayMs = 100, maxDelayMs = 100, maxAttempts = 8),
        )
        runCurrent()
        connection.connectionState.value = ConnectionState.Away(clock.now, AwayReason.LeftCar)
        runCurrent()
        assertTrue(repository.isScanning.value)

        settings.setPeriodicScanWhenAway(false)
        runCurrent()

        assertFalse(repository.isScanning.value)
    }

    @Test
    fun startupAlreadyInCarReconnectsPersistedAway() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        settings.settings.value = settings.settings.value.copy(
            savedReceivers = listOf(
                com.akrapovic.soundkit.community.domain.SavedReceiver(device.address, device.name, isDefault = true),
            ),
            awaySinceMillis = clock.now,
            awayReason = AwayReason.LeftCar,
        )
        carPresence.presence.value = CarPresence(bluetoothConnected = true)
        repository()
        runCurrent()

        assertEquals(device.address, connection.connectedDevices.single().address)
    }

    @Test
    fun startupAlreadyInCarDoesNotConnectWithoutPersistedAway() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        settings.settings.value = settings.settings.value.copy(
            savedReceivers = listOf(
                com.akrapovic.soundkit.community.domain.SavedReceiver(device.address, device.name, isDefault = true),
            ),
        )
        carPresence.presence.value = CarPresence(bluetoothConnected = true)
        repository()
        runCurrent()

        assertTrue(connection.connectedDevices.isEmpty())
    }

    @Test
    fun returnToCarConnectsOnceAfterGivingUp() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        settings.settings.value = settings.settings.value.copy(
            savedReceivers = listOf(
                com.akrapovic.soundkit.community.domain.SavedReceiver(device.address, device.name, isDefault = true),
            ),
            onboardingCompletedAt = 1L,
        )
        val repository = repository()
        runCurrent()
        connection.connectionState.value = ConnectionState.Away(clock.now, AwayReason.LeftCar)
        runCurrent()

        carPresence.presence.value = CarPresence(bluetoothConnected = true)
        runCurrent()

        assertEquals(device.address, connection.connectedDevices.single().address)
    }

    @Test
    fun userDisconnectDoesNotReconnectWhenCarReturns() = runTest(mainDispatcherRule.dispatcher) {
        val device = testDevice()
        settings.settings.value = settings.settings.value.copy(
            savedReceivers = listOf(
                com.akrapovic.soundkit.community.domain.SavedReceiver(device.address, device.name, isDefault = true),
            ),
        )
        val repository = repository()
        repository.connect(device)
        connection.connectionState.value = ConnectionState.Connected(device)
        runCurrent()
        repository.disconnect()
        runCurrent()

        carPresence.presence.value = CarPresence(projectionConnected = true)
        runCurrent()

        assertEquals(1, connection.connectedDevices.size)
    }

    @Test
    fun secondaryPhoneYieldsAfterQuickDropWithoutManualControl() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository()
        val device = testDevice()

        repository.connect(device, userInitiated = false)
        connection.connectionState.value = ConnectionState.Connected(device)
        runCurrent()
        connection.connectionState.value = ConnectionState.Disconnected
        runCurrent()

        assertTrue(repository.connectionYieldState.value is ConnectionYieldState.Yielded)
        assertTrue(connection.reconnectMarks.isEmpty())
    }

    @Test
    fun carSessionPrimaryReconnectsAfterDrop() = runTest(mainDispatcherRule.dispatcher) {
        val tracker = CarSessionTracker()
        tracker.beginSession()
        val repository = repository(carSessionTracker = tracker)
        val device = testDevice()

        repository.connect(device, userInitiated = false)
        connection.connectionState.value = ConnectionState.Connected(device)
        runCurrent()
        connection.connectionState.value = ConnectionState.Disconnected
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(repository.connectionYieldState.value is ConnectionYieldState.None)
        assertTrue(connection.reconnectMarks.isNotEmpty())
    }
}

