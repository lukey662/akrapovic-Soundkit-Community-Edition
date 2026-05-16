package com.akrapovic.soundkit.community.diagnostics

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsShareTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun shareIntentDoesNotIncludeEmailMetadata() {
        val uri = Uri.parse("content://test/example.txt")

        val intent = DiagnosticsShare.buildShareIntent(
            context = context,
            uri = uri,
            fileName = "example.txt",
        )

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertNull(
            "Subject leaks email context",
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
        )
        assertNull(
            "Body leaks email context (Gmail will auto-populate)",
            intent.getStringExtra(Intent.EXTRA_TEXT),
        )
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertTrue(
            "Read URI permission flag must be set",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    @Test
    fun saveIntentTargetsCreateDocumentForTextFile() {
        val intent = DiagnosticsShare.buildSaveIntent(suggestedFileName = "report.txt")

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("text/plain", intent.type)
        assertTrue(intent.categories.contains(Intent.CATEGORY_OPENABLE))
        assertEquals("report.txt", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertFalse(
            "Save intent must not contain email metadata",
            intent.hasExtra(Intent.EXTRA_SUBJECT) || intent.hasExtra(Intent.EXTRA_TEXT),
        )
    }
}
