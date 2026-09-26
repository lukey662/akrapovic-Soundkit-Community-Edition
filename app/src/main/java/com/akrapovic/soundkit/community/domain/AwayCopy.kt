package com.akrapovic.soundkit.community.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object AwayCopy {
    fun statusText(
        reason: AwayReason,
        sinceMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val time = formatClock(sinceMillis, zone, locale)
        return when (reason) {
            AwayReason.LeftCar -> "Left the car at $time"
            AwayReason.OutOfRange -> "Receiver out of range since $time"
        }
    }

    fun formatClock(sinceMillis: Long, zone: ZoneId, locale: Locale): String {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
            .format(Instant.ofEpochMilli(sinceMillis))
    }
}
