package com.akrapovic.soundkit.community.test

import com.akrapovic.soundkit.community.ble.BleConnectionGateway
import com.akrapovic.soundkit.community.ble.BleScannerGateway
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.domain.ValveState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    val connectedDevices = mutableListOf<SoundKitDevice>()
    val writtenCommands = mutableListOf<ValveCommand>()
    val reconnectMarks = mutableListOf<ConnectionState.Reconnecting>()
    var disconnectCount = 0
    var writeResult: CommandResult = CommandResult.Failure("protocol not verified", recoverable = false)
    var connectResults: MutableList<Result<Unit>> = mutableListOf(Result.success(Unit))

    override fun markReconnecting(device: SoundKitDevice, attempt: Int, nextDelayMs: Long) {
        reconnectMarks += ConnectionState.Reconnecting(device, attempt, nextDelayMs)
        connectionState.value = reconnectMarks.last()
    }

    override suspend fun connect(device: SoundKitDevice): Result<Unit> {
        connectedDevices += device
        val result = if (connectResults.isNotEmpty()) connectResults.removeAt(0) else Result.success(Unit)
        if (result.isSuccess) {
            connectionState.value = ConnectionState.Connecting(device)
        }
        return result
    }

    override suspend fun disconnect() {
        disconnectCount += 1
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
    var debugLoggingChanges = mutableListOf<Boolean>()
    var garageThemeChanges = mutableListOf<String>()
    var riskNoticeAcceptCount = 0
    var onboardingCompleteCount = 0

    override suspend fun rememberDevice(device: SoundKitDevice) {
        rememberedDevices += device
        settings.value = settings.value.copy(
            rememberedDeviceName = device.name,
            rememberedDeviceAddress = device.address,
        )
    }

    override suspend fun forgetDevice() {
        forgetCount += 1
        settings.value = settings.value.copy(
            rememberedDeviceName = null,
            rememberedDeviceAddress = null,
        )
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
}

class FakeBleRepository : BleRepository {
    override val discoveredDevices = MutableStateFlow<List<SoundKitDevice>>(emptyList())
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val valveState = MutableStateFlow(ValveState.Unknown)
    override val receiverStatusMessage = MutableStateFlow<String?>(null)
    override val isScanning = MutableStateFlow(false)

    var startScanCount = 0
    var stopScanCount = 0
    val connectedDevices = mutableListOf<SoundKitDevice>()
    var disconnectCount = 0
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

    override suspend fun connect(device: SoundKitDevice) {
        connectedDevices += device
        connectionState.value = ConnectionState.Connecting(device)
    }

    override suspend fun disconnect() {
        disconnectCount += 1
        connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun openValve(): CommandResult = openResult.await()

    override suspend fun closeValve(): CommandResult = closeResult.await()
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

