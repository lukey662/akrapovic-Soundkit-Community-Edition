package com.akrapovic.soundkit.community.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.car.app.CarAppService
import com.akrapovic.soundkit.community.ble.PermissionPolicy
import com.akrapovic.soundkit.community.ble.SoundKitProtocol
import com.akrapovic.soundkit.community.car.SoundKitCarAppService

object CarAppDiagnostics {
    fun format(
        context: Context,
        hasDefaultReceiver: Boolean,
        connectInCar: Boolean,
        carSessionActive: Boolean,
    ): String {
        return buildString {
            appendLine("CAR APP READINESS")
            appendLine("carAppServiceRegistered=${isCarAppServiceRegistered(context)}")
            appendLine("packageName=${context.packageName}")
            appendLine("packageSuffix=${context.packageName.substringAfter("community", missingDelimiterValue = "")}")
            appendLine("minCarApiLevel=6")
            appendLine("protocolVerified=${SoundKitProtocol.VERIFIED}")
            appendLine("blePermissionsGranted=${hasBlePermissions(context)}")
            appendLine("defaultReceiverSaved=$hasDefaultReceiver")
            appendLine("connectInCar=$connectInCar")
            appendLine("carSessionActive=$carSessionActive")
            appendLine("hostStatus=${if (carSessionActive) "session active" else "no active car host session"}")
            appendLine("androidAutoNote=Settings>Connected devices>Android Auto; enable Developer mode and Unknown sources")
        }
    }

    private fun hasBlePermissions(context: Context): Boolean {
        return PermissionPolicy.requiredBlePermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isCarAppServiceRegistered(context: Context): Boolean {
        val intent = Intent(CarAppService.SERVICE_INTERFACE).apply {
            setPackage(context.packageName)
            addCategory("androidx.car.app.category.IOT")
        }
        val services = context.packageManager.queryIntentServices(
            intent,
            PackageManager.GET_RESOLVED_FILTER,
        )
        return services.any { it.serviceInfo.name == SoundKitCarAppService::class.java.name }
    }
}
