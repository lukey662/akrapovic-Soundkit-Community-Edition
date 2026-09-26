package com.akrapovic.soundkit.community.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.akrapovic.soundkit.community.service.BleConnectionService

/**
 * Wakes the process when the phone joins a Bluetooth device or Android Auto starts.
 * Presence itself is decided by [CarPresenceMonitor], which Application starts before receivers run.
 * ACL and car-connection broadcasts are system/host events. This receiver only starts the service.
 */
class CarPresenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        BleConnectionService.start(context)
    }
}
