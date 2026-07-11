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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akrapovic.soundkit.community.ui.diagnostics.DiagnosticsScreen
import com.akrapovic.soundkit.community.ui.garage.GarageThemeScreen
import com.akrapovic.soundkit.community.ui.home.HomeScreen
import com.akrapovic.soundkit.community.ui.more.AdvancedScreen
import com.akrapovic.soundkit.community.ui.more.AndroidAutoSetupScreen
import com.akrapovic.soundkit.community.ui.more.DeveloperScreen
import com.akrapovic.soundkit.community.ui.more.MoreScreen
import com.akrapovic.soundkit.community.ui.onboarding.OnboardingFlow
import com.akrapovic.soundkit.community.ui.roadmap.RoadmapScreen
import com.akrapovic.soundkit.community.ui.settings.DriveModeScreen
import com.akrapovic.soundkit.community.ui.settings.SettingsScreen
import com.akrapovic.soundkit.community.ui.theme.GarageThemePresets
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundKitApp(
    viewModel: SoundKitViewModel,
    blePermissions: List<String>,
    blePermissionsGranted: Boolean,
    notificationsGranted: Boolean,
    onRequestBlePermissions: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showOnboarding = !state.settings.onboardingCompleted

    LaunchedEffect(blePermissionsGranted, showOnboarding) {
        if (blePermissionsGranted && !showOnboarding) {
            viewModel.tryConnectOnLaunch()
        }
    }

    var primaryScreen by remember { mutableStateOf(AppScreen.Home) }
    var subScreen by remember { mutableStateOf<AppScreen?>(null) }

    val screen = subScreen ?: primaryScreen

    val onBack: () -> Unit = {
        subScreen = null
    }

    BackHandler(enabled = subScreen != null, onBack = onBack)

    val activeTheme = GarageThemePresets.find { it.id == state.settings.garageThemeId }
        ?: GarageThemePresets.first()

    SoundKitTheme(garageTheme = activeTheme) {
        if (showOnboarding) {
            OnboardingFlow(
                blePermissionsGranted = blePermissionsGranted,
                notificationsGranted = notificationsGranted,
                selectedVehicleId = state.settings.selectedVehicleId,
                onAcceptRisk = viewModel::acceptRiskNotice,
                onSelectVehicle = viewModel::setSelectedVehicle,
                onRequestBlePermissions = onRequestBlePermissions,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onComplete = viewModel::completeOnboarding,
            )
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    AkraTopBar(
                        title = screen.topBarTitle(),
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
                    AppScreen.Home -> HomeScreen(
                        modifier = modifier,
                        state = state,
                        permissions = blePermissions,
                        permissionsGranted = blePermissionsGranted,
                        onRequestPermissions = onRequestBlePermissions,
                        onStartScan = viewModel::startScan,
                        onStopScan = viewModel::stopScan,
                        onConnect = {
                            viewModel.connect(it)
                            primaryScreen = AppScreen.Home
                            subScreen = null
                        },
                        onToggleValve = viewModel::toggleValve,
                        onDisconnect = viewModel::disconnect,
                        onRetryConnection = viewModel::retryConnection,
                        onTakeControl = viewModel::takeControl,
                        onSetDefaultReceiver = viewModel::setDefaultReceiver,
                        onOpenDriveMode = {
                            primaryScreen = AppScreen.More
                            subScreen = AppScreen.DriveMode
                        },
                    )
                    AppScreen.More -> MoreScreen(
                        modifier = modifier,
                        onNavigate = { destination -> subScreen = destination },
                    )
                    AppScreen.Diagnostics -> {
                        val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
                        DiagnosticsScreen(
                            modifier = modifier,
                            entries = diagnostics,
                            hasPendingCrash = state.hasPendingCrash,
                            onBuildReport = viewModel::buildDiagnosticsReport,
                            onCreateReportFile = viewModel::writeDiagnosticsReportFile,
                            onBuildCrashReport = viewModel::buildCrashReport,
                            onCreateCrashReportFile = viewModel::writeCrashReportFile,
                            onCrashHandled = viewModel::clearPendingCrash,
                            supportTriageBody = viewModel.buildSupportTriageBody(state),
                            appVersionLabel = viewModel.buildAppVersionLabel(),
                        )
                    }
                    AppScreen.Settings -> SettingsScreen(
                        modifier = modifier,
                        state = state,
                        onAutoReconnectChanged = viewModel::setAutoReconnect,
                        onConnectOnLaunchChanged = viewModel::setConnectOnLaunch,
                        onConnectInCarChanged = viewModel::setConnectInCar,
                        onHeadUnitPriorityChanged = viewModel::setHeadUnitPriorityEnabled,
                        onSetDefaultReceiver = viewModel::setDefaultReceiver,
                        onRemoveReceiver = viewModel::removeReceiver,
                        onUpdateNickname = viewModel::updateNickname,
                        onForgetAll = viewModel::forgetDevice,
                        onDriveModeEnabledChanged = viewModel::setDriveModeEnabled,
                        onPreferredModeChanged = viewModel::setPreferredValveMode,
                        onQuietStartChanged = viewModel::setQuietStart,
                        onDriveModePausedChanged = viewModel::setDriveModePaused,
                        onExportSettingsBackup = viewModel::exportSettingsBackup,
                        onImportSettingsBackup = viewModel::importSettingsBackup,
                        onApplyDriveModeProfile = viewModel::applyDriveModeProfile,
                    )
                    AppScreen.DriveMode -> DriveModeScreen(
                        modifier = modifier,
                        state = state,
                        onDriveModeEnabledChanged = viewModel::setDriveModeEnabled,
                        onPreferredModeChanged = viewModel::setPreferredValveMode,
                        onQuietStartChanged = viewModel::setQuietStart,
                        onDriveModePausedChanged = viewModel::setDriveModePaused,
                        onApplyDriveModeProfile = viewModel::applyDriveModeProfile,
                    )
                    AppScreen.Roadmap -> RoadmapScreen(
                        modifier = modifier,
                    )
                    AppScreen.GarageThemes -> GarageThemeScreen(
                        modifier = modifier,
                        selectedThemeId = state.settings.garageThemeId,
                        onThemeSelected = viewModel::setGarageTheme,
                    )
                    AppScreen.AndroidAutoSetup -> AndroidAutoSetupScreen(
                        modifier = modifier,
                    )
                    AppScreen.Advanced -> AdvancedScreen(
                        modifier = modifier,
                        onNavigate = { destination -> subScreen = destination },
                    )
                    AppScreen.Developer -> DeveloperScreen(
                        modifier = modifier,
                        debugLoggingEnabled = state.settings.debugLoggingEnabled,
                        onDebugLoggingChanged = viewModel::setDebugLogging,
                    )
                }
            }
        }
    }
}

@Composable
private fun AkraTopBar(
    title: String = "Sound Kit",
    canGoBack: Boolean = false,
    onBack: () -> Unit = {},
) {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canGoBack) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable(onClick = onBack)
                        .semantics { contentDescription = "Go back" },
                    contentAlignment = Alignment.Center,
                ) {
                    BackChevron(color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

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

@Composable
private fun AkraBottomNav(
    selected: AppScreen,
    onSelect: (AppScreen) -> Unit,
) {
    val primaryTabs = listOf(AppScreen.Home, AppScreen.More)
    val accent = LocalAkraTheme.current.accent

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            primaryTabs.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(tab) }
                        .semantics {
                            role = androidx.compose.ui.semantics.Role.Tab
                            this.selected = isSelected
                            contentDescription = "${tab.label()} tab"
                        }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tab.label(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun AppScreen.topBarTitle(): String = when (this) {
    AppScreen.Home -> "Sound Kit"
    AppScreen.More -> "More"
    AppScreen.Settings -> "Settings"
    AppScreen.DriveMode -> "Drive mode"
    AppScreen.Diagnostics -> "Diagnostics"
    AppScreen.Roadmap -> "Roadmap"
    AppScreen.GarageThemes -> "Appearance"
    AppScreen.AndroidAutoSetup -> "Android Auto"
    AppScreen.Advanced -> "Advanced"
    AppScreen.Developer -> "Developer"
}

private fun AppScreen.label(): String {
    return when (this) {
        AppScreen.Home -> "Home"
        AppScreen.More -> "More"
        else -> name
    }
}
