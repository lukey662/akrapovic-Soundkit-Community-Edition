package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.test.testDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QsTilePresenterTest {
    @Test
    fun disconnectedOpensAppOnClick() {
        val presentation = QsTilePresenter.present(
            connectionState = ConnectionState.Disconnected,
            valveState = ValveState.Closed,
            receiverStatusMessage = null,
            defaultReceiver = null,
        )

        assertTrue(presentation.clickOpensApp)
        assertNull(presentation.valveAction)
        assertEquals("open app", presentation.subtitle)
    }

    @Test
    fun notReadyBlocksValveAction() {
        val device = testDevice()
        val presentation = QsTilePresenter.present(
            connectionState = ConnectionState.Connected(device),
            valveState = ValveState.Open,
            receiverStatusMessage = "status 04",
            defaultReceiver = null,
        )

        assertEquals("not ready", presentation.subtitle)
        assertFalse(presentation.active)
        assertNull(presentation.valveAction)
    }

    @Test
    fun connectedClosedOffersOpenWithNickname() {
        val device = testDevice(name = "Akra")
        val presentation = QsTilePresenter.present(
            connectionState = ConnectionState.Connected(device),
            valveState = ValveState.Closed,
            receiverStatusMessage = null,
            defaultReceiver = SavedReceiver(device.address, device.name, nickname = "RS6", isDefault = true),
        )

        assertEquals(ValveTileAction.Open, presentation.valveAction)
        assertEquals("RS6 · closed", presentation.subtitle)
        assertTrue(presentation.active)
    }
}
