package com.akrapovic.soundkit.community.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "soundkit_settings")

interface SettingsStore {
    val settings: Flow<SoundKitSettings>

    suspend fun rememberDevice(device: SoundKitDevice)
    suspend fun forgetDevice()
    suspend fun setAutoReconnect(enabled: Boolean)
    suspend fun setDebugLoggingEnabled(enabled: Boolean)
    suspend fun setGarageThemeId(themeId: String)
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsStore {
    private object Keys {
        val RememberedDeviceName = stringPreferencesKey("remembered_device_name")
        val RememberedDeviceAddress = stringPreferencesKey("remembered_device_address")
        val AutoReconnect = booleanPreferencesKey("auto_reconnect")
        val DebugLoggingEnabled = booleanPreferencesKey("debug_logging_enabled")
        val GarageThemeId = stringPreferencesKey("garage_theme_id")
    }

    override val settings: Flow<SoundKitSettings> = context.settingsDataStore.data.map { preferences ->
        SoundKitSettings(
            rememberedDeviceName = preferences[Keys.RememberedDeviceName],
            rememberedDeviceAddress = preferences[Keys.RememberedDeviceAddress],
            autoReconnect = preferences[Keys.AutoReconnect] ?: true,
            debugLoggingEnabled = preferences[Keys.DebugLoggingEnabled] ?: true,
            garageThemeId = preferences[Keys.GarageThemeId] ?: "studio-dark",
        )
    }

    override suspend fun rememberDevice(device: SoundKitDevice) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.RememberedDeviceName] = device.name
            preferences[Keys.RememberedDeviceAddress] = device.address
        }
    }

    override suspend fun forgetDevice() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.RememberedDeviceName)
            preferences.remove(Keys.RememberedDeviceAddress)
        }
    }

    override suspend fun setAutoReconnect(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AutoReconnect] = enabled
        }
    }

    override suspend fun setDebugLoggingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DebugLoggingEnabled] = enabled
        }
    }

    override suspend fun setGarageThemeId(themeId: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.GarageThemeId] = themeId
        }
    }
}

