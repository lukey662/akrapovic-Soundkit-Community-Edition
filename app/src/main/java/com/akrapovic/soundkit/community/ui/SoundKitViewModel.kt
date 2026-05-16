package com.akrapovic.soundkit.community.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akrapovic.soundkit.community.ble.SoundKitProtocol
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.diagnostics.CrashReporter
import com.akrapovic.soundkit.community.diagnostics.DiagnosticsReportBuilder
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SoundKitViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val settingsRepository: SettingsStore,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val diagnosticsReportBuilder: DiagnosticsReportBuilder,
    private val crashReporter: CrashReporter,
) : ViewModel() {
    private val commandInFlight = MutableStateFlow(false)
    private val lastError = MutableStateFlow<String?>(null)
    private val hasPendingCrash = MutableStateFlow(crashReporter.hasPendingCrash())

    private val coreState = combine(
        bleRepository.discoveredDevices,
        bleRepository.connectionState,
        bleRepository.valveState,
        bleRepository.isScanning,
        settingsRepository.settings,
    ) { devices, connectionState, valveState, isScanning, settings ->
        CoreUiState(
            devices = devices,
            connectionState = connectionState,
            valveState = valveState,
            isScanning = isScanning,
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
            protocolVerified = SoundKitProtocol.VERIFIED,
            hasPendingCrash = pendingCrash,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SoundKitUiState(protocolVerified = SoundKitProtocol.VERIFIED),
    )

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
            bleRepository.connect(device)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bleRepository.disconnect()
        }
    }

    fun openValve() {
        sendCommand { bleRepository.openValve() }
    }

    fun closeValve() {
        sendCommand { bleRepository.closeValve() }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoReconnect(enabled)
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

    fun forgetDevice() {
        viewModelScope.launch {
            settingsRepository.forgetDevice()
            bleRepository.disconnect()
        }
    }

    fun buildDiagnosticsReport(): String {
        return diagnosticsReportBuilder.buildDiagnosticsReport(uiState.value.diagnostics)
    }

    fun writeDiagnosticsReportFile(): java.io.File {
        return diagnosticsReportBuilder.writeDiagnosticsReportFile(uiState.value.diagnostics)
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

private data class CoreUiState(
    val devices: List<SoundKitDevice>,
    val connectionState: ConnectionState,
    val valveState: ValveState,
    val isScanning: Boolean,
    val settings: SoundKitSettings,
)

