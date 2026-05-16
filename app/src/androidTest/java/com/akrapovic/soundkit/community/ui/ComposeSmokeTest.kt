package com.akrapovic.soundkit.community.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DiagnosticsEntry
import com.akrapovic.soundkit.community.domain.DiagnosticsLevel
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.testDeviceForSmoke
import com.akrapovic.soundkit.community.ui.control.ConnectedDeviceScreen
import com.akrapovic.soundkit.community.ui.diagnostics.DiagnosticsScreen
import com.akrapovic.soundkit.community.ui.scan.ScanScreen
import com.akrapovic.soundkit.community.ui.settings.SettingsScreen
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme
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
}

