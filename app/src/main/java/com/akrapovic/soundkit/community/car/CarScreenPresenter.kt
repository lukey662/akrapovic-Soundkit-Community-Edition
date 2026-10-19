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
 * After setup the car shows one PaneTemplate: a constant header, the valve art, one row whose
 * title is the receiver name and whose text is the live status, and at most two buttons. The
 * host treats a pane as a refresh while the title and row titles stay the same, so buttons can
 * appear, disappear and change colour on every connection change without using up the host's
 * template quota.
 *
 * Open, Close and Connect act inside the car, so they work while driving; nothing here sends the
 * driver to the phone.
 */
object CarScreenPresenter {
    const val TITLE = "Car Sound Kit"

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

        val connected = connectionState is ConnectionState.Connected
        val inFlightTarget = commandPhase.inFlightTarget()
        val rowText = when (connectionState) {
            is ConnectionState.Connected -> "Connected · " + when {
                receiverStatusMessage != null -> "Receiver not ready"
                inFlightTarget == ValveState.Open -> "Opening…"
                inFlightTarget == ValveState.Closed -> "Closing…"
                else -> valveState.statusText(quietHoldEndsAtMillis, nowMillis)
            }
            ConnectionState.Scanning,
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting,
            -> "Connecting…"
            ConnectionState.Disconnected -> "Not connected"
            is ConnectionState.Error -> "Couldn't connect"
            is ConnectionState.Away -> when (connectionState.reason) {
                AwayReason.LeftCar -> "Left the car"
                AwayReason.OutOfRange -> "Receiver out of range"
            }
        }
        val buttons = when {
            connectionState is ConnectionState.Disconnected ||
                connectionState is ConnectionState.Error ||
                connectionState is ConnectionState.Away -> CarButtons.Connect
            !connected || receiverStatusMessage != null || valveState == ValveState.Unknown -> CarButtons.None
            inFlightTarget != null -> CarButtons.OpenClose(
                primary = if (inFlightTarget == ValveState.Open) ValveTarget.Open else ValveTarget.Close,
                openEnabled = false,
                closeEnabled = false,
            )
            valveState == ValveState.Closed -> CarButtons.OpenClose(ValveTarget.Open, openEnabled = true, closeEnabled = false)
            else -> CarButtons.OpenClose(ValveTarget.Close, openEnabled = false, closeEnabled = true)
        }
        return CarScreenModel.Controls(
            rowText = rowText,
            buttons = buttons,
            valveOpen = connected && valveState == ValveState.Open,
            dimmed = !connected,
            busy = connected && inFlightTarget != null,
        )
    }

    private fun CommandPhase.inFlightTarget(): ValveState? = when (this) {
        is CommandPhase.Writing -> target
        is CommandPhase.AwaitingConfirmation -> target
        else -> null
    }

    private fun ValveState.statusText(quietHoldEndsAtMillis: Long?, nowMillis: Long): String {
        if (this == ValveState.Closed && quietHoldEndsAtMillis != null && quietHoldEndsAtMillis > nowMillis) {
            return QuietHoldCopy.shortStatus(quietHoldEndsAtMillis, nowMillis)
        }
        return when (this) {
            ValveState.Closed -> "Valves closed"
            ValveState.Open -> "Valves open"
            ValveState.Unknown -> "Checking valves"
        }
    }
}

enum class CarTemplateKind {
    Controls,
    Message,
}

/**
 * Setup is a MessageTemplate. Everything after setup is one PaneTemplate, so a status change can
 * never swap template types mid-drive.
 */
fun carTemplateKind(model: CarScreenModel): CarTemplateKind {
    return when (model) {
        is CarScreenModel.SetupRequired -> CarTemplateKind.Message
        is CarScreenModel.Controls -> CarTemplateKind.Controls
    }
}

enum class ValveTarget { Open, Close }

/** The pane's action row: nothing while connecting, Connect when the link is down, else Open and Close. */
sealed interface CarButtons {
    data object None : CarButtons
    data object Connect : CarButtons

    /** [primary] is the button that would change the valves; the other mirrors the current state and is disabled. */
    data class OpenClose(
        val primary: ValveTarget,
        val openEnabled: Boolean,
        val closeEnabled: Boolean,
    ) : CarButtons
}

sealed interface CarScreenModel {
    data class SetupRequired(val message: String) : CarScreenModel

    data class Controls(
        val rowText: String,
        val buttons: CarButtons,
        val valveOpen: Boolean,
        val dimmed: Boolean,
        val busy: Boolean,
    ) : CarScreenModel
}
