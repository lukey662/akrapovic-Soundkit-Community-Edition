package com.akrapovic.soundkit.community.car

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.SoundKitSettings

/**
 * Decides whether the car entry path should auto-connect to the remembered receiver.
 */
object CarRememberedDeviceConnector {
    fun rememberedDevice(settings: SoundKitSettings): SoundKitDevice? {
        val address = settings.rememberedDeviceAddress ?: return null
        return SoundKitDevice(
            name = settings.rememberedDeviceName ?: "Sound Kit",
            address = address,
        )
    }

    fun shouldAutoConnect(connectionState: ConnectionState, settings: SoundKitSettings): Boolean {
        if (rememberedDevice(settings) == null) return false
        return when (connectionState) {
            ConnectionState.Disconnected,
            is ConnectionState.Error,
            -> true
            ConnectionState.Scanning,
            is ConnectionState.Connecting,
            is ConnectionState.Connected,
            is ConnectionState.Reconnecting,
            -> false
        }
    }
}
