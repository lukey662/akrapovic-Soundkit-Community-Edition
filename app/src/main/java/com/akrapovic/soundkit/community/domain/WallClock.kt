package com.akrapovic.soundkit.community.domain

import javax.inject.Inject

fun interface WallClock {
    fun nowMillis(): Long
}

class SystemWallClock @Inject constructor() : WallClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
