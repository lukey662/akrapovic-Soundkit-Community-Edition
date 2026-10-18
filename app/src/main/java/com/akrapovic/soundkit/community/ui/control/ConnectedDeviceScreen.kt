package com.akrapovic.soundkit.community.ui.control

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.domain.CommandFailureCopy
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.QuietHoldCopy
import com.akrapovic.soundkit.community.domain.QuietStartSettings
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraBanner
import com.akrapovic.soundkit.community.ui.components.AkraListDivider
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraListRow
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill
import com.akrapovic.soundkit.community.ui.components.BrandStripes
import com.akrapovic.soundkit.community.ui.components.ReceiverNotReadyChecklistContent
import com.akrapovic.soundkit.community.ui.components.ValveVisual
import com.akrapovic.soundkit.community.ui.components.rememberQuietHoldNow
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import com.akrapovic.soundkit.community.ui.theme.GarageThemeFamilies
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ConnectedDeviceScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    onToggleValve: () -> Unit,
    onDisconnect: () -> Unit,
    onRetryConnection: () -> Unit = {},
    onOpenDriveMode: () -> Unit = {},
) {
    val view = LocalView.current
    val showReceiverLearnMore = remember { mutableStateOf(false) }
    val showDisconnectConfirm = remember { mutableStateOf(false) }
    val wasCommandInFlight = remember { mutableStateOf(false) }
    val announcedError = remember { mutableStateOf<String?>(null) }
    val successRippleTrigger = remember { mutableIntStateOf(0) }
    val quietNow = rememberQuietHoldNow(state.quietHoldEndsAtMillis)
    val quietCountdown = state.quietHoldEndsAtMillis?.let { endsAt ->
        if (state.valveState == ValveState.Closed && endsAt > quietNow) {
            QuietHoldCopy.phoneHelper(endsAt, quietNow)
        } else {
            null
        }
    }

    LaunchedEffect(state.commandInFlight, state.lastError) {
        if (wasCommandInFlight.value && !state.commandInFlight && state.lastError == null) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            successRippleTrigger.intValue += 1
        }
        if (state.lastError != null && state.lastError != announcedError.value) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            announcedError.value = state.lastError
        } else if (state.lastError == null) {
            announcedError.value = null
        }
        wasCommandInFlight.value = state.commandInFlight
    }

    val connectedDevice = state.activeDevice()
    val action = state.primaryValveAction()
    val controlsEnabled = connectedDevice != null &&
        state.connectionState is ConnectionState.Connected &&
        !state.commandInFlight &&
        state.protocolVerified &&
        state.valveState != ValveState.Unknown &&
        state.receiverStatusMessage == null

    val theme = LocalAkraTheme.current
    val familyName = GarageThemeFamilies.firstOrNull { it.contains(theme.id) }?.name ?: theme.name
    val confirmedLabel = rememberConfirmedLabel(state.valveStateConfirmedAtMillis)

    AkraScreen(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Car Sound Kit",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BrandStripes(theme)
                    Text(
                        text = familyName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ReceiverChip(state = state, device = connectedDevice)
        }

        if (state.connectionState is ConnectionState.Connecting) {
            AkraBanner(
                title = "Connecting",
                body = "Preparing ${connectedDevice?.name ?: "receiver"} for valve control.",
                accent = AkraColors.Amber,
            )
        }

        if (state.connectionState is ConnectionState.Reconnecting) {
            val reconnecting = state.connectionState as ConnectionState.Reconnecting
            AkraBanner(
                title = "Reconnecting · attempt ${reconnecting.attempt}",
                body = "Checking the link to ${reconnecting.device.name}.",
                accent = AkraColors.Amber,
                actionLabel = "Retry ${reconnecting.device.name}",
                onAction = onRetryConnection,
            )
        }

        if (state.receiverStatusMessage != null) {
            AkraBanner(
                title = "Receiver not ready",
                body = state.receiverStatusMessage,
                accent = AkraColors.Amber,
                actionLabel = "Learn more",
                onAction = { showReceiverLearnMore.value = true },
            )
        } else if (state.lastError != null) {
            val failure = CommandFailureCopy.from(state.lastError, state.valveState)
            AkraBanner(
                title = failure.title,
                body = failure.body,
                accent = if (state.valveState == ValveState.Unknown) AkraColors.Amber else MaterialTheme.colorScheme.error,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "${state.valveState.displayTitle()}. " +
                        state.valveState.helperText(state.receiverStatusMessage)
                    stateDescription = state.valveState.displayTitle()
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            ValveVisual(
                state = state.valveState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                commandInFlight = state.commandInFlight,
                successRippleTrigger = successRippleTrigger.intValue,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.valveState.displayTitle(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                state.valveState.modeTag()?.let { tag -> AkraStatusPill(text = tag) }
            }
            Text(
                text = quietCountdown ?: state.valveState.helperText(state.receiverStatusMessage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (confirmedLabel != null) {
                Text(
                    text = confirmedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AkraActionButton(
            label = if (state.commandInFlight) "Changing…" else action.button,
            enabled = controlsEnabled && !state.commandInFlight,
            contentDescription = action.contentDescription,
            onClick = onToggleValve,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )

        AkraListGroup {
            AkraListRow(
                title = "Drive mode",
                trailing = state.settings.driveModeLabel(),
                showChevron = true,
                leadingIcon = ImageVector.vectorResource(R.drawable.ic_row_drive),
                onClick = onOpenDriveMode,
            )
            AkraListDivider()
            AkraListRow(
                title = "Quiet start",
                trailing = state.settings.quietStart.windowLabel(),
                showChevron = true,
                leadingIcon = ImageVector.vectorResource(R.drawable.ic_row_quiet),
                onClick = onOpenDriveMode,
            )
            AkraListDivider()
            AkraListRow(
                title = connectedDevice?.name ?: "Receiver",
                trailing = "Disconnect",
                leadingIcon = ImageVector.vectorResource(R.drawable.ic_row_receiver),
                onClick = { if (connectedDevice != null) showDisconnectConfirm.value = true },
                contentDescription = "Disconnect from receiver",
            )
        }

        if (showReceiverLearnMore.value) {
            AlertDialog(
                onDismissRequest = { showReceiverLearnMore.value = false },
                title = { Text("Receiver not ready") },
                text = { ReceiverNotReadyChecklistContent() },
                confirmButton = {
                    TextButton(onClick = { showReceiverLearnMore.value = false }) {
                        Text("OK")
                    }
                },
            )
        }

        if (showDisconnectConfirm.value) {
            AlertDialog(
                onDismissRequest = { showDisconnectConfirm.value = false },
                title = { Text("Disconnect from receiver?") },
                text = {
                    Text("The valves will keep their current position. You can reconnect from Home.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDisconnectConfirm.value = false
                        onDisconnect()
                    }) { Text("Disconnect") }
                },
                dismissButton = {
                    TextButton(onClick = { showDisconnectConfirm.value = false }) { Text("Cancel") }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReceiverChip(state: SoundKitUiState, device: SoundKitDevice?) {
    val dot = when (state.connectionState) {
        is ConnectionState.Connected -> AkraColors.Signal
        is ConnectionState.Connecting, is ConnectionState.Reconnecting -> AkraColors.Amber
        is ConnectionState.Error -> AkraColors.Danger
        else -> AkraColors.Mist
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Text(
            text = "${device?.name ?: "Receiver"} · ${state.connectionState.shortText()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** "Confirmed just now", "Confirmed 12 s ago", or "Confirmed 3 min ago", refreshed every 10 s. */
@Composable
private fun rememberConfirmedLabel(confirmedAtMillis: Long?): String? {
    val now by produceState(initialValue = System.currentTimeMillis(), confirmedAtMillis) {
        while (confirmedAtMillis != null) {
            value = System.currentTimeMillis()
            delay(10_000)
        }
    }
    if (confirmedAtMillis == null) return null
    val seconds = ((now - confirmedAtMillis) / 1_000L).coerceAtLeast(0L)
    return when {
        seconds < 10 -> "Confirmed just now"
        seconds < 60 -> "Confirmed $seconds s ago"
        else -> "Confirmed ${seconds / 60} min ago"
    }
}

private fun SoundKitSettings.driveModeLabel(): String = when {
    !driveModeEnabled -> "Off"
    automationPaused -> "Paused"
    else -> "Auto"
}

private fun QuietStartSettings.windowLabel(): String {
    if (!enabled) return "Off"
    fun clock(minute: Int) = String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60)
    return "${clock(windowStartMinute)}–${clock(windowEndMinute)}"
}

private fun ValveState.modeTag(): String? = when (this) {
    ValveState.Open -> "SPORT"
    ValveState.Closed -> "QUIET"
    ValveState.Unknown -> null
}

private enum class ValveAction(
    val button: String,
    val contentDescription: String,
) {
    Open("Open valves", "Open exhaust valves"),
    Close("Close valves", "Close exhaust valves"),
    Wait("Waiting for status", "Valve controls waiting for receiver status"),
    NotReady("Receiver not ready", "Receiver not ready for valve control"),
}

private fun SoundKitUiState.primaryValveAction(): ValveAction {
    if (receiverStatusMessage != null) return ValveAction.NotReady
    return when (valveState) {
        ValveState.Closed -> ValveAction.Open
        ValveState.Open -> ValveAction.Close
        ValveState.Unknown -> ValveAction.Wait
    }
}

private fun SoundKitUiState.activeDevice(): SoundKitDevice? {
    return when (val connection = connectionState) {
        is ConnectionState.Connected -> connection.device
        is ConnectionState.Connecting -> connection.device
        is ConnectionState.Reconnecting -> connection.device
        else -> null
    }
}

private fun ValveState.displayTitle(): String = when (this) {
    ValveState.Open -> "Open"
    ValveState.Closed -> "Closed"
    ValveState.Unknown -> "Checking…"
}

private fun ValveState.helperText(receiverNotReady: String?): String {
    if (receiverNotReady != null) return "Receiver not ready"
    return when (this) {
        ValveState.Open -> "Full exhaust note"
        ValveState.Closed -> "Valves sealed"
        ValveState.Unknown -> "Waiting for the receiver"
    }
}

private fun ConnectionState.shortText(): String = when (this) {
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.Scanning -> "Scanning"
    is ConnectionState.Connecting -> "Connecting"
    is ConnectionState.Connected -> "Connected"
    is ConnectionState.Reconnecting -> "Reconnecting"
    is ConnectionState.Error -> "Needs attention"
    is ConnectionState.Away -> "Away"
}
