package com.akrapovic.soundkit.community.companion

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.allowsAutomaticConnect

/** Decides whether Android reporting the paired receiver nearby should start a connection. */
object CompanionPresencePolicy {
    fun shouldConnect(address: String, settings: SoundKitSettings, connectionState: ConnectionState): Boolean {
        if (!settings.onboardingCompleted) return false
        val receiver = settings.defaultReceiver ?: return false
        if (!receiver.address.equals(address, ignoreCase = true)) return false
        if (!settings.connectInCar && !settings.autoReconnect) return false
        return connectionState.allowsAutomaticConnect()
    }
}
