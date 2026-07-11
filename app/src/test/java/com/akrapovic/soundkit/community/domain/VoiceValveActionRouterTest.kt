package com.akrapovic.soundkit.community.domain

import com.akrapovic.soundkit.community.test.FakeBleRepository
import com.akrapovic.soundkit.community.test.FakeSettingsStore
import com.akrapovic.soundkit.community.test.testDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceValveActionRouterTest {
    @get:Rule
    val mainDispatcherRule = com.akrapovic.soundkit.community.test.MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun openRejectsBeforeConnectingWhenOnboardingOrDefaultReceiverIsMissing() = runTest {
        val ble = FakeBleRepository()
        val settings = FakeSettingsStore()
        val router = VoiceValveActionRouter(ble, settings, ValveCommandCoordinator(ble))

        val onboardingResult = router.execute(VoiceValveAction.Open)
        assertEquals(
            "Finish Sound Kit setup on your phone before using this shortcut.",
            (onboardingResult as VoiceValveActionResult.Failure).message,
        )
        assertTrue(ble.connectedDevices.isEmpty())

        settings.completeOnboarding()
        val noDefaultResult = router.execute(VoiceValveAction.Open)
        assertEquals(
            "Choose a default receiver in Sound Kit before using this shortcut.",
            (noDefaultResult as VoiceValveActionResult.Failure).message,
        )
        assertTrue(ble.connectedDevices.isEmpty())
    }

    @Test
    fun openNoOpUsesCoordinatorAndReturnsConfirmedCopyWithoutWrite() = runTest {
        val ble = FakeBleRepository()
        val device = testDevice()
        ble.connectionState.value = ConnectionState.Connected(device)
        ble.valveState.value = ValveState.Open
        val settings = FakeSettingsStore(
            SoundKitSettings(
                savedReceivers = listOf(SavedReceiver(device.address, device.name, isDefault = true)),
                onboardingCompletedAt = 1L,
            ),
        )
        val router = VoiceValveActionRouter(ble, settings, ValveCommandCoordinator(ble))

        val result = router.execute(VoiceValveAction.Open)

        assertEquals("Valves are open.", (result as VoiceValveActionResult.Success).message)
        assertEquals(0, ble.openValveCount)
    }

    @Test
    fun shortcutReconnectsOnlySavedDefaultAndTimesCommandAfterConnection() = runTest {
        val ble = FakeBleRepository()
        val default = testDevice(address = "00:11:22:33:44:55")
        val settings = FakeSettingsStore(
            SoundKitSettings(
                savedReceivers = listOf(SavedReceiver(default.address, default.name, isDefault = true)),
                onboardingCompletedAt = 1L,
            ),
        )
        ble.valveState.value = ValveState.Closed
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))
        val router = VoiceValveActionRouter(ble, settings, ValveCommandCoordinator(ble))

        val result = async { router.execute(VoiceValveAction.Open) }
        runCurrent()
        assertEquals(default.address, ble.connectedDevices.single().address)
        ble.connectionState.value = ConnectionState.Connected(ble.connectedDevices.single())
        runCurrent()

        assertEquals("Valves are open.", (result.await() as VoiceValveActionResult.Success).message)
        assertEquals(1, ble.openValveCount)
    }

    @Test
    fun statusReturnsFailureCopyWhenReceiverHasNotReportedState() = runTest {
        val ble = FakeBleRepository()
        val device = testDevice()
        ble.connectionState.value = ConnectionState.Connected(device)
        val settings = FakeSettingsStore(
            SoundKitSettings(
                savedReceivers = listOf(SavedReceiver(device.address, device.name, isDefault = true)),
                onboardingCompletedAt = 1L,
            ),
        )
        val router = VoiceValveActionRouter(ble, settings, ValveCommandCoordinator(ble))

        val result = router.execute(VoiceValveAction.Status)

        assertEquals(
            "Valve status is unavailable because the receiver has not reported its status.",
            (result as VoiceValveActionResult.Failure).message,
        )
    }
}
