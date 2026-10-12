package com.akrapovic.soundkit.community.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarWakePolicyTest {
    @Test
    fun androidAutoProjectionAlwaysWakes() {
        assertTrue(
            CarWakePolicy.shouldWake(
                action = CarWakePolicy.ACTION_CAR_CONNECTION_UPDATED,
                deviceAddress = null,
                savedCarAddress = null,
            ),
        )
    }

    @Test
    fun onlyTheSavedCarBluetoothAddressWakes() {
        assertTrue(
            CarWakePolicy.shouldWake(
                action = CarWakePolicy.ACTION_ACL_CONNECTED,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                savedCarAddress = "aa:bb:cc:dd:ee:ff",
            ),
        )
        assertFalse(
            CarWakePolicy.shouldWake(
                action = CarWakePolicy.ACTION_ACL_DISCONNECTED,
                deviceAddress = "11:22:33:44:55:66",
                savedCarAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
        assertFalse(
            CarWakePolicy.shouldWake(
                action = CarWakePolicy.ACTION_ACL_CONNECTED,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                savedCarAddress = null,
            ),
        )
    }
}
