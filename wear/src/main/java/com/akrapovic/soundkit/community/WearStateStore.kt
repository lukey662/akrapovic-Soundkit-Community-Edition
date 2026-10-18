package com.akrapovic.soundkit.community

import android.content.Context

/** Last valve state the phone published, plus the last command result, kept on the watch. */
data class WearState(
    val connected: Boolean,
    val ready: Boolean,
    val valve: String,
    val glow: Int,
    val updatedAt: Long,
    val lastResult: String?,
    val lastResultAt: Long,
)

object WearStateStore {
    private const val PREFS = "soundkit_wear"
    const val DEFAULT_GLOW = 0xFFD6BD73.toInt()

    fun read(context: Context): WearState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WearState(
            connected = prefs.getBoolean(WearProtocol.KEY_CONNECTED, false),
            ready = prefs.getBoolean(WearProtocol.KEY_READY, false),
            valve = prefs.getString(WearProtocol.KEY_VALVE, "UNKNOWN") ?: "UNKNOWN",
            glow = prefs.getInt(WearProtocol.KEY_GLOW, DEFAULT_GLOW),
            updatedAt = prefs.getLong(WearProtocol.KEY_UPDATED_AT, 0L),
            lastResult = prefs.getString("lastResult", null),
            lastResultAt = prefs.getLong("lastResultAt", 0L),
        )
    }

    fun writeState(context: Context, connected: Boolean, ready: Boolean, valve: String, glow: Int, updatedAt: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(WearProtocol.KEY_CONNECTED, connected)
            .putBoolean(WearProtocol.KEY_READY, ready)
            .putString(WearProtocol.KEY_VALVE, valve)
            .putInt(WearProtocol.KEY_GLOW, glow)
            .putLong(WearProtocol.KEY_UPDATED_AT, updatedAt)
            .apply()
    }

    fun writeResult(context: Context, result: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("lastResult", result)
            .putLong("lastResultAt", System.currentTimeMillis())
            .apply()
    }
}
