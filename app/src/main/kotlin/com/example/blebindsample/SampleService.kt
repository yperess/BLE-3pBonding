package com.example.blebindsample

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
import android.bluetooth.BluetoothGattService
import java.util.*

/**
 *
 */
class SampleService {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("c958edeb-9ad5-456c-929f-f6ac38a6e353")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("ed82c27e-63ea-48bb-ac3b-6fc39f1b5582")

        val SERVICE = BluetoothGattService(SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY)

        init {
            SERVICE.addCharacteristic(BluetoothGattCharacteristic(CHARACTERISTIC_UUID,
                    PROPERTY_READ, PERMISSION_READ_ENCRYPTED))
        }
    }
}