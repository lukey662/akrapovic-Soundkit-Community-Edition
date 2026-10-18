package com.akrapovic.soundkit.community.domain

/**
 * Turns a valve command failure into a short title and next step.
 * The receiver toggles, so an unconfirmed command must read as "checking", never as a plain error.
 */
object CommandFailureCopy {
    data class Copy(val title: String, val body: String)

    fun from(message: String, valveState: ValveState): Copy {
        val text = message.lowercase()
        return when {
            "did not confirm" in text || ("unknown" in text && valveState == ValveState.Unknown) -> Copy(
                title = "Valve didn't confirm",
                body = "Checking its position before you can tap again.",
            )
            "not connected" in text || "no active ble connection" in text -> Copy(
                title = "Receiver not connected",
                body = "Reconnect, then try again.",
            )
            "waiting for receiver status" in text -> Copy(
                title = "Checking the valves",
                body = "This takes a moment after connecting.",
            )
            "write failed" in text || "rejected" in text -> Copy(
                title = "Command didn't send",
                body = "Try again. If it repeats, export diagnostics.",
            )
            "not ready" in text -> Copy(
                title = "Receiver not ready",
                body = message,
            )
            else -> Copy(
                title = "Couldn't change the valves",
                body = message,
            )
        }
    }
}
