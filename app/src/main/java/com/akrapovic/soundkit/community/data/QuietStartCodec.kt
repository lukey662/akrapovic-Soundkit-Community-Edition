package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.QuietStartSettings
import org.json.JSONObject

object QuietStartCodec {
    fun encode(settings: QuietStartSettings): String {
        return JSONObject()
            .put("enabled", settings.enabled)
            .put("daysOfWeek", settings.daysOfWeek.sorted().joinToString(","))
            .put("windowStartMinute", settings.windowStartMinute)
            .put("windowEndMinute", settings.windowEndMinute)
            .put("holdClosedMinutes", settings.holdClosedMinutes)
            .toString()
    }

    fun decode(json: String?): QuietStartSettings {
        if (json.isNullOrBlank()) return QuietStartSettings()
        return runCatching {
            val item = JSONObject(json)
            QuietStartSettings(
                enabled = item.optBoolean("enabled", false),
                daysOfWeek = item.optString("daysOfWeek")
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toSet()
                    .ifEmpty { QuietStartSettings().daysOfWeek },
                windowStartMinute = item.optInt("windowStartMinute", 6 * 60),
                windowEndMinute = item.optInt("windowEndMinute", 9 * 60),
                holdClosedMinutes = item.optInt("holdClosedMinutes", 5).coerceIn(1, 15),
            )
        }.getOrDefault(QuietStartSettings())
    }
}
