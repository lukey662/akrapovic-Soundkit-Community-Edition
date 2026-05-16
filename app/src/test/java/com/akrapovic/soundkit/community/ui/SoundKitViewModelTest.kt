package com.akrapovic.soundkit.community.ui

import app.cash.turbine.test
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.diagnostics.CrashReporter
import com.akrapovic.soundkit.community.diagnostics.DiagnosticsReportBuilder
import com.akrapovic.soundkit.community.diagnostics.DiagnosticsReportMetadata
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.test.FakeBleRepository
import com.akrapovic.soundkit.community.test.FakeSettingsStore
import com.akrapovic.soundkit.community.test.MainDispatcherRule
import com.akrapovic.soundkit.community.test.testDevice
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundKitViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val bleRepository = FakeBleRepository()
    private val settingsStore = FakeSettingsStore()
    private val diagnosticsRepository = DiagnosticsRepository()

    private fun viewModel(): SoundKitViewModel {
        val outputDirectory = File(System.getProperty("java.io.tmpdir"), "soundkit-${System.nanoTime()}")
        val crashReporter = CrashReporter(
            crashFile = File(outputDirectory, "last_crash.txt"),
            metadataProvider = { "applicationId=test" },
        )
        return SoundKitViewModel(
            bleRepository = bleRepository,
            settingsRepository = settingsStore,
            diagnosticsRepository = diagnosticsRepository,
            diagnosticsReportBuilder = DiagnosticsReportBuilder(
                metadataProvider = {
                    DiagnosticsReportMetadata(
                        exportedAt = "2026-05-16 16:10:00.000 +1000",
                        applicationId = "test",
                        versionName = "test",
                        versionCode = 1,
                        buildType = "debug",
                        debug = true,
                        manufacturer = "Google",
                        model = "Pixel",
                        androidRelease = "16",
                        androidApi = 35,
                    )
                },
                crashReader = { crashReporter.readPendingCrash() },
                outputDirectoryProvider = { outputDirectory },
            ),
            crashReporter = crashReporter,
        )
    }

    @Test
    fun startScanDelegatesToRepositoryAndUpdatesState() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.startScan()
            val scanning = awaitItem()

            assertEquals(1, bleRepository.startScanCount)
            assertTrue(scanning.isScanning)
        }
    }

    @Test
    fun connectDelegatesSelectedDevice() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        val device = testDevice()

        viewModel.connect(device)
        runCurrent()

        assertEquals(listOf(device), bleRepository.connectedDevices)
    }

    @Test
    fun commandInFlightTogglesAndFailureReachesLastError() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        bleRepository.openResult = CompletableDeferred()

        viewModel.uiState.test {
            awaitItem()
            viewModel.openValve()
            val inFlight = awaitItem()
            assertTrue(inFlight.commandInFlight)

            bleRepository.openResult.complete(CommandResult.Failure("protocol not verified", recoverable = false))
            val finalState = awaitUntil { it.lastError == "protocol not verified" && !it.commandInFlight }

            assertFalse(finalState.commandInFlight)
            assertEquals("protocol not verified", finalState.lastError)
        }
    }

    @Test
    fun settingsTogglesDelegateToStore() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()

        viewModel.setAutoReconnect(false)
        viewModel.setDebugLogging(false)
        viewModel.setGarageTheme("audi-rs-light")
        runCurrent()

        assertEquals(listOf(false), settingsStore.autoReconnectChanges)
        assertEquals(listOf(false), settingsStore.debugLoggingChanges)
        assertEquals(listOf("audi-rs-light"), settingsStore.garageThemeChanges)
    }

    @Test
    fun acceptRiskNoticePersistsThroughSettingsStore() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()

        assertFalse(settingsStore.settings.value.riskNoticeAccepted)

        viewModel.acceptRiskNotice()
        runCurrent()

        assertEquals(1, settingsStore.riskNoticeAcceptCount)
        assertTrue(settingsStore.settings.value.riskNoticeAccepted)
    }

    @Test
    fun forgetDeviceClearsStoreAndDisconnects() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()

        viewModel.forgetDevice()
        runCurrent()

        assertEquals(1, settingsStore.forgetCount)
        assertEquals(1, bleRepository.disconnectCount)
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<SoundKitUiState>.awaitUntil(
        predicate: (SoundKitUiState) -> Boolean,
    ): SoundKitUiState {
        repeat(10) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("Expected UI state was not emitted")
    }
}

