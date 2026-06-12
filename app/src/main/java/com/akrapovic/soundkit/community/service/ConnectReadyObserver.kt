package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState

/**
 * Detects the first moment a BLE link is ready for drive-mode automation within a session.
 * Valve state changes while connected must not count as a new connect-ready event.
 */
internal object ConnectReadyObserver {
    data class Transition(
        val isConnectReady: Boolean,
        val becameReady: Boolean,
        val disconnected: Boolean,
    )

    fun evaluate(
        connection: ConnectionState,
        valve: ValveState,
        notReady: String?,
        wasConnectReady: Boolean,
    ): Transition {
        val disconnected = connection is ConnectionState.Disconnected ||
            connection is ConnectionState.Error
        val isConnectReady = connection is ConnectionState.Connected &&
            valve != ValveState.Unknown &&
            notReady == null
        val becameReady = isConnectReady && !wasConnectReady
        return Transition(isConnectReady, becameReady, disconnected)
    }
}
