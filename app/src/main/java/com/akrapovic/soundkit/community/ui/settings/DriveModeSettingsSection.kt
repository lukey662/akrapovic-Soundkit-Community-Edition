package com.akrapovic.soundkit.community.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.data.DriveModeProfile
import com.akrapovic.soundkit.community.domain.DriveModeSummary
import com.akrapovic.soundkit.community.domain.PreferredValveMode
import com.akrapovic.soundkit.community.domain.QuietStartSettings
import com.akrapovic.soundkit.community.domain.QuietWindowEvaluator
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.ui.components.AkraListDivider
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraListRow
import com.akrapovic.soundkit.community.ui.components.AkraSectionTitle
import com.akrapovic.soundkit.community.ui.components.AkraSegmentedControl
import com.akrapovic.soundkit.community.ui.components.AkraSwitchRow

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private const val MINUTES_PER_DAY = 24 * 60

@Composable
fun DriveModeSettingsSection(
    settings: SoundKitSettings,
    onDriveModeEnabledChanged: (Boolean) -> Unit,
    onPreferredModeChanged: (PreferredValveMode) -> Unit,
    onQuietStartChanged: (QuietStartSettings) -> Unit,
    onDriveModePausedChanged: (Boolean) -> Unit,
    onApplyProfile: ((DriveModeProfile) -> Unit)? = null,
) {
    AkraSectionTitle("Drive mode")
    if (onApplyProfile != null) {
        Text(
            text = "Quick profiles",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val profiles = DriveModeProfile.entries
        val selectedProfile = profiles.firstOrNull { profile ->
            profile.mode == settings.preferredValveMode &&
                profile.quietEnabled == settings.quietStart.enabled
        } ?: DriveModeProfile.Everyday
        AkraSegmentedControl(
            options = profiles,
            selected = selectedProfile,
            label = { it.label },
            onSelected = onApplyProfile,
        )
        Spacer(Modifier.height(12.dp))
    }
    AkraListGroup {
        AkraSwitchRow(
            title = "Drive mode",
            subtitle = "Apply preferred valves when connected",
            checked = settings.driveModeEnabled,
            onCheckedChange = onDriveModeEnabledChanged,
        )
        if (settings.driveModeEnabled) {
            AkraListDivider()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "Preferred valves",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                AkraSegmentedControl(
                    options = listOf(PreferredValveMode.Open, PreferredValveMode.Closed),
                    selected = settings.preferredValveMode,
                    label = { it.name },
                    onSelected = onPreferredModeChanged,
                )
            }
            AkraListDivider()
            AkraSwitchRow(
                title = "Pause drive mode",
                subtitle = "Manual control until you resume",
                checked = settings.automationPaused,
                onCheckedChange = onDriveModePausedChanged,
            )
            AkraListDivider()
            QuietStartRows(
                quietStart = settings.quietStart,
                onQuietStartChanged = onQuietStartChanged,
            )
        }
    }
}

@Composable
private fun QuietStartRows(
    quietStart: QuietStartSettings,
    onQuietStartChanged: (QuietStartSettings) -> Unit,
) {
    var holdMinutes by remember(quietStart.holdClosedMinutes) {
        mutableIntStateOf(quietStart.holdClosedMinutes)
    }
    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }

    AkraSwitchRow(
        title = "Quiet neighbours",
        subtitle = "Hold closed briefly when you connect during the window",
        checked = quietStart.enabled,
        onCheckedChange = { enabled -> onQuietStartChanged(quietStart.copy(enabled = enabled)) },
    )

    if (quietStart.enabled) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Days",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                DAY_LABELS.forEachIndexed { index, label ->
                    val selected = index in quietStart.daysOfWeek
                    TextButton(
                        onClick = {
                            val updated = quietStart.daysOfWeek.toMutableSet().apply {
                                if (selected) remove(index) else add(index)
                            }
                            onQuietStartChanged(quietStart.copy(daysOfWeek = updated))
                        },
                    ) {
                        Text(
                            text = label,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Text(
                text = "Quiet window",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            AkraListRow(
                title = "Start",
                trailing = DriveModeSummary.formatMinute(quietStart.windowStartMinute),
                showChevron = true,
                onClick = { editingStart = true },
            )
            AkraListRow(
                title = "End",
                trailing = DriveModeSummary.formatEndMinute(quietStart),
                showChevron = true,
                onClick = { editingEnd = true },
            )
            if (QuietWindowEvaluator.isOvernight(quietStart)) {
                Text(
                    text = "Runs overnight (ends next morning)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Closes valves for ${holdMinutes.coerceIn(1, 15)} min when you connect during this window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "Hold closed: ${holdMinutes.coerceIn(1, 15)} min",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            Slider(
                value = holdMinutes.coerceIn(1, 15).toFloat(),
                onValueChange = { holdMinutes = it.toInt() },
                onValueChangeFinished = {
                    onQuietStartChanged(quietStart.copy(holdClosedMinutes = holdMinutes.coerceIn(1, 15)))
                },
                valueRange = 1f..15f,
                steps = 13,
            )
        }
    }

    if (editingStart) {
        QuietTimePickerDialog(
            title = "Quiet window start",
            initialMinuteOfDay = quietStart.windowStartMinute,
            onDismiss = { editingStart = false },
            onConfirm = { minute ->
                onQuietStartChanged(quietStart.copy(windowStartMinute = minute))
            },
        )
    }
    if (editingEnd) {
        QuietTimePickerDialog(
            title = "Quiet window end",
            initialMinuteOfDay = quietStart.windowEndMinute,
            onDismiss = { editingEnd = false },
            onConfirm = { minute ->
                onQuietStartChanged(quietStart.copy(windowEndMinute = minute))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietTimePickerDialog(
    title: String,
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val clampedMinute = initialMinuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
    val timeState = rememberTimePickerState(
        initialHour = clampedMinute / 60,
        initialMinute = clampedMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = timeState) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(timeState.hour * 60 + timeState.minute)
                    onDismiss()
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
