package com.akrapovic.soundkit.community.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface BleScannerGateway {
    fun scan(): Flow<List<SoundKitDevice>>
}

class BleScanner @Inject constructor(
    @ApplicationContext
    private val context: Context,
    bluetoothManager: BluetoothManager,
    private val diagnosticsRepository: DiagnosticsRepository,
) : BleScannerGateway {
    private val bluetoothAdapter = bluetoothManager.adapter

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<SoundKitDevice>> = callbackFlow {
        if (!hasScanPermission()) {
            trySend(emptyList())
            close(SecurityException("Missing Bluetooth scan permission"))
            return@callbackFlow
        }
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null || bluetoothAdapter?.isEnabled != true) {
            trySend(emptyList())
            close(IllegalStateException("Bluetooth is unavailable or disabled"))
            return@callbackFlow
        }

        val devices = linkedMapOf<String, SoundKitDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device.toSoundKitDevice(result.rssi)
                if (device.isLikelySoundKit || SoundKitProtocol.hasAdvertisingSignature(result.scanRecord?.bytes)) {
                    devices[device.address] = device
                    diagnosticsRepository.debug("BLE scan result ${device.name} ${device.address} rssi=${device.rssi}")
                    trySend(devices.values.toList())
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    val device = result.device.toSoundKitDevice(result.rssi)
                    if (device.isLikelySoundKit || SoundKitProtocol.hasAdvertisingSignature(result.scanRecord?.bytes)) {
                        devices[device.address] = device
                    }
                }
                trySend(devices.values.toList())
            }

            override fun onScanFailed(errorCode: Int) {
                diagnosticsRepository.error("BLE scan failed with code $errorCode")
                close(IllegalStateException("BLE scan failed with code $errorCode"))
            }
        }

        val filters = SoundKitProtocol.serviceUuid?.let { uuid ->
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build())
        }.orEmpty()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        diagnosticsRepository.info("Starting BLE scan with ${filters.size} service filters")
        scanner.startScan(filters, settings, callback)
        awaitClose {
            diagnosticsRepository.info("Stopping BLE scan")
            scanner.stopScan(callback)
        }
    }.distinctUntilChanged()

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toSoundKitDevice(rssi: Int?): SoundKitDevice {
        val safeName = runCatching { name }.getOrNull().orEmpty().ifBlank { "Unknown BLE device" }
        return SoundKitDevice(
            name = safeName,
            address = address,
            rssi = rssi,
            isLikelySoundKit = SoundKitProtocol.isLikelySoundKitDevice(safeName),
        )
    }
}

