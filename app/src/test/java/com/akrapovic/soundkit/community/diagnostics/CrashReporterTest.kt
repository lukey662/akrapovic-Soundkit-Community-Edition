package com.akrapovic.soundkit.community.diagnostics

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashReporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesReadsAndClearsPendingCrash() {
        val crashFile = File(temporaryFolder.newFolder("crashes"), "last_crash.txt")
        val reporter = CrashReporter(
            crashFile = crashFile,
            metadataProvider = { "applicationId=test.app\nversionName=test" },
        )

        reporter.recordCrash(
            threadName = "main",
            throwable = IllegalStateException("boom"),
            occurredAtMillis = 1_000L,
        )

        assertTrue(reporter.hasPendingCrash())
        assertTrue(reporter.readPendingCrash().orEmpty().contains("IllegalStateException: boom"))
        assertTrue(reporter.clearPendingCrash())
        assertFalse(reporter.hasPendingCrash())
    }
}
