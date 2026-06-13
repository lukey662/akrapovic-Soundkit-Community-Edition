package com.akrapovic.soundkit.community.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.PreferredValveMode
import com.akrapovic.soundkit.community.domain.QuietStartSettings
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.testDeviceForSmoke
import com.akrapovic.soundkit.community.ui.control.ConnectedDeviceScreen
import com.akrapovic.soundkit.community.ui.garage.GarageThemeScreen
import com.akrapovic.soundkit.community.ui.onboarding.OnboardingFlow
import com.akrapovic.soundkit.community.ui.onboarding.VehicleSelectionContent
import com.akrapovic.soundkit.community.ui.scan.ScanScreen
import com.akrapovic.soundkit.community.ui.settings.DriveModeScreen
import com.akrapovic.soundkit.community.ui.theme.GarageTheme
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme
import org.junit.Rule
import org.junit.Test

/**
 * Marketing screenshots for README — Audi RS Dark, drive mode, quiet neighbours.
 * Run [scripts/capture-docs-screenshots.sh] to refresh [docs/screenshots/].
 */
class DocsScreenshotPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test
    fun onboardingRisk() {
        paparazzi.snapshot(name = "01-onboarding-risk") {
            PhoneFrame {
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
    }

    @Test
    fun vehicleSelection() {
        paparazzi.snapshot(name = "02-vehicle-selection") {
            PhoneFrame {
                VehicleSelectionContent(
                    selectedVehicleId = "audi-rs3",
                    onSelectVehicle = {},
                )
            }
        }
    }

    @Test
    fun scanReceivers() {
        paparazzi.snapshot(name = "03-scan-receivers") {
            PhoneFrame {
                ScanScreen(
                    state = SoundKitUiState(
                        isScanning = false,
                        devices = listOf(
                            testDeviceForSmoke(),
                            testDeviceForSmoke().copy(
                                name = "Sound Kit",
                                address = "AA:BB:CC:DD:EE:01",
                                rssi = -68,
                            ),
                        ),
                        settings = demoAudiSettings(),
                    ),
                    permissions = emptyList(),
                    permissionsGranted = true,
                    onRequestPermissions = {},
                    onStartScan = {},
                    onStopScan = {},
                    onConnect = {},
                )
            }
        }
    }

    @Test
    fun homeConnectedAudi() {
        val device = testDeviceForSmoke()
        paparazzi.snapshot(name = "04-home-connected-audi") {
            PhoneFrame {
                ConnectedDeviceScreen(
                    state = SoundKitUiState(
                        connectionState = ConnectionState.Connected(device),
                        valveState = ValveState.Open,
                        protocolVerified = true,
                        settings = demoAudiSettings(
                            preferredValveMode = PreferredValveMode.Open,
                            quietStart = QuietStartSettings(enabled = false),
                        ),
                    ),
                    onToggleValve = {},
                    onDisconnect = {},
                )
            }
        }
    }

    @Test
    fun driveModeOpenOnConnect() {
        paparazzi.snapshot(name = "05-drive-mode-open-on-connect") {
            PhoneFrame {
                DriveModeScreen(
                    state = SoundKitUiState(
                        settings = demoAudiSettings(
                            preferredValveMode = PreferredValveMode.Open,
                            quietStart = QuietStartSettings(enabled = false),
                        ),
                    ),
                    onDriveModeEnabledChanged = {},
                    onPreferredModeChanged = {},
                    onQuietStartChanged = {},
                    onDriveModePausedChanged = {},
                )
            }
        }
    }

    @Test
    fun driveModeClosedOnConnect() {
        paparazzi.snapshot(name = "06-drive-mode-closed-on-connect") {
            PhoneFrame {
                DriveModeScreen(
                    state = SoundKitUiState(
                        settings = demoAudiSettings(
                            preferredValveMode = PreferredValveMode.Closed,
                            quietStart = QuietStartSettings(enabled = false),
                        ),
                    ),
                    onDriveModeEnabledChanged = {},
                    onPreferredModeChanged = {},
                    onQuietStartChanged = {},
                    onDriveModePausedChanged = {},
                )
            }
        }
    }

    @Test
    fun quietNeighbours() {
        paparazzi.snapshot(name = "07-quiet-neighbours") {
            PhoneFrame {
                DriveModeScreen(
                    state = SoundKitUiState(
                        settings = demoAudiSettings(
                            preferredValveMode = PreferredValveMode.Open,
                            quietStart = demoQuietNeighboursSettings,
                        ),
                    ),
                    onDriveModeEnabledChanged = {},
                    onPreferredModeChanged = {},
                    onQuietStartChanged = {},
                    onDriveModePausedChanged = {},
                )
            }
        }
    }

    @Test
    fun audiAppearance() {
        paparazzi.snapshot(name = "08-audi-rs-dark-theme") {
            PhoneFrame {
                GarageThemeScreen(
                    selectedThemeId = AudiRsDarkTheme.id,
                    onThemeSelected = {},
                )
            }
        }
    }

    @Composable
    private fun PhoneFrame(
        garageTheme: GarageTheme = AudiRsDarkTheme,
        content: @Composable () -> Unit,
    ) {
        SoundKitTheme(garageTheme = garageTheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                content()
            }
        }
    }
}
