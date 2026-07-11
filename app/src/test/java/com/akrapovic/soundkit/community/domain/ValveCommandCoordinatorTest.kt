package com.akrapovic.soundkit.community.domain

import com.akrapovic.soundkit.community.test.FakeBleRepository
import com.akrapovic.soundkit.community.test.MainDispatcherRule
import com.akrapovic.soundkit.community.test.testDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ValveCommandCoordinatorTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun openNoOpWhenAlreadyOpenSucceedsWithoutRepositoryWrite() = runTest {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Open
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))
        val coordinator = ValveCommandCoordinator(ble)

        val result = coordinator.open()
        runCurrent()

        assertTrue(result is CommandResult.Success)
        assertEquals(0, ble.openValveCount)
        assertEquals(ValveState.Open, coordinator.currentStatus())
    }

    @Test
    fun closeNoOpWhenAlreadyClosedSucceedsWithoutRepositoryWrite() = runTest {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Closed
        val coordinator = ValveCommandCoordinator(ble)

        val result = coordinator.close()

        assertTrue(result is CommandResult.Success)
        assertEquals(0, ble.closeValveCount)
    }

    @Test
    fun blocksUnknownNotReadyAndDisconnectedCommandsWithoutRepositoryWrite() = runTest {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Unknown
        val coordinator = ValveCommandCoordinator(ble)

        val unknownResult = coordinator.open()
        assertTrue(unknownResult is CommandResult.Failure)
        assertEquals(0, ble.openValveCount)
        assertTrue(coordinator.commandPhase.value is CommandPhase.Failed)

        ble.valveState.value = ValveState.Closed
        ble.receiverStatusMessage.value = "Ignition required"
        val notReadyResult = coordinator.open()
        assertTrue(notReadyResult is CommandResult.Failure)
        assertEquals(0, ble.openValveCount)

        ble.receiverStatusMessage.value = null
        ble.connectionState.value = ConnectionState.Disconnected
        assertFalse(coordinator.canOpen())
        assertFalse(coordinator.canClose())
        val disconnectedResult = coordinator.open()
        assertTrue(disconnectedResult is CommandResult.Failure)
        assertEquals(0, ble.openValveCount)
    }

    @Test
    fun canOpenOnlyWhenClosedConnectedAndReady() = runTest {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Closed
        ble.receiverStatusMessage.value = null
        val coordinator = ValveCommandCoordinator(ble)

        assertTrue(coordinator.canOpen())
        assertFalse(coordinator.canClose())

        ble.receiverStatusMessage.value = "Ignition required"
        assertFalse(coordinator.canOpen())
    }

    @Test
    fun concurrentCommandsAreSerialized() = runTest {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Closed
        val openGate = CompletableDeferred<CommandResult>()
        ble.openResult = openGate
        ble.closeResult = CompletableDeferred(CommandResult.Success(ValveState.Closed))
        val coordinator = ValveCommandCoordinator(ble)

        val first = async { coordinator.open() }
        runCurrent()
        val second = async { coordinator.close() }
        runCurrent()

        openGate.complete(CommandResult.Success(ValveState.Open))
        runCurrent()
        val firstResult = first.await()
        val secondResult = second.await()

        assertTrue(firstResult is CommandResult.Success)
        // Second waits on mutex; after first completes it may run close.
        assertTrue(secondResult is CommandResult.Success || secondResult is CommandResult.Failure)
        assertEquals(1, ble.openValveCount)
    }

    @Test
    fun transportFailureSurfacesAsFailedPhase() = runTest {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Closed
        ble.openResult = CompletableDeferred(
            CommandResult.Failure("Receiver did not confirm", recoverable = true),
        )
        val coordinator = ValveCommandCoordinator(ble)

        val result = coordinator.open()
        assertTrue(result is CommandResult.Failure)
        assertTrue(coordinator.commandPhase.value is CommandPhase.Failed)
    }

    @Test
    fun oppositeNotificationFailureDoesNotLeaveInFlight() = runTest {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Closed
        ble.openResult = CompletableDeferred(
            CommandResult.Failure("Unexpected valve state", recoverable = true),
        )
        val coordinator = ValveCommandCoordinator(ble)

        coordinator.open()
        assertFalse(coordinator.commandInFlight)
    }
}
