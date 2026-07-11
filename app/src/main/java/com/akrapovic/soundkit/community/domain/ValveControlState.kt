package com.akrapovic.soundkit.community.domain

/**
 * Typed valve-control state shared by phone UI, notification, widget, QS, car, and voice.
 * User-facing copy is a projection of this model — never drive behavior from strings.
 */
sealed interface ConnectionPhase {
    data object Disconnected : ConnectionPhase
    data object Scanning : ConnectionPhase
    data class Connecting(val device: SoundKitDevice) : ConnectionPhase
    data class Discovering(val device: SoundKitDevice) : ConnectionPhase
    data class Subscribing(val device: SoundKitDevice) : ConnectionPhase
    data class Ready(val device: SoundKitDevice) : ConnectionPhase
    data class Reconnecting(
        val device: SoundKitDevice,
        val attempt: Int,
        val nextDelayMs: Long,
    ) : ConnectionPhase
    data class Yielded(val reason: ConnectionYieldReason) : ConnectionPhase
    data class Failed(val message: String, val recoverable: Boolean) : ConnectionPhase
}

sealed interface CommandPhase {
    data object Idle : CommandPhase
    data class Writing(val target: ValveState) : CommandPhase
    data class AwaitingConfirmation(val target: ValveState) : CommandPhase
    data class Succeeded(val target: ValveState) : CommandPhase
    data class Failed(val target: ValveState, val reason: String, val recoverable: Boolean) : CommandPhase
}

data class ValveControlState(
    val activeReceiver: SoundKitDevice? = null,
    val connectionPhase: ConnectionPhase = ConnectionPhase.Disconnected,
    val valveState: ValveState = ValveState.Unknown,
    val receiverReady: Boolean = true,
    val receiverStatusMessage: String? = null,
    val commandPhase: CommandPhase = CommandPhase.Idle,
) {
    val commandInFlight: Boolean
        get() = when (commandPhase) {
            is CommandPhase.Writing,
            is CommandPhase.AwaitingConfirmation,
            -> true
            else -> false
        }

    val isLinkReady: Boolean
        get() = connectionPhase is ConnectionPhase.Ready &&
            valveState != ValveState.Unknown &&
            receiverReady &&
            receiverStatusMessage == null

    val canOpen: Boolean
        get() = isLinkReady && !commandInFlight && valveState == ValveState.Closed

    val canClose: Boolean
        get() = isLinkReady && !commandInFlight && valveState == ValveState.Open

    fun toConnectionState(): ConnectionState = when (val phase = connectionPhase) {
        ConnectionPhase.Disconnected -> ConnectionState.Disconnected
        ConnectionPhase.Scanning -> ConnectionState.Scanning
        is ConnectionPhase.Connecting -> ConnectionState.Connecting(phase.device)
        is ConnectionPhase.Discovering -> ConnectionState.Connecting(phase.device)
        is ConnectionPhase.Subscribing -> ConnectionState.Connecting(phase.device)
        is ConnectionPhase.Ready -> ConnectionState.Connected(phase.device)
        is ConnectionPhase.Reconnecting -> ConnectionState.Reconnecting(
            device = phase.device,
            attempt = phase.attempt,
            nextDelayMs = phase.nextDelayMs,
        )
        is ConnectionPhase.Yielded -> ConnectionState.Disconnected
        is ConnectionPhase.Failed -> ConnectionState.Error(phase.message, phase.recoverable)
    }
}

object BleTimeouts {
    const val CONNECT_MS = 15_000L
    const val DISCOVERY_MS = 10_000L
    const val SUBSCRIPTION_MS = 5_000L
    const val COMMAND_CONFIRMATION_MS = 5_000L
    const val ACTIVE_SCAN_MS = 15_000L
    const val LOW_LATENCY_SCAN_MS = 10_000L
}
