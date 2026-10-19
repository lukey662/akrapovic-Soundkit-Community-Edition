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
    fun closedValvesMakeOpenThePrimaryButton() {
        val closed = connected(ValveState.Closed)
        assertEquals("Connected · Valves closed", closed.rowText)
        assertEquals(CarButtons.OpenClose(ValveTarget.Open, openEnabled = true, closeEnabled = false), closed.buttons)
        assertFalse(closed.valveOpen)
        assertFalse(closed.dimmed)
        assertFalse(closed.busy)
    }

    @Test
    fun openValvesMakeCloseThePrimaryButton() {
        val open = connected(ValveState.Open)
        assertEquals("Connected · Valves open", open.rowText)
        assertEquals(CarButtons.OpenClose(ValveTarget.Close, openEnabled = false, closeEnabled = true), open.buttons)
        assertTrue(open.valveOpen)
    }

    @Test
    fun quietHoldCountdownShowsInTheStatusLine() {
        val countdown = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), ValveState.Closed,
            null, CommandPhase.Idle,
            quietHoldEndsAtMillis = 125_000L,
            nowMillis = 0L,
        ) as CarScreenModel.Controls
        assertEquals("Connected · Opens in 2:05", countdown.rowText)
        assertEquals(ValveTarget.Open, (countdown.buttons as CarButtons.OpenClose).primary)
    }

    @Test
    fun noButtonsWhileConnectingCheckingOrNotReady() {
        listOf<ConnectionState>(
            ConnectionState.Scanning,
            ConnectionState.Connecting(device),
            ConnectionState.Reconnecting(device, attempt = 2, nextDelayMs = 4_000L),
        ).forEach { connection ->
            val model = present(connection, ValveState.Closed)
            assertEquals("$connection", "Connecting…", model.rowText)
            assertEquals("$connection", CarButtons.None, model.buttons)
            assertTrue("$connection", model.dimmed)
        }

        val unknown = connected(ValveState.Unknown)
        assertEquals("Connected · Checking valves", unknown.rowText)
        assertEquals(CarButtons.None, unknown.buttons)

        val notReady = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), ValveState.Open,
            "Receiver isn't ready", CommandPhase.Idle,
        ) as CarScreenModel.Controls
        assertEquals("Connected · Receiver not ready", notReady.rowText)
        assertEquals(CarButtons.None, notReady.buttons)
    }

    @Test
    fun bothButtonsDisableWhileACommandIsInFlight() {
        val writing = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), ValveState.Closed,
            null, CommandPhase.Writing(ValveState.Open),
        ) as CarScreenModel.Controls
        assertEquals("Connected · Opening…", writing.rowText)
        assertEquals(CarButtons.OpenClose(ValveTarget.Open, openEnabled = false, closeEnabled = false), writing.buttons)
        assertTrue(writing.busy)

        val confirming = CarScreenPresenter.present(
            true, true, true, ConnectionState.Connected(device), ValveState.Open,
            null, CommandPhase.AwaitingConfirmation(ValveState.Closed),
        ) as CarScreenModel.Controls
        assertEquals("Connected · Closing…", confirming.rowText)
        assertEquals(CarButtons.OpenClose(ValveTarget.Close, openEnabled = false, closeEnabled = false), confirming.buttons)
    }

    @Test
    fun offersConnectWhenDisconnectedFailedOrAway() {
        val cases = listOf(
            ConnectionState.Disconnected to "Not connected",
            ConnectionState.Error("Timed out", recoverable = true) to "Couldn't connect",
            ConnectionState.Away(1_700_000_000_000L, AwayReason.LeftCar) to "Left the car",
            ConnectionState.Away(1_700_000_000_000L, AwayReason.OutOfRange) to "Receiver out of range",
        )
        cases.forEach { (connection, text) ->
            val model = present(connection, ValveState.Open)
            assertEquals(text, model.rowText)
            assertEquals(CarButtons.Connect, model.buttons)
            assertTrue(model.dimmed)
            assertFalse("a stale open state must not glow while disconnected", model.valveOpen)
        }
    }

    @Test
    fun everyStateAfterSetupIsTheSamePaneSoUpdatesStayRefreshes() {
        val states = listOf(
            ConnectionState.Scanning to ValveState.Unknown,
            ConnectionState.Connecting(device) to ValveState.Closed,
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
        assertEquals("Car Sound Kit", CarScreenPresenter.TITLE)
    }

    private fun connected(valveState: ValveState): CarScreenModel.Controls =
        present(ConnectionState.Connected(device), valveState)

    private fun present(connection: ConnectionState, valveState: ValveState): CarScreenModel.Controls {
        return CarScreenPresenter.present(
            true, true, true, connection, valveState, null, CommandPhase.Idle,
        ) as CarScreenModel.Controls
    }

    private fun assertSetup(model: CarScreenModel, message: String) {
        assertEquals(message, (model as CarScreenModel.SetupRequired).message)
    }
}
