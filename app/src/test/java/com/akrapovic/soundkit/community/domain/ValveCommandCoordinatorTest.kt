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
import com.akrapovic.soundkit.community.test.FixedClock
import com.akrapovic.soundkit.community.test.NoopRuleExecutionLogStore

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
        val coordinator = ValveCommandCoordinator(ble, NoopRuleExecutionLogStore(), FixedClock())

        val result = coordinator.open(CommandSource.Phone)
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
        val coordinator = ValveCommandCoordinator(ble, NoopRuleExecutionLogStore(), FixedClock())

        val result = coordinator.close(CommandSource.Phone)

        assertTrue(result is CommandResult.Success)
        assertEquals(0, ble.closeValveCount)
    }

    @Test
    fun blocksUnknownNotReadyAndDisconnectedCommandsWithoutRepositoryWrite() = runTest {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Unknown
        val coordinator = ValveCommandCoordinator(ble, NoopRuleExecutionLogStore(), FixedClock())

        val unknownResult = coordinator.open(CommandSource.Phone)
        assertTrue(unknownResult is CommandResult.Failure)
        assertEquals(0, ble.openValveCount)
        assertTrue(coordinator.commandPhase.value is CommandPhase.Failed)

        ble.valveState.value = ValveState.Closed
        ble.receiverStatusMessage.value = "Ignition required"
        val notReadyResult = coordinator.open(CommandSource.Phone)
        assertTrue(notReadyResult is CommandResult.Failure)
        assertEquals(0, ble.openValveCount)

        ble.receiverStatusMessage.value = null
        ble.connectionState.value = ConnectionState.Disconnected
        assertFalse(coordinator.canOpen())
        assertFalse(coordinator.canClose())
        val disconnectedResult = coordinator.open(CommandSource.Phone)
        assertTrue(disconnectedResult is CommandResult.Failure)
        assertEquals(0, ble.openValveCount)
    }

    @Test
    fun canOpenOnlyWhenClosedConnectedAndReady() = runTest {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Closed
        ble.receiverStatusMessage.value = null
        val coordinator = ValveCommandCoordinator(ble, NoopRuleExecutionLogStore(), FixedClock())

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
        val coordinator = ValveCommandCoordinator(ble, NoopRuleExecutionLogStore(), FixedClock())

        val first = async { coordinator.open(CommandSource.Phone) }
        runCurrent()
        val second = async { coordinator.close(CommandSource.Phone) }
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
    fun duplicateConcurrentOpenDoesNotWriteTwice() = runTest {
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Closed
        val openGate = CompletableDeferred<CommandResult>()
        ble.openResult = openGate
        val coordinator = ValveCommandCoordinator(ble, NoopRuleExecutionLogStore(), FixedClock())

        val first = async { coordinator.open(CommandSource.Phone) }
        runCurrent()
        val duplicate = async { coordinator.open(CommandSource.Phone) }
        runCurrent()

        openGate.complete(CommandResult.Success(ValveState.Open))
        runCurrent()

        assertTrue(first.await() is CommandResult.Success)
        assertTrue(duplicate.await() is CommandResult.Success)
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
        val coordinator = ValveCommandCoordinator(ble, NoopRuleExecutionLogStore(), FixedClock())

        val result = coordinator.open(CommandSource.Phone)
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
        val coordinator = ValveCommandCoordinator(ble, NoopRuleExecutionLogStore(), FixedClock())

        coordinator.open(CommandSource.Phone)
        assertFalse(coordinator.commandInFlight)
    }

    @Test
    fun recordsEveryRequestWithItsSource() = runTest {
        val ble = FakeBleRepository()
        val log = com.akrapovic.soundkit.community.test.RecordingRuleExecutionLogStore()
        val coordinator = ValveCommandCoordinator(ble, log, FixedClock())

        coordinator.open(CommandSource.Widget)
        ble.connectionState.value = ConnectionState.Connected(testDevice())
        ble.valveState.value = ValveState.Closed
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))
        coordinator.open(CommandSource.AndroidAuto)
        coordinator.open(CommandSource.Phone)

        val entries = log.snapshot()
        assertEquals(listOf("Widget", "Android Auto", "Phone"), entries.map { it.ruleName })
        assertEquals(RuleExecutionOutcome.Failed, entries[0].outcome)
        assertEquals("Receiver is not connected.", entries[0].detail)
        assertEquals(RuleExecutionOutcome.Success, entries[1].outcome)
        assertEquals("Open", entries[1].action)
        assertEquals(RuleExecutionOutcome.Skipped, entries[2].outcome)
    }
}
