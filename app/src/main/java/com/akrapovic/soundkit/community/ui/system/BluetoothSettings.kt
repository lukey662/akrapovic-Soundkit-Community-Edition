package com.akrapovic.soundkit.community.ui.system

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Opens Android's Bluetooth settings so the user can forget SoundKit. Apps cannot remove a pairing
 * with public APIs, and this app does not use hidden ones.
 */
fun openBluetoothSettings(context: Context) {
    val opened = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
