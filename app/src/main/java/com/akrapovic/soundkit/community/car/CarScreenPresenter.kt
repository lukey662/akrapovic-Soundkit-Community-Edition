package com.akrapovic.soundkit.community.car

import com.akrapovic.soundkit.community.domain.CommandPhase
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState

/**
 * Converts state from Android-facing collaborators into a small, testable car-screen contract.
 * Setup remains phone-only because the car surface must stay low distraction.
 */
object CarScreenPresenter {
    fun present(
        onboardingCompleted: Boolean,
        permissionsGranted: Boolean,
        hasDefaultReceiver: Boolean,
        connectionState: ConnectionState,
        valveState: ValveState,
        receiverStatusMessage: String?,
        commandPhase: CommandPhase,
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

        val commandInFlight = commandPhase.isInFlight()
        val connectionInFlight = connectionState is ConnectionState.Connecting ||
            connectionState is ConnectionState.Reconnecting ||
            connectionState == ConnectionState.Scanning
        val controlsVisible = connectionState is ConnectionState.Connected &&
            valveState != ValveState.Unknown &&
            receiverStatusMessage == null
        return CarScreenModel.Controls(
            status = receiverStatusMessage ?: connectionState.statusText(valveState),
            loading = commandInFlight || connectionInFlight,
            showControls = controlsVisible,
            openEnabled = controlsVisible && valveState == ValveState.Closed && !commandInFlight,
            closeEnabled = controlsVisible && valveState == ValveState.Open && !commandInFlight,
        )
    }

    private fun CommandPhase.isInFlight(): Boolean {
        return this is CommandPhase.Writing || this is CommandPhase.AwaitingConfirmation
    }

    private fun ConnectionState.statusText(valveState: ValveState): String = when (this) {
        ConnectionState.Disconnected -> "Receiver disconnected"
        ConnectionState.Scanning -> "Connecting"
        is ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Reconnecting -> "Connecting"
        is ConnectionState.Connected -> valveState.statusText()
        is ConnectionState.Error -> "Connection failed"
    }

    private fun ValveState.statusText(): String = when (this) {
        ValveState.Closed -> "Closed"
        ValveState.Open -> "Open"
        ValveState.Unknown -> "Checking status"
    }
}

sealed interface CarScreenModel {
    data class SetupRequired(val message: String) : CarScreenModel

    data class Controls(
        val status: String,
        val loading: Boolean,
        val showControls: Boolean,
        val openEnabled: Boolean,
        val closeEnabled: Boolean,
    ) : CarScreenModel
}
