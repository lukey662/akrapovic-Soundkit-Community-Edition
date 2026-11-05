package com.akrapovic.soundkit.community.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.akrapovic.soundkit.community.domain.ConnectionErrorKind
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.testDeviceForSmoke
import com.akrapovic.soundkit.community.ui.control.ConnectedDeviceScreen
import com.akrapovic.soundkit.community.ui.scan.ScanScreen
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme
import org.junit.Rule
import org.junit.Test

/** Pairing guidance on Home: the in-progress banner and each pairing failure. */
class PairingPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test
    fun pairingInProgressBanner() {
        val device = testDeviceForSmoke()
        paparazzi.snapshot(name = "pairing-in-progress") {
            Frame {
                ConnectedDeviceScreen(
                    state = SoundKitUiState(
                        connectionState = ConnectionState.Connecting(device, pairing = true),
                        settings = demoAudiSettings(),
                    ),
                    onToggleValve = {},
                    onDisconnect = {},
                )
            }
        }
    }

    @Test
    fun pairingFailedOffersRetryAndBluetoothSettings() {
        snapshotError("pairing-failed", ConnectionErrorKind.PairingFailed)
    }

    @Test
    fun pairingCancelledOffersRetry() {
        snapshotError("pairing-cancelled", ConnectionErrorKind.PairingCancelled)
    }

    @Test
    fun stalePairingPointsToBluetoothSettings() {
        snapshotError("pairing-stale", ConnectionErrorKind.StalePairing)
    }

    private fun snapshotError(name: String, kind: ConnectionErrorKind) {
        paparazzi.snapshot(name = name) {
            Frame {
                ScanScreen(
                    state = SoundKitUiState(
                        connectionState = ConnectionState.Error("raw diagnostics text", recoverable = false, kind = kind),
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

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        SoundKitTheme(garageTheme = AudiRsDarkTheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}
