package com.akrapovic.soundkit.community.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

object DiagnosticsSupport {
    const val EMAIL = "support@appsforgood.net"

    fun emailSupportIntent(
        subject: String,
        body: String,
    ): Intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$EMAIL".toUri()
        putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    fun buildTriageBody(
        appVersion: String,
        vehicleLine: String?,
        connectionLine: String,
    ): String = buildString {
        appendLine("Sound Kit Community support request")
        appendLine("appVersion=$appVersion")
        vehicleLine?.let { appendLine(it) }
        appendLine(connectionLine)
        appendLine()
        appendLine("Please attach your exported diagnostics .txt file (Share or Save from Diagnostics).")
    }

    fun copyEmailAddress(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Support email", EMAIL))
    }
}
