package com.akrapovic.soundkit.community.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QuietStartCodecTest {
    @Test
    fun decodeUsesThreeMinuteHoldDefault() {
        val decoded = QuietStartCodec.decode(
            """{"enabled":true,"daysOfWeek":"0,1,2,3,4,5,6"}""",
        )

        assertEquals(3, decoded.holdClosedMinutes)
    }
}
