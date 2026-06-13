package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.PreferredValveMode
import com.akrapovic.soundkit.community.domain.QuietStartSettings
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import org.json.JSONObject

data class SettingsBackupPayload(
    val savedReceiversJson: String? = null,
    val connectOnLaunch: Boolean? = null,
    val autoReconnect: Boolean? = null,
    val garageThemeId: String? = null,
    val selectedVehicleId: String? = null,
    val driveModeEnabled: Boolean? = null,
    val preferredValveMode: String? = null,
    val quietStartJson: String? = null,
)

object SettingsBackupCodec {
    private const val VERSION = 1

    fun encode(settings: SoundKitSettings): String {
        return JSONObject().apply {
            put("version", VERSION)
            put("savedReceiversJson", SavedReceiversCodec.encode(settings.savedReceivers))
            put("connectOnLaunch", settings.connectOnLaunch)
            put("autoReconnect", settings.autoReconnect)
            put("garageThemeId", settings.garageThemeId)
            settings.selectedVehicleId?.let { put("selectedVehicleId", it) }
            put("driveModeEnabled", settings.driveModeEnabled)
            put("preferredValveMode", settings.preferredValveMode.name)
            put("quietStartJson", QuietStartCodec.encode(settings.quietStart))
        }.toString(2)
    }

    fun decode(json: String): SettingsBackupPayload {
        val root = JSONObject(json)
        if (root.optInt("version", 0) != VERSION) {
            error("Unsupported settings backup version")
        }
        return SettingsBackupPayload(
            savedReceiversJson = root.optString("savedReceiversJson").takeIf { it.isNotBlank() },
            connectOnLaunch = if (root.has("connectOnLaunch")) root.getBoolean("connectOnLaunch") else null,
            autoReconnect = if (root.has("autoReconnect")) root.getBoolean("autoReconnect") else null,
            garageThemeId = root.optString("garageThemeId").takeIf { it.isNotBlank() },
            selectedVehicleId = root.optString("selectedVehicleId").takeIf { it.isNotBlank() },
            driveModeEnabled = if (root.has("driveModeEnabled")) root.getBoolean("driveModeEnabled") else null,
            preferredValveMode = root.optString("preferredValveMode").takeIf { it.isNotBlank() },
            quietStartJson = root.optString("quietStartJson").takeIf { it.isNotBlank() },
        )
    }
}

enum class DriveModeProfile(val label: String, val mode: PreferredValveMode, val quietEnabled: Boolean) {
    Everyday("Everyday", PreferredValveMode.Open, quietEnabled = false),
    QuietStreet("Quiet street", PreferredValveMode.Closed, quietEnabled = true),
    Track("Track", PreferredValveMode.Open, quietEnabled = false),
}

fun DriveModeProfile.applyTo(settings: SoundKitSettings): SoundKitSettings {
    val quiet = if (quietEnabled) {
        settings.quietStart.copy(enabled = true)
    } else {
        settings.quietStart.copy(enabled = false)
    }
    return settings.copy(
        driveModeEnabled = true,
        preferredValveMode = mode,
        quietStart = quiet,
    )
}
