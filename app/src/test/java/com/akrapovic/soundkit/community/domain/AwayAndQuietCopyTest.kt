package com.akrapovic.soundkit.community.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwayAndQuietCopyTest {
    @Test
    fun awaySessionExpiresAfterTwelveHours() {
        val now = 1_700_000_000_000L
        assertTrue(AwaySessionPolicy.isFresh(now, now))
        assertTrue(AwaySessionPolicy.isFresh(now - AwaySessionPolicy.MAX_AGE_MS, now))
        assertFalse(AwaySessionPolicy.isFresh(now - AwaySessionPolicy.MAX_AGE_MS - 1, now))
        assertFalse(AwaySessionPolicy.isFresh(0L, now))
    }

    @Test
    fun quietHoldCopyFormatsMinutesAndSeconds() {
        val now = 1_000L
        val endsAt = now + (2 * 60 + 5) * 1_000L
        assertEquals("2:05", QuietHoldCopy.formatRemaining(endsAt, now))
        assertEquals("Quiet start — opening in 2:05", QuietHoldCopy.phoneHelper(endsAt, now))
        assertEquals("Opening in 2:05", QuietHoldCopy.shortStatus(endsAt, now))
        assertEquals("0:00", QuietHoldCopy.formatRemaining(now, now + 5_000L))
    }
}
