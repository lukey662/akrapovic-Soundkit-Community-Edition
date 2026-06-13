package com.akrapovic.soundkit.community.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akrapovic.soundkit.community.BuildConfig
import com.akrapovic.soundkit.community.ble.SoundKitProtocol
import com.akrapovic.soundkit.community.data.DriveModeProfile
import com.akrapovic.soundkit.community.data.SettingsBackupCodec
import com.akrapovic.soundkit.community.data.applyTo
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.diagnostics.CrashReporter
import com.akrapovic.soundkit.community.diagnostics.DiagnosticsSupport
import com.akrapovic.soundkit.community.diagnostics.DiagnosticsReportBuilder
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DriveModeEngine
import com.akrapovic.soundkit.community.domain.PreferredValveMode
import com.akrapovic.soundkit.community.domain.QuietStartSettings
import com.akrapovic.soundkit.community.domain.RememberedDeviceConnector
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.domain.VehicleCompatibilityCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SoundKitViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val settingsRepository: SettingsStore,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val diagnosticsReportBuilder: DiagnosticsReportBuilder,
    private val crashReporter: CrashReporter,
    private val driveModeEngine: DriveModeEngine,
) : ViewModel() {
    private val commandInFlight = MutableStateFlow(false)
    private val lastError = MutableStateFlow<String?>(null)
    private val hasPendingCrash = MutableStateFlow(crashReporter.hasPendingCrash())
    private val launchConnectAttempted = AtomicBoolean(false)

    private val bleState = combine(
        bleRepository.discoveredDevices,
        bleRepository.connectionState,
        bleRepository.valveState,
        bleRepository.receiverStatusMessage,
        bleRepository.isScanning,
    ) { devices, connectionState, valveState, receiverStatusMessage, isScanning ->
        BleUiState(
            devices = devices,
            connectionState = connectionState,
            valveState = valveState,
            receiverStatusMessage = receiverStatusMessage,
            isScanning = isScanning,
        )
    }

    private val coreState = combine(bleState, settingsRepository.settings) { ble, settings ->
        CoreUiState(
            devices = ble.devices,
            connectionState = ble.connectionState,
            valveState = ble.valveState,
            receiverStatusMessage = ble.receiverStatusMessage,
            isScanning = ble.isScanning,
            settings = settings,
        )
    }

    val uiState = combine(
        coreState,
        diagnosticsRepository.entries,
        commandInFlight,
        lastError,
        hasPendingCrash,
    ) { core, diagnostics, inFlight, error, pendingCrash ->
        SoundKitUiState(
            devices = core.devices,
            connectionState = core.connectionState,
            valveState = core.valveState,
            isScanning = core.isScanning,
            settings = core.settings,
            diagnostics = diagnostics,
            commandInFlight = inFlight,
            lastError = error,
            receiverStatusMessage = core.receiverStatusMessage,
            protocolVerified = SoundKitProtocol.VERIFIED,
            hasPendingCrash = pendingCrash,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SoundKitUiState(protocolVerified = SoundKitProtocol.VERIFIED),
    )

    fun tryConnectOnLaunch() {
        if (!launchConnectAttempted.compareAndSet(false, true)) return
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.onboardingCompleted || !settings.connectOnLaunch) return@launch
            val device = RememberedDeviceConnector.defaultDevice(settings) ?: return@launch
            if (RememberedDeviceConnector.shouldAutoConnect(bleRepository.connectionState.value, settings)) {
                bleRepository.connect(device)
            }
        }
    }

    fun startScan() {
        lastError.value = null
        bleRepository.startScan()
    }

    fun stopScan() {
        bleRepository.stopScan()
    }

    fun connect(device: SoundKitDevice) {
        viewModelScope.launch {
            lastError.value = null
            val settings = settingsRepository.settings.first()
            val vehicle = VehicleCompatibilityCatalog.findById(settings.selectedVehicleId)
            settingsRepository.saveReceiver(device, setAsDefault = true)
            vehicle?.defaultNickname?.let { nickname ->
                val existing = settings.savedReceivers.firstOrNull { it.address == device.address }
                if (existing?.nickname.isNullOrBlank()) {
                    settingsRepository.updateNickname(device.address, nickname)
                }
            }
            bleRepository.connect(device)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bleRepository.disconnect()
        }
    }

    fun openValve() {
        driveModeEngine.onUserValveAdjustment()
        sendCommand { bleRepository.openValve() }
    }

    fun closeValve() {
        driveModeEngine.onUserValveAdjustment()
        sendCommand { bleRepository.closeValve() }
    }

    fun toggleValve() {
        driveModeEngine.onUserValveAdjustment()
        when (uiState.value.valveState) {
            ValveState.Open -> closeValve()
            ValveState.Closed -> openValve()
            ValveState.Unknown -> Unit
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoReconnect(enabled)
        }
    }

    fun setConnectOnLaunch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setConnectOnLaunch(enabled)
        }
    }

    fun setDebugLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDebugLoggingEnabled(enabled)
        }
    }

    fun setGarageTheme(themeId: String) {
        viewModelScope.launch {
            settingsRepository.setGarageThemeId(themeId)
        }
    }

    fun acceptRiskNotice() {
        viewModelScope.launch {
            settingsRepository.acceptRiskNotice()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.completeOnboarding()
        }
    }

    fun setSelectedVehicle(vehicleId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedVehicle(vehicleId)
            VehicleCompatibilityCatalog.findById(vehicleId)?.suggestedGarageThemeId?.let { themeId ->
                settingsRepository.setGarageThemeId(themeId)
            }
        }
    }

    fun exportSettingsBackup(): String {
        return SettingsBackupCodec.encode(uiState.value.settings)
    }

    fun importSettingsBackup(json: String) {
        viewModelScope.launch {
            settingsRepository.importSettingsBackup(json)
        }
    }

    fun applyDriveModeProfile(profile: DriveModeProfile) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val updated = profile.applyTo(settings)
            settingsRepository.setDriveModeEnabled(updated.driveModeEnabled)
            settingsRepository.setPreferredValveMode(updated.preferredValveMode)
            settingsRepository.setQuietStart(updated.quietStart)
        }
    }

    fun retryConnection() {
        viewModelScope.launch {
            lastError.value = null
            val settings = settingsRepository.settings.first()
            val device = RememberedDeviceConnector.defaultDevice(settings) ?: return@launch
            bleRepository.connect(device)
        }
    }

    fun setDefaultReceiver(address: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultReceiver(address)
        }
    }

    fun removeReceiver(address: String) {
        viewModelScope.launch {
            settingsRepository.removeReceiver(address)
        }
    }

    fun updateNickname(address: String, nickname: String?) {
        viewModelScope.launch {
            settingsRepository.updateNickname(address, nickname)
        }
    }

    fun forgetDevice() {
        viewModelScope.launch {
            settingsRepository.forgetDevice()
            bleRepository.disconnect()
        }
    }

    fun setDriveModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDriveModeEnabled(enabled)
        }
    }

    fun setPreferredValveMode(mode: PreferredValveMode) {
        viewModelScope.launch {
            settingsRepository.setPreferredValveMode(mode)
        }
    }

    fun setQuietStart(quietStart: QuietStartSettings) {
        viewModelScope.launch {
            settingsRepository.setQuietStart(quietStart)
        }
    }

    fun setDriveModePaused(paused: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutomationPaused(paused)
        }
    }

    fun buildDiagnosticsReport(): String {
        val settings = uiState.value.settings
        val hasDefault = RememberedDeviceConnector.defaultDevice(settings) != null
        val vehicle = VehicleCompatibilityCatalog.findById(settings.selectedVehicleId)
        return diagnosticsReportBuilder.buildDiagnosticsReport(
            entries = uiState.value.diagnostics,
            hasDefaultReceiver = hasDefault,
            vehicleDisplayName = vehicle?.displayName,
            vehicleTier = vehicle?.tierLabel,
            connectionState = uiState.value.connectionState.toString(),
        )
    }

    fun writeDiagnosticsReportFile(): java.io.File {
        val settings = uiState.value.settings
        val hasDefault = RememberedDeviceConnector.defaultDevice(settings) != null
        return diagnosticsReportBuilder.writeDiagnosticsReportFile(
            entries = uiState.value.diagnostics,
            hasDefaultReceiver = hasDefault,
        )
    }

    fun buildCrashReport(): String {
        return diagnosticsReportBuilder.buildCrashReport()
    }

    fun writeCrashReportFile(): java.io.File {
        return diagnosticsReportBuilder.writeCrashReportFile()
    }

    fun clearPendingCrash() {
        if (crashReporter.clearPendingCrash()) {
            hasPendingCrash.value = false
        }
    }

    fun buildAppVersionLabel(): String = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    fun buildSupportTriageBody(state: SoundKitUiState): String {
        val vehicle = VehicleCompatibilityCatalog.findById(state.settings.selectedVehicleId)
        return DiagnosticsSupport.buildTriageBody(
            appVersion = buildAppVersionLabel(),
            vehicleLine = vehicle?.let { "vehicle=${it.displayName} tier=${it.tierLabel}" },
            connectionLine = "connection=${state.connectionState}",
        )
    }

    private fun sendCommand(block: suspend () -> CommandResult) {
        viewModelScope.launch {
            commandInFlight.value = true
            lastError.value = null
            when (val result = block()) {
                is CommandResult.Success -> lastError.value = null
                is CommandResult.Failure -> lastError.value = result.message
            }
            commandInFlight.value = false
        }
    }
}

private data class BleUiState(
    val devices: List<SoundKitDevice>,
    val connectionState: ConnectionState,
    val valveState: ValveState,
    val receiverStatusMessage: String?,
    val isScanning: Boolean,
)

private data class CoreUiState(
    val devices: List<SoundKitDevice>,
    val connectionState: ConnectionState,
    val valveState: ValveState,
    val receiverStatusMessage: String?,
    val isScanning: Boolean,
    val settings: com.akrapovic.soundkit.community.domain.SoundKitSettings,
)
