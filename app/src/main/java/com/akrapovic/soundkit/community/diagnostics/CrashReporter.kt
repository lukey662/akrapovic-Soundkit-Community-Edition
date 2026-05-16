package com.akrapovic.soundkit.community.diagnostics

import android.content.Context
import com.akrapovic.soundkit.community.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReporter(
    private val crashFile: File,
    private val metadataProvider: () -> String,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        crashFile = File(File(context.filesDir, "crashes"), "last_crash.txt"),
        metadataProvider = { buildMetadata(context) },
    )

    private val installed = AtomicBoolean(false)

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            recordCrash(thread.name, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun hasPendingCrash(): Boolean = crashFile.exists() && crashFile.length() > 0L

    fun readPendingCrash(): String? {
        return runCatching {
            crashFile.takeIf { it.exists() && it.length() > 0L }?.readText()
        }.getOrNull()
    }

    fun clearPendingCrash(): Boolean {
        return runCatching {
            !crashFile.exists() || crashFile.delete()
        }.getOrDefault(false)
    }

    fun recordCrash(
        threadName: String,
        throwable: Throwable,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ) {
        runCatching {
            crashFile.parentFile?.mkdirs()
            crashFile.writeText(
                buildString {
                    appendLine("SOUND KIT COMMUNITY CRASH")
                    appendLine("occurredAt=${TIMESTAMP_FORMAT.format(Date(occurredAtMillis))}")
                    appendLine("thread=$threadName")
                    append(metadataProvider().trimEnd())
                    appendLine()
                    appendLine("exception=${throwable::class.java.name}: ${throwable.message.orEmpty()}")
                    appendLine(throwable.stackTraceToText().trimEnd())
                },
            )
        }
    }

    private fun Throwable.stackTraceToText(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private companion object {
        val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)

        fun buildMetadata(context: Context): String {
            return buildString {
                appendLine("applicationId=${context.packageName}")
                appendLine("versionName=${BuildConfig.VERSION_NAME}")
                appendLine("versionCode=${BuildConfig.VERSION_CODE}")
                appendLine("buildType=${BuildConfig.BUILD_TYPE}")
                appendLine("debug=${BuildConfig.DEBUG}")
            }
        }
    }
}
