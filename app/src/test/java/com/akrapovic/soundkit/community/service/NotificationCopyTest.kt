package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.domain.RuleExecutionOutcome
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.test.testDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCopyTest {
    @Test
    fun disconnectedDisablesValveActions() {
        val presentation = NotificationCopy.build(
            connectionState = ConnectionState.Disconnected,
            valveState = ValveState.Closed,
            receiverStatusMessage = null,
            defaultReceiver = null,
        )

        assertFalse(presentation.openValveEnabled)
        assertFalse(presentation.closeValveEnabled)
        assertFalse(presentation.disconnectEnabled)
    }

    @Test
    fun connectedEnablesValveActionsWhenKnown() {
        val device = testDevice()
        val presentation = NotificationCopy.build(
            connectionState = ConnectionState.Connected(device),
            valveState = ValveState.Closed,
            receiverStatusMessage = null,
            defaultReceiver = SavedReceiver(device.address, device.name, isDefault = true),
        )

        assertTrue(presentation.openValveEnabled)
        assertFalse(presentation.closeValveEnabled)
        assertTrue(presentation.disconnectEnabled)
        assertEquals(device.name, presentation.title)
    }

    @Test
    fun notReadyDisablesValveActions() {
        val device = testDevice()
        val presentation = NotificationCopy.build(
            connectionState = ConnectionState.Connected(device),
            valveState = ValveState.Open,
            receiverStatusMessage = "Receiver warming up",
            defaultReceiver = null,
        )

        assertFalse(presentation.openValveEnabled)
        assertFalse(presentation.closeValveEnabled)
        assertTrue(presentation.contentText.contains("Receiver not ready"))
    }

    @Test
    fun unknownValveStateDisablesValveActionsUntilStatusArrives() {
        val presentation = NotificationCopy.build(
            connectionState = ConnectionState.Connected(testDevice()),
            valveState = ValveState.Unknown,
            receiverStatusMessage = null,
            defaultReceiver = null,
        )

        assertFalse(presentation.openValveEnabled)
        assertFalse(presentation.closeValveEnabled)
        assertTrue(presentation.disconnectEnabled)
        assertTrue(presentation.contentText.contains("Checking valves"))
    }

    @Test
    fun driveModePausedShownInContent() {
        val presentation = NotificationCopy.build(
            connectionState = ConnectionState.Disconnected,
            valveState = ValveState.Closed,
            receiverStatusMessage = null,
            defaultReceiver = null,
            driveModeEnabled = true,
            driveModePaused = true,
        )

        assertTrue(presentation.contentText.contains("Drive mode paused"))
        assertFalse(presentation.pauseDriveModeEnabled)
    }

    @Test
    fun lastDriveModeApplyShownInContent() {
        val presentation = NotificationCopy.build(
            connectionState = ConnectionState.Connected(testDevice()),
            valveState = ValveState.Open,
            receiverStatusMessage = null,
            defaultReceiver = null,
            driveModeEnabled = true,
            lastExecution = RuleExecutionEntry(
                timestampMillis = 1L,
                ruleName = "Drive mode",
                action = "Open",
                reason = "connect",
                outcome = RuleExecutionOutcome.Success,
            ),
        )

        assertTrue(presentation.contentText.contains("Last: Drive mode → Open"))
    }
}
