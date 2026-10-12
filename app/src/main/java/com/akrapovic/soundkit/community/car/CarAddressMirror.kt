package com.akrapovic.soundkit.community.car

import android.content.Context

/**
 * Synchronous copy of the saved car Bluetooth address.
 * The presence receiver cannot block on DataStore, and it must ignore ACL events for other devices.
 */
object CarAddressMirror {
    private const val PREFS = "soundkit_car_mirror"
    private const val KEY = "car_bluetooth_address"

    fun read(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun write(context: Context, address: String?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (address.isNullOrBlank()) {
            editor.remove(KEY)
        } else {
            editor.putString(KEY, address)
        }
        editor.commit()
    }
}
