package com.akrapovic.soundkit.community.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class SoundKitCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return SoundKitCarScreen(carContext)
    }
}

