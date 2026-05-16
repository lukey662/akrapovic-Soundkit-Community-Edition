package com.akrapovic.soundkit.community

import com.akrapovic.soundkit.community.domain.SoundKitDevice

fun testDeviceForSmoke() = SoundKitDevice(
    name = "Akrapovic SoundKit",
    address = "00:11:22:33:44:55",
    rssi = -52,
    isLikelySoundKit = true,
)

