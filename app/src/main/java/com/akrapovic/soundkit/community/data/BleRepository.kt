package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.ble.BleConnectionGateway
import com.akrapovic.soundkit.community.ble.BleScannerGateway
import com.akrapovic.soundkit.community.ble.RetryPolicy
import com.akrapovic.soundkit.community.ble.ScanDuty
import com.akrapovic.soundkit.community.car.CarPresenceSource
import com.akrapovic.soundkit.community.car.CarSessionTracker
import com.akrapovic.soundkit.community.domain.AwaySessionPolicy
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
    val startupSettled: StateFlow<Boolean>

    fun startScan()
    fun stopScan()
    suspend fun connect(device: SoundKitDevice, userInitiated: Boolean = true)
    suspend fun takeControl(device: SoundKitDevice)
    suspend fun disconnect()
    suspend fun dismissAway()
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
    private var leaveReleaseJob: Job? = null
    private var reclaimJob: Job? = null
    private var reclaimDelayMs: Long = RECLAIM_INITIAL_MS
    private var lastRequestedDevice: SoundKitDevice? = null
    private var currentSettings: SoundKitSettings = SoundKitSettings()
    private var hadStableConnection: Boolean = false
    private var suppressNextAutoReconnect: Boolean = false
    private var suppressReturnConnect: Boolean = false
    private var userRequestedControl: Boolean = false
    private var reconnectAttempt: Int = 0
    private val contentionDetector = BleContentionDetector(clock = clock::nowMillis)

    private val _discoveredDevices = MutableStateFlow<List<SoundKitDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<SoundKitDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionYieldState = MutableStateFlow<ConnectionYieldState>(ConnectionYieldState.None)
    override val connectionYieldState: StateFlow<ConnectionYieldState> = _connectionYieldState.asStateFlow()

    private val _startupSettled = MutableStateFlow(false)
    override val startupSettled: StateFlow<Boolean> = _startupSettled.asStateFlow()

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
            val now = clock.nowMillis()
            val awayFresh = AwaySessionPolicy.isFresh(settings.awaySinceMillis, now) &&
                connectionState.value == ConnectionState.Disconnected
            if (settings.awaySinceMillis > 0L && !AwaySessionPolicy.isFresh(settings.awaySinceMillis, now)) {
                settingsRepository.clearAwaySession()
            }
            if (awayFresh) {
                connectionManager.markAway(
                    settings.awaySinceMillis,
                    settings.awayReason ?: AwayReason.LeftCar,
                )
            }
            var wasInCar = carPresence.presence.value.orSession(carSessionTracker.isCarSessionActive.value)
            // Process start has no false→true edge when the phone is already on the car link.
            if (wasInCar) {
                onReturnedToCar()
            }
            _startupSettled.value = true
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
                        reclaimJob?.cancel()
                        reclaimJob = null
                        reclaimDelayMs = RECLAIM_INITIAL_MS
                        leaveReleaseJob?.cancel()
                        leaveReleaseJob = null
                        _connectionYieldState.value = ConnectionYieldState.None
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
        leaveReleaseJob?.cancel()
        leaveReleaseJob = null
        reclaimJob?.cancel()
        reclaimJob = null
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

    override suspend fun dismissAway() {
        reconnectJob?.cancel()
        reconnectJob = null
        awayScanJob?.cancel()
        awayScanJob = null
        leaveReleaseJob?.cancel()
        leaveReleaseJob = null
        reclaimJob?.cancel()
        reclaimJob = null
        suppressNextAutoReconnect = true
        hadStableConnection = false
        settingsRepository.clearAwaySession()
        if (connectionState.value is ConnectionState.Away) {
            connectionManager.disconnect()
        }
    }

    override suspend fun openValve(): CommandResult {
        return connectionManager.writeCommand(ValveCommand.Open).also { logCommandResult("OPEN", it) }
    }

    override suspend fun closeValve(): CommandResult {
        return connectionManager.writeCommand(ValveCommand.Close).also { logCommandResult("CLOSE", it) }
    }

    private fun onReturnedToCar() {
        leaveReleaseJob?.cancel()
        leaveReleaseJob = null
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
        reclaimJob?.cancel()
        reclaimJob = null
        _connectionYieldState.value = ConnectionYieldState.None
        contentionDetector.reset()
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
            is ConnectionState.Connected -> scheduleLeaveRelease()
            else -> {
                if (reconnectJob?.isActive == true) {
                    reconnectJob?.cancel()
                    suppressNextAutoReconnect = true
                    scope.launch { enterAway("Car link dropped during reconnect") }
                }
            }
        }
    }

    private fun scheduleLeaveRelease() {
        leaveReleaseJob?.cancel()
        leaveReleaseJob = scope.launch {
            delay(LEAVE_GRACE_MS)
            if (phoneIsInCar()) return@launch
            if (connectionState.value !is ConnectionState.Connected) return@launch
            diagnosticsRepository.info("Left the car; releasing the receiver")
            suppressNextAutoReconnect = true
            hadStableConnection = false
            connectionManager.disconnect()
            enterAway("Left the car")
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
            reclaimJob?.cancel()
            reclaimJob = null
            reclaimDelayMs = RECLAIM_INITIAL_MS
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
        if (!ConnectionPriorityPolicy.shouldEnterYieldOnContention(currentSettings)) return
        enterYield(signal)
    }

    private fun enterYield(signal: BleContentionDetector.ContentionSignal) {
        reconnectJob?.cancel()
        reconnectAttempt = 0
        val alreadyYielded = _connectionYieldState.value is ConnectionYieldState.Yielded
        diagnosticsRepository.warning(
            "BLE contention detected ($signal); yielding so another phone can keep the receiver",
        )
        _connectionYieldState.value = ConnectionYieldState.Yielded(ConnectionYieldReason.HeadUnitMayBeActive)
        if (!phoneIsInCar()) {
            reclaimJob?.cancel()
            reclaimJob = null
            return
        }
        reclaimDelayMs = if (alreadyYielded) {
            (reclaimDelayMs * 2).coerceAtMost(RECLAIM_MAX_MS)
        } else {
            RECLAIM_INITIAL_MS
        }
        scheduleReclaim()
    }

    private fun scheduleReclaim() {
        if (!phoneIsInCar() || _connectionYieldState.value !is ConnectionYieldState.Yielded) return
        val delayMs = reclaimDelayMs
        val next = scope.launch {
            delay(delayMs)
            runReclaimProbe()
        }
        val previous = reclaimJob
        reclaimJob = next
        if (previous != null && previous !== next) {
            previous.cancel()
        }
    }

    private suspend fun runReclaimProbe() {
        if (!phoneIsInCar() || _connectionYieldState.value !is ConnectionYieldState.Yielded) return
        val device = RememberedDeviceConnector.defaultDevice(currentSettings) ?: return
        lastRequestedDevice = device
        diagnosticsRepository.info("Reclaim probe for ${device.name}")
        val result = connectionManager.connect(device)
        if (result.isSuccess) return
        diagnosticsRepository.warning("Reclaim probe failed: ${result.exceptionOrNull()?.message}")
        contentionDetector.onConnectFailed()
        reclaimDelayMs = (reclaimDelayMs * 2).coerceAtMost(RECLAIM_MAX_MS)
        scheduleReclaim()
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
            if (_connectionYieldState.value is ConnectionYieldState.Yielded) {
                if (phoneIsInCar() && reclaimJob?.isActive != true) {
                    scheduleReclaim()
                }
                return
            }
            // A walk-away is not a fault. Yield stays on the contention message instead.
            if (currentSettings.autoReconnect) {
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
                    if (ConnectionPriorityPolicy.shouldEnterYieldOnContention(currentSettings) && phoneIsInCar()) {
                        enterYield(BleContentionDetector.ContentionSignal.ConnectStorm)
                        return@launch
                    }
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
                    if (_connectionYieldState.value is ConnectionYieldState.Yielded) return@launch
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
                if (_connectionYieldState.value is ConnectionYieldState.Yielded) return@launch
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
        const val LEAVE_GRACE_MS = 15_000L
        const val RECLAIM_INITIAL_MS = 60_000L
        const val RECLAIM_MAX_MS = 5L * 60L * 1000L
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
