package com.akrapovic.soundkit.community.car

import com.akrapovic.soundkit.community.domain.CommandPhase
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.ValveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarScreenPresenterTest {
    private val device = SoundKitDevice("Sound Kit", "00:11:22:33:44:55")

    @Test
    fun requiresPhoneSetupForIncompleteOnboardingMissingPermissionsOrNoReceiver() {
        assertSetup(
            CarScreenPresenter.present(false, true, true, ConnectionState.Disconnected, ValveState.Unknown, null, CommandPhase.Idle),
            "Finish setup on phone",
        )
        assertSetup(
            CarScreenPresenter.present(true, false, true, ConnectionState.Disconnected, ValveState.Unknown, null, CommandPhase.Idle),
            "Allow Bluetooth on phone",
        )
        assertSetup(
            CarScreenPresenter.present(true, true, false, ConnectionState.Disconnected, ValveState.Unknown, null, CommandPhase.Idle),
            "Choose a default receiver on phone",
        )
    }

    @Test
    fun presentsSeparateOpenAndCloseAvailabilityForKnownValveState() {
        val closed = present(ValveState.Closed)
        assertTrue(closed.showControls)
        assertTrue(closed.openEnabled)
        assertFalse(closed.closeEnabled)
        assertEquals("Closed", closed.status)

        val open = present(ValveState.Open)
        assertTrue(open.showControls)
        assertFalse(open.openEnabled)
        assertTrue(open.closeEnabled)
        assertEquals("Open", open.status)
    }

    @Test
    fun hidesControlsWhileUnknownOrReceiverNotReadyAndShowsLoadingDuringCommands() {
        val unknown = present(ValveState.Unknown)
        assertFalse(unknown.showControls)
        assertEquals("Checking status", unknown.status)

        val notReady = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), ValveState.Open,
            "Receiver not ready", CommandPhase.Idle,
        ) as CarScreenModel.Controls
        assertFalse(notReady.showControls)
        assertEquals("Receiver not ready", notReady.status)

        val writing = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), ValveState.Closed,
            null, CommandPhase.Writing(ValveState.Open),
        ) as CarScreenModel.Controls
        assertTrue(writing.loading)
        assertFalse(writing.openEnabled)
        assertFalse(writing.closeEnabled)
    }

    @Test
    fun reportsConnectingAndHidesControlsUntilReceiverIsReady() {
        val model = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connecting(device), ValveState.Closed,
            null, CommandPhase.Idle,
        ) as CarScreenModel.Controls

        assertEquals("Connecting", model.status)
        assertTrue(model.loading)
        assertFalse(model.showControls)
    }

    private fun present(valveState: ValveState): CarScreenModel.Controls {
        return CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), valveState, null, CommandPhase.Idle,
        ) as CarScreenModel.Controls
    }

    private fun assertSetup(model: CarScreenModel, message: String) {
        assertEquals(message, (model as CarScreenModel.SetupRequired).message)
    }
}
