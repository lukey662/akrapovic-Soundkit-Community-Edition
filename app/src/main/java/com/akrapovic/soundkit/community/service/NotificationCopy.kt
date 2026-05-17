package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.ValveState

data class NotificationPresentation(
    val title: String,
    val contentText: String,
    val ongoing: Boolean,
    val openValveEnabled: Boolean,
    val closeValveEnabled: Boolean,
    val disconnectEnabled: Boolean,
    val pauseAutomationEnabled: Boolean,
    val resumeAutomationEnabled: Boolean,
)

object NotificationCopy {
    fun build(
        connectionState: ConnectionState,
        valveState: ValveState,
        receiverStatusMessage: String?,
        defaultReceiver: SavedReceiver?,
        automationPaused: Boolean = false,
        lastExecution: RuleExecutionEntry? = null,
        hasAutomationRules: Boolean = false,
    ): NotificationPresentation {
        val displayName = defaultReceiver?.displayName()
        val title = when (connectionState) {
            is ConnectionState.Connected -> displayName ?: "Sound Kit Community"
            else -> "Sound Kit Community"
        }
        val statusText = connectionState.asNotificationText(displayName)
        val valveText = when {
            receiverStatusMessage != null -> "Receiver not ready"
            valveState == ValveState.Open -> "Valves open"
            valveState == ValveState.Closed -> "Valves closed"
            else -> "Checking valves"
        }
        val automationText = when {
            automationPaused -> "Automation paused"
            lastExecution != null -> "Last: ${lastExecution.displaySummary()}"
            else -> null
        }
        val contentText = listOf(statusText, valveText, receiverStatusMessage, automationText)
            .filterNotNull()
            .joinToString(" · ")

        val valveControlsEnabled = connectionState is ConnectionState.Connected &&
            valveState != ValveState.Unknown &&
            receiverStatusMessage == null

        val showAutomationActions = hasAutomationRules &&
            connectionState is ConnectionState.Connected

        return NotificationPresentation(
            title = title,
            contentText = contentText,
            ongoing = connectionState is ConnectionState.Connected ||
                connectionState is ConnectionState.Connecting ||
                connectionState is ConnectionState.Reconnecting,
            openValveEnabled = valveControlsEnabled && valveState != ValveState.Open,
            closeValveEnabled = valveControlsEnabled && valveState != ValveState.Closed,
            disconnectEnabled = connectionState is ConnectionState.Connected,
            pauseAutomationEnabled = showAutomationActions && !automationPaused,
            resumeAutomationEnabled = showAutomationActions && automationPaused,
        )
    }

    private fun ConnectionState.asNotificationText(displayName: String?): String {
        return when (this) {
            ConnectionState.Disconnected -> "Disconnected"
            ConnectionState.Scanning -> "Scanning"
            is ConnectionState.Connecting -> "Connecting to ${displayName ?: device.name}"
            is ConnectionState.Connected -> "Connected"
            is ConnectionState.Reconnecting -> "Reconnecting (attempt $attempt)"
            is ConnectionState.Error -> "Error: $message"
        }
    }
}
