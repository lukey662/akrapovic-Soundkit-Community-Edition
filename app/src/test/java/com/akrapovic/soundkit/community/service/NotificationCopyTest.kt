package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.ConnectionState
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
    fun automationPausedShownInContent() {
        val presentation = NotificationCopy.build(
            connectionState = ConnectionState.Disconnected,
            valveState = ValveState.Closed,
            receiverStatusMessage = null,
            defaultReceiver = null,
            automationPaused = true,
            hasAutomationRules = true,
        )

        assertTrue(presentation.contentText.contains("Automation paused"))
        assertFalse(presentation.pauseAutomationEnabled)
    }
}
