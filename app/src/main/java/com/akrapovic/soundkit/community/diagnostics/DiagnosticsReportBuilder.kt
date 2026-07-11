package com.akrapovic.soundkit.community.diagnostics

import android.content.Context
import android.os.Build
import com.akrapovic.soundkit.community.BuildConfig
import com.akrapovic.soundkit.community.car.CarSessionTracker
import com.akrapovic.soundkit.community.domain.DiagnosticsEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsReportBuilder(
    private val metadataProvider: () -> DiagnosticsReportMetadata,
    private val crashReader: () -> String?,
    private val outputDirectoryProvider: () -> File,
    private val carAppReadinessProvider: (Boolean, Boolean) -> String,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        crashReporter: CrashReporter,
        carSessionTracker: CarSessionTracker,
    ) : this(
        metadataProvider = { DiagnosticsReportMetadata.from(context) },
        crashReader = { crashReporter.readPendingCrash() },
        outputDirectoryProvider = { File(context.cacheDir, "diagnostics") },
        carAppReadinessProvider = { hasDefaultReceiver, connectInCar ->
            CarAppDiagnostics.format(
                context = context,
                hasDefaultReceiver = hasDefaultReceiver,
                connectInCar = connectInCar,
                carSessionActive = carSessionTracker.isCarSessionActive.value,
            )
        },
    )

    fun buildDiagnosticsReport(
        entries: List<DiagnosticsEntry>,
        hasDefaultReceiver: Boolean = false,
        connectInCar: Boolean = true,
        includeCrash: Boolean = true,
        vehicleDisplayName: String? = null,
        vehicleTier: String? = null,
        connectionState: String? = null,
    ): String {
        val metadata = metadataProvider()
        val crash = if (includeCrash) crashReader()?.takeIf { it.isNotBlank() } else null
        return buildString {
            appendHeader(metadata)
            appendLine()
            appendVehicleContext(vehicleDisplayName, vehicleTier, connectionState)
            appendLine()
            appendLine(carAppReadinessProvider(hasDefaultReceiver, connectInCar))
            appendLine()
            appendLine("PRIVACY NOTE")
            appendLine("Review before sending. This report may include BLE device names and MAC addresses.")
            appendLine()
            appendLine("BLE DIAGNOSTICS START")
            if (entries.isEmpty()) {
                appendLine("No diagnostics entries captured.")
            } else {
                append(entries.toExportText())
                appendLine()
            }
            appendLine("BLE DIAGNOSTICS END")
            if (crash != null) {
                appendLine()
                appendLine("CRASH LOG START")
                appendLine(crash.trimEnd())
                appendLine("CRASH LOG END")
            }
        }
    }

    fun buildCrashReport(): String {
        val metadata = metadataProvider()
        val crash = crashReader()?.takeIf { it.isNotBlank() }
        return buildString {
            appendHeader(metadata)
            appendLine()
            appendLine("CRASH LOG START")
            if (crash == null) {
                appendLine("No crash log captured.")
            } else {
                appendLine(crash.trimEnd())
            }
            appendLine("CRASH LOG END")
        }
    }

    fun writeDiagnosticsReportFile(
        entries: List<DiagnosticsEntry>,
        hasDefaultReceiver: Boolean = false,
        connectInCar: Boolean = true,
    ): File {
        return writeReportFile(
            "soundkit-diagnostics",
            buildDiagnosticsReport(entries, hasDefaultReceiver, connectInCar),
        )
    }

    fun writeCrashReportFile(): File {
        return writeReportFile("soundkit-crash", buildCrashReport())
    }

    private fun writeReportFile(prefix: String, text: String): File {
        val directory = outputDirectoryProvider()
        directory.mkdirs()
        val timestamp = FILE_TIMESTAMP_FORMAT.format(Date())
        return File(directory, "$prefix-$timestamp.txt").apply {
            writeText(text)
        }
    }

    private fun StringBuilder.appendHeader(metadata: DiagnosticsReportMetadata) {
        appendLine("SOUND KIT COMMUNITY DIAGNOSTICS")
        appendLine("exportedAt=${metadata.exportedAt}")
        appendLine("applicationId=${metadata.applicationId}")
        appendLine("versionName=${metadata.versionName}")
        appendLine("versionCode=${metadata.versionCode}")
        appendLine("buildType=${metadata.buildType}")
        appendLine("debug=${metadata.debug}")
        appendLine("device=${metadata.manufacturer} ${metadata.model}")
        appendLine("android=${metadata.androidRelease} api=${metadata.androidApi}")
    }

    private fun StringBuilder.appendVehicleContext(
        vehicleDisplayName: String?,
        vehicleTier: String?,
        connectionState: String?,
    ) {
        appendLine("VEHICLE CONTEXT")
        appendLine("vehicle=${vehicleDisplayName ?: "not set"}")
        appendLine("vehicleTier=${vehicleTier ?: "not set"}")
        appendLine("connectionState=${connectionState ?: "unknown"}")
        appendLine("supportEmail=${DiagnosticsSupport.EMAIL}")
    }

    private fun List<DiagnosticsEntry>.toExportText(): String {
        return joinToString(separator = "\n") { entry ->
            "${ENTRY_TIMESTAMP_FORMAT.format(Date(entry.timestampMillis))} " +
                "${entry.level.name.uppercase(Locale.US)} ${entry.message}"
        }
    }

    private companion object {
        val ENTRY_TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val FILE_TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}

data class DiagnosticsReportMetadata(
    val exportedAt: String,
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val debug: Boolean,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val androidApi: Int,
) {
    companion object {
        fun from(context: Context): DiagnosticsReportMetadata {
            return DiagnosticsReportMetadata(
                exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date()),
                applicationId = context.packageName,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                buildType = BuildConfig.BUILD_TYPE,
                debug = BuildConfig.DEBUG,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                androidApi = Build.VERSION.SDK_INT,
            )
        }
    }
}
