package com.akrapovic.soundkit.community.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.akrapovic.soundkit.community.R

class SoundKitCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // ALLOW_ALL is for debug/DHU only. Release builds must allow-list signed hosts
        // (CVE-2024-10382 mitigation path also requires androidx.car.app 1.7.0+).
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        }
        return HostValidator.Builder(applicationContext)
            .addAllowedHosts(R.array.soundkit_car_app_hosts_allowlist)
            .build()
    }

    override fun onCreateSession(): Session = SoundKitCarSession()
}
