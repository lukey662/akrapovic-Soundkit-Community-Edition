package com.akrapovic.soundkit.community.domain

/**
 * An away session is only worth showing for the rest of the day.
 * Older timestamps are forgotten so a later Bluetooth event cannot repost "left the car at 9:40 am".
 */
object AwaySessionPolicy {
    const val MAX_AGE_MS = 12L * 60L * 60L * 1000L

    fun isFresh(sinceMillis: Long, nowMillis: Long): Boolean {
        if (sinceMillis <= 0L) return false
        val age = nowMillis - sinceMillis
        return age in 0..MAX_AGE_MS
    }
}
