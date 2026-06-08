package com.akrapovic.soundkit.community.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akrapovic.soundkit.community.domain.DriveModeSummary
import com.akrapovic.soundkit.community.domain.SoundKitSettings

@Composable
fun DriveModeShortcutRow(
    settings: SoundKitSettings,
    onClick: () -> Unit,
) {
    AkraListRow(
        title = "Drive mode",
        subtitle = DriveModeSummary.headline(settings),
        trailing = null,
        showChevron = true,
        onClick = onClick,
        contentDescription = "Open drive mode settings",
    )
}

/** @deprecated Use [DriveModeShortcutRow] in list layouts. */
@Composable
fun DriveModeShortcutCard(
    modifier: Modifier = Modifier,
    settings: SoundKitSettings,
    onClick: () -> Unit,
) {
    AkraListGroup(modifier = modifier) {
        DriveModeShortcutRow(settings = settings, onClick = onClick)
    }
}
