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
        assertEquals("tap to connect", presentation.subtitle)
        assertEquals(QsTileState.Inactive, presentation.tileState)
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
        assertEquals(QsTileState.Unavailable, presentation.tileState)
        assertNull(presentation.valveAction)
    }

    @Test
    fun unknownStateBlocksWidgetAndQuickSettingsValveActions() {
        val presentation = QsTilePresenter.present(
            connectionState = ConnectionState.Connected(testDevice()),
            valveState = ValveState.Unknown,
            receiverStatusMessage = null,
            defaultReceiver = null,
        )

        assertEquals("checking", presentation.subtitle)
        assertEquals(QsTileState.Unavailable, presentation.tileState)
        assertFalse(presentation.clickOpensApp)
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
        assertEquals("closed · tap to open", presentation.subtitle)
        assertEquals(QsTileState.Inactive, presentation.tileState)
    }

    @Test
    fun connectedOpenIsActiveAndOffersClose() {
        val presentation = QsTilePresenter.present(
            connectionState = ConnectionState.Connected(testDevice()),
            valveState = ValveState.Open,
            receiverStatusMessage = null,
            defaultReceiver = null,
        )

        assertEquals(ValveTileAction.Close, presentation.valveAction)
        assertEquals(QsTileState.Active, presentation.tileState)
        assertEquals("open · tap to close", presentation.subtitle)
    }
}
