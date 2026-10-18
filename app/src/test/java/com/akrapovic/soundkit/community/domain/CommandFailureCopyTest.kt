package com.akrapovic.soundkit.community.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandFailureCopyTest {
    @Test
    fun unconfirmedCommandReadsAsChecking() {
        val copy = CommandFailureCopy.from(
            "The receiver did not confirm the change. Checking the valve position before allowing another tap.",
            ValveState.Unknown,
        )
        assertEquals("Valve didn't confirm", copy.title)
    }

    @Test
    fun mapsKnownFailures() {
        assertEquals("Receiver not connected", CommandFailureCopy.from("Receiver is not connected.", ValveState.Unknown).title)
        assertEquals("Checking the valves", CommandFailureCopy.from("Waiting for receiver status.", ValveState.Unknown).title)
        assertEquals("Command didn't send", CommandFailureCopy.from("BLE write failed with status 133", ValveState.Closed).title)
        assertEquals("Command didn't send", CommandFailureCopy.from("Bluetooth stack rejected the characteristic write", ValveState.Open).title)
        assertEquals("Receiver not ready", CommandFailureCopy.from("Receiver is not ready.", ValveState.Open).title)
    }

    @Test
    fun unknownFailureKeepsTheMessage() {
        val copy = CommandFailureCopy.from("Something odd", ValveState.Open)
        assertEquals("Couldn't change the valves", copy.title)
        assertEquals("Something odd", copy.body)
    }
}
