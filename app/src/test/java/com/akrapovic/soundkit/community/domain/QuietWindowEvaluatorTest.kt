package com.akrapovic.soundkit.community.domain

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietWindowEvaluatorTest {
    private val allDays = setOf(0, 1, 2, 3, 4, 5, 6)

    @Test
    fun sameDayDefaultWindowActiveAtSevenAm() {
        val quiet = QuietStartSettings(
            enabled = true,
            daysOfWeek = allDays,
            windowStartMinute = 6 * 60,
            windowEndMinute = 9 * 60,
        )

        assertTrue(QuietWindowEvaluator.isActive(quiet, calendarAt(dayIndex = 0, hour = 7, minute = 0)))
    }

    @Test
    fun sameDayEarlyRiserWindowActiveAtThreeThirtyAm() {
        val quiet = QuietStartSettings(
            enabled = true,
            daysOfWeek = allDays,
            windowStartMinute = 3 * 60,
            windowEndMinute = 9 * 60,
        )

        assertTrue(QuietWindowEvaluator.isActive(quiet, calendarAt(dayIndex = 0, hour = 3, minute = 30)))
    }

    @Test
    fun sameDayEarlyRiserWindowInactiveBeforeStart() {
        val quiet = QuietStartSettings(
            enabled = true,
            daysOfWeek = allDays,
            windowStartMinute = 3 * 60,
            windowEndMinute = 9 * 60,
        )

        assertFalse(QuietWindowEvaluator.isActive(quiet, calendarAt(dayIndex = 0, hour = 2, minute = 0)))
    }

    @Test
    fun overnightWindowActiveInEvening() {
        val quiet = QuietStartSettings(
            enabled = true,
            daysOfWeek = allDays,
            windowStartMinute = 22 * 60,
            windowEndMinute = 6 * 60,
        )

        assertTrue(QuietWindowEvaluator.isActive(quiet, calendarAt(dayIndex = 0, hour = 23, minute = 0)))
    }

    @Test
    fun overnightWindowActiveInMorning() {
        val quiet = QuietStartSettings(
            enabled = true,
            daysOfWeek = allDays,
            windowStartMinute = 22 * 60,
            windowEndMinute = 6 * 60,
        )

        assertTrue(QuietWindowEvaluator.isActive(quiet, calendarAt(dayIndex = 0, hour = 3, minute = 0)))
    }

    @Test
    fun overnightWindowInactiveMidday() {
        val quiet = QuietStartSettings(
            enabled = true,
            daysOfWeek = allDays,
            windowStartMinute = 22 * 60,
            windowEndMinute = 6 * 60,
        )

        assertFalse(QuietWindowEvaluator.isActive(quiet, calendarAt(dayIndex = 0, hour = 12, minute = 0)))
    }

    @Test
    fun overnightWindowRespectsDayFilterOnMorningSegment() {
        val quiet = QuietStartSettings(
            enabled = true,
            daysOfWeek = setOf(0),
            windowStartMinute = 22 * 60,
            windowEndMinute = 6 * 60,
        )

        assertFalse(QuietWindowEvaluator.isActive(quiet, calendarAt(dayIndex = 6, hour = 3, minute = 0)))
    }

    @Test
    fun disabledQuietWindowIsNeverActive() {
        val quiet = QuietStartSettings(
            enabled = false,
            daysOfWeek = allDays,
            windowStartMinute = 0,
            windowEndMinute = 24 * 60 - 1,
        )

        assertFalse(QuietWindowEvaluator.isActive(quiet, calendarAt(dayIndex = 0, hour = 3, minute = 0)))
    }

    @Test
    fun isOvernightWhenEndBeforeStart() {
        val quiet = QuietStartSettings(
            windowStartMinute = 22 * 60,
            windowEndMinute = 6 * 60,
        )

        assertTrue(QuietWindowEvaluator.isOvernight(quiet))
    }

    @Test
    fun isNotOvernightForSameDayWindow() {
        val quiet = QuietStartSettings(
            windowStartMinute = 6 * 60,
            windowEndMinute = 9 * 60,
        )

        assertFalse(QuietWindowEvaluator.isOvernight(quiet))
    }

    /** Mon=0 … Sun=6; uses Jan 6–12 2025 (Mon–Sun). */
    private fun calendarAt(dayIndex: Int, hour: Int, minute: Int): Calendar {
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            set(2025, Calendar.JANUARY, 6 + dayIndex, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
