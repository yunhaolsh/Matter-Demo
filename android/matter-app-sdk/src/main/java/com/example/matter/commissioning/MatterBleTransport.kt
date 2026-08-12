package com.example.matter.commissioning

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import chip.platform.AndroidBleManager
import chip.platform.BleCallback
import java.io.Closeable
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

internal class MatterBleTransport(
    context: Context,
    private val chipBleManager: AndroidBleManager,
) : BleCallback, Closeable {
    private val applicationContext = context.applicationContext
    private val adapter: BluetoothAdapter =
        applicationContext.getSystemService(BluetoothManager::class.java).adapter
            ?: error("Bluetooth is not available on this phone")

    private var gatt: BluetoothGatt? = null
    var connectionId: Int = 0
        private set

    init {
        chipBleManager.setBleCallback(this)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    suspend fun scan(discriminator: Int, shortDiscriminator: Boolean): BluetoothDevice {
        check(adapter.isEnabled) { "Turn on Bluetooth to add this device" }
        val scanner = adapter.bluetoothLeScanner ?: error("Bluetooth scanning is unavailable")
        val advertisementFilter = MatterBleAdvertisement.filter(discriminator, shortDiscriminator)
        val filter =
            ScanFilter.Builder()
                .setServiceData(
                    ParcelUuid(MATTER_BLE_UUID),
                    advertisementFilter.serviceData,
                    advertisementFilter.serviceDataMask,
                )
                .build()
        val settings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        return withTimeout(SCAN_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                @SuppressLint("MissingPermission")
                val callback =
                    object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            if (continuation.isActive) {
                                scanner.stopScan(this)
                                continuation.resume(result.device)
                            }
                        }

                        override fun onScanFailed(errorCode: Int) {
                            if (continuation.isActive) {
                                scanner.stopScan(this)
                                continuation.resumeWithException(
                                    IllegalStateException("Bluetooth scan failed ($errorCode)"),
                                )
                            }
                        }
                    }
                scanner.startScan(listOf(filter), settings, callback)
                continuation.invokeOnCancellation { scanner.stopScan(callback) }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connect(device: BluetoothDevice): BluetoothGatt =
        withTimeout(CONNECT_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val callback = forwardingCallback(continuation)
                val connection =
                    device.connectGatt(applicationContext, false, callback, BluetoothDevice.TRANSPORT_LE)
                gatt = connection
                connectionId = chipBleManager.addConnection(connection)
                if (connectionId == 0) {
                    connection.close()
                    continuation.resumeWithException(
                        IllegalStateException("Unable to register the Matter Bluetooth connection"),
                    )
                    return@suspendCancellableCoroutine
                }
                continuation.invokeOnCancellation { close() }
            }
        }

    @SuppressLint("MissingPermission")
    private fun forwardingCallback(
        continuation: kotlinx.coroutines.CancellableContinuation<BluetoothGatt>,
    ): BluetoothGattCallback {
        val delegate = chipBleManager.callback
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                delegate.onConnectionStateChange(gatt, status, newState)
                when {
                    status != BluetoothGatt.GATT_SUCCESS -> failConnection(continuation, status)
                    newState == BluetoothProfile.STATE_CONNECTED && !gatt.discoverServices() ->
                        failConnection(continuation, status)
                    newState == BluetoothProfile.STATE_DISCONNECTED ->
                        failConnection(continuation, status)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                delegate.onServicesDiscovered(gatt, status)
                if (status != BluetoothGatt.GATT_SUCCESS || !gatt.requestMtu(MATTER_MTU)) {
                    failConnection(continuation, status)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                delegate.onMtuChanged(gatt, mtu, status)
                if (status == BluetoothGatt.GATT_SUCCESS && continuation.isActive) {
                    continuation.resume(gatt)
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                    failConnection(continuation, status)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) =
                delegate.onCharacteristicChanged(gatt, characteristic)

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
                delegate.onCharacteristicRead(gatt, characteristic, status)

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
                delegate.onCharacteristicWrite(gatt, characteristic, status)

            override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
                delegate.onDescriptorRead(gatt, descriptor, status)

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
                delegate.onDescriptorWrite(gatt, descriptor, status)

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) =
                delegate.onReadRemoteRssi(gatt, rssi, status)

            override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) =
                delegate.onReliableWriteCompleted(gatt, status)
        }
    }

    private fun failConnection(
        continuation: kotlinx.coroutines.CancellableContinuation<BluetoothGatt>,
        status: Int,
    ) {
        if (continuation.isActive) {
            continuation.resumeWithException(
                IllegalStateException("Unable to connect to the Matter device ($status)"),
            )
        }
        close()
    }

    override fun onCloseBleComplete(connId: Int) {
        if (connId == connectionId) close()
    }

    override fun onNotifyChipConnectionClosed(connId: Int) {
        if (connId == connectionId) close()
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        val activeGatt = gatt
        gatt = null
        if (connectionId != 0) {
            chipBleManager.removeConnection(connectionId)
            connectionId = 0
        }
        activeGatt?.disconnect()
        activeGatt?.close()
    }

    private companion object {
        val MATTER_BLE_UUID: UUID = UUID.fromString("0000FFF6-0000-1000-8000-00805F9B34FB")
        const val MATTER_MTU = 247
        const val SCAN_TIMEOUT_MILLIS = 15_000L
        const val CONNECT_TIMEOUT_MILLIS = 20_000L
    }
}
