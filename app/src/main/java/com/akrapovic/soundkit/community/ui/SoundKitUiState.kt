package com.akrapovic.soundkit.community.ui

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ConnectionYieldState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveState

data class SoundKitUiState(
    val devices: List<SoundKitDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val valveState: ValveState = ValveState.Unknown,
    val isScanning: Boolean = false,
    val settings: SoundKitSettings = SoundKitSettings(),
    val commandInFlight: Boolean = false,
    val lastError: String? = null,
    val receiverStatusMessage: String? = null,
    val connectionYieldState: ConnectionYieldState = ConnectionYieldState.None,
    val protocolVerified: Boolean = false,
    val hasPendingCrash: Boolean = false,
)

enum class AppScreen {
    Home,
    More,
    Diagnostics,
    Settings,
    DriveMode,
    Roadmap,
    GarageThemes,
    AndroidAutoSetup,
    Advanced,
    Developer,
}

