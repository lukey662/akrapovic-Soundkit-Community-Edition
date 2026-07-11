package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.PreferredValveMode
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import org.json.JSONObject

data class SettingsBackupPayload(
    val savedReceiversJson: String? = null,
    val connectOnLaunch: Boolean? = null,
    val connectInCar: Boolean? = null,
    val headUnitPriorityEnabled: Boolean? = null,
    val autoReconnect: Boolean? = null,
    val garageThemeId: String? = null,
    val selectedVehicleId: String? = null,
    val driveModeEnabled: Boolean? = null,
    val preferredValveMode: String? = null,
    val quietStartJson: String? = null,
)

class SettingsBackupException(message: String) : IllegalArgumentException(message)

object SettingsBackupCodec {
    const val VERSION = 1
    private const val MAX_STRING_LENGTH = 128
    private const val MAX_JSON_FIELD_LENGTH = 8_192

    private val knownThemeIds = setOf(
        "studio-dark",
        "studio-light",
        "akrapovic-dark",
        "akrapovic-light",
        "audi-rs-dark",
        "audi-rs-light",
        "bmw-m-dark",
        "bmw-m-light",
        "mercedes-amg-dark",
        "mercedes-amg-light",
        "porsche-dark",
        "porsche-light",
        "volkswagen-r-dark",
        "volkswagen-r-light",
        "lamborghini-dark",
        "lamborghini-light",
    )

    fun encode(settings: SoundKitSettings): String {
        return JSONObject().apply {
            put("version", VERSION)
            put("savedReceiversJson", SavedReceiversCodec.encode(settings.savedReceivers))
            put("connectOnLaunch", settings.connectOnLaunch)
            put("connectInCar", settings.connectInCar)
            put("headUnitPriorityEnabled", settings.headUnitPriorityEnabled)
            put("autoReconnect", settings.autoReconnect)
            put("garageThemeId", settings.garageThemeId)
            settings.selectedVehicleId?.let { put("selectedVehicleId", it) }
            put("driveModeEnabled", settings.driveModeEnabled)
            put("preferredValveMode", settings.preferredValveMode.name)
            put("quietStartJson", QuietStartCodec.encode(settings.quietStart))
        }.toString(2)
    }

    /**
     * Validates schema/version and field constraints before any mutation.
     * Throws [SettingsBackupException] on invalid input so callers can fail closed.
     */
    fun decode(json: String): SettingsBackupPayload {
        if (json.isBlank()) {
            throw SettingsBackupException("Settings backup is empty")
        }
        if (json.length > 64_000) {
            throw SettingsBackupException("Settings backup is too large")
        }
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            throw SettingsBackupException("Settings backup is not valid JSON")
        }
        if (root.optInt("version", 0) != VERSION) {
            throw SettingsBackupException("Unsupported settings backup version")
        }

        val savedReceiversJson = root.optString("savedReceiversJson").takeIf { it.isNotBlank() }
        if (savedReceiversJson != null) {
            if (savedReceiversJson.length > MAX_JSON_FIELD_LENGTH) {
                throw SettingsBackupException("Saved receivers field is too large")
            }
            val receivers = SavedReceiversCodec.decode(savedReceiversJson)
            if (receivers.isEmpty() && savedReceiversJson.trim() != "[]") {
                throw SettingsBackupException("Saved receivers could not be parsed")
            }
            if (receivers.size > SavedReceiversCodec.MAX_SAVED_RECEIVERS) {
                throw SettingsBackupException(
                    "Backup contains more than ${SavedReceiversCodec.MAX_SAVED_RECEIVERS} receivers",
                )
            }
            receivers.forEach { receiver ->
                requireBounded(receiver.address, "receiver address")
                requireBounded(receiver.name, "receiver name")
                receiver.nickname?.let { requireBounded(it, "receiver nickname") }
            }
        }

        val garageThemeId = root.optString("garageThemeId").takeIf { it.isNotBlank() }
        if (garageThemeId != null && garageThemeId !in knownThemeIds) {
            throw SettingsBackupException("Unknown garage theme: $garageThemeId")
        }

        val preferredValveMode = root.optString("preferredValveMode").takeIf { it.isNotBlank() }
        if (preferredValveMode != null) {
            runCatching { PreferredValveMode.valueOf(preferredValveMode) }.getOrElse {
                throw SettingsBackupException("Unknown preferred valve mode: $preferredValveMode")
            }
        }

        val selectedVehicleId = root.optString("selectedVehicleId").takeIf { it.isNotBlank() }
        if (selectedVehicleId != null) {
            requireBounded(selectedVehicleId, "selected vehicle id")
        }

        val quietStartJson = root.optString("quietStartJson").takeIf { it.isNotBlank() }
        if (quietStartJson != null) {
            if (quietStartJson.length > MAX_JSON_FIELD_LENGTH) {
                throw SettingsBackupException("Quiet start field is too large")
            }
            // Decode to validate structure; discard result — import stores the original JSON.
            QuietStartCodec.decode(quietStartJson)
        }

        return SettingsBackupPayload(
            savedReceiversJson = savedReceiversJson,
            connectOnLaunch = if (root.has("connectOnLaunch")) root.getBoolean("connectOnLaunch") else null,
            connectInCar = if (root.has("connectInCar")) root.getBoolean("connectInCar") else null,
            headUnitPriorityEnabled = if (root.has("headUnitPriorityEnabled")) {
                root.getBoolean("headUnitPriorityEnabled")
            } else {
                null
            },
            autoReconnect = if (root.has("autoReconnect")) root.getBoolean("autoReconnect") else null,
            garageThemeId = garageThemeId,
            selectedVehicleId = selectedVehicleId,
            driveModeEnabled = if (root.has("driveModeEnabled")) root.getBoolean("driveModeEnabled") else null,
            preferredValveMode = preferredValveMode,
            quietStartJson = quietStartJson,
        )
    }

    private fun requireBounded(value: String, label: String) {
        if (value.isBlank() || value.length > MAX_STRING_LENGTH) {
            throw SettingsBackupException("Invalid $label in settings backup")
        }
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
