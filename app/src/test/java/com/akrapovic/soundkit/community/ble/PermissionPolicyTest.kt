package com.akrapovic.soundkit.community.ble

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {
    @Test
    fun androidElevenRequiresLocationForBleScan() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PermissionPolicy.requiredBlePermissions(Build.VERSION_CODES.R),
        )
    }

    @Test
    fun androidTwelveRequiresScanAndConnect() {
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            PermissionPolicy.requiredBlePermissions(Build.VERSION_CODES.S),
        )
    }

    @Test
    fun androidThirteenAddsNotificationRuntimePermission() {
        val permissions = PermissionPolicy.requiredRuntimePermissions(Build.VERSION_CODES.TIRAMISU)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
    }
}

