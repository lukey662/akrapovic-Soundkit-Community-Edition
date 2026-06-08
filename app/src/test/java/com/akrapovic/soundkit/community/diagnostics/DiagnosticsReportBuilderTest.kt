package com.akrapovic.soundkit.community.diagnostics

import com.akrapovic.soundkit.community.domain.DiagnosticsEntry
import com.akrapovic.soundkit.community.domain.DiagnosticsLevel
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsReportBuilderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun reportContainsMetadataAndPreservesMultilineGattProfile() {
        val builder = builder(crash = null)
        val report = builder.buildDiagnosticsReport(
            entries = listOf(
                DiagnosticsEntry(
                    id = 1L,
                    timestampMillis = 1_000L,
                    level = DiagnosticsLevel.Info,
                    message = "GATT PROFILE START\nservice[0]=00001800-0000-1000-8000-00805f9b34fb\nGATT PROFILE END",
                ),
            ),
        )

        assertTrue(report.contains("applicationId=com.akrapovic.soundkit.community.debug"))
        assertTrue(report.contains("CAR APP READINESS"))
        assertTrue(report.contains("versionName=0.1.0-debug"))
        assertTrue(report.contains("GATT PROFILE START"))
        assertTrue(report.contains("service[0]=00001800-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun reportIncludesCrashSectionWhenCrashExists() {
        val builder = builder(crash = "IllegalArgumentException: duplicate key")

        val report = builder.buildDiagnosticsReport(entries = emptyList())

        assertTrue(report.contains("CRASH LOG START"))
        assertTrue(report.contains("IllegalArgumentException: duplicate key"))
        assertTrue(report.contains("CRASH LOG END"))
    }

    @Test
    fun writesReportFileForSharing() {
        val outputDirectory = temporaryFolder.newFolder("diagnostics")
        val builder = builder(crash = null, outputDirectory = outputDirectory)

        val file = builder.writeDiagnosticsReportFile(entries = emptyList())

        assertTrue(file.exists())
        assertTrue(file.name.startsWith("soundkit-diagnostics-"))
        assertTrue(file.readText().contains("SOUND KIT COMMUNITY DIAGNOSTICS"))
    }

    private fun builder(
        crash: String?,
        outputDirectory: File = temporaryFolder.root,
    ): DiagnosticsReportBuilder {
        return DiagnosticsReportBuilder(
            metadataProvider = {
                DiagnosticsReportMetadata(
                    exportedAt = "2026-05-16 16:10:00.000 +1000",
                    applicationId = "com.akrapovic.soundkit.community.debug",
                    versionName = "0.1.0-debug",
                    versionCode = 1,
                    buildType = "debug",
                    debug = true,
                    manufacturer = "Google",
                    model = "Pixel",
                    androidRelease = "16",
                    androidApi = 35,
                )
            },
            crashReader = { crash },
            outputDirectoryProvider = { outputDirectory },
            carAppReadinessProvider = { hasDefault ->
                buildString {
                    appendLine("CAR APP READINESS")
                    appendLine("carAppServiceRegistered=true")
                    appendLine("protocolVerified=true")
                    appendLine("defaultReceiverSaved=$hasDefault")
                }
            },
        )
    }
}
