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
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DiagnosticsEntry
import com.akrapovic.soundkit.community.domain.DiagnosticsLevel
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.testDeviceForSmoke
import com.akrapovic.soundkit.community.ui.control.ConnectedDeviceScreen
import com.akrapovic.soundkit.community.ui.diagnostics.DiagnosticsScreen
import com.akrapovic.soundkit.community.ui.garage.GarageThemeScreen
import com.akrapovic.soundkit.community.ui.more.MoreScreen
import com.akrapovic.soundkit.community.ui.roadmap.RoadmapScreen
import com.akrapovic.soundkit.community.ui.scan.ScanScreen
import com.akrapovic.soundkit.community.ui.settings.SettingsScreen
import com.akrapovic.soundkit.community.ui.theme.GarageThemePresets
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

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
    fun scanScreenShowsEmptyState() {
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

        composeRule.onNodeWithText("No receiver selected").assertIsDisplayed()
        composeRule.onNodeWithText("Scan for receiver").assertIsDisplayed()
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
                    onOpen = {},
                    onClose = {},
                    onDisconnect = {},
                )
            }
        }

        composeRule.onNodeWithText("Waiting for receiver status").assertIsDisplayed()
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
        composeRule.onNodeWithText("Copy report").assertIsDisplayed()
        composeRule.onNodeWithText("Share report").assertIsDisplayed()
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
        composeRule.onNodeWithText("Share crash").assertIsDisplayed()
    }

    @Test
    fun settingsScreenShowsConnectionAndBatteryControls() {
        composeRule.setContent {
            SoundKitTheme {
                SettingsScreen(
                    state = SoundKitUiState(),
                    onAutoReconnectChanged = {},
                    onDebugLoggingChanged = {},
                    onForgetDevice = {},
                )
            }
        }

        composeRule.onNodeWithText("Auto reconnect").assertIsDisplayed()
        composeRule.onNodeWithText("Background connection").assertIsDisplayed()
        composeRule.onNodeWithText("Detailed logs").assertIsDisplayed()
    }

    @Test
    fun appShellShowsTidiedPrimaryNavigation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crashReporter = CrashReporter(context)
        composeRule.setContent {
            SoundKitApp(
                viewModel = SoundKitViewModel(
                    bleRepository = FakeBleRepositoryForSmoke(),
                    settingsRepository = FakeSettingsStoreForSmoke(),
                    diagnosticsRepository = DiagnosticsRepository(),
                    diagnosticsReportBuilder = DiagnosticsReportBuilder(context, crashReporter),
                    crashReporter = crashReporter,
                ),
                permissions = emptyList(),
                permissionsGranted = true,
                onRequestPermissions = {},
            )
        }

        composeRule.onNodeWithText("Find").assertIsDisplayed()
        composeRule.onNodeWithText("Control").assertIsDisplayed()
        composeRule.onNodeWithText("More").assertIsDisplayed()
    }

    @Test
    fun moreScreenShowsSecondaryDestinations() {
        composeRule.setContent {
            SoundKitTheme {
                MoreScreen(onNavigate = {})
            }
        }

        composeRule.onNodeWithText("Diagnostics").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Roadmap").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun roadmapScreenShowsProgressSections() {
        composeRule.setContent {
            SoundKitTheme {
                RoadmapScreen()
            }
        }

        composeRule.onNodeWithText("Done").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()
        composeRule.onNodeWithText("Later").assertIsDisplayed()
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

        composeRule.onNodeWithText("Studio").assertIsDisplayed()
        composeRule.onNodeWithText("Audi RS").assertIsDisplayed()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithText("Dark").assertIsDisplayed()
    }
}

private class FakeBleRepositoryForSmoke : BleRepository {
    override val discoveredDevices = MutableStateFlow<List<SoundKitDevice>>(emptyList())
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val valveState = MutableStateFlow(ValveState.Unknown)
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

private class FakeSettingsStoreForSmoke : SettingsStore {
    override val settings = MutableStateFlow(SoundKitSettings())

    override suspend fun rememberDevice(device: SoundKitDevice) = Unit

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
}

