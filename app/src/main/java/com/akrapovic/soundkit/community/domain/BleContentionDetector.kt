package com.akrapovic.soundkit.community.domain

/**
 * Heuristic detector for BLE contention when another phone holds the receiver link.
 */
class BleContentionDetector(
    private val quickDropWindowMs: Long = QUICK_DROP_WINDOW_MS,
    private val stormWindowMs: Long = STORM_WINDOW_MS,
    private val stormEventThreshold: Int = STORM_EVENT_THRESHOLD,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var connectedAtMs: Long? = null
    private val recentEvents = ArrayDeque<Long>()

    fun onConnected() {
        connectedAtMs = clock()
    }

    fun onDisconnected(userInitiated: Boolean): ContentionSignal? {
        val connectedAt = connectedAtMs
        connectedAtMs = null
        if (userInitiated) {
            recordEvent(clock())
            return null
        }
        val now = clock()
        recordEvent(now)
        if (connectedAt != null && now - connectedAt <= quickDropWindowMs) {
            return ContentionSignal.QuickDrop
        }
        if (countEventsWithin(now, stormWindowMs) >= stormEventThreshold) {
            return ContentionSignal.ConnectStorm
        }
        return null
    }

    fun onConnectFailed(): ContentionSignal? {
        val now = clock()
        recordEvent(now)
        if (countEventsWithin(now, stormWindowMs) >= stormEventThreshold) {
            return ContentionSignal.ConnectStorm
        }
        return null
    }

    fun reset() {
        connectedAtMs = null
        recentEvents.clear()
    }

    private fun recordEvent(timestampMs: Long) {
        recentEvents.addLast(timestampMs)
        trimEvents(timestampMs)
    }

    private fun trimEvents(now: Long) {
        while (recentEvents.isNotEmpty() && now - recentEvents.first() > stormWindowMs) {
            recentEvents.removeFirst()
        }
    }

    private fun countEventsWithin(now: Long, windowMs: Long): Int {
        trimEvents(now)
        return recentEvents.count { now - it <= windowMs }
    }

    enum class ContentionSignal {
        QuickDrop,
        ConnectStorm,
    }

    companion object {
        const val QUICK_DROP_WINDOW_MS = 3_000L
        const val STORM_WINDOW_MS = 30_000L
        const val STORM_EVENT_THRESHOLD = 3
    }
}
