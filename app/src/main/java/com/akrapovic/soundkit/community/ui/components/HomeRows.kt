package com.akrapovic.soundkit.community.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.domain.QuietStartSettings
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import java.util.Locale

/** Drive mode row shared by both Home states; the trailing label is the mode at a glance. */
@Composable
fun DriveModeRow(settings: SoundKitSettings, onClick: () -> Unit) {
    AkraListRow(
        title = "Drive mode",
        trailing = settings.driveModeLabel(),
        showChevron = true,
        leadingIcon = ImageVector.vectorResource(R.drawable.ic_row_drive),
        onClick = onClick,
    )
}

/** Quiet start row shared by both Home states; the trailing label is the window or "Off". */
@Composable
fun QuietStartRow(settings: SoundKitSettings, onClick: () -> Unit) {
    AkraListRow(
        title = "Quiet start",
        trailing = settings.quietStart.windowLabel(),
        showChevron = true,
        leadingIcon = ImageVector.vectorResource(R.drawable.ic_row_quiet),
        onClick = onClick,
    )
}

fun SoundKitSettings.driveModeLabel(): String = when {
    !driveModeEnabled -> "Off"
    automationPaused -> "Paused"
    else -> "Auto"
}

fun QuietStartSettings.windowLabel(): String {
    if (!enabled) return "Off"
    fun clock(minute: Int) = String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60)
    return "${clock(windowStartMinute)}–${clock(windowEndMinute)}"
}
