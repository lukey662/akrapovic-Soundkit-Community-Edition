package com.akrapovic.soundkit.community.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.akrapovic.soundkit.community.ui.more.ValveStatesPreviewScreen
import com.akrapovic.soundkit.community.ui.theme.GarageThemePresets
import com.akrapovic.soundkit.community.ui.theme.SoundKitTheme
import org.junit.Rule
import org.junit.Test

class ValveStatesPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test
    fun valveStatesGallery() {
        paparazzi.snapshot(name = "valve-states-gallery") {
            SoundKitTheme(garageTheme = GarageThemePresets.first { it.id == "akrapovic-dark" }) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ValveStatesPreviewScreen()
                }
            }
        }
    }
}
