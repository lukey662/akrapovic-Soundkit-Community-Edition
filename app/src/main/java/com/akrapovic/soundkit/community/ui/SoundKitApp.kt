package com.akrapovic.soundkit.community.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akrapovic.soundkit.community.ui.control.ConnectedDeviceScreen
import com.akrapovic.soundkit.community.ui.diagnostics.DiagnosticsScreen
import com.akrapovic.soundkit.community.ui.garage.GarageThemeScreen
import com.akrapovic.soundkit.community.ui.more.MoreScreen
import com.akrapovic.soundkit.community.ui.roadmap.RoadmapScreen
import com.akrapovic.soundkit.community.ui.scan.ScanScreen
import com.akrapovic.soundkit.community.ui.settings.SettingsScreen
import com.akrapovic.soundkit.community.ui.theme.GarageThemePresets
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme
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

    // Primary tabs: Scan | Control | More (no back-stack entry, tab-switch resets sub-screen).
    var primaryScreen by remember { mutableStateOf(AppScreen.Scan) }
    // Sub-screens live under More. Non-null means we drilled in from More.
    var subScreen by remember { mutableStateOf<AppScreen?>(null) }

    val screen = subScreen ?: primaryScreen
    val onBack: () -> Unit = { subScreen = null }

    // System back / gesture: pop sub-screen if one is open, otherwise OS handles it.
    BackHandler(enabled = subScreen != null, onBack = onBack)

    val activeTheme = GarageThemePresets.find { it.id == state.settings.garageThemeId }
        ?: GarageThemePresets.first()

    SoundKitTheme(garageTheme = activeTheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AkraTopBar(
                    canGoBack = subScreen != null,
                    onBack = onBack,
                )
            },
            bottomBar = {
                AkraBottomNav(
                    selected = primaryScreen,
                    onSelect = { tab ->
                        primaryScreen = tab
                        subScreen = null
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
                        primaryScreen = AppScreen.Control
                        subScreen = null
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
                    onNavigate = { destination -> subScreen = destination },
                )
                AppScreen.Diagnostics -> DiagnosticsScreen(
                    modifier = modifier,
                    entries = state.diagnostics,
                    hasPendingCrash = state.hasPendingCrash,
                    onBuildReport = viewModel::buildDiagnosticsReport,
                    onCreateReportFile = viewModel::writeDiagnosticsReportFile,
                    onBuildCrashReport = viewModel::buildCrashReport,
                    onCreateCrashReportFile = viewModel::writeCrashReportFile,
                    onCrashHandled = viewModel::clearPendingCrash,
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
                    selectedThemeId = state.settings.garageThemeId,
                    onThemeSelected = viewModel::setGarageTheme,
                )
            }
        }
    }
}

/**
 * Custom branded top bar.
 *
 * When [canGoBack] is true, a back chevron replaces the amber square so the
 * user always has a visible escape from sub-screens in addition to the gesture.
 * The amber hairline below uses the active [LocalAkraTheme] accent so it
 * reacts to theme changes.
 */
@Composable
private fun AkraTopBar(
    canGoBack: Boolean = false,
    onBack: () -> Unit = {},
) {
    val accent = LocalAkraTheme.current.accent
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canGoBack) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clickable(onClick = onBack)
                        .semantics { contentDescription = "Go back" },
                    contentAlignment = Alignment.Center,
                ) {
                    BackChevron(color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
            } else {
                Box(
                    Modifier
                        .size(width = 22.dp, height = 10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = "SOUND KIT",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to accent.copy(alpha = 0.4f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

/**
 * Back-navigation chevron drawn with Canvas.
 * Two lines meeting at a point — no vector asset dependency.
 */
@Composable
private fun BackChevron(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val sw = 1.6.dp.toPx()
        val cx = size.width * 0.62f
        val midY = size.height / 2f
        val arm = size.height * 0.28f
        drawLine(
            color = color,
            start = Offset(cx, midY - arm),
            end = Offset(cx - arm * 1.1f, midY),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(cx - arm * 1.1f, midY),
            end = Offset(cx, midY + arm),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Custom bottom navigation.
 *
 * Uses [LocalAkraTheme] so the selected-tab accent and underline react when
 * the user picks a Garage theme. Tab switch clears any open sub-screen.
 */
@Composable
private fun AkraBottomNav(
    selected: AppScreen,
    onSelect: (AppScreen) -> Unit,
) {
    val primaryTabs = listOf(AppScreen.Scan, AppScreen.Control, AppScreen.More)
    val accent = LocalAkraTheme.current.accent

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            primaryTabs.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tab.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun AppScreen.label(): String {
    return when (this) {
        AppScreen.Scan -> "Find"
        AppScreen.Control -> "Control"
        AppScreen.More -> "More"
        else -> name
    }
}
