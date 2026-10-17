package com.akrapovic.soundkit.community.domain

import java.util.Locale

object QuietHoldCopy {
    fun phoneHelper(endsAtMillis: Long, nowMillis: Long): String {
        return "Quiet start: opening in ${formatRemaining(endsAtMillis, nowMillis)}"
    }

    fun shortStatus(endsAtMillis: Long, nowMillis: Long): String {
        return "Opening in ${formatRemaining(endsAtMillis, nowMillis)}"
    }

    fun formatRemaining(endsAtMillis: Long, nowMillis: Long): String {
        val totalSeconds = (endsAtMillis - nowMillis).coerceAtLeast(0L) / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
