package com.akrapovic.soundkit.community.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import dagger.hilt.android.EntryPointAccessors

class SoundKitCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                carContext.applicationContext,
                CarDependenciesEntryPoint::class.java,
            )
            val hostPackage = runCatching { carContext.hostInfo?.packageName }.getOrNull() ?: "unknown"
            val apiLevel = runCatching { carContext.carAppApiLevel }.getOrNull() ?: "unknown"
            entryPoint.carErrorLog().event("session: hostPackage=$hostPackage carApiLevel=$apiLevel")
        }
        return SoundKitCarScreen(carContext)
    }
}
