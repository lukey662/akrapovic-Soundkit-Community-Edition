package com.akrapovic.soundkit.community.ui

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DiagnosticsEntry
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveState

data class SoundKitUiState(
    val devices: List<SoundKitDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val valveState: ValveState = ValveState.Unknown,
    val isScanning: Boolean = false,
    val settings: SoundKitSettings = SoundKitSettings(),
    val diagnostics: List<DiagnosticsEntry> = emptyList(),
    val commandInFlight: Boolean = false,
    val lastError: String? = null,
    val protocolVerified: Boolean = false,
)

enum class AppScreen {
    Scan,
    Control,
    More,
    Diagnostics,
    Settings,
    Roadmap,
    GarageThemes,
}

