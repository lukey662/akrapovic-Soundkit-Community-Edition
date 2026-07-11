package com.akrapovic.soundkit.community.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akrapovic.soundkit.community.domain.PreferredValveMode
import com.akrapovic.soundkit.community.domain.QuietStartSettings
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
    suspend fun setConnectInCar(enabled: Boolean)
    suspend fun setHeadUnitPriorityEnabled(enabled: Boolean)
    suspend fun forgetDevice()
    suspend fun setAutoReconnect(enabled: Boolean)
    suspend fun setDebugLoggingEnabled(enabled: Boolean)
    suspend fun setGarageThemeId(themeId: String)
    suspend fun acceptRiskNotice()
    suspend fun completeOnboarding()
    suspend fun setSelectedVehicle(vehicleId: String?)
    suspend fun importSettingsBackup(json: String)
    suspend fun setAutomationPaused(paused: Boolean)
    suspend fun acceptBetaDisclaimer()
    suspend fun setDriveModeEnabled(enabled: Boolean)
    suspend fun setPreferredValveMode(mode: PreferredValveMode)
    suspend fun setQuietStart(settings: QuietStartSettings)
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
        val ConnectInCar = booleanPreferencesKey("connect_in_car")
        val HeadUnitPriorityEnabled = booleanPreferencesKey("head_unit_priority_enabled")
        val AutoReconnect = booleanPreferencesKey("auto_reconnect")
        val DebugLoggingEnabled = booleanPreferencesKey("debug_logging_enabled")
        val GarageThemeId = stringPreferencesKey("garage_theme_id")
        val RiskNoticeAcceptedAt = androidx.datastore.preferences.core.longPreferencesKey("risk_notice_accepted_at")
        val OnboardingCompletedAt = androidx.datastore.preferences.core.longPreferencesKey("onboarding_completed_at")
        val SelectedVehicleId = stringPreferencesKey("selected_vehicle_id")
        val AutomationPaused = booleanPreferencesKey("automation_paused")
        val BetaDisclaimerAcceptedAt = androidx.datastore.preferences.core.longPreferencesKey("beta_disclaimer_accepted_at")
        val DriveModeEnabled = booleanPreferencesKey("drive_mode_enabled")
        val PreferredValveMode = stringPreferencesKey("preferred_valve_mode")
        val QuietStartJson = stringPreferencesKey("quiet_start_json")
    }

    override val settings: Flow<SoundKitSettings> = context.settingsDataStore.data.map { preferences ->
        val receivers = loadReceivers(preferences)
        SoundKitSettings(
            savedReceivers = receivers,
            connectOnLaunch = preferences[Keys.ConnectOnLaunch] ?: true,
            connectInCar = preferences[Keys.ConnectInCar] ?: true,
            headUnitPriorityEnabled = preferences[Keys.HeadUnitPriorityEnabled] ?: true,
            autoReconnect = preferences[Keys.AutoReconnect] ?: true,
            debugLoggingEnabled = preferences[Keys.DebugLoggingEnabled] ?: true,
            garageThemeId = preferences[Keys.GarageThemeId] ?: "studio-dark",
            riskNoticeAcceptedAt = preferences[Keys.RiskNoticeAcceptedAt] ?: 0L,
            onboardingCompletedAt = preferences[Keys.OnboardingCompletedAt] ?: 0L,
            selectedVehicleId = preferences[Keys.SelectedVehicleId],
            automationPaused = preferences[Keys.AutomationPaused] ?: false,
            betaDisclaimerAcceptedAt = preferences[Keys.BetaDisclaimerAcceptedAt] ?: 0L,
            driveModeEnabled = preferences[Keys.DriveModeEnabled] ?: true,
            preferredValveMode = preferences[Keys.PreferredValveMode]
                ?.let { runCatching { PreferredValveMode.valueOf(it) }.getOrNull() }
                ?: PreferredValveMode.Open,
            quietStart = QuietStartCodec.decode(preferences[Keys.QuietStartJson]),
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

    override suspend fun setConnectInCar(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ConnectInCar] = enabled
        }
    }

    override suspend fun setHeadUnitPriorityEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.HeadUnitPriorityEnabled] = enabled
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

    override suspend fun setSelectedVehicle(vehicleId: String?) {
        context.settingsDataStore.edit { preferences ->
            if (vehicleId.isNullOrBlank()) {
                preferences.remove(Keys.SelectedVehicleId)
            } else {
                preferences[Keys.SelectedVehicleId] = vehicleId
            }
        }
    }

    override suspend fun importSettingsBackup(json: String) {
        val backup = SettingsBackupCodec.decode(json)
        context.settingsDataStore.edit { preferences ->
            backup.savedReceiversJson?.let { preferences[Keys.SavedReceiversJson] = it }
            backup.connectOnLaunch?.let { preferences[Keys.ConnectOnLaunch] = it }
            backup.connectInCar?.let { preferences[Keys.ConnectInCar] = it }
            backup.headUnitPriorityEnabled?.let { preferences[Keys.HeadUnitPriorityEnabled] = it }
            backup.autoReconnect?.let { preferences[Keys.AutoReconnect] = it }
            backup.garageThemeId?.let { preferences[Keys.GarageThemeId] = it }
            backup.selectedVehicleId?.let { preferences[Keys.SelectedVehicleId] = it }
            backup.driveModeEnabled?.let { preferences[Keys.DriveModeEnabled] = it }
            backup.preferredValveMode?.let { preferences[Keys.PreferredValveMode] = it }
            backup.quietStartJson?.let { preferences[Keys.QuietStartJson] = it }
        }
    }

    override suspend fun setAutomationPaused(paused: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AutomationPaused] = paused
        }
    }

    override suspend fun acceptBetaDisclaimer() {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.BetaDisclaimerAcceptedAt] = System.currentTimeMillis()
        }
    }

    override suspend fun setDriveModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DriveModeEnabled] = enabled
        }
    }

    override suspend fun setPreferredValveMode(mode: PreferredValveMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.PreferredValveMode] = mode.name
        }
    }

    override suspend fun setQuietStart(settings: QuietStartSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.QuietStartJson] = QuietStartCodec.encode(settings)
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
