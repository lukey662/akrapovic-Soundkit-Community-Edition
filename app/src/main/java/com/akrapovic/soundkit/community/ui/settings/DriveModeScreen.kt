package com.akrapovic.soundkit.community.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akrapovic.soundkit.community.data.DriveModeProfile
import com.akrapovic.soundkit.community.domain.PreferredValveMode
import com.akrapovic.soundkit.community.domain.QuietStartSettings
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.components.AkraScreen

@Composable
fun DriveModeScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    onDriveModeEnabledChanged: (Boolean) -> Unit,
    onPreferredModeChanged: (PreferredValveMode) -> Unit,
    onQuietStartChanged: (QuietStartSettings) -> Unit,
    onDriveModePausedChanged: (Boolean) -> Unit,
    onApplyDriveModeProfile: (DriveModeProfile) -> Unit = {},
) {
    AkraScreen(modifier = modifier) {
        DriveModeSettingsSection(
            settings = state.settings,
            onDriveModeEnabledChanged = onDriveModeEnabledChanged,
            onPreferredModeChanged = onPreferredModeChanged,
            onQuietStartChanged = onQuietStartChanged,
            onDriveModePausedChanged = onDriveModePausedChanged,
            onApplyProfile = onApplyDriveModeProfile,
        )
    }
}
