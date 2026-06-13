package com.akrapovic.soundkit.community.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DiagnosticsEntry
import com.akrapovic.soundkit.community.domain.DiagnosticsLevel
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.testDeviceForSmoke
import com.akrapovic.soundkit.community.ui.control.ConnectedDeviceScreen
import com.akrapovic.soundkit.community.ui.diagnostics.DiagnosticsScreen
import com.akrapovic.soundkit.community.ui.more.AdvancedScreen
import com.akrapovic.soundkit.community.ui.more.MoreScreen
import com.akrapovic.soundkit.community.ui.onboarding.OnboardingFlow
import com.akrapovic.soundkit.community.ui.onboarding.VehicleSelectionContent
import com.akrapovic.soundkit.community.ui.scan.ScanScreen
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme
import org.junit.Rule
import org.junit.Test

/**
 * JVM screenshot tests for README / docs. Run [scripts/capture-docs-screenshots.sh]
 * to record PNGs into docs/screenshots/ (works without a device or emulator).
 *
 * Uses full [DeviceConfig.PIXEL_6] pixel dimensions (1080×2400) — do not override
 * screenWidth/Height with dp-like values or text and buttons clip badly.
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
    fun homeConnected() {
        val device = testDeviceForSmoke()
        paparazzi.snapshot(name = "04-home-connected") {
            PhoneFrame {
                ConnectedDeviceScreen(
                    state = SoundKitUiState(
                        connectionState = ConnectionState.Connected(device),
                        valveState = ValveState.Open,
                        protocolVerified = true,
                        settings = SoundKitSettings(selectedVehicleId = "audi-rs3"),
                    ),
                    onToggleValve = {},
                    onDisconnect = {},
                )
            }
        }
    }

    @Test
    fun moreMenu() {
        paparazzi.snapshot(name = "05-more-menu") {
            PhoneFrame {
                MoreScreen(onNavigate = {})
            }
        }
    }

    @Test
    fun advancedHub() {
        paparazzi.snapshot(name = "06-advanced-hub") {
            PhoneFrame {
                AdvancedScreen(onNavigate = {})
            }
        }
    }

    @Test
    fun diagnosticsSupport() {
        paparazzi.snapshot(name = "07-diagnostics-support") {
            PhoneFrame {
                WithStubActivityResultRegistry {
                    DiagnosticsScreen(
                        entries = listOf(
                            DiagnosticsEntry(
                                id = 1L,
                                timestampMillis = 1_700_000_000_000L,
                                level = DiagnosticsLevel.Info,
                                message = "Connected to Akrapovic SoundKit",
                            ),
                            DiagnosticsEntry(
                                id = 2L,
                                timestampMillis = 1_700_000_001_000L,
                                level = DiagnosticsLevel.Debug,
                                message = "Valve state: OPEN",
                            ),
                        ),
                        onBuildReport = { "Sound Kit Community diagnostics report" },
                    )
                }
            }
        }
    }

    @Composable
    private fun PhoneFrame(content: @Composable () -> Unit) {
        SoundKitTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                content()
            }
        }
    }
}
