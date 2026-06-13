package com.akrapovic.soundkit.community.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.diagnostics.CrashReporter
import com.akrapovic.soundkit.community.diagnostics.DiagnosticsReportBuilder
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import com.akrapovic.soundkit.community.domain.DriveModeEngine
import com.akrapovic.soundkit.community.domain.PreferredValveMode
import com.akrapovic.soundkit.community.domain.QuietStartSettings
import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DiagnosticsEntry
import com.akrapovic.soundkit.community.domain.DiagnosticsLevel
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.testDeviceForSmoke
import com.akrapovic.soundkit.community.ui.control.ConnectedDeviceScreen
import com.akrapovic.soundkit.community.ui.diagnostics.DiagnosticsScreen
import com.akrapovic.soundkit.community.ui.garage.GarageThemeScreen
import com.akrapovic.soundkit.community.ui.more.AdvancedScreen
import com.akrapovic.soundkit.community.ui.more.MoreScreen
import com.akrapovic.soundkit.community.ui.onboarding.OnboardingFlow
import com.akrapovic.soundkit.community.ui.roadmap.RoadmapScreen
import com.akrapovic.soundkit.community.ui.scan.ScanScreen
import com.akrapovic.soundkit.community.ui.settings.SettingsScreen
import com.akrapovic.soundkit.community.ui.theme.GarageThemePresets
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

private class SmokeRuleExecutionLogStore : RuleExecutionLogStore {
    override val entries = MutableStateFlow<List<RuleExecutionEntry>>(emptyList())
    override val lastExecution = MutableStateFlow<RuleExecutionEntry?>(null)

    override suspend fun append(entry: RuleExecutionEntry) {
        lastExecution.value = entry
    }

    override suspend fun clear() {
        lastExecution.value = null
    }
}

private fun smokeDriveModeEngine(
    bleRepository: BleRepository,
    settingsRepository: SettingsStore,
    diagnosticsRepository: DiagnosticsRepository,
) = DriveModeEngine(
    bleRepository = bleRepository,
    settingsStore = settingsRepository,
    executionLog = SmokeRuleExecutionLogStore(),
    diagnosticsRepository = diagnosticsRepository,
)

@SdkSuppress(maxSdkVersion = 35)
class ComposeSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scanScreenShowsPermissionRationale() {
        composeRule.setContent {
            SoundKitTheme {
                ScanScreen(
                    state = SoundKitUiState(),
                    permissions = listOf("android.permission.BLUETOOTH_SCAN"),
                    permissionsGranted = false,
                    onRequestPermissions = {},
                    onStartScan = {},
                    onStopScan = {},
                    onConnect = {},
                )
            }
        }

        composeRule.onNodeWithText("Bluetooth permission required").assertIsDisplayed()
        composeRule.onNodeWithText("Grant permissions").assertIsDisplayed()
    }

    @Test
    fun scanScreenShowsEmptyStateWithScanAction() {
        composeRule.setContent {
            SoundKitTheme {
                ScanScreen(
                    state = SoundKitUiState(isScanning = false),
                    permissions = emptyList(),
                    permissionsGranted = true,
                    onRequestPermissions = {},
                    onStartScan = {},
                    onStopScan = {},
                    onConnect = {},
                )
            }
        }

        composeRule.onNodeWithText("No receivers yet").assertIsDisplayed()
        composeRule.onNodeWithText("Scan nearby").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Scan for Sound Kit receiver").assertIsDisplayed()
    }

    @Test
    fun scanScreenShowsConnectionErrorRetry() {
        composeRule.setContent {
            SoundKitTheme {
                ScanScreen(
                    state = SoundKitUiState(
                        connectionState = ConnectionState.Error("Link lost", recoverable = true),
                    ),
                    permissions = emptyList(),
                    permissionsGranted = true,
                    onRequestPermissions = {},
                    onStartScan = {},
                    onStopScan = {},
                    onConnect = {},
                    onRetryConnection = {},
                )
            }
        }

        composeRule.onNodeWithText("Could not connect").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Retry connection to remembered receiver").assertIsDisplayed()
    }

    @Test
    fun controlScreenWaitsForReceiverStatusBeforeCommands() {
        val device = testDeviceForSmoke()
        composeRule.setContent {
            SoundKitTheme {
                ConnectedDeviceScreen(
                    state = SoundKitUiState(
                        connectionState = ConnectionState.Connected(device),
                        valveState = ValveState.Unknown,
                        protocolVerified = true,
                    ),
                    onToggleValve = {},
                    onDisconnect = {},
                )
            }
        }

        composeRule.onNodeWithText("Waiting for status").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Valve controls waiting for receiver status").assertIsNotEnabled()
    }

    @Test
    fun diagnosticsScreenShowsExportActionsAndHandlesDuplicateTimestamps() {
        composeRule.setContent {
            SoundKitTheme {
                DiagnosticsScreen(
                    entries = listOf(
                        DiagnosticsEntry(
                            id = 0L,
                            timestampMillis = 1_000L,
                            level = DiagnosticsLevel.Info,
                            message = "GATT services discovered",
                        ),
                        DiagnosticsEntry(
                            id = 1L,
                            timestampMillis = 1_000L,
                            level = DiagnosticsLevel.Debug,
                            message = "GATT PROFILE START\nGATT PROFILE END",
                        ),
                    ),
                    onBuildReport = { "report" },
                )
            }
        }

        composeRule.onNodeWithText("Diagnostics").assertIsDisplayed()
        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsDisplayed()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
    }

    @Test
    fun diagnosticsScreenShowsEmptyStateHint() {
        composeRule.setContent {
            SoundKitTheme {
                DiagnosticsScreen(
                    entries = emptyList(),
                    onBuildReport = { "report" },
                )
            }
        }

        composeRule.onNodeWithText("No diagnostics yet").assertIsDisplayed()
        composeRule.onNodeWithText("Copy").assertIsNotEnabled()
    }

    @Test
    fun diagnosticsScreenShowsPendingCrashPanel() {
        composeRule.setContent {
            SoundKitTheme {
                DiagnosticsScreen(
                    entries = emptyList(),
                    hasPendingCrash = true,
                    onBuildReport = { "report" },
                    onBuildCrashReport = { "crash" },
                )
            }
        }

        composeRule.onNodeWithText("Crash detected on last session").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Save crash log to a file").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Share crash log file").assertIsDisplayed()
    }

    @Test
    fun settingsScreenShowsConnectionAndBatteryControls() {
        composeRule.setContent {
            SoundKitTheme {
                SettingsScreen(
                    state = SoundKitUiState(),
                    onAutoReconnectChanged = {},
                    onConnectOnLaunchChanged = {},
                    onSetDefaultReceiver = {},
                    onRemoveReceiver = {},
                    onUpdateNickname = { _, _ -> },
                    onForgetAll = {},
                    onDriveModeEnabledChanged = {},
                    onPreferredModeChanged = {},
                    onQuietStartChanged = {},
                    onDriveModePausedChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("Connect on launch").assertIsDisplayed()
        composeRule.onNodeWithText("Drive mode").assertIsDisplayed()
        composeRule.onNodeWithText("Auto reconnect").assertIsDisplayed()
        composeRule.onNodeWithText("Background connection").assertIsDisplayed()
    }

    @Test
    fun settingsScreenShowsSavedReceiversAndDefaultStar() {
        val receiver = SavedReceiver(
            address = "AA:BB:CC:DD:EE:FF",
            name = "Sound Kit",
            nickname = "Garage",
            isDefault = true,
        )
        composeRule.setContent {
            SoundKitTheme {
                SettingsScreen(
                    state = SoundKitUiState(
                        settings = SoundKitSettings(
                            savedReceivers = listOf(receiver),
                            connectOnLaunch = true,
                        ),
                    ),
                    onAutoReconnectChanged = {},
                    onConnectOnLaunchChanged = {},
                    onSetDefaultReceiver = {},
                    onRemoveReceiver = {},
                    onUpdateNickname = { _, _ -> },
                    onForgetAll = {},
                    onDriveModeEnabledChanged = {},
                    onPreferredModeChanged = {},
                    onQuietStartChanged = {},
                    onDriveModePausedChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("Garage").assertIsDisplayed()
        composeRule.onNodeWithText("★ Default").assertIsDisplayed()
        composeRule.onNodeWithText("Connect on launch").assertIsDisplayed()
    }

    @Test
    fun appShellShowsHomeWhenOnboardingComplete() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crashReporter = CrashReporter(context)
        val settingsRepository = FakeSettingsStoreForSmoke(
            initial = SoundKitSettings(onboardingCompletedAt = 1L),
        )
        val diagnosticsRepository = DiagnosticsRepository()
        composeRule.setContent {
            SoundKitApp(
                viewModel = SoundKitViewModel(
                    bleRepository = FakeBleRepositoryForSmoke(),
                    settingsRepository = settingsRepository,
                    diagnosticsRepository = diagnosticsRepository,
                    diagnosticsReportBuilder = DiagnosticsReportBuilder(context, crashReporter),
                    crashReporter = crashReporter,
                    driveModeEngine = smokeDriveModeEngine(
                        FakeBleRepositoryForSmoke(),
                        settingsRepository,
                        diagnosticsRepository,
                    ),
                ),
                blePermissions = emptyList(),
                blePermissionsGranted = true,
                notificationsGranted = true,
                onRequestBlePermissions = {},
                onRequestNotificationPermission = {},
            )
        }

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("More").assertIsDisplayed()
    }

    @Test
    fun onboardingFlowShownWhenNotComplete() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crashReporter = CrashReporter(context)
        val settingsRepository = FakeSettingsStoreForSmoke(
            initial = SoundKitSettings(onboardingCompletedAt = 0L),
        )
        val diagnosticsRepository = DiagnosticsRepository()
        composeRule.setContent {
            SoundKitApp(
                viewModel = SoundKitViewModel(
                    bleRepository = FakeBleRepositoryForSmoke(),
                    settingsRepository = settingsRepository,
                    diagnosticsRepository = diagnosticsRepository,
                    diagnosticsReportBuilder = DiagnosticsReportBuilder(context, crashReporter),
                    crashReporter = crashReporter,
                    driveModeEngine = smokeDriveModeEngine(
                        FakeBleRepositoryForSmoke(),
                        settingsRepository,
                        diagnosticsRepository,
                    ),
                ),
                blePermissions = emptyList(),
                blePermissionsGranted = true,
                notificationsGranted = true,
                onRequestBlePermissions = {},
                onRequestNotificationPermission = {},
            )
        }

        composeRule.onNodeWithText("Set up Sound Kit").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Onboarding progress").assertIsDisplayed()
    }

    @Test
    fun moreScreenShowsSecondaryDestinations() {
        composeRule.setContent {
            SoundKitTheme {
                MoreScreen(onNavigate = {})
            }
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Advanced").assertIsDisplayed()
    }

    @Test
    fun advancedScreenShowsDestinations() {
        composeRule.setContent {
            SoundKitTheme {
                AdvancedScreen(onNavigate = {})
            }
        }

        composeRule.onNodeWithText("Diagnostics").assertIsDisplayed()
        composeRule.onNodeWithText("Android Auto").assertIsDisplayed()
        composeRule.onNodeWithText("Roadmap").assertIsDisplayed()
        composeRule.onNodeWithText("Developer").assertIsDisplayed()
    }

    @Test
    fun roadmapScreenShowsProgressSections() {
        composeRule.setContent {
            SoundKitTheme {
                RoadmapScreen()
            }
        }

        composeRule.onNodeWithText("Shipped").assertIsDisplayed()
        composeRule.onNodeWithText("Up next").assertIsDisplayed()
    }

    @Test
    fun garageThemeScreenShowsLightAndCarPresets() {
        composeRule.setContent {
            SoundKitTheme {
                GarageThemeScreen(
                    selectedThemeId = GarageThemePresets.first().id,
                    onThemeSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Studio").assertIsDisplayed()
        composeRule.onNodeWithText("Audi RS").assertIsDisplayed()
        composeRule.onNodeWithText("Preview").assertIsDisplayed()
    }

    @Test
    fun onboardingFlowRiskStepVisibleInIsolation() {
        composeRule.setContent {
            SoundKitTheme {
                OnboardingFlow(
                    blePermissionsGranted = false,
                    notificationsGranted = false,
                    selectedVehicleId = null,
                    onAcceptRisk = {},
                    onSelectVehicle = {},
                    onRequestBlePermissions = {},
                    onRequestNotificationPermission = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithText("Set up Sound Kit").assertIsDisplayed()
        composeRule.onNodeWithText("Get started").assertIsDisplayed()
    }
}

private class FakeBleRepositoryForSmoke : BleRepository {
    override val discoveredDevices = MutableStateFlow<List<SoundKitDevice>>(emptyList())
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val valveState = MutableStateFlow(ValveState.Unknown)
    override val receiverStatusMessage = MutableStateFlow<String?>(null)
    override val isScanning = MutableStateFlow(false)

    override fun startScan() {
        isScanning.value = true
    }

    override fun stopScan() {
        isScanning.value = false
    }

    override suspend fun connect(device: SoundKitDevice) {
        connectionState.value = ConnectionState.Connecting(device)
    }

    override suspend fun disconnect() {
        connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun openValve(): CommandResult {
        return CommandResult.Failure("protocol not verified", recoverable = false)
    }

    override suspend fun closeValve(): CommandResult {
        return CommandResult.Failure("protocol not verified", recoverable = false)
    }
}

private class FakeSettingsStoreForSmoke(
    initial: SoundKitSettings = SoundKitSettings(
        riskNoticeAcceptedAt = 1L,
        onboardingCompletedAt = 1L,
    ),
) : SettingsStore {
    override val settings = MutableStateFlow(initial)

    override suspend fun rememberDevice(device: SoundKitDevice) = Unit

    override suspend fun saveReceiver(device: SoundKitDevice, setAsDefault: Boolean) = Unit

    override suspend fun removeReceiver(address: String) = Unit

    override suspend fun setDefaultReceiver(address: String) = Unit

    override suspend fun updateNickname(address: String, nickname: String?) = Unit

    override suspend fun setConnectOnLaunch(enabled: Boolean) {
        settings.value = settings.value.copy(connectOnLaunch = enabled)
    }

    override suspend fun forgetDevice() = Unit

    override suspend fun setAutoReconnect(enabled: Boolean) {
        settings.value = settings.value.copy(autoReconnect = enabled)
    }

    override suspend fun setDebugLoggingEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(debugLoggingEnabled = enabled)
    }

    override suspend fun setGarageThemeId(themeId: String) {
        settings.value = settings.value.copy(garageThemeId = themeId)
    }

    override suspend fun acceptRiskNotice() {
        settings.value = settings.value.copy(riskNoticeAcceptedAt = System.currentTimeMillis())
    }

    override suspend fun completeOnboarding() {
        settings.value = settings.value.copy(onboardingCompletedAt = System.currentTimeMillis())
    }

    override suspend fun setSelectedVehicle(vehicleId: String?) {
        settings.value = settings.value.copy(selectedVehicleId = vehicleId)
    }

    override suspend fun importSettingsBackup(json: String) = Unit

    override suspend fun setAutomationPaused(paused: Boolean) {
        settings.value = settings.value.copy(automationPaused = paused)
    }

    override suspend fun acceptBetaDisclaimer() {
        settings.value = settings.value.copy(betaDisclaimerAcceptedAt = System.currentTimeMillis())
    }

    override suspend fun setDriveModeEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(driveModeEnabled = enabled)
    }

    override suspend fun setPreferredValveMode(mode: PreferredValveMode) {
        settings.value = settings.value.copy(preferredValveMode = mode)
    }

    override suspend fun setQuietStart(quietStart: QuietStartSettings) {
        settings.value = settings.value.copy(quietStart = quietStart)
    }
}
