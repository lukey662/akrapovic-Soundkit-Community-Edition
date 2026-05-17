package com.akrapovic.soundkit.community.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.akrapovic.soundkit.community.domain.rules.RuleExecutionEngine
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GeofenceTransitionReceiver : BroadcastReceiver() {
    @Inject lateinit var activeGeofenceState: ActiveGeofenceState
    @Inject lateinit var ruleExecutionEngine: RuleExecutionEngine

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val event = GeofencingEvent.fromIntent(intent) ?: run {
            pendingResult.finish()
            return
        }
        if (event.hasError()) {
            pendingResult.finish()
            return
        }
        CoroutineScope(Dispatchers.Default).launch {
            try {
                event.triggeringGeofences?.forEach { geofence ->
                    val zoneId = geofence.requestId
                    val inside = when (event.geofenceTransition) {
                        Geofence.GEOFENCE_TRANSITION_ENTER -> true
                        Geofence.GEOFENCE_TRANSITION_EXIT -> false
                        else -> return@forEach
                    }
                    activeGeofenceState.setInside(zoneId, inside)
                }
                ruleExecutionEngine.evaluateNow(triggerReason = "geofence")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
