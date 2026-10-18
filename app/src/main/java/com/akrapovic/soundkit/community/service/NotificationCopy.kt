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
    val retryEnabled: Boolean = false,
    val dismissibleAway: Boolean = false,
    val chronometerEndsAtMillis: Long? = null,
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
        quietHoldEndsAtMillis: Long? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): NotificationPresentation {
        val displayName = defaultReceiver?.displayName()
        val title = when (connectionState) {
            is ConnectionState.Connected -> displayName ?: "Car Sound Kit Control"
            else -> "Car Sound Kit Control"
        }
        val away = connectionState is ConnectionState.Away
        val quietCountdown = quietHoldEndsAtMillis?.takeIf { endsAt ->
            connectionState is ConnectionState.Connected &&
                valveState == ValveState.Closed &&
                endsAt > nowMillis
        }
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
            quietCountdown != null -> "Quiet start"
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
            retryEnabled = connectionState is ConnectionState.Error && defaultReceiver != null,
            dismissibleAway = away && !periodicScanWhenAway,
            chronometerEndsAtMillis = quietCountdown,
        )
    }

    private fun ConnectionState.asNotificationText(displayName: String?): String {
        return when (this) {
            ConnectionState.Disconnected -> "Disconnected"
            ConnectionState.Scanning -> "Scanning"
            is ConnectionState.Connecting -> "Connecting to ${displayName ?: device.name}"
            is ConnectionState.Connected -> "Connected"
            is ConnectionState.Reconnecting -> "Reconnecting…"
            is ConnectionState.Error -> ConnectionErrorCopy.friendly(message)
            is ConnectionState.Away -> AwayCopy.statusText(reason, sinceMillis)
        }
    }
}

/** Plain-language connection errors for the shade; raw GATT text stays in diagnostics. */
object ConnectionErrorCopy {
    private val gattStatus = Regex("status (\\d+)")

    fun friendly(message: String): String {
        val text = message.lowercase()
        return when {
            "gatt disconnected" in text && gattStatus.find(text) != null -> "Receiver dropped the connection"
            "service discovery" in text || "characteristic was not found" in text -> "Receiver didn't respond"
            "pairing" in text -> "Pairing was cancelled"
            "notification" in text -> "Receiver didn't start sending status"
            else -> "Connection problem"
        }
    }
}
