package com.akrapovic.soundkit.community.ble

import com.akrapovic.soundkit.community.domain.ValveCommand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundKitProtocolTest {
    @Test
    fun protocolFailsClosedUntilVerified() {
        assertFalse(SoundKitProtocol.VERIFIED)
        assertTrue(SoundKitProtocol.requireVerified().isFailure)
        assertTrue(SoundKitProtocol.commandPayload(ValveCommand.Open).isFailure)
        assertTrue(SoundKitProtocol.commandPayload(ValveCommand.Close).isFailure)
    }

    @Test
    fun likelyDeviceNameMatchesKnownHints() {
        assertTrue(SoundKitProtocol.isLikelySoundKitDevice("Akrapovic SoundKit"))
        assertTrue(SoundKitProtocol.isLikelySoundKitDevice("Akrapovič Receiver"))
        assertFalse(SoundKitProtocol.isLikelySoundKitDevice("Kitchen Light"))
    }
}

