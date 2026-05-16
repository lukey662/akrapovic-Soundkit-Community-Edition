package com.akrapovic.soundkit.community.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object DiagnosticsShare {
    /**
     * Share a diagnostics or crash report file as an attachment only.
     *
     * No `EXTRA_SUBJECT` or `EXTRA_TEXT` is set so email handlers (e.g. Gmail)
     * cannot prefill the user's account or message body. The user picks the
     * destination app from the system chooser and starts with a blank compose.
     */
    fun shareReport(
        context: Context,
        file: File,
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = buildShareIntent(context, uri, file.name)
        context.startActivity(Intent.createChooser(intent, "Share diagnostics report"))
    }

    /**
     * Build (without launching) the file-only share intent. Exposed for tests
     * so we can assert no email metadata is attached.
     */
    fun buildShareIntent(
        context: Context,
        uri: Uri,
        fileName: String,
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Build an `ACTION_CREATE_DOCUMENT` intent for use with the Storage Access
     * Framework. Lets the user save the report to Files, Drive, etc., without
     * any email handler intercepting the flow.
     */
    fun buildSaveIntent(suggestedFileName: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "text/plain"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, suggestedFileName)
        }
    }

    /**
     * Copy the source [File] bytes into the destination [Uri] selected by the
     * user via `ACTION_CREATE_DOCUMENT`.
     */
    fun writeToUri(context: Context, source: File, destination: Uri) {
        context.contentResolver.openOutputStream(destination, "w")?.use { out ->
            source.inputStream().use { input ->
                input.copyTo(out)
            }
        }
    }
}
