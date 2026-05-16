package com.akrapovic.soundkit.community.ble

import kotlin.math.min

class RetryPolicy(
    private val initialDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L,
    private val multiplier: Double = 2.0,
) {
    fun delayForAttempt(attempt: Int): Long {
        require(attempt >= 1) { "attempt must be 1 or greater" }
        var delay = initialDelayMs.toDouble()
        repeat(attempt - 1) {
            delay *= multiplier
        }
        return min(delay.toLong(), maxDelayMs)
    }
}

