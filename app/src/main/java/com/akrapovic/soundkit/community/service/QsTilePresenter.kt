package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.ValveState

enum class QsTileState {
    Active,
    Inactive,
    /** Not tappable: the receiver is checking or not ready, so no command can be sent. */
    Unavailable,
}

data class QsTilePresentation(
    val subtitle: String,
    val tileState: QsTileState,
    val clickOpensApp: Boolean,
    val valveAction: ValveTileAction?,
)

enum class ValveTileAction {
    Open,
    Close,
}

/** Shared by Quick Settings and the home-screen widget so both gate commands the same way. */
object QsTilePresenter {
    @Suppress("UNUSED_PARAMETER")
    fun present(
        connectionState: ConnectionState,
        valveState: ValveState,
        receiverStatusMessage: String?,
        defaultReceiver: SavedReceiver?,
    ): QsTilePresentation {
        if (connectionState !is ConnectionState.Connected) {
            return QsTilePresentation(
                subtitle = "tap to connect",
                tileState = QsTileState.Inactive,
                clickOpensApp = true,
                valveAction = null,
            )
        }
        if (receiverStatusMessage != null) {
            return QsTilePresentation(
                subtitle = "not ready",
                tileState = QsTileState.Unavailable,
                clickOpensApp = false,
                valveAction = null,
            )
        }
        return when (valveState) {
            ValveState.Unknown -> QsTilePresentation(
                subtitle = "checking",
                tileState = QsTileState.Unavailable,
                clickOpensApp = false,
                valveAction = null,
            )
            ValveState.Open -> QsTilePresentation(
                subtitle = "open · tap to close",
                tileState = QsTileState.Active,
                clickOpensApp = false,
                valveAction = ValveTileAction.Close,
            )
            ValveState.Closed -> QsTilePresentation(
                subtitle = "closed · tap to open",
                tileState = QsTileState.Inactive,
                clickOpensApp = false,
                valveAction = ValveTileAction.Open,
            )
        }
    }
}
