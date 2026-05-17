package com.akrapovic.soundkit.community.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akrapovic.soundkit.community.domain.GeofenceZone
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.geofenceDataStore by preferencesDataStore(name = "soundkit_geofences")

interface GeofenceZonesStore {
    val zones: Flow<List<GeofenceZone>>

    suspend fun upsertZone(zone: GeofenceZone)
    suspend fun deleteZone(id: String)
}

@Singleton
class GeofenceZonesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : GeofenceZonesStore {
    private val jsonKey = stringPreferencesKey("geofence_zones_json")

    override val zones: Flow<List<GeofenceZone>> = context.geofenceDataStore.data.map { preferences ->
        GeofenceZonesCodec.normalize(GeofenceZonesCodec.decode(preferences[jsonKey]))
    }

    override suspend fun upsertZone(zone: GeofenceZone) {
        context.geofenceDataStore.edit { preferences ->
            val current = GeofenceZonesCodec.decode(preferences[jsonKey])
            val without = current.filterNot { it.id == zone.id }
            writeZones(preferences, GeofenceZonesCodec.normalize(without + zone))
        }
    }

    override suspend fun deleteZone(id: String) {
        context.geofenceDataStore.edit { preferences ->
            val updated = GeofenceZonesCodec.decode(preferences[jsonKey]).filterNot { it.id == id }
            writeZones(preferences, GeofenceZonesCodec.normalize(updated))
        }
    }

    fun newZoneId(): String = UUID.randomUUID().toString()

    private fun writeZones(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        zones: List<GeofenceZone>,
    ) {
        if (zones.isEmpty()) {
            preferences.remove(jsonKey)
        } else {
            preferences[jsonKey] = GeofenceZonesCodec.encode(zones)
        }
    }
}
