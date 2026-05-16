package com.akrapovic.soundkit.community.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.SettingsStore
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
    fun controlScreenDisablesValveButtonsWhenProtocolIsUnverified() {
        val device = testDeviceForSmoke()
        composeRule.setContent {
            SoundKitTheme {
                ConnectedDeviceScreen(
                    state = SoundKitUiState(
                        connectionState = ConnectionState.Connected(device),
                        valveState = ValveState.Unknown,
                        protocolVerified = false,
                    ),
                    onOpen = {},
                    onClose = {},
                    onDisconnect = {},
                )
            }
        }

        composeRule.onNodeWithText("Protocol verification required").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open exhaust valve").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Close exhaust valve").assertIsNotEnabled()
    }

    @Test
    fun diagnosticsScreenShowsExportAction() {
        composeRule.setContent {
            SoundKitTheme {
                DiagnosticsScreen(
                    entries = listOf(
                        DiagnosticsEntry(
                            timestampMillis = 1_000L,
                            level = DiagnosticsLevel.Info,
                            message = "GATT services discovered",
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Diagnostics").assertIsDisplayed()
        composeRule.onNodeWithText("Copy diagnostics report").assertIsDisplayed()
    }

    @Test
    fun settingsScreenShowsSafetyAndBatteryControls() {
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
        composeRule.onNodeWithText("Battery optimization").assertIsDisplayed()
        composeRule.onNodeWithText("Safety").assertIsDisplayed()
    }

    @Test
    fun appShellShowsTidiedPrimaryNavigation() {
        composeRule.setContent {
            SoundKitApp(
                viewModel = SoundKitViewModel(
                    bleRepository = FakeBleRepositoryForSmoke(),
                    settingsRepository = FakeSettingsStoreForSmoke(),
                    diagnosticsRepository = DiagnosticsRepository(),
                ),
                permissions = emptyList(),
                permissionsGranted = true,
                onRequestPermissions = {},
            )
        }

        composeRule.onNodeWithText("SCAN").assertIsDisplayed()
        composeRule.onNodeWithText("CONTROL").assertIsDisplayed()
        composeRule.onNodeWithText("MORE").assertIsDisplayed()
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
        composeRule.onNodeWithText("Garage / Themes").assertIsDisplayed()
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
    fun garageThemeScreenShowsAudiRs3Preset() {
        composeRule.setContent {
            SoundKitTheme {
                GarageThemeScreen(
                    selectedThemeId = GarageThemePresets.first().id,
                    onThemeSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Audi RS3 White Sportback").assertIsDisplayed()
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
}

