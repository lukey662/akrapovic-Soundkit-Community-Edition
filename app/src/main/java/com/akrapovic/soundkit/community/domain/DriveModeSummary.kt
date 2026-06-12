package com.akrapovic.soundkit.community.domain

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object DriveModeSummary {
    fun headline(settings: SoundKitSettings): String {
        if (!settings.driveModeEnabled) return "Off — tap to set up"
        val preferred = settings.preferredValveMode.name.lowercase()
        if (settings.automationPaused) return "Paused · prefers $preferred"
        if (settings.quietStart.enabled && isQuietWindowActive(settings.quietStart)) {
            return "Prefers $preferred · quiet hours"
        }
        return "Prefers $preferred on connect"
    }

    fun detail(settings: SoundKitSettings): String {
        if (!settings.driveModeEnabled) return "Choose Open or Closed for auto-apply"
        if (settings.automationPaused) return "Manual control until you resume"
        if (settings.quietStart.enabled && isQuietWindowActive(settings.quietStart)) {
            val hold = settings.quietStart.holdClosedMinutes
            return "Closed for $hold min after connect, then ${preferred(settings)}"
        }
        return "Auto-applies ${settings.preferredValveMode.name.lowercase()} when you're linked"
    }

    private fun preferred(settings: SoundKitSettings): String =
        settings.preferredValveMode.name.lowercase()

    private fun isQuietWindowActive(quiet: QuietStartSettings): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        if (dayOfWeek !in quiet.daysOfWeek) return false
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return minuteOfDay in quiet.windowStartMinute..quiet.windowEndMinute
    }

    fun formatMinute(minuteOfDay: Int): String {
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }
}
