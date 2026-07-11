package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.ble.BleConnectionGateway
import com.akrapovic.soundkit.community.ble.BleScannerGateway
import com.akrapovic.soundkit.community.ble.RetryPolicy
import com.akrapovic.soundkit.community.car.CarSessionTracker
import com.akrapovic.soundkit.community.domain.BleContentionDetector
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionPriorityPolicy
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ConnectionYieldReason
import com.akrapovic.soundkit.community.domain.ConnectionYieldState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.domain.ValveState
import javax.inject.Inject
import javax.inject.Singleton
import com.akrapovic.soundkit.community.domain.BleTimeouts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

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
) : BleRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scanJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastRequestedDevice: SoundKitDevice? = null
    private var currentSettings: SoundKitSettings = SoundKitSettings()
    private var hadStableConnection: Boolean = false
    private var suppressNextAutoReconnect: Boolean = false
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
                currentSettings = settings
                diagnosticsRepository.debugLoggingEnabled = settings.debugLoggingEnabled
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
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    override suspend fun connect(device: SoundKitDevice, userInitiated: Boolean) {
        connectInternal(device, userInitiated = userInitiated, clearYield = userInitiated)
    }

    override suspend fun takeControl(device: SoundKitDevice) {
        connectInternal(device, userInitiated = true, clearYield = true)
    }

    override suspend fun disconnect() {
        reconnectJob?.cancel()
        reconnectAttempt = 0
        suppressNextAutoReconnect = true
        hadStableConnection = false
        userRequestedControl = false
        contentionDetector.reset()
        lastRequestedDevice = null
        _connectionYieldState.value = ConnectionYieldState.None
        connectionManager.disconnect()
    }

    override suspend fun openValve(): CommandResult {
        return connectionManager.writeCommand(ValveCommand.Open).also { logCommandResult("OPEN", it) }
    }

    override suspend fun closeValve(): CommandResult {
        return connectionManager.writeCommand(ValveCommand.Close).also { logCommandResult("CLOSE", it) }
    }

    private suspend fun connectInternal(
        device: SoundKitDevice,
        userInitiated: Boolean,
        clearYield: Boolean,
    ) {
        stopScan()
        settingsRepository.rememberDevice(device)
        if (connectionState.value.isActiveFor(device)) {
            lastRequestedDevice = device
            if (userInitiated) {
                userRequestedControl = true
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
                carSessionTracker.isCarSessionActive.value,
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
                carSessionActive = carSessionTracker.isCarSessionActive.value,
                userRequestedControl = userRequestedControl,
                yieldState = _connectionYieldState.value,
            )
        ) {
            diagnosticsRepository.debug("Auto-reconnect skipped by head-unit priority policy")
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
                    diagnosticsRepository.warning("Auto-reconnect gave up after $attempt attempts")
                    connectionManager.markReconnectGaveUp(RECONNECT_GAVE_UP_MESSAGE)
                    maybeYieldOnContention(signal = contentionDetector.onConnectFailed())
                    return@launch
                }
                val delayMs = retryPolicy.delayForAttempt(attempt)
                diagnosticsRepository.warning("Scheduling reconnect attempt $attempt in ${delayMs}ms")
                delay(delayMs)
                if (lastRequestedDevice?.address != device.address) return@launch
                if (!ConnectionPriorityPolicy.shouldAutoReconnect(
                        settings = currentSettings,
                        carSessionActive = carSessionTracker.isCarSessionActive.value,
                        userRequestedControl = userRequestedControl,
                        yieldState = _connectionYieldState.value,
                    )
                ) {
                    return@launch
                }
                connectionManager.markReconnecting(device, attempt, delayMs)
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

    companion object {
        const val RECONNECT_GAVE_UP_MESSAGE = "Couldn't reach receiver — tap to retry"
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
            -> null
        }
    }
}
