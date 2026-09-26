package com.akrapovic.soundkit.community.domain

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object CarBluetoothCatalog {
    @SuppressLint("MissingPermission")
    fun bondedCandidates(context: Context): List<CarBluetoothCandidate> {
        if (!hasConnectPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return adapter.bondedDevices.orEmpty()
            .map { device ->
                val name = runCatching { device.name }.getOrNull().orEmpty().ifBlank { device.address }
                CarBluetoothCandidate(
                    address = device.address,
                    name = name,
                    likelyCar = CarBluetoothClassifier.isLikelyCar(device.bluetoothClass?.deviceClass),
                )
            }
            .sortedWith(compareByDescending<CarBluetoothCandidate> { it.likelyCar }.thenBy { it.name })
    }

    private fun hasConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
