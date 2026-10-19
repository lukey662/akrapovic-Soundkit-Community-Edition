package com.akrapovic.soundkit.community.car

import com.akrapovic.soundkit.community.domain.AwayReason
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
        assertTrue(closed.openEnabled)
        assertFalse(closed.closeEnabled)
        assertFalse(closed.retryEnabled)
        assertEquals("Closed", closed.status)

        val countdown = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), ValveState.Closed,
            null, CommandPhase.Idle,
            quietHoldEndsAtMillis = 125_000L,
            nowMillis = 0L,
        ) as CarScreenModel.Controls
        assertEquals("Opens in 2:05", countdown.status)
        assertTrue(countdown.openEnabled)

        val open = present(ValveState.Open)
        assertFalse(open.openEnabled)
        assertTrue(open.closeEnabled)
        assertEquals("Open", open.status)
    }

    @Test
    fun disablesBothActionsWhileUnknownNotReadyOrChanging() {
        val unknown = present(ValveState.Unknown)
        assertFalse(unknown.openEnabled)
        assertFalse(unknown.closeEnabled)
        assertEquals("Checking", unknown.status)

        val notReady = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), ValveState.Open,
            "Receiver isn't ready", CommandPhase.Idle,
        ) as CarScreenModel.Controls
        assertFalse(notReady.openEnabled)
        assertFalse(notReady.closeEnabled)
        assertEquals("Not ready", notReady.status)

        val writing = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), ValveState.Closed,
            null, CommandPhase.Writing(ValveState.Open),
        ) as CarScreenModel.Controls
        assertEquals(CarTemplateKind.Controls, carTemplateKind(writing))
        assertFalse(writing.openEnabled)
        assertFalse(writing.closeEnabled)
        assertEquals("Opening…", writing.status)

        val confirming = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), ValveState.Open,
            null, CommandPhase.AwaitingConfirmation(ValveState.Closed),
        ) as CarScreenModel.Controls
        assertFalse(confirming.closeEnabled)
        assertEquals("Closing…", confirming.status)
    }

    @Test
    fun everyStateAfterSetupIsTheSameGridSoUpdatesStayRefreshes() {
        val states = listOf(
            ConnectionState.Scanning to ValveState.Unknown,
            ConnectionState.Connecting(device) to ValveState.Closed,
            ConnectionState.Reconnecting(device, attempt = 2, nextDelayMs = 4_000L) to ValveState.Unknown,
            ConnectionState.Connected(device) to ValveState.Unknown,
            ConnectionState.Connected(device) to ValveState.Closed,
            ConnectionState.Connected(device) to ValveState.Open,
            ConnectionState.Disconnected to ValveState.Unknown,
            ConnectionState.Error("Timed out", recoverable = true) to ValveState.Unknown,
            ConnectionState.Away(1_700_000_000_000L, AwayReason.OutOfRange) to ValveState.Unknown,
        )
        states.forEach { (connection, valve) ->
            val model = CarScreenPresenter.present(
                true, true, true, connection, valve, null, CommandPhase.Idle,
                quietHoldEndsAtMillis = 60_000L,
                nowMillis = 0L,
            )
            assertEquals("$connection", CarTemplateKind.Controls, carTemplateKind(model))
        }
        assertEquals("Car Sound Kit Control", CarScreenPresenter.TITLE)
    }

    @Test
    fun reportsConnectingWithoutControls() {
        val model = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connecting(device), ValveState.Closed,
            null, CommandPhase.Idle,
        ) as CarScreenModel.Controls

        assertEquals("Connecting…", model.status)
        assertFalse(model.openEnabled)
        assertFalse(model.closeEnabled)
        assertFalse(model.retryEnabled)
    }

    @Test
    fun offersRetryWhenDisconnectedFailedOrAway() {
        val disconnected = CarScreenPresenter.present(
            true, true, true, ConnectionState.Disconnected, ValveState.Open,
            null, CommandPhase.Idle,
        ) as CarScreenModel.Controls
        val error = CarScreenPresenter.present(
            true, true, true, ConnectionState.Error("Timed out", recoverable = true), ValveState.Closed,
            null, CommandPhase.Idle,
        ) as CarScreenModel.Controls
        val away = CarScreenPresenter.present(
            true, true, true, ConnectionState.Away(1_700_000_000_000L, AwayReason.LeftCar), ValveState.Unknown,
            null, CommandPhase.Idle,
        ) as CarScreenModel.Controls

        assertEquals("Disconnected", disconnected.status)
        assertEquals("Tap to retry", disconnected.statusText)
        assertFalse(disconnected.openEnabled)
        assertFalse(disconnected.closeEnabled)
        assertTrue(disconnected.retryEnabled)
        assertEquals("Failed · retry", error.status)
        assertTrue(error.retryEnabled)
        assertFalse(error.openEnabled)
        assertEquals("Left the car", away.status)
        assertTrue(away.retryEnabled)
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
