package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.AwaySessionPolicy
import com.akrapovic.soundkit.community.domain.ConnectionState

/**
 * A car-wake or sticky restart should not leave a shade card when there is nothing live to show.
 * A service the user opened from the app stays up so a later connect still has a foreground session.
 */
object ConnectionServicePresence {
    fun shouldReleaseWhenIdle(
        carWake: Boolean,
        connectionState: ConnectionState,
        inCar: Boolean,
        scanning: Boolean,
        nowMillis: Long,
        startupSettled: Boolean,
    ): Boolean {
        if (!startupSettled || !carWake || inCar || scanning) return false
        return when (connectionState) {
            is ConnectionState.Connected,
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting,
            ConnectionState.Scanning,
            -> false
            is ConnectionState.Away -> !AwaySessionPolicy.isFresh(connectionState.sinceMillis, nowMillis)
            ConnectionState.Disconnected,
            is ConnectionState.Error,
            -> true
        }
    }
}
