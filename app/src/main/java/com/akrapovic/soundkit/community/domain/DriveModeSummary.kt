package com.akrapovic.soundkit.community.domain

import java.util.Locale

object DriveModeSummary {
    fun headline(settings: SoundKitSettings): String {
        if (!settings.driveModeEnabled) return "Off — tap to set up"
        val preferred = settings.preferredValveMode.name.lowercase()
        if (settings.automationPaused) return "Paused · prefers $preferred"
        if (QuietWindowEvaluator.isActive(settings.quietStart)) {
            return "Prefers $preferred · quiet hours"
        }
        return "Prefers $preferred on connect"
    }

    fun detail(settings: SoundKitSettings): String {
        if (!settings.driveModeEnabled) return "Choose Open or Closed for auto-apply"
        if (settings.automationPaused) return "Manual control until you resume"
        if (QuietWindowEvaluator.isActive(settings.quietStart)) {
            val hold = settings.quietStart.holdClosedMinutes
            return "Closed for $hold min after connect, then ${preferred(settings)}"
        }
        return "Auto-applies ${settings.preferredValveMode.name.lowercase()} when you're linked"
    }

    fun formatEndMinute(quiet: QuietStartSettings): String {
        val formatted = formatMinute(quiet.windowEndMinute)
        return if (QuietWindowEvaluator.isOvernight(quiet)) "$formatted (next day)" else formatted
    }

    private fun preferred(settings: SoundKitSettings): String =
        settings.preferredValveMode.name.lowercase()

    fun formatMinute(minuteOfDay: Int): String {
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }
}
