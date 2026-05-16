package com.akrapovic.soundkit.community.domain

data class SoundKitDevice(
    val name: String,
    val address: String,
    val rssi: Int? = null,
    val isLikelySoundKit: Boolean = false,
)

enum class ValveCommand {
    Open,
    Close,
}

enum class ValveState {
    Unknown,
    Open,
    Closed,
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Scanning : ConnectionState
    data class Connecting(val device: SoundKitDevice) : ConnectionState
    data class Connected(val device: SoundKitDevice) : ConnectionState
    data class Reconnecting(val device: SoundKitDevice, val attempt: Int, val nextDelayMs: Long) : ConnectionState
    data class Error(val message: String, val recoverable: Boolean) : ConnectionState
}

sealed interface CommandResult {
    data class Success(val valveState: ValveState) : CommandResult
    data class Failure(val message: String, val recoverable: Boolean) : CommandResult
}

data class DiagnosticsEntry(
    /** Monotonic id — required for stable LazyColumn keys when timestamps collide. */
    val id: Long,
    val timestampMillis: Long,
    val level: DiagnosticsLevel,
    val message: String,
)

enum class DiagnosticsLevel {
    Debug,
    Info,
    Warning,
    Error,
}

data class SoundKitSettings(
    val rememberedDeviceName: String? = null,
    val rememberedDeviceAddress: String? = null,
    val autoReconnect: Boolean = true,
    val debugLoggingEnabled: Boolean = true,
    val garageThemeId: String = "akra-carbon",
)

