package com.akrapovic.soundkit.community.car

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.test.testDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarRememberedDeviceConnectorTest {
    @Test
    fun rememberedDeviceReturnsNullWhenAddressMissing() {
        assertNull(
            CarRememberedDeviceConnector.rememberedDevice(
                SoundKitSettings(rememberedDeviceName = "Kit"),
            ),
        )
    }

    @Test
    fun rememberedDeviceBuildsFromSettings() {
        val device = CarRememberedDeviceConnector.rememberedDevice(
            SoundKitSettings(
                rememberedDeviceName = "My Kit",
                rememberedDeviceAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )

        assertEquals("My Kit", device?.name)
        assertEquals("AA:BB:CC:DD:EE:FF", device?.address)
    }

    @Test
    fun shouldAutoConnectWhenDisconnectedWithRememberedDevice() {
        val settings = SoundKitSettings(rememberedDeviceAddress = "00:11:22:33:44:55")

        assertTrue(
            CarRememberedDeviceConnector.shouldAutoConnect(ConnectionState.Disconnected, settings),
        )
        assertTrue(
            CarRememberedDeviceConnector.shouldAutoConnect(
                ConnectionState.Error("timeout", recoverable = true),
                settings,
            ),
        )
    }

    @Test
    fun shouldNotAutoConnectWhenAlreadyActive() {
        val settings = SoundKitSettings(rememberedDeviceAddress = "00:11:22:33:44:55")
        val device = testDevice()

        assertFalse(
            CarRememberedDeviceConnector.shouldAutoConnect(ConnectionState.Connected(device), settings),
        )
        assertFalse(
            CarRememberedDeviceConnector.shouldAutoConnect(ConnectionState.Connecting(device), settings),
        )
        assertFalse(
            CarRememberedDeviceConnector.shouldAutoConnect(
                ConnectionState.Reconnecting(device, attempt = 1, nextDelayMs = 1000L),
                settings,
            ),
        )
    }
}
