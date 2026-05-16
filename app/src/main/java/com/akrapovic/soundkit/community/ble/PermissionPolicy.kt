package com.akrapovic.soundkit.community.ble

import android.Manifest
import android.os.Build

object PermissionPolicy {
    fun requiredRuntimePermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
        val permissions = mutableListOf<String>()
        if (sdkInt >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions
    }

    fun requiredBlePermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
        return if (sdkInt >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

