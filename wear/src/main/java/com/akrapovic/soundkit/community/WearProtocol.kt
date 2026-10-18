package com.akrapovic.soundkit.community

/**
 * Wearable Data Layer contract shared with the watch module (keep both copies identical).
 * Only valve state and Open/Close requests cross it; never receiver addresses.
 */
object WearProtocol {
    const val CAPABILITY_PHONE = "soundkit_phone"
    const val PATH_PREFIX = "/soundkit"

    /** DataItem: connected, ready, valve (OPEN, CLOSED, UNKNOWN), updatedAt, glow (ARGB). */
    const val PATH_STATE = "/soundkit/state"
    const val PATH_OPEN = "/soundkit/open"
    const val PATH_CLOSE = "/soundkit/close"

    /** Message phone to watch with a short UTF-8 result. */
    const val PATH_RESULT = "/soundkit/result"

    const val KEY_CONNECTED = "connected"
    const val KEY_READY = "ready"
    const val KEY_VALVE = "valve"
    const val KEY_UPDATED_AT = "updatedAt"
    const val KEY_GLOW = "glow"
}
