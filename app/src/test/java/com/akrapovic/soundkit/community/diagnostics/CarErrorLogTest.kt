package com.akrapovic.soundkit.community.diagnostics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CarErrorLogTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun recordsReadsAndClearsLastCarError() {
        val log = newLog()

        assertNull(log.read())
        assertFalse(log.hasError())

        log.event("session: hostPackage=com.example.host carApiLevel=7")
        log.record("onGetTemplate", IllegalStateException("template rejected"))

        val text = log.read().orEmpty()
        assertTrue(log.hasError())
        assertTrue(text.startsWith("SOUND KIT COMMUNITY CAR ERROR"))
        assertTrue(text.contains("where=onGetTemplate"))
        assertTrue(text.contains("applicationId=test.app"))
        assertTrue(text.contains("IllegalStateException: template rejected"))
        assertTrue(text.contains("SESSION EVENTS"))
        assertTrue(text.contains("hostPackage=com.example.host carApiLevel=7"))

        assertTrue(log.clear())
        assertFalse(log.hasError())
        assertNull(log.read())
        assertTrue(log.clear())
    }

    @Test
    fun eventRingKeepsOnlyTheLastTwenty() {
        val log = newLog()

        repeat(25) { index -> log.event("event-$index") }

        val events = log.recentEvents()
        assertEquals(CarErrorLog.MAX_EVENTS, events.size)
        assertEquals("event-5", events.first())
        assertEquals("event-24", events.last())

        log.record("screenScope", RuntimeException("late"))
        val text = log.read().orEmpty()
        assertFalse(text.contains("event-4\n"))
        assertTrue(text.contains("event-5"))
        assertTrue(text.contains("event-24"))
    }

    @Test
    fun latestEventContainingReturnsNewestMatch() {
        val log = newLog()

        assertNull(log.latestEventContaining("session:"))
        log.event("session: hostPackage=a carApiLevel=6")
        log.event("hostValidator=allowlist")
        log.event("session: hostPackage=b carApiLevel=7")

        assertEquals("session: hostPackage=b carApiLevel=7", log.latestEventContaining("session:"))
    }

    @Test
    fun recordWithoutEventsWritesNone() {
        val log = newLog()

        log.record("valveIcon", OutOfMemoryError("bitmap"))

        val text = log.read().orEmpty()
        assertTrue(text.contains("OutOfMemoryError: bitmap"))
        assertTrue(text.contains("SESSION EVENTS\nnone"))
    }

    private fun newLog(): CarErrorLog {
        var now = 1_000L
        return CarErrorLog(
            errorFile = File(File(temporaryFolder.root, "crashes"), "last_car_error.txt"),
            metadataProvider = { "applicationId=test.app\nversionName=test\nversionCode=1\nbuildType=debug" },
            clock = { now++ },
        )
    }
}
