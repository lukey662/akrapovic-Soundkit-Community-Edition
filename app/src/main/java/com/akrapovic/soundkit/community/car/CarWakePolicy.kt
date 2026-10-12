package com.akrapovic.soundkit.community.car

/**
 * Decides which system broadcasts may start the connection service.
 * Headphones, watches, and other ACL devices must not wake the shade.
 */
object CarWakePolicy {
    const val ACTION_ACL_CONNECTED = "android.bluetooth.device.action.ACL_CONNECTED"
    const val ACTION_ACL_DISCONNECTED = "android.bluetooth.device.action.ACL_DISCONNECTED"
    const val ACTION_CAR_CONNECTION_UPDATED = "androidx.car.app.connection.action.CAR_CONNECTION_UPDATED"

    fun shouldWake(action: String?, deviceAddress: String?, savedCarAddress: String?): Boolean {
        if (action == ACTION_CAR_CONNECTION_UPDATED) return true
        if (action != ACTION_ACL_CONNECTED && action != ACTION_ACL_DISCONNECTED) return false
        if (savedCarAddress.isNullOrBlank() || deviceAddress.isNullOrBlank()) return false
        return deviceAddress.equals(savedCarAddress, ignoreCase = true)
    }
}
