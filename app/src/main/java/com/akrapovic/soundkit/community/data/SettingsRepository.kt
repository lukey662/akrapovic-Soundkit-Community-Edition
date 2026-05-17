package com.akrapovic.soundkit.community.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akrapovic.soundkit.community.domain.SavedReceiver
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
    suspend fun saveReceiver(device: SoundKitDevice, setAsDefault: Boolean = true)
    suspend fun removeReceiver(address: String)
    suspend fun setDefaultReceiver(address: String)
    suspend fun updateNickname(address: String, nickname: String?)
    suspend fun setConnectOnLaunch(enabled: Boolean)
    suspend fun forgetDevice()
    suspend fun setAutoReconnect(enabled: Boolean)
    suspend fun setDebugLoggingEnabled(enabled: Boolean)
    suspend fun setGarageThemeId(themeId: String)
    suspend fun acceptRiskNotice()
    suspend fun completeOnboarding()
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsStore {
    private object Keys {
        val SavedReceiversJson = stringPreferencesKey("saved_receivers_json")
        val RememberedDeviceName = stringPreferencesKey("remembered_device_name")
        val RememberedDeviceAddress = stringPreferencesKey("remembered_device_address")
        val ConnectOnLaunch = booleanPreferencesKey("connect_on_launch")
        val AutoReconnect = booleanPreferencesKey("auto_reconnect")
        val DebugLoggingEnabled = booleanPreferencesKey("debug_logging_enabled")
        val GarageThemeId = stringPreferencesKey("garage_theme_id")
        val RiskNoticeAcceptedAt = androidx.datastore.preferences.core.longPreferencesKey("risk_notice_accepted_at")
        val OnboardingCompletedAt = androidx.datastore.preferences.core.longPreferencesKey("onboarding_completed_at")
    }

    override val settings: Flow<SoundKitSettings> = context.settingsDataStore.data.map { preferences ->
        val receivers = loadReceivers(preferences)
        SoundKitSettings(
            savedReceivers = receivers,
            connectOnLaunch = preferences[Keys.ConnectOnLaunch] ?: true,
            autoReconnect = preferences[Keys.AutoReconnect] ?: true,
            debugLoggingEnabled = preferences[Keys.DebugLoggingEnabled] ?: true,
            garageThemeId = preferences[Keys.GarageThemeId] ?: "studio-dark",
            riskNoticeAcceptedAt = preferences[Keys.RiskNoticeAcceptedAt] ?: 0L,
            onboardingCompletedAt = preferences[Keys.OnboardingCompletedAt] ?: 0L,
        )
    }

    private fun loadReceivers(preferences: androidx.datastore.preferences.core.Preferences): List<SavedReceiver> {
        val json = preferences[Keys.SavedReceiversJson]
        val decoded = SavedReceiversCodec.decode(json)
        if (decoded.isNotEmpty()) {
            return SavedReceiversCodec.normalize(decoded)
        }
        return SavedReceiversCodec.migrateLegacy(
            preferences[Keys.RememberedDeviceName],
            preferences[Keys.RememberedDeviceAddress],
        )
    }

    override suspend fun rememberDevice(device: SoundKitDevice) {
        saveReceiver(device, setAsDefault = true)
    }

    override suspend fun saveReceiver(device: SoundKitDevice, setAsDefault: Boolean) {
        context.settingsDataStore.edit { preferences ->
            val current = loadReceivers(preferences)
            val without = current.filterNot { it.address == device.address }
            val incoming = SavedReceiver(
                address = device.address,
                name = device.name,
                nickname = current.firstOrNull { it.address == device.address }?.nickname,
                isDefault = setAsDefault,
            )
            val merged = without + incoming
            val updated = if (setAsDefault) {
                merged.map { it.copy(isDefault = it.address == device.address) }
            } else {
                merged
            }
            writeReceivers(preferences, SavedReceiversCodec.normalize(updated))
        }
    }

    override suspend fun removeReceiver(address: String) {
        context.settingsDataStore.edit { preferences ->
            val updated = loadReceivers(preferences).filterNot { it.address == address }
            writeReceivers(preferences, SavedReceiversCodec.normalize(updated))
        }
    }

    override suspend fun setDefaultReceiver(address: String) {
        context.settingsDataStore.edit { preferences ->
            val updated = loadReceivers(preferences).map { it.copy(isDefault = it.address == address) }
            writeReceivers(preferences, SavedReceiversCodec.normalize(updated))
        }
    }

    override suspend fun updateNickname(address: String, nickname: String?) {
        context.settingsDataStore.edit { preferences ->
            val updated = loadReceivers(preferences).map { receiver ->
                if (receiver.address == address) receiver.copy(nickname = nickname?.trim()?.takeIf { it.isNotEmpty() }) else receiver
            }
            writeReceivers(preferences, SavedReceiversCodec.normalize(updated))
        }
    }

    override suspend fun setConnectOnLaunch(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ConnectOnLaunch] = enabled
        }
    }

    override suspend fun forgetDevice() {
        context.settingsDataStore.edit { preferences ->
            writeReceivers(preferences, emptyList())
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

    override suspend fun acceptRiskNotice() {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.RiskNoticeAcceptedAt] = System.currentTimeMillis()
        }
    }

    override suspend fun completeOnboarding() {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.OnboardingCompletedAt] = System.currentTimeMillis()
        }
    }

    private fun writeReceivers(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        receivers: List<SavedReceiver>,
    ) {
        val normalized = SavedReceiversCodec.normalize(receivers)
        if (normalized.isEmpty()) {
            preferences.remove(Keys.SavedReceiversJson)
            preferences.remove(Keys.RememberedDeviceName)
            preferences.remove(Keys.RememberedDeviceAddress)
        } else {
            preferences[Keys.SavedReceiversJson] = SavedReceiversCodec.encode(normalized)
            val default = normalized.firstOrNull { it.isDefault } ?: normalized.first()
            preferences[Keys.RememberedDeviceName] = default.displayName()
            preferences[Keys.RememberedDeviceAddress] = default.address
        }
    }
}
