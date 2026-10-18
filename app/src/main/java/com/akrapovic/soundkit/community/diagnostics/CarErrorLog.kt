package com.akrapovic.soundkit.community.diagnostics

import android.content.Context
import com.akrapovic.soundkit.community.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records failures inside the Android Auto screen. The host shows its own "unexpected error" page
 * when a template throws or fails validation, and the process survives, so [CrashReporter] never
 * sees it. This log keeps the last error plus a short ring of session events (host package, API
 * level, validator choice) so the phone's diagnostics export can explain what the car saw.
 */
@Singleton
class CarErrorLog(
    private val errorFile: File,
    private val metadataProvider: () -> String,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        errorFile = File(File(context.filesDir, "crashes"), "last_car_error.txt"),
        metadataProvider = { buildMetadata(context) },
    )

    private val lock = Any()
    private val events = ArrayDeque<CarEvent>(MAX_EVENTS)

    /** Appends a session event to the in-memory ring, trimming the oldest beyond [MAX_EVENTS]. */
    fun event(text: String) {
        synchronized(lock) {
            if (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(CarEvent(clock(), text))
        }
    }

    /** Most recent event text first, newest last. */
    fun recentEvents(): List<String> = synchronized(lock) { events.map { it.text } }

    /** The newest event whose text contains [prefix], or null. */
    fun latestEventContaining(prefix: String): String? {
        return synchronized(lock) { events.lastOrNull { it.text.contains(prefix) }?.text }
    }

    fun record(where: String, throwable: Throwable) {
        val occurredAt = clock()
        val snapshot = synchronized(lock) { events.toList() }
        runCatching {
            errorFile.parentFile?.mkdirs()
            errorFile.writeText(
                buildString {
                    appendLine("SOUND KIT COMMUNITY CAR ERROR")
                    appendLine("occurredAt=${TIMESTAMP_FORMAT.format(Date(occurredAt))}")
                    appendLine("where=$where")
                    append(metadataProvider().trimEnd())
                    appendLine()
                    appendLine("exception=${throwable::class.java.name}: ${throwable.message.orEmpty()}")
                    appendLine(throwable.stackTraceToText().trimEnd())
                    appendLine("SESSION EVENTS")
                    if (snapshot.isEmpty()) {
                        appendLine("none")
                    } else {
                        snapshot.forEach { event ->
                            appendLine("${TIMESTAMP_FORMAT.format(Date(event.atMillis))} ${event.text}")
                        }
                    }
                },
            )
        }
    }

    fun hasError(): Boolean = errorFile.exists() && errorFile.length() > 0L

    fun read(): String? {
        return runCatching {
            errorFile.takeIf { it.exists() && it.length() > 0L }?.readText()
        }.getOrNull()
    }

    fun clear(): Boolean {
        return runCatching {
            !errorFile.exists() || errorFile.delete()
        }.getOrDefault(false)
    }

    private fun Throwable.stackTraceToText(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private data class CarEvent(val atMillis: Long, val text: String)

    companion object {
        const val MAX_EVENTS = 20
        private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)

        private fun buildMetadata(context: Context): String {
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
