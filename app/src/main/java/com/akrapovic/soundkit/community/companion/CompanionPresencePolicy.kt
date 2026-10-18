package com.akrapovic.soundkit.community.companion

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitSettings

/** Decides whether Android reporting the paired receiver nearby should start a connection. */
object CompanionPresencePolicy {
    fun shouldConnect(address: String, settings: SoundKitSettings, connectionState: ConnectionState): Boolean {
        if (!settings.onboardingCompleted) return false
        val receiver = settings.defaultReceiver ?: return false
        if (!receiver.address.equals(address, ignoreCase = true)) return false
        if (!settings.connectInCar && !settings.autoReconnect) return false
        return when (connectionState) {
            ConnectionState.Disconnected,
            is ConnectionState.Error,
            is ConnectionState.Away,
            -> true
            ConnectionState.Scanning,
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting,
            is ConnectionState.Connected,
            -> false
        }
    }
}
