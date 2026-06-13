package com.akrapovic.soundkit.community.domain

sealed interface ConnectionYieldState {
    data object None : ConnectionYieldState

    data class Yielded(val reason: ConnectionYieldReason) : ConnectionYieldState
}

enum class ConnectionYieldReason {
    HeadUnitMayBeActive,
}
