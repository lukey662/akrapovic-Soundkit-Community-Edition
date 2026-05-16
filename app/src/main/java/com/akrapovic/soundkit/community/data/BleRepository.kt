package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.ble.BleConnectionGateway
import com.akrapovic.soundkit.community.ble.BleScannerGateway
import com.akrapovic.soundkit.community.ble.RetryPolicy
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.domain.ValveState
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
import kotlinx.coroutines.launch

interface BleRepository {
    val discoveredDevices: StateFlow<List<SoundKitDevice>>
    val connectionState: StateFlow<ConnectionState>
    val valveState: StateFlow<ValveState>
    val isScanning: StateFlow<Boolean>

    fun startScan()
    fun stopScan()
    suspend fun connect(device: SoundKitDevice)
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
) : BleRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scanJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastRequestedDevice: SoundKitDevice? = null
    private var autoReconnectEnabled: Boolean = true
    private var hadStableConnection: Boolean = false
    private var suppressNextAutoReconnect: Boolean = false

    private val _discoveredDevices = MutableStateFlow<List<SoundKitDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<SoundKitDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    override val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    override val valveState: StateFlow<ValveState> = connectionManager.valveState

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                autoReconnectEnabled = settings.autoReconnect
            }
        }
        scope.launch {
            connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        suppressNextAutoReconnect = false
                        hadStableConnection = true
                    }
                    is ConnectionState.Error -> {
                        val device = lastRequestedDevice
                        if (suppressNextAutoReconnect) {
                            suppressNextAutoReconnect = false
                            hadStableConnection = false
                            diagnosticsRepository.debug("Suppressing auto reconnect during deliberate connection transition")
                        } else if (state.recoverable && autoReconnectEnabled && device != null) {
                            hadStableConnection = false
                            scheduleReconnect(device, startAttempt = 1)
                        }
                    }
                    ConnectionState.Disconnected -> {
                        val device = lastRequestedDevice
                        if (suppressNextAutoReconnect) {
                            suppressNextAutoReconnect = false
                            hadStableConnection = false
                            diagnosticsRepository.debug("Suppressing auto reconnect after deliberate disconnect")
                        } else if (hadStableConnection && autoReconnectEnabled && device != null) {
                            hadStableConnection = false
                            scheduleReconnect(device, startAttempt = 1)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun startScan() {
        if (scanJob?.isActive == true) return
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
    }

    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    override suspend fun connect(device: SoundKitDevice) {
        stopScan()
        settingsRepository.rememberDevice(device)
        if (connectionState.value.isActiveFor(device)) {
            lastRequestedDevice = device
            diagnosticsRepository.info("Already connected or connecting to ${device.name}")
            return
        }
        if (connectionState.value.hasDifferentActiveDevice(device)) {
            suppressNextAutoReconnect = true
        }
        lastRequestedDevice = device
        reconnectJob?.cancel()
        diagnosticsRepository.info("User requested connection to ${device.name}")
        connectionManager.connect(device).onFailure { error ->
            suppressNextAutoReconnect = false
            diagnosticsRepository.error("Initial connection failed: ${error.message}", error)
            scheduleReconnect(device, startAttempt = 1)
        }
    }

    override suspend fun disconnect() {
        reconnectJob?.cancel()
        suppressNextAutoReconnect = true
        hadStableConnection = false
        lastRequestedDevice = null
        connectionManager.disconnect()
    }

    override suspend fun openValve(): CommandResult {
        return connectionManager.writeCommand(ValveCommand.Open).also { logCommandResult("OPEN", it) }
    }

    override suspend fun closeValve(): CommandResult {
        return connectionManager.writeCommand(ValveCommand.Close).also { logCommandResult("CLOSE", it) }
    }

    private fun scheduleReconnect(device: SoundKitDevice, startAttempt: Int) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = startAttempt
            while (lastRequestedDevice?.address == device.address) {
                val delayMs = retryPolicy.delayForAttempt(attempt)
                diagnosticsRepository.warning("Scheduling reconnect attempt $attempt in ${delayMs}ms")
                delay(delayMs)
                connectionManager.markReconnecting(device, attempt, delayMs)
                val result = connectionManager.connect(device)
                if (result.isSuccess) {
                    diagnosticsRepository.info("Reconnect attempt $attempt started")
                    return@launch
                }
                diagnosticsRepository.error("Reconnect attempt $attempt failed: ${result.exceptionOrNull()?.message}")
                attempt += 1
            }
        }
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

