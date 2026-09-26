package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.ble.BleConnectionGateway
import com.akrapovic.soundkit.community.ble.BleScannerGateway
import com.akrapovic.soundkit.community.ble.RetryPolicy
import com.akrapovic.soundkit.community.ble.ScanDuty
import com.akrapovic.soundkit.community.car.CarPresenceSource
import com.akrapovic.soundkit.community.car.CarSessionTracker
import com.akrapovic.soundkit.community.domain.AwayReason
import com.akrapovic.soundkit.community.domain.BleContentionDetector
import com.akrapovic.soundkit.community.domain.BleTimeouts
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionPriorityPolicy
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ConnectionYieldReason
import com.akrapovic.soundkit.community.domain.ConnectionYieldState
import com.akrapovic.soundkit.community.domain.RememberedDeviceConnector
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.domain.WallClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

interface BleRepository {
    val discoveredDevices: StateFlow<List<SoundKitDevice>>
    val connectionState: StateFlow<ConnectionState>
    val valveState: StateFlow<ValveState>
    val receiverStatusMessage: StateFlow<String?>
    val connectionYieldState: StateFlow<ConnectionYieldState>
    val isScanning: StateFlow<Boolean>

    fun startScan()
    fun stopScan()
    suspend fun connect(device: SoundKitDevice, userInitiated: Boolean = true)
    suspend fun takeControl(device: SoundKitDevice)
    suspend fun disconnect()
    suspend fun openValve(): CommandResult
    suspend fun closeValve(): CommandResult
}

@Singleton
class BleRepositoryImpl @Inject constructor(
    private val scanner: BleScannerGateway,
    private val connectionManager: BleConnectionGateway,
    private val settingsRepository: SettingsStore,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val retryPolicy: RetryPolicy,
    private val carSessionTracker: CarSessionTracker,
    private val carPresence: CarPresenceSource,
    private val clock: WallClock,
) : BleRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scanJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var awayScanJob: Job? = null
    private var lastRequestedDevice: SoundKitDevice? = null
    private var currentSettings: SoundKitSettings = SoundKitSettings()
    private var hadStableConnection: Boolean = false
    private var suppressNextAutoReconnect: Boolean = false
    private var suppressReturnConnect: Boolean = false
    private var userRequestedControl: Boolean = false
    private var reconnectAttempt: Int = 0
    private val contentionDetector = BleContentionDetector()

    private val _discoveredDevices = MutableStateFlow<List<SoundKitDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<SoundKitDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionYieldState = MutableStateFlow<ConnectionYieldState>(ConnectionYieldState.None)
    override val connectionYieldState: StateFlow<ConnectionYieldState> = _connectionYieldState.asStateFlow()

    override val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    override val valveState: StateFlow<ValveState> = connectionManager.valveState
    override val receiverStatusMessage: StateFlow<String?> = connectionManager.receiverStatusMessage

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                val wasPeriodic = currentSettings.periodicScanWhenAway
                currentSettings = settings
                diagnosticsRepository.debugLoggingEnabled = settings.debugLoggingEnabled
                if (settings.periodicScanWhenAway && connectionState.value is ConnectionState.Away) {
                    startAwayScan()
                } else if (wasPeriodic && !settings.periodicScanWhenAway) {
                    awayScanJob?.cancel()
                    awayScanJob = null
                }
            }
        }
        scope.launch {
            val settings = settingsRepository.settings.first()
            currentSettings = settings
            val restoredAway = settings.awaySinceMillis > 0L &&
                connectionState.value == ConnectionState.Disconnected
            if (restoredAway) {
                connectionManager.markAway(
                    settings.awaySinceMillis,
                    settings.awayReason ?: AwayReason.LeftCar,
                )
            }
            var wasInCar = carPresence.presence.value.orSession(carSessionTracker.isCarSessionActive.value)
            // Process start has no false→true edge when the phone is already on the car link.
            // A persisted away session is the "we lost the receiver" case that should connect once.
            if (wasInCar && restoredAway) {
                onReturnedToCar()
            }
            combine(
                carPresence.presence,
                carSessionTracker.isCarSessionActive,
            ) { presence, sessionActive ->
                presence.orSession(sessionActive)
            }.collect { inCar ->
                if (!wasInCar && inCar) {
                    onReturnedToCar()
                } else if (wasInCar && !inCar) {
                    onLeftCar()
                }
                wasInCar = inCar
            }
        }
        scope.launch {
            connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        suppressNextAutoReconnect = false
                        hadStableConnection = true
                        reconnectAttempt = 0
                        contentionDetector.onConnected()
                        awayScanJob?.cancel()
                        awayScanJob = null
                        settingsRepository.clearAwaySession()
                    }
                    is ConnectionState.Error -> {
                        val device = lastRequestedDevice
                        if (suppressNextAutoReconnect) {
                            suppressNextAutoReconnect = false
                            hadStableConnection = false
                            diagnosticsRepository.debug("Suppressing auto reconnect during deliberate connection transition")
                        } else if (state.recoverable) {
                            handleConnectionLoss(
                                device = device,
                                userInitiated = false,
                                connectFailed = true,
                            )
                        } else {
                            hadStableConnection = false
                        }
                    }
                    is ConnectionState.Away -> {
                        hadStableConnection = false
                        if (currentSettings.periodicScanWhenAway) {
                            startAwayScan()
                        }
                    }
                    ConnectionState.Disconnected -> {
                        val device = lastRequestedDevice
                        if (suppressNextAutoReconnect) {
                            suppressNextAutoReconnect = false
                            hadStableConnection = false
                            diagnosticsRepository.debug("Suppressing auto reconnect after deliberate disconnect")
                        } else if (hadStableConnection) {
                            handleConnectionLoss(
                                device = device,
                                userInitiated = false,
                                connectFailed = false,
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun startScan() {
        awayScanJob?.cancel()
        awayScanJob = null
        if (scanJob?.isActive == true) return
        scanTimeoutJob?.cancel()
        scanJob = scope.launch {
            _isScanning.value = true
            scanner.scan()
                .catch { error ->
                    diagnosticsRepository.error("Scan stopped: ${error.message}", error)
                    _isScanning.value = false
                }
                .collect { devices ->
                    _discoveredDevices.value = devices.sortedWith(
                        compareByDescending<SoundKitDevice> { it.isLikelySoundKit }
                            .thenByDescending { it.rssi ?: Int.MIN_VALUE },
                    )
                }
        }
        scanTimeoutJob = scope.launch {
            delay(BleTimeouts.ACTIVE_SCAN_MS)
            if (_isScanning.value) {
                diagnosticsRepository.info("Scan timed out after ${BleTimeouts.ACTIVE_SCAN_MS}ms")
                stopScan()
            }
        }
    }

    override fun stopScan() {
        stopScanning(resumeAwayScan = true)
    }

    private fun stopScanning(resumeAwayScan: Boolean) {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        scanJob?.cancel()
        scanJob = null
        val shouldResumeAway = resumeAwayScan &&
            awayScanJob?.isActive == true &&
            connectionState.value is ConnectionState.Away &&
            currentSettings.periodicScanWhenAway
        awayScanJob?.cancel()
        awayScanJob = null
        _isScanning.value = false
        if (shouldResumeAway) {
            scope.launch {
                delay(BleTimeouts.AWAY_SCAN_GAP_MS)
                if (connectionState.value is ConnectionState.Away && currentSettings.periodicScanWhenAway) {
                    startAwayScan()
                }
            }
        }
    }

    override suspend fun connect(device: SoundKitDevice, userInitiated: Boolean) {
        connectInternal(device, userInitiated = userInitiated, clearYield = userInitiated)
    }

    override suspend fun takeControl(device: SoundKitDevice) {
        suppressReturnConnect = false
        connectInternal(device, userInitiated = true, clearYield = true)
    }

    override suspend fun disconnect() {
        reconnectJob?.cancel()
        awayScanJob?.cancel()
        awayScanJob = null
        reconnectAttempt = 0
        suppressNextAutoReconnect = true
        suppressReturnConnect = true
        hadStableConnection = false
        userRequestedControl = false
        contentionDetector.reset()
        lastRequestedDevice = null
        _connectionYieldState.value = ConnectionYieldState.None
        settingsRepository.clearAwaySession()
        connectionManager.disconnect()
    }

    override suspend fun openValve(): CommandResult {
        return connectionManager.writeCommand(ValveCommand.Open).also { logCommandResult("OPEN", it) }
    }

    override suspend fun closeValve(): CommandResult {
        return connectionManager.writeCommand(ValveCommand.Close).also { logCommandResult("CLOSE", it) }
    }

    private fun onReturnedToCar() {
        if (suppressReturnConnect) {
            diagnosticsRepository.debug("Return connect suppressed after a user disconnect")
            return
        }
        if (!ConnectionPriorityPolicy.shouldReconnectOnReturn(
                settings = currentSettings,
                connectionState = connectionState.value,
                yieldState = _connectionYieldState.value,
            )
        ) {
            return
        }
        val device = RememberedDeviceConnector.defaultDevice(currentSettings) ?: return
        diagnosticsRepository.info("Car link returned; connecting to ${device.name}")
        scope.launch { connect(device, userInitiated = false) }
    }

    private fun onLeftCar() {
        when (val state = connectionState.value) {
            is ConnectionState.Away -> {
                if (state.reason == AwayReason.LeftCar) return
                scope.launch { enterAway("Car link dropped") }
            }
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting,
            -> {
                reconnectJob?.cancel()
                // GATT callbacks must not revive a reconnect after the phone has left.
                suppressNextAutoReconnect = true
                scope.launch {
                    connectionManager.disconnect()
                    enterAway("Car link dropped during connect")
                }
            }
            else -> {
                if (state !is ConnectionState.Connected && reconnectJob?.isActive == true) {
                    reconnectJob?.cancel()
                    suppressNextAutoReconnect = true
                    scope.launch { enterAway("Car link dropped during reconnect") }
                }
            }
        }
    }

    private suspend fun connectInternal(
        device: SoundKitDevice,
        userInitiated: Boolean,
        clearYield: Boolean,
    ) {
        awayScanJob?.cancel()
        awayScanJob = null
        stopScanning(resumeAwayScan = false)
        settingsRepository.rememberDevice(device)
        if (connectionState.value.isActiveFor(device)) {
            lastRequestedDevice = device
            if (userInitiated) {
                userRequestedControl = true
                suppressReturnConnect = false
            }
            diagnosticsRepository.info("Already connected or connecting to ${device.name}")
            return
        }
        if (connectionState.value.hasDifferentActiveDevice(device)) {
            suppressNextAutoReconnect = true
        }
        lastRequestedDevice = device
        reconnectJob?.cancel()
        reconnectAttempt = 0
        if (clearYield) {
            _connectionYieldState.value = ConnectionYieldState.None
            contentionDetector.reset()
        }
        if (userInitiated) {
            userRequestedControl = true
            suppressReturnConnect = false
            diagnosticsRepository.info("User requested connection to ${device.name}")
        } else {
            diagnosticsRepository.info("Auto requested connection to ${device.name}")
        }
        connectionManager.connect(device).onFailure { error ->
            suppressNextAutoReconnect = false
            diagnosticsRepository.error("Initial connection failed: ${error.message}", error)
            maybeYieldOnContention(signal = contentionDetector.onConnectFailed())
            maybeScheduleReconnect(device)
        }
    }

    private fun handleConnectionLoss(
        device: SoundKitDevice?,
        userInitiated: Boolean,
        connectFailed: Boolean,
    ) {
        hadStableConnection = false
        val signal = if (connectFailed) {
            contentionDetector.onConnectFailed()
        } else {
            contentionDetector.onDisconnected(userInitiated)
        }
        maybeYieldOnContention(signal = signal)
        maybeScheduleReconnect(device)
    }

    private fun maybeYieldOnContention(
        signal: BleContentionDetector.ContentionSignal?,
    ) {
        if (signal == null) return
        if (userRequestedControl && signal == BleContentionDetector.ContentionSignal.ConnectStorm) return
        if (!ConnectionPriorityPolicy.shouldEnterYieldOnContention(
                currentSettings,
                phoneIsInCar(),
            )
        ) {
            return
        }
        reconnectJob?.cancel()
        reconnectAttempt = 0
        diagnosticsRepository.warning(
            "BLE contention detected ($signal); yielding until user takes control",
        )
        _connectionYieldState.value = ConnectionYieldState.Yielded(ConnectionYieldReason.HeadUnitMayBeActive)
    }

    private fun maybeScheduleReconnect(device: SoundKitDevice?) {
        if (device == null) return
        if (!ConnectionPriorityPolicy.shouldAutoReconnect(
                settings = currentSettings,
                inCar = phoneIsInCar(),
                userRequestedControl = userRequestedControl,
                yieldState = _connectionYieldState.value,
            )
        ) {
            diagnosticsRepository.debug("Auto-reconnect skipped by head-unit priority policy")
            // A walk-away is not a fault. Yield stays on the contention message instead.
            if (currentSettings.autoReconnect && _connectionYieldState.value !is ConnectionYieldState.Yielded) {
                scope.launch { enterAway("Auto-reconnect skipped because this phone is not with the car") }
            }
            return
        }
        scheduleReconnect(device)
    }

    private fun scheduleReconnect(device: SoundKitDevice) {
        if (reconnectJob?.isActive == true) {
            diagnosticsRepository.debug("Reconnect already scheduled; skipping duplicate request")
            return
        }
        reconnectJob = scope.launch {
            while (lastRequestedDevice?.address == device.address) {
                reconnectAttempt += 1
                val attempt = reconnectAttempt
                if (!retryPolicy.hasMoreAttempts(attempt)) {
                    enterAway("Auto-reconnect gave up after $attempt attempts")
                    maybeYieldOnContention(signal = contentionDetector.onConnectFailed())
                    return@launch
                }
                val delayMs = retryPolicy.delayForAttempt(attempt)
                diagnosticsRepository.warning("Scheduling reconnect attempt $attempt in ${delayMs}ms")
                connectionManager.markReconnecting(device, attempt, delayMs)
                delay(delayMs)
                if (lastRequestedDevice?.address != device.address) return@launch
                if (!ConnectionPriorityPolicy.shouldAutoReconnect(
                        settings = currentSettings,
                        inCar = phoneIsInCar(),
                        userRequestedControl = userRequestedControl,
                        yieldState = _connectionYieldState.value,
                    )
                ) {
                    // Leaving mid-burst must not freeze the shade on Reconnecting.
                    // Away is what lets a later return to the car connect once.
                    enterAway("Auto-reconnect stopped before the next attempt")
                    return@launch
                }
                val result = connectionManager.connect(device)
                if (result.isSuccess) {
                    diagnosticsRepository.info("Reconnect attempt $attempt started")
                    return@launch
                }
                diagnosticsRepository.error("Reconnect attempt $attempt failed: ${result.exceptionOrNull()?.message}")
                maybeYieldOnContention(signal = contentionDetector.onConnectFailed())
            }
        }
    }

    private suspend fun enterAway(logMessage: String) {
        diagnosticsRepository.warning(logMessage)
        val since = clock.nowMillis()
        val reason = if (phoneIsInCar()) AwayReason.OutOfRange else AwayReason.LeftCar
        settingsRepository.recordAwaySession(since, reason)
        connectionManager.markAway(since, reason)
    }

    private fun startAwayScan() {
        if (awayScanJob?.isActive == true) return
        val address = currentSettings.defaultReceiver?.address ?: return
        awayScanJob = scope.launch {
            while (connectionState.value is ConnectionState.Away && currentSettings.periodicScanWhenAway) {
                _isScanning.value = true
                val match = try {
                    withTimeoutOrNull(BleTimeouts.AWAY_SCAN_WINDOW_MS) {
                        scanner.scan(ScanDuty.LowPower).first { devices ->
                            devices.any { it.address.equals(address, ignoreCase = true) }
                        }
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    diagnosticsRepository.warning("Away scan stopped: ${error.message}")
                    null
                } finally {
                    // Callers null awayScanJob before cancel, so the flag cannot depend on that job.
                    if (scanJob?.isActive != true) {
                        _isScanning.value = false
                    }
                }
                val device = match?.firstOrNull { it.address.equals(address, ignoreCase = true) }
                if (device != null) {
                    diagnosticsRepository.info("Away scan found ${device.name}")
                    // Drop the job handle first so connect does not cancel this coroutine.
                    awayScanJob = null
                    connect(device, userInitiated = false)
                    return@launch
                }
                delay(BleTimeouts.AWAY_SCAN_GAP_MS)
            }
            _isScanning.value = false
        }
    }

    private fun phoneIsInCar(): Boolean {
        return carPresence.presence.value.orSession(carSessionTracker.isCarSessionActive.value)
    }

    companion object {
        const val YIELD_MESSAGE = "Another phone may be controlling the receiver. Tap Take control if you need this phone."
    }

    private fun logCommandResult(command: String, result: CommandResult) {
        when (result) {
            is CommandResult.Success -> diagnosticsRepository.info("$command command accepted; state=${result.valveState}")
            is CommandResult.Failure -> diagnosticsRepository.warning("$command command failed: ${result.message}")
        }
    }

    private fun ConnectionState.isActiveFor(device: SoundKitDevice): Boolean {
        return activeDeviceAddress() == device.address
    }

    private fun ConnectionState.hasDifferentActiveDevice(device: SoundKitDevice): Boolean {
        val activeAddress = activeDeviceAddress()
        return activeAddress != null && activeAddress != device.address
    }

    private fun ConnectionState.activeDeviceAddress(): String? {
        return when (this) {
            is ConnectionState.Connected -> device.address
            is ConnectionState.Connecting -> device.address
            is ConnectionState.Reconnecting -> device.address
            ConnectionState.Disconnected,
            ConnectionState.Scanning,
            is ConnectionState.Error,
            is ConnectionState.Away,
            -> null
        }
    }
}
