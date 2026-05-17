package com.akrapovic.soundkit.community.domain

/**
 * Shared policy for connecting to the user's default saved receiver on launch or car entry.
 */
object RememberedDeviceConnector {
    fun defaultDevice(settings: SoundKitSettings): SoundKitDevice? {
        val receiver = settings.defaultReceiver ?: return null
        return SoundKitDevice(
            name = receiver.displayName(),
            address = receiver.address,
        )
    }

    fun shouldAutoConnect(connectionState: ConnectionState, settings: SoundKitSettings): Boolean {
        if (!settings.connectOnLaunch) return false
        if (defaultDevice(settings) == null) return false
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
