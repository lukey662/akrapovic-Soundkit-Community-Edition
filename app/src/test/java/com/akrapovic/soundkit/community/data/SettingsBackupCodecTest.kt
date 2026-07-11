package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.PreferredValveMode
import com.akrapovic.soundkit.community.domain.QuietStartSettings
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCodecTest {
    @Test
    fun roundTripPreservesCoreFields() {
        val settings = SoundKitSettings(
            savedReceivers = listOf(
                SavedReceiver(address = "AA:BB", name = "Sound Kit", isDefault = true),
            ),
            connectOnLaunch = true,
            connectInCar = false,
            autoReconnect = false,
            garageThemeId = "studio-dark",
            preferredValveMode = PreferredValveMode.Closed,
            quietStart = QuietStartSettings(enabled = true, holdClosedMinutes = 5),
        )
        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(settings))
        assertEquals(true, decoded.connectOnLaunch)
        assertEquals(false, decoded.connectInCar)
        assertEquals(false, decoded.autoReconnect)
        assertEquals("studio-dark", decoded.garageThemeId)
        assertEquals("Closed", decoded.preferredValveMode)
        assertTrue(!decoded.savedReceiversJson.isNullOrBlank())
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertThrows(SettingsBackupException::class.java) {
            SettingsBackupCodec.decode("""{"version":99}""")
        }
    }

    @Test
    fun rejectsUnknownTheme() {
        assertThrows(SettingsBackupException::class.java) {
            SettingsBackupCodec.decode(
                """{"version":1,"garageThemeId":"not-a-theme"}""",
            )
        }
    }

    @Test
    fun rejectsUnknownPreferredMode() {
        assertThrows(SettingsBackupException::class.java) {
            SettingsBackupCodec.decode(
                """{"version":1,"preferredValveMode":"HalfOpen"}""",
            )
        }
    }

    @Test
    fun rejectsInvalidJson() {
        assertThrows(SettingsBackupException::class.java) {
            SettingsBackupCodec.decode("{not-json")
        }
    }
}
