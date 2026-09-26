package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.AwayCopy
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
    val pauseDriveModeEnabled: Boolean,
    val resumeDriveModeEnabled: Boolean,
    val connectEnabled: Boolean,
)

object NotificationCopy {
    fun build(
        connectionState: ConnectionState,
        valveState: ValveState,
        receiverStatusMessage: String?,
        defaultReceiver: SavedReceiver?,
        driveModeEnabled: Boolean = true,
        driveModePaused: Boolean = false,
        lastExecution: RuleExecutionEntry? = null,
        periodicScanWhenAway: Boolean = false,
    ): NotificationPresentation {
        val displayName = defaultReceiver?.displayName()
        val title = when (connectionState) {
            is ConnectionState.Connected -> displayName ?: "Sound Kit Community"
            else -> "Sound Kit Community"
        }
        val away = connectionState is ConnectionState.Away
        val statusText = connectionState.asNotificationText(displayName)
        val valveText = when {
            away -> null
            receiverStatusMessage != null -> "Receiver not ready"
            valveState == ValveState.Open -> "Valves open"
            valveState == ValveState.Closed -> "Valves closed"
            else -> "Checking valves"
        }
        val driveModeText = when {
            away -> null
            !driveModeEnabled -> "Drive mode off"
            driveModePaused -> "Drive mode paused"
            lastExecution?.ruleName == "Drive mode" -> "Last: Drive mode → ${lastExecution.action}"
            else -> null
        }
        val contentText = listOf(statusText, valveText, receiverStatusMessage, driveModeText)
            .filterNotNull()
            .joinToString(" · ")

        val valveControlsEnabled = connectionState is ConnectionState.Connected &&
            valveState != ValveState.Unknown &&
            receiverStatusMessage == null

        val showDriveModeActions = driveModeEnabled &&
            connectionState is ConnectionState.Connected

        return NotificationPresentation(
            title = title,
            contentText = contentText,
            ongoing = connectionState is ConnectionState.Connected ||
                connectionState is ConnectionState.Connecting ||
                connectionState is ConnectionState.Reconnecting ||
                (away && periodicScanWhenAway),
            openValveEnabled = valveControlsEnabled && valveState != ValveState.Open,
            closeValveEnabled = valveControlsEnabled && valveState != ValveState.Closed,
            disconnectEnabled = connectionState is ConnectionState.Connected,
            pauseDriveModeEnabled = showDriveModeActions && !driveModePaused,
            resumeDriveModeEnabled = showDriveModeActions && driveModePaused,
            connectEnabled = away,
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
            is ConnectionState.Away -> AwayCopy.statusText(reason, sinceMillis)
        }
    }
}
