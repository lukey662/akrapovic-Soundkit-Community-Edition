package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.ble.RetryPolicy
import com.akrapovic.soundkit.community.car.CarSessionTracker
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ConnectionYieldState
import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.test.FakeBleConnectionGateway
import com.akrapovic.soundkit.community.test.FakeBleScannerGateway
import com.akrapovic.soundkit.community.test.FakeSettingsStore
import com.akrapovic.soundkit.community.test.MainDispatcherRule
import com.akrapovic.soundkit.community.test.testDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private fun repository(
        carSessionTracker: CarSessionTracker = CarSessionTracker(),
    ) = BleRepositoryImpl(
        scanner = scanner,
        connectionManager = connection,
        settingsRepository = settings,
        diagnosticsRepository = diagnostics,
        retryPolicy = retryPolicy,
        carSessionTracker = carSessionTracker,
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
        val repository = BleRepositoryImpl(
            scanner = scanner,
            connectionManager = connection,
            settingsRepository = FakeSettingsStore(settings.settings.value.copy(autoReconnect = false)),
            diagnosticsRepository = diagnostics,
            retryPolicy = retryPolicy,
            carSessionTracker = CarSessionTracker(),
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
        val repository = BleRepositoryImpl(
            scanner = scanner,
            connectionManager = connection,
            settingsRepository = FakeSettingsStore(settings.settings.value.copy(autoReconnect = false)),
            diagnosticsRepository = diagnostics,
            retryPolicy = retryPolicy,
            carSessionTracker = CarSessionTracker(),
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
        val repository = BleRepositoryImpl(
            scanner = scanner,
            connectionManager = connection,
            settingsRepository = settings,
            diagnosticsRepository = diagnostics,
            retryPolicy = cappedPolicy,
            carSessionTracker = CarSessionTracker(),
        )
        val device = testDevice()
        connection.connectResults = MutableList(10) { Result.failure(IllegalStateException("offline")) }

        repository.connect(device)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, connection.reconnectGaveUpMessages.size)
        assertEquals(BleRepositoryImpl.RECONNECT_GAVE_UP_MESSAGE, connection.reconnectGaveUpMessages.single())
        assertTrue(connection.connectionState.value is ConnectionState.Error)
        assertEquals(2, connection.reconnectMarks.size)
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

