package com.akrapovic.soundkit.community.automation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.akrapovic.soundkit.community.data.GeofenceZonesStore
import com.akrapovic.soundkit.community.domain.GeofenceZone
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

@Singleton
class GeofenceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zonesStore: GeofenceZonesStore,
) {
    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    suspend fun syncZones() {
        val zones = zonesStore.zones.first()
        if (zones.isEmpty()) {
            runCatching { client.removeGeofences(pendingIntent).await() }
            return
        }
        val geofences = zones.map { it.toGeofence() }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        client.addGeofences(request, pendingIntent).await()
    }

    suspend fun removeAll() {
        runCatching { client.removeGeofences(pendingIntent).await() }
    }

    private fun GeofenceZone.toGeofence(): Geofence {
        return Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
    }
}
