package com.akrapovic.soundkit.community.test

import com.akrapovic.soundkit.community.ble.BleConnectionGateway
import com.akrapovic.soundkit.community.ble.BleScannerGateway
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.QuietStartCodec
import com.akrapovic.soundkit.community.data.SettingsBackupCodec
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.car.CarSessionTracker
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ConnectionYieldState
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.domain.ValveState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NoopRuleExecutionLogStore : com.akrapovic.soundkit.community.data.RuleExecutionLogStore {
    override val entries: Flow<List<RuleExecutionEntry>> = MutableStateFlow(emptyList())
    override val lastExecution = MutableStateFlow<RuleExecutionEntry?>(null)

    override suspend fun append(entry: RuleExecutionEntry) {
        lastExecution.value = entry
    }

    override suspend fun clear() {
        lastExecution.value = null
    }
}

class FakeBleScannerGateway : BleScannerGateway {
    val emissions = MutableSharedFlow<List<SoundKitDevice>>()
    var scanCollectionCount = 0

    override fun scan(): Flow<List<SoundKitDevice>> {
        scanCollectionCount += 1
        return emissions
    }
}

class FakeBleConnectionGateway : BleConnectionGateway {
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val valveState = MutableStateFlow(ValveState.Unknown)
    override val receiverStatusMessage = MutableStateFlow<String?>(null)
    override val notificationsEnabled = MutableStateFlow(false)

    val connectedDevices = mutableListOf<SoundKitDevice>()
    val writtenCommands = mutableListOf<ValveCommand>()
    val reconnectMarks = mutableListOf<ConnectionState.Reconnecting>()
    var disconnectCount = 0
    var writeResult: CommandResult = CommandResult.Failure("protocol not verified", recoverable = false)
    var connectResults: MutableList<Result<Unit>> = mutableListOf(Result.success(Unit))

    val reconnectGaveUpMessages = mutableListOf<String>()

    override fun markReconnecting(device: SoundKitDevice, attempt: Int, nextDelayMs: Long) {
        reconnectMarks += ConnectionState.Reconnecting(device, attempt, nextDelayMs)
        connectionState.value = reconnectMarks.last()
    }

    override fun markReconnectGaveUp(message: String) {
        reconnectGaveUpMessages += message
        connectionState.value = ConnectionState.Error(message, recoverable = false)
    }

    override suspend fun connect(device: SoundKitDevice): Result<Unit> {
        connectedDevices += device
        val result = if (connectResults.isNotEmpty()) connectResults.removeAt(0) else Result.success(Unit)
        if (result.isSuccess) {
            connectionState.value = ConnectionState.Connecting(device)
            notificationsEnabled.value = false
        }
        return result
    }

    override suspend fun disconnect() {
        disconnectCount += 1
        notificationsEnabled.value = false
        connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun writeCommand(command: ValveCommand): CommandResult {
        writtenCommands += command
        return writeResult
    }
}

class FakeSettingsStore(
    initialSettings: SoundKitSettings = SoundKitSettings(),
) : SettingsStore {
    override val settings = MutableStateFlow(initialSettings)
    val rememberedDevices = mutableListOf<SoundKitDevice>()
    var forgetCount = 0
    var autoReconnectChanges = mutableListOf<Boolean>()
    var connectOnLaunchChanges = mutableListOf<Boolean>()
    var connectInCarChanges = mutableListOf<Boolean>()
    var debugLoggingChanges = mutableListOf<Boolean>()
    var garageThemeChanges = mutableListOf<String>()
    var riskNoticeAcceptCount = 0
    var onboardingCompleteCount = 0

    override suspend fun rememberDevice(device: SoundKitDevice) {
        saveReceiver(device, setAsDefault = true)
    }

    override suspend fun saveReceiver(device: SoundKitDevice, setAsDefault: Boolean) {
        rememberedDevices += device
        val existing = settings.value.savedReceivers.filterNot { it.address == device.address }
        val incoming = SavedReceiver(
            address = device.address,
            name = device.name,
            isDefault = setAsDefault,
        )
        val merged = existing + incoming
        val updated = if (setAsDefault) {
            merged.map { it.copy(isDefault = it.address == device.address) }
        } else {
            merged
        }
        settings.value = settings.value.copy(savedReceivers = updated)
    }

    override suspend fun removeReceiver(address: String) {
        settings.value = settings.value.copy(
            savedReceivers = settings.value.savedReceivers.filterNot { it.address == address },
        )
    }

    override suspend fun setDefaultReceiver(address: String) {
        settings.value = settings.value.copy(
            savedReceivers = settings.value.savedReceivers.map {
                it.copy(isDefault = it.address == address)
            },
        )
    }

    override suspend fun updateNickname(address: String, nickname: String?) {
        settings.value = settings.value.copy(
            savedReceivers = settings.value.savedReceivers.map {
                if (it.address == address) it.copy(nickname = nickname) else it
            },
        )
    }

    override suspend fun setConnectOnLaunch(enabled: Boolean) {
        connectOnLaunchChanges += enabled
        settings.value = settings.value.copy(connectOnLaunch = enabled)
    }

    override suspend fun setConnectInCar(enabled: Boolean) {
        connectInCarChanges += enabled
        settings.value = settings.value.copy(connectInCar = enabled)
    }

    override suspend fun setHeadUnitPriorityEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(headUnitPriorityEnabled = enabled)
    }

    override suspend fun forgetDevice() {
        forgetCount += 1
        settings.value = settings.value.copy(savedReceivers = emptyList())
    }

    override suspend fun setAutoReconnect(enabled: Boolean) {
        autoReconnectChanges += enabled
        settings.value = settings.value.copy(autoReconnect = enabled)
    }

    override suspend fun setDebugLoggingEnabled(enabled: Boolean) {
        debugLoggingChanges += enabled
        settings.value = settings.value.copy(debugLoggingEnabled = enabled)
    }

    override suspend fun setGarageThemeId(themeId: String) {
        garageThemeChanges += themeId
        settings.value = settings.value.copy(garageThemeId = themeId)
    }

    override suspend fun acceptRiskNotice() {
        riskNoticeAcceptCount += 1
        settings.value = settings.value.copy(riskNoticeAcceptedAt = 1L)
    }

    override suspend fun completeOnboarding() {
        onboardingCompleteCount += 1
        settings.value = settings.value.copy(onboardingCompletedAt = 1L)
    }

    override suspend fun setSelectedVehicle(vehicleId: String?) {
        settings.value = settings.value.copy(selectedVehicleId = vehicleId)
    }

    override suspend fun importSettingsBackup(json: String) {
        val backup = SettingsBackupCodec.decode(json)
        var next = settings.value
        backup.selectedVehicleId?.let { next = next.copy(selectedVehicleId = it) }
        backup.connectOnLaunch?.let { next = next.copy(connectOnLaunch = it) }
        backup.headUnitPriorityEnabled?.let { next = next.copy(headUnitPriorityEnabled = it) }
        backup.autoReconnect?.let { next = next.copy(autoReconnect = it) }
        backup.garageThemeId?.let { next = next.copy(garageThemeId = it) }
        backup.driveModeEnabled?.let { next = next.copy(driveModeEnabled = it) }
        backup.preferredValveMode?.let { mode ->
            runCatching { com.akrapovic.soundkit.community.domain.PreferredValveMode.valueOf(mode) }
                .getOrNull()
                ?.let { next = next.copy(preferredValveMode = it) }
        }
        backup.quietStartJson?.let { next = next.copy(quietStart = QuietStartCodec.decode(it)) }
        settings.value = next
    }

    override suspend fun setAutomationPaused(paused: Boolean) {
        settings.value = settings.value.copy(automationPaused = paused)
    }

    override suspend fun acceptBetaDisclaimer() {
        settings.value = settings.value.copy(betaDisclaimerAcceptedAt = 1L)
    }

    override suspend fun setDriveModeEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(driveModeEnabled = enabled)
    }

    override suspend fun setPreferredValveMode(mode: com.akrapovic.soundkit.community.domain.PreferredValveMode) {
        settings.value = settings.value.copy(preferredValveMode = mode)
    }

    override suspend fun setQuietStart(quietStart: com.akrapovic.soundkit.community.domain.QuietStartSettings) {
        settings.value = settings.value.copy(quietStart = quietStart)
    }
}

class FakeBleRepository : BleRepository {
    override val discoveredDevices = MutableStateFlow<List<SoundKitDevice>>(emptyList())
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val valveState = MutableStateFlow(ValveState.Unknown)
    override val receiverStatusMessage = MutableStateFlow<String?>(null)
    override val connectionYieldState = MutableStateFlow<ConnectionYieldState>(ConnectionYieldState.None)
    override val isScanning = MutableStateFlow(false)

    var startScanCount = 0
    var stopScanCount = 0
    val connectedDevices = mutableListOf<SoundKitDevice>()
    var disconnectCount = 0
    var openValveCount = 0
    var closeValveCount = 0
    var openResult: CompletableDeferred<CommandResult> =
        CompletableDeferred(CommandResult.Failure("protocol not verified", recoverable = false))
    var closeResult: CompletableDeferred<CommandResult> =
        CompletableDeferred(CommandResult.Failure("protocol not verified", recoverable = false))

    override fun startScan() {
        startScanCount += 1
        isScanning.value = true
    }

    override fun stopScan() {
        stopScanCount += 1
        isScanning.value = false
    }

    override suspend fun connect(device: SoundKitDevice, userInitiated: Boolean) {
        connectedDevices += device
        connectionState.value = ConnectionState.Connecting(device)
    }

    override suspend fun takeControl(device: SoundKitDevice) {
        connectionYieldState.value = ConnectionYieldState.None
        connect(device, userInitiated = true)
    }

    override suspend fun disconnect() {
        disconnectCount += 1
        connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun openValve(): CommandResult {
        openValveCount += 1
        return openResult.await().also { result ->
            if (result is CommandResult.Success) {
                valveState.value = result.valveState
            }
        }
    }

    override suspend fun closeValve(): CommandResult {
        closeValveCount += 1
        return closeResult.await().also { result ->
            if (result is CommandResult.Success) {
                valveState.value = result.valveState
            }
        }
    }
}

fun testDevice(
    name: String = "Akrapovic SoundKit",
    address: String = "00:11:22:33:44:55",
    rssi: Int? = -52,
    isLikelySoundKit: Boolean = true,
) = SoundKitDevice(
    name = name,
    address = address,
    rssi = rssi,
    isLikelySoundKit = isLikelySoundKit,
)

