package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.DiagnosticsEntry
import com.akrapovic.soundkit.community.domain.DiagnosticsLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

@Singleton
class DiagnosticsRepository @Inject constructor() {
    private val nextEntryId = AtomicLong(0L)
    private val _entries = MutableStateFlow<List<DiagnosticsEntry>>(emptyList())
    val entries: StateFlow<List<DiagnosticsEntry>> = _entries

    @Volatile
    var debugLoggingEnabled: Boolean = true

    fun debug(message: String) {
        if (!debugLoggingEnabled) {
            Timber.d(message)
            return
        }
        add(DiagnosticsLevel.Debug, message)
    }

    fun info(message: String) = add(DiagnosticsLevel.Info, message)
    fun warning(message: String) = add(DiagnosticsLevel.Warning, message)
    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.e(throwable, message)
        }
        add(DiagnosticsLevel.Error, message)
    }

    fun exportText(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return entries.value.joinToString(separator = "\n") { entry ->
            "${formatter.format(Date(entry.timestampMillis))} ${entry.level.name.uppercase(Locale.US)} ${entry.message}"
        }
    }

    private fun add(level: DiagnosticsLevel, message: String) {
        when (level) {
            DiagnosticsLevel.Debug -> Timber.d(message)
            DiagnosticsLevel.Info -> Timber.i(message)
            DiagnosticsLevel.Warning -> Timber.w(message)
            DiagnosticsLevel.Error -> Timber.e(message)
        }
        val entry = DiagnosticsEntry(
            id = nextEntryId.getAndIncrement(),
            timestampMillis = System.currentTimeMillis(),
            level = level,
            message = message,
        )
        _entries.update { existing ->
            (existing + entry).takeLast(MAX_ENTRIES)
        }
    }

    private companion object {
        const val MAX_ENTRIES = 300
    }
}

