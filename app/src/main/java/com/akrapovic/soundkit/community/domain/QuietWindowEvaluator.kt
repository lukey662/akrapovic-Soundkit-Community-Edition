package com.akrapovic.soundkit.community.domain

import java.util.Calendar
import java.util.TimeZone

object QuietWindowEvaluator {
    fun isOvernight(quiet: QuietStartSettings): Boolean =
        quiet.windowEndMinute < quiet.windowStartMinute

    fun isActive(
        quiet: QuietStartSettings,
        now: Calendar = Calendar.getInstance(TimeZone.getDefault()),
    ): Boolean {
        if (!quiet.enabled) return false
        val dayOfWeek = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
        if (dayOfWeek !in quiet.daysOfWeek) return false
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (quiet.windowEndMinute >= quiet.windowStartMinute) {
            minuteOfDay in quiet.windowStartMinute..quiet.windowEndMinute
        } else {
            minuteOfDay >= quiet.windowStartMinute || minuteOfDay <= quiet.windowEndMinute
        }
    }
}
