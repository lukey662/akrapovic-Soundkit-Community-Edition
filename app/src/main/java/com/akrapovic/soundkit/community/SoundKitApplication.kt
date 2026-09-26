package com.akrapovic.soundkit.community

import android.app.Application
import com.akrapovic.soundkit.community.car.CarPresenceMonitor
import com.akrapovic.soundkit.community.diagnostics.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class SoundKitApplication : Application() {
    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var carPresenceMonitor: CarPresenceMonitor

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        carPresenceMonitor.start()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
