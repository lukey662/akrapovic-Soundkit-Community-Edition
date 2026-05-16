package com.akrapovic.soundkit.community.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akrapovic.soundkit.community.ui.control.ConnectedDeviceScreen
import com.akrapovic.soundkit.community.ui.diagnostics.DiagnosticsScreen
import com.akrapovic.soundkit.community.ui.garage.GarageThemeScreen
import com.akrapovic.soundkit.community.ui.more.MoreScreen
import com.akrapovic.soundkit.community.ui.roadmap.RoadmapScreen
import com.akrapovic.soundkit.community.ui.scan.ScanScreen
import com.akrapovic.soundkit.community.ui.settings.SettingsScreen
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import com.akrapovic.soundkit.community.ui.theme.GarageThemePresets
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundKitApp(
    viewModel: SoundKitViewModel,
    permissions: List<String>,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(AppScreen.Scan) }
    var selectedGarageThemeId by remember { mutableStateOf(GarageThemePresets.first().id) }

    SoundKitTheme {
        Scaffold(
            // Force the page background to ink so panels read as
            // raised carbon on top, not flat Material surfaces.
            containerColor = AkraColors.Ink,
            topBar = { AkraTopBar() },
            bottomBar = {
                AkraBottomNav(
                    selected = screen,
                    onSelect = { tab ->
                        screen = tab
                    },
                )
            },
        ) { paddingValues ->
            val modifier = Modifier.padding(paddingValues)
            when (screen) {
                AppScreen.Scan -> ScanScreen(
                    modifier = modifier,
                    state = state,
                    permissions = permissions,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = onRequestPermissions,
                    onStartScan = viewModel::startScan,
                    onStopScan = viewModel::stopScan,
                    onConnect = {
                        viewModel.connect(it)
                        screen = AppScreen.Control
                    },
                )
                AppScreen.Control -> ConnectedDeviceScreen(
                    modifier = modifier,
                    state = state,
                    onOpen = viewModel::openValve,
                    onClose = viewModel::closeValve,
                    onDisconnect = viewModel::disconnect,
                )
                AppScreen.More -> MoreScreen(
                    modifier = modifier,
                    onNavigate = { destination -> screen = destination },
                )
                AppScreen.Diagnostics -> DiagnosticsScreen(
                    modifier = modifier,
                    entries = state.diagnostics,
                )
                AppScreen.Settings -> SettingsScreen(
                    modifier = modifier,
                    state = state,
                    onAutoReconnectChanged = viewModel::setAutoReconnect,
                    onDebugLoggingChanged = viewModel::setDebugLogging,
                    onForgetDevice = viewModel::forgetDevice,
                )
                AppScreen.Roadmap -> RoadmapScreen(
                    modifier = modifier,
                )
                AppScreen.GarageThemes -> GarageThemeScreen(
                    modifier = modifier,
                    selectedThemeId = selectedGarageThemeId,
                    onThemeSelected = { selectedGarageThemeId = it },
                )
            }
        }
    }
}

/**
 * Custom branded top bar.
 *
 * Why not Material's TopAppBar:
 *   - We want the brand mark to behave as a hero element, not a Material chip.
 *   - The full-width amber gradient hairline below acts as a subtle
 *     "instrument cluster" boundary.
 *
 * Composition:
 *   - 8.dp solid amber square graphic anchor (no logo asset required;
 *     swap for an SVG/PathPainter later without touching this layout).
 *   - "AKRAPOVIČ" tracked, Pearl text — primary brand mark.
 *   - 1.dp vertical hairline divider.
 *   - "SOUND KIT" tracked, Mist text — product mark.
 */
@Composable
private fun AkraTopBar() {
    Column(Modifier.background(AkraColors.Ink)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(AkraColors.Amber),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "AKRAPOVIČ",
                style = MaterialTheme.typography.labelLarge,
                color = AkraColors.Pearl,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .height(12.dp)
                    .width(1.dp)
                    .background(AkraColors.Titanium),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "SOUND KIT",
                style = MaterialTheme.typography.labelLarge,
                color = AkraColors.Mist,
            )
        }
        // Amber-kissed hairline. Almost invisible, but anchors the header.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to AkraColors.Amber.copy(alpha = 0.4f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

/**
 * Custom bottom navigation.
 *
 * Why not Material's NavigationBar:
 *   - Material's NavigationBar pill highlight does not match a HUD aesthetic.
 *   - We want tracked uppercase labels and a thin amber selection underline,
 *     which reads like a track-side telemetry bar.
 *
 * Accessibility:
 *   - Each tab is a clickable Column with the tab name as visible text,
 *     so TalkBack reads it correctly without extra semantics.
 */
@Composable
private fun AkraBottomNav(
    selected: AppScreen,
    onSelect: (AppScreen) -> Unit,
) {
    val primaryTabs = listOf(AppScreen.Scan, AppScreen.Control, AppScreen.More)
    val selectedPrimary = if (selected in primaryTabs) selected else AppScreen.More

    Column(
        Modifier
            .fillMaxWidth()
            .background(AkraColors.Carbon)
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AkraColors.Titanium),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            primaryTabs.forEach { tab ->
                val isSelected = tab == selectedPrimary
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tab.name.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) AkraColors.Amber else AkraColors.Mist,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(2.dp)
                            .width(if (isSelected) 24.dp else 0.dp)
                            .background(AkraColors.Amber),
                    )
                }
            }
        }
    }
}
