package com.akrapovic.soundkit.community.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.domain.ValveState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

interface BleConnectionGateway {
    val connectionState: StateFlow<ConnectionState>
    val valveState: StateFlow<ValveState>

    fun markReconnecting(device: SoundKitDevice, attempt: Int, nextDelayMs: Long)
    suspend fun connect(device: SoundKitDevice): Result<Unit>
    suspend fun disconnect()
    suspend fun writeCommand(command: ValveCommand): CommandResult
}

class BleConnectionManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    bluetoothManager: BluetoothManager,
    private val diagnosticsRepository: DiagnosticsRepository,
) : BleConnectionGateway {
    private val bluetoothAdapter = bluetoothManager.adapter
    private val operationMutex = Mutex()

    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedDevice: SoundKitDevice? = null
    private var pendingWrite: CompletableDeferred<CommandResult>? = null
    private var pendingCommand: ValveCommand? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _valveState = MutableStateFlow(ValveState.Unknown)
    override val valveState: StateFlow<ValveState> = _valveState

    override fun markReconnecting(device: SoundKitDevice, attempt: Int, nextDelayMs: Long) {
        _connectionState.value = ConnectionState.Reconnecting(device, attempt, nextDelayMs)
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: SoundKitDevice): Result<Unit> = operationMutex.withLock {
        if (!hasConnectPermission()) {
            return@withLock Result.failure(SecurityException("Missing Bluetooth connect permission"))
        }
        val adapter = bluetoothAdapter ?: return@withLock Result.failure(IllegalStateException("Bluetooth is unavailable"))
        val remoteDevice = runCatching { adapter.getRemoteDevice(device.address) }
            .getOrElse { return@withLock Result.failure(it) }

        disconnectLocked()
        connectedDevice = device
        _connectionState.value = ConnectionState.Connecting(device)
        diagnosticsRepository.info("Connecting to BLE device ${device.name} ${device.address}")

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remoteDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            remoteDevice.connectGatt(context, false, gattCallback)
        }
        Result.success(Unit)
    }

    override suspend fun disconnect() = operationMutex.withLock {
        disconnectLocked()
    }

    @SuppressLint("MissingPermission")
    override suspend fun writeCommand(command: ValveCommand): CommandResult = operationMutex.withLock {
        val protocolReady = SoundKitProtocol.requireVerified()
        if (protocolReady.isFailure) {
            val message = protocolReady.exceptionOrNull()?.message.orEmpty()
            diagnosticsRepository.warning(message)
            return@withLock CommandResult.Failure(message, recoverable = false)
        }
        if (!hasConnectPermission()) {
            return@withLock CommandResult.Failure("Missing Bluetooth connect permission", recoverable = true)
        }
        val activeGatt = gatt ?: return@withLock CommandResult.Failure("No active BLE connection", recoverable = true)
        val characteristic = commandCharacteristic
            ?: return@withLock CommandResult.Failure("Command characteristic was not discovered", recoverable = true)
        val payload = SoundKitProtocol.commandPayload(command).getOrElse { error ->
            return@withLock CommandResult.Failure(error.message.orEmpty(), recoverable = false)
        }
        val writeType = SoundKitProtocol.writeType
            ?: return@withLock CommandResult.Failure("Write type is not verified", recoverable = false)

        characteristic.writeType = writeType
        val deferred = CompletableDeferred<CommandResult>()
        pendingWrite = deferred
        pendingCommand = command
        diagnosticsRepository.debug("BLE write ${command.name}: ${payload.toHexString()} writeType=$writeType")

        val started = writeCharacteristic(activeGatt, characteristic, payload, writeType)
        if (!started) {
            pendingWrite = null
            pendingCommand = null
            return@withLock CommandResult.Failure("Bluetooth stack rejected the characteristic write", recoverable = true)
        }

        val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) { deferred.await() }
            ?: CommandResult.Failure("Timed out waiting for BLE write confirmation", recoverable = true)
        if (result is CommandResult.Success) {
            _valveState.value = result.valveState
        }
        result
    }

    @SuppressLint("MissingPermission")
    private fun disconnectLocked() {
        pendingWrite?.complete(CommandResult.Failure("Disconnected before write completed", recoverable = true))
        pendingWrite = null
        pendingCommand = null
        commandCharacteristic = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        connectedDevice = null
        _valveState.value = ValveState.Unknown
        _connectionState.value = ConnectionState.Disconnected
        diagnosticsRepository.info("BLE connection closed")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = connectedDevice ?: SoundKitDevice(
                name = safeDeviceName(gatt.device),
                address = gatt.device.address,
            )
            diagnosticsRepository.debug("GATT connection state status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.Connecting(device)
                    diagnosticsRepository.info("Connected at link layer; discovering GATT services")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    commandCharacteristic = null
                    _valveState.value = ValveState.Unknown
                    _connectionState.value = if (status == BluetoothGatt.GATT_SUCCESS) {
                        ConnectionState.Disconnected
                    } else {
                        ConnectionState.Error("GATT disconnected with status $status", recoverable = true)
                    }
                    pendingWrite?.complete(CommandResult.Failure("Disconnected", recoverable = true))
                    pendingWrite = null
                    pendingCommand = null
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                diagnosticsRepository.error("GATT service discovery failed with status $status")
                _connectionState.value = ConnectionState.Error("Service discovery failed: $status", recoverable = true)
                return
            }

            gatt.services.forEach { service ->
                diagnosticsRepository.debug("Discovered service ${service.uuid}")
                service.characteristics.forEach { characteristic ->
                    diagnosticsRepository.debug(
                        "Discovered characteristic ${characteristic.uuid} properties=${characteristic.properties}",
                    )
                }
            }
            diagnosticsRepository.info(buildGattProfileReport(gatt))

            val serviceUuid = SoundKitProtocol.serviceUuid
            val characteristicUuid = SoundKitProtocol.commandCharacteristicUuid
            if (serviceUuid != null && characteristicUuid != null) {
                commandCharacteristic = gatt.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
                maybeEnableNotifications(gatt, SoundKitProtocol.notificationCharacteristicUuid)
            }

            val device = connectedDevice ?: SoundKitDevice(
                name = safeDeviceName(gatt.device),
                address = gatt.device.address,
            )
            _connectionState.value = ConnectionState.Connected(device)
            diagnosticsRepository.info("GATT services discovered for ${device.name}")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val commandState = when (status) {
                BluetoothGatt.GATT_SUCCESS -> inferStateFromPendingCommand()
                else -> null
            }
            diagnosticsRepository.debug("BLE write completed characteristic=${characteristic.uuid} status=$status")
            pendingWrite?.complete(
                if (commandState != null) {
                    CommandResult.Success(commandState)
                } else {
                    CommandResult.Failure("BLE write failed with status $status", recoverable = true)
                },
            )
            pendingWrite = null
            pendingCommand = null
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            diagnosticsRepository.debug("BLE notify ${characteristic.uuid}: ${value.toHexString()}")
        }

        @Deprecated("Required by Android BluetoothGattCallback before API 33.")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            diagnosticsRepository.debug("BLE notify ${characteristic.uuid}: ${characteristic.value?.toHexString().orEmpty()}")
        }
    }

    private fun inferStateFromPendingCommand(): ValveState {
        return when (pendingCommand) {
            ValveCommand.Open -> ValveState.Open
            ValveCommand.Close -> ValveState.Closed
            null -> ValveState.Unknown
        }
    }

    @SuppressLint("MissingPermission")
    private fun maybeEnableNotifications(gatt: BluetoothGatt, uuid: UUID?) {
        if (uuid == null) return
        val characteristic = gatt.services
            .asSequence()
            .mapNotNull { it.getCharacteristic(uuid) }
            .firstOrNull() ?: return

        val enabled = gatt.setCharacteristicNotification(characteristic, true)
        diagnosticsRepository.debug("Notification registration ${characteristic.uuid} enabled=$enabled")

        val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return
        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, value)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        writeType: Int,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, payload, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            // Android exposes the value-setting write API only before API 33. The call is deprecated
            // on newer SDKs, so it is isolated here and never used on Android 13+.
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildGattProfileReport(gatt: BluetoothGatt): String {
        return buildString {
            appendLine("GATT PROFILE START")
            appendLine("services=${gatt.services.size}")
            gatt.services.forEachIndexed { serviceIndex, service ->
                appendLine("service[$serviceIndex]=${service.uuid}")
                appendLine("  type=${if (service.type == android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY) "PRIMARY" else "SECONDARY"}")
                service.characteristics.forEachIndexed { characteristicIndex, characteristic ->
                    appendLine("  characteristic[$characteristicIndex]=${characteristic.uuid}")
                    appendLine("    properties=${characteristic.properties.toCharacteristicPropertiesText()}")
                    appendLine("    permissions=${characteristic.permissions.toPermissionsText()}")
                    if (characteristic.descriptors.isEmpty()) {
                        appendLine("    descriptors=none")
                    } else {
                        characteristic.descriptors.forEachIndexed { descriptorIndex, descriptor ->
                            appendLine("    descriptor[$descriptorIndex]=${descriptor.uuid}")
                            appendLine("      permissions=${descriptor.permissions.toPermissionsText()}")
                            if (descriptor.uuid == CCCD_UUID) {
                                appendLine("      known=CCCD")
                            }
                        }
                    }
                }
            }
            append("GATT PROFILE END")
        }
    }

    private fun Int.toCharacteristicPropertiesText(): String {
        val values = buildList {
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("BROADCAST")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add("SIGNED_WRITE")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) add("EXTENDED_PROPS")
        }
        return values.joinToString(separator = "|").ifBlank { "NONE" } + " ($this)"
    }

    private fun Int.toPermissionsText(): String {
        val values = buildList {
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_READ != 0) add("READ")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED != 0) add("READ_ENCRYPTED")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM != 0) add("READ_ENCRYPTED_MITM")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_WRITE != 0) add("WRITE")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED != 0) add("WRITE_ENCRYPTED")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM != 0) add("WRITE_ENCRYPTED_MITM")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED != 0) add("WRITE_SIGNED")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM != 0) add("WRITE_SIGNED_MITM")
        }
        return values.joinToString(separator = "|").ifBlank { "NONE" } + " ($this)"
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String {
        return runCatching { device.name }.getOrNull().orEmpty().ifBlank { "Unknown BLE device" }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = " ") { "%02X".format(it) }

    private companion object {
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val WRITE_TIMEOUT_MS = 5_000L
    }
}

