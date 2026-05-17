package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.ValveState

data class QsTilePresentation(
    val subtitle: String,
    val active: Boolean,
    val clickOpensApp: Boolean,
    val valveAction: ValveTileAction?,
)

enum class ValveTileAction {
    Open,
    Close,
}

object QsTilePresenter {
    fun present(
        connectionState: ConnectionState,
        valveState: ValveState,
        receiverStatusMessage: String?,
        defaultReceiver: SavedReceiver?,
    ): QsTilePresentation {
        if (connectionState !is ConnectionState.Connected) {
            return QsTilePresentation(
                subtitle = "open app",
                active = false,
                clickOpensApp = true,
                valveAction = null,
            )
        }
        if (receiverStatusMessage != null) {
            return QsTilePresentation(
                subtitle = "not ready",
                active = false,
                clickOpensApp = false,
                valveAction = null,
            )
        }
        if (valveState == ValveState.Unknown) {
            return QsTilePresentation(
                subtitle = "waiting",
                active = false,
                clickOpensApp = false,
                valveAction = null,
            )
        }
        val name = defaultReceiver?.displayName()
        val subtitle = when (valveState) {
            ValveState.Open -> name?.let { "$it · open" } ?: "open"
            ValveState.Closed -> name?.let { "$it · closed" } ?: "closed"
            ValveState.Unknown -> "waiting"
        }
        val action = when (valveState) {
            ValveState.Open -> ValveTileAction.Close
            ValveState.Closed -> ValveTileAction.Open
            ValveState.Unknown -> null
        }
        return QsTilePresentation(
            subtitle = subtitle,
            active = true,
            clickOpensApp = false,
            valveAction = action,
        )
    }
}
