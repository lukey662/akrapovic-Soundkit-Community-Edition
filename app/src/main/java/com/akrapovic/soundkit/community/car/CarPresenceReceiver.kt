package com.akrapovic.soundkit.community.car

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.akrapovic.soundkit.community.data.readStoredCarBluetoothAddress
import com.akrapovic.soundkit.community.service.BleConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wakes the process when this phone joins the saved car or Android Auto starts.
 * Other Bluetooth devices must not rebuild the shade card.
 */
class CarPresenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        val deviceAddress = deviceAddress(intent)
        val saved = CarAddressMirror.read(context)
        if (CarWakePolicy.shouldWake(action, deviceAddress, saved)) {
            BleConnectionService.startFromCarWake(context)
            return
        }
        // A cold mirror means this process has not loaded settings yet. Read DataStore off the main thread.
        if (!saved.isNullOrBlank()) return
        if (action != CarWakePolicy.ACTION_ACL_CONNECTED &&
            action != CarWakePolicy.ACTION_ACL_DISCONNECTED
        ) {
            return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val stored = readStoredCarBluetoothAddress(appContext)
                CarAddressMirror.write(appContext, stored)
                if (CarWakePolicy.shouldWake(action, deviceAddress, stored)) {
                    BleConnectionService.startFromCarWake(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun deviceAddress(intent: Intent?): String? {
        if (intent == null) return null
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        return device?.address
    }
}
