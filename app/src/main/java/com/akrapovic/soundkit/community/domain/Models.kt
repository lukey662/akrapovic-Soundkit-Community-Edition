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

enum class PreferredValveMode {
    Open,
    Closed,
}

data class QuietStartSettings(
    val enabled: Boolean = false,
    val daysOfWeek: Set<Int> = setOf(0, 1, 2, 3, 4, 5, 6),
    val windowStartMinute: Int = 6 * 60,
    val windowEndMinute: Int = 9 * 60,
    val holdClosedMinutes: Int = 3,
)

data class SavedReceiver(
    val address: String,
    val name: String,
    val nickname: String? = null,
    val isDefault: Boolean = false,
) {
    fun displayName(): String = nickname?.takeIf { it.isNotBlank() } ?: name
}

data class GeofenceZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
)

enum class RuleExecutionOutcome {
    Success,
    Skipped,
    Failed,
}

data class RuleExecutionEntry(
    val timestampMillis: Long,
    val ruleName: String,
    val action: String,
    val reason: String,
    val outcome: RuleExecutionOutcome,
    val detail: String? = null,
) {
    fun displaySummary(): String {
        val status = when (outcome) {
            RuleExecutionOutcome.Success -> "OK"
            RuleExecutionOutcome.Skipped -> "Skipped"
            RuleExecutionOutcome.Failed -> "Failed"
        }
        return "$ruleName → $action ($status)"
    }
}

data class SoundKitSettings(
    val savedReceivers: List<SavedReceiver> = emptyList(),
    val connectOnLaunch: Boolean = true,
    val autoReconnect: Boolean = true,
    val debugLoggingEnabled: Boolean = true,
    val garageThemeId: String = "studio-dark",
    val riskNoticeAcceptedAt: Long = 0L,
    val onboardingCompletedAt: Long = 0L,
    val automationPaused: Boolean = false,
    val betaDisclaimerAcceptedAt: Long = 0L,
    val driveModeEnabled: Boolean = true,
    val preferredValveMode: PreferredValveMode = PreferredValveMode.Open,
    val quietStart: QuietStartSettings = QuietStartSettings(),
) {
    val riskNoticeAccepted: Boolean get() = riskNoticeAcceptedAt > 0L
    val onboardingCompleted: Boolean get() = onboardingCompletedAt > 0L
    val betaDisclaimerAccepted: Boolean get() = betaDisclaimerAcceptedAt > 0L

    val defaultReceiver: SavedReceiver? get() = savedReceivers.firstOrNull { it.isDefault }

    /** @deprecated Use [defaultReceiver]; kept for migration and tests. */
    val rememberedDeviceName: String? get() = defaultReceiver?.displayName()

    /** @deprecated Use [defaultReceiver]; kept for migration and tests. */
    val rememberedDeviceAddress: String? get() = defaultReceiver?.address
}

