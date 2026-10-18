package com.akrapovic.soundkit.community.wear

import com.akrapovic.soundkit.community.domain.ConnectionState

/**
 * The watch may only act on a live link: the phone's foreground service is running then, and the
 * coordinator still refuses unknown or not-ready states. It never starts a connection.
 */
object WearCommandPolicy {
    fun allow(connectionState: ConnectionState): Boolean = connectionState is ConnectionState.Connected
}
