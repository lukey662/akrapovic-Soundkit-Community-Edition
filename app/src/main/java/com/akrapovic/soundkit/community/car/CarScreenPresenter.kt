package com.akrapovic.soundkit.community.car

import com.akrapovic.soundkit.community.domain.AwayReason
import com.akrapovic.soundkit.community.domain.CommandPhase
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.QuietHoldCopy
import com.akrapovic.soundkit.community.domain.ValveState

/**
 * Converts state from Android-facing collaborators into a small, testable car-screen contract.
 * Setup remains phone-only because the car surface must stay low distraction.
 *
 * After setup the car shows one GridTemplate whose title and item titles never change, so every
 * update is a template refresh and does not use up the host's step quota. Live status goes in
 * item text only.
 */
object CarScreenPresenter {
    const val TITLE = "Car Sound Kit Control"
    const val STATUS_ITEM_TITLE = "Status"
    const val OPEN_ITEM_TITLE = "Open"
    const val CLOSE_ITEM_TITLE = "Close"

    fun present(
        onboardingCompleted: Boolean,
        permissionsGranted: Boolean,
        hasDefaultReceiver: Boolean,
        connectionState: ConnectionState,
        valveState: ValveState,
        receiverStatusMessage: String?,
        commandPhase: CommandPhase,
        quietHoldEndsAtMillis: Long? = null,
        nowMillis: Long = 0L,
    ): CarScreenModel {
        val setupReason = when {
            !onboardingCompleted -> "Finish setup on phone"
            !permissionsGranted -> "Allow Bluetooth on phone"
            !hasDefaultReceiver -> "Choose a default receiver on phone"
            else -> null
        }
        if (setupReason != null) {
            return CarScreenModel.SetupRequired(setupReason)
        }

        val inFlightTarget = commandPhase.inFlightTarget()
        val controlsReady = connectionState is ConnectionState.Connected &&
            valveState != ValveState.Unknown &&
            receiverStatusMessage == null
        // Grid item text gets roughly 14 characters on a compact head unit before it is cut off.
        val status = when {
            receiverStatusMessage != null -> "Not ready"
            inFlightTarget == ValveState.Open -> "Opening…"
            inFlightTarget == ValveState.Closed -> "Closing…"
            else -> connectionState.statusText(valveState, quietHoldEndsAtMillis, nowMillis)
        }
        return CarScreenModel.Controls(
            status = status,
            openEnabled = controlsReady && valveState == ValveState.Closed && inFlightTarget == null,
            closeEnabled = controlsReady && valveState == ValveState.Open && inFlightTarget == null,
            retryEnabled = connectionState is ConnectionState.Disconnected ||
                connectionState is ConnectionState.Error ||
                connectionState is ConnectionState.Away,
        )
    }

    private fun CommandPhase.inFlightTarget(): ValveState? = when (this) {
        is CommandPhase.Writing -> target
        is CommandPhase.AwaitingConfirmation -> target
        else -> null
    }

    private fun ConnectionState.statusText(
        valveState: ValveState,
        quietHoldEndsAtMillis: Long?,
        nowMillis: Long,
    ): String = when (this) {
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Scanning -> "Connecting…"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Reconnecting -> "Connecting…"
        is ConnectionState.Connected -> valveState.statusText(quietHoldEndsAtMillis, nowMillis)
        is ConnectionState.Error -> "Failed · retry"
        is ConnectionState.Away -> when (reason) {
            AwayReason.LeftCar -> "Left the car"
            AwayReason.OutOfRange -> "Out of range"
        }
    }

    private fun ValveState.statusText(quietHoldEndsAtMillis: Long?, nowMillis: Long): String {
        if (this == ValveState.Closed && quietHoldEndsAtMillis != null && quietHoldEndsAtMillis > nowMillis) {
            return QuietHoldCopy.shortStatus(quietHoldEndsAtMillis, nowMillis)
        }
        return when (this) {
            ValveState.Closed -> "Closed"
            ValveState.Open -> "Open"
            ValveState.Unknown -> "Checking"
        }
    }
}

enum class CarTemplateKind {
    Controls,
    Message,
}

/**
 * Setup is a MessageTemplate. Everything after setup is one GridTemplate with a list and never
 * a loading state, so a status change can never swap template types mid-drive.
 */
fun carTemplateKind(model: CarScreenModel): CarTemplateKind {
    return when (model) {
        is CarScreenModel.SetupRequired -> CarTemplateKind.Message
        is CarScreenModel.Controls -> CarTemplateKind.Controls
    }
}

sealed interface CarScreenModel {
    data class SetupRequired(val message: String) : CarScreenModel

    data class Controls(
        val status: String,
        val openEnabled: Boolean,
        val closeEnabled: Boolean,
        val retryEnabled: Boolean,
    ) : CarScreenModel {
        /**
         * Grid item text for the status tile; the item title stays [CarScreenPresenter.STATUS_ITEM_TITLE].
         * Kept short enough not to truncate on a compact head unit; a plain disconnect reads as the
         * action the tap performs.
         */
        val statusText: String get() = if (retryEnabled && status == "Disconnected") "Tap to retry" else status
    }
}
