package com.example.blebindsample

import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.TextView
import org.jetbrains.anko.*

/**
 * 
 */
class MainActivity : AppCompatActivity() {

    companion object {
        val TAG = "MainActivity"
    }

    /** Button used to start advertising. */
    private var mAdvertiseButton: Button? = null
    /** View used to display the current state. */
    private var mStateView: TextView? = null

    private var mBluetoothManager: BluetoothManager? = null
    /** Bluetooth adapter to use. */
    private val mBluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var mGattServer: BluetoothGattServer? = null
    private var mAdvertiser: BluetoothLeAdvertiser? = null
    private var mGattCallback: GattCallback? = null

    private val mMainHandler: Handler = Handler(Looper.getMainLooper())

    private val mAdvertiseSettings: AdvertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
    private val mAdvertiseData: AdvertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SampleService.SERVICE_UUID))
            .build()
    private val mAdvertiseCallback = object: AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            mStateView?.text = getString(R.string.state_advertising, mBluetoothAdapter.name)
            Log.d(TAG, "Advertising as ${mBluetoothAdapter.name}@${mBluetoothAdapter.address}")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.d(TAG, "Advertise failed")
            reset()
        }
    }

    val mBroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onBluetoothStateChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        verticalLayout {
            button {
                mAdvertiseButton = this
                textResource = R.string.advertise
                onClick { advertise() }
            }.lparams {
                width = wrapContent
                height = wrapContent
            }
            textView {
                mStateView = this
            }.lparams {
                width = wrapContent
                height = wrapContent
            }
        }
        mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        onBluetoothStateChanged()
    }

    override fun onPostResume() {
        super.onPostResume()
        registerReceiver(mBroadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        Log.d(TAG, "Resuming UI, refreshing BT state...")
        onBluetoothStateChanged()
    }

    override fun onPause() {
        unregisterReceiver(mBroadcastReceiver)
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "Stopping activity, resetting state...")
        reset()
        super.onStop()
    }

    //
    // AdvertiseCallback
    //

    private fun onBluetoothStateChanged() {
        if (!mBluetoothAdapter.isEnabled) {
            mAdvertiseButton?.enabled = false
            mStateView?.textResource = R.string.state_disabled
        } else if (mGattServer == null) {
            mAdvertiseButton?.enabled = true
            mStateView?.textResource = R.string.state_idle
        }
    }

    private fun advertise() {
        mGattCallback = GattCallback()
        mGattServer = mBluetoothManager!!.openGattServer(this, mGattCallback!!)
        mAdvertiser = mBluetoothAdapter.bluetoothLeAdvertiser

        mAdvertiseButton?.enabled = false
        mStateView?.textResource = R.string.state_initializing

        Log.d(TAG, "Creating GATT server")
        mGattServer!!.addService(SampleService.SERVICE)
    }

    private fun reset() {
        mGattServer?.close()
        mGattServer = null
        mAdvertiser?.stopAdvertising(mAdvertiseCallback)
        mAdvertiser = null
    }

    private inner class GattCallback : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            mMainHandler.post {
                if (status == BluetoothGatt.GATT_FAILURE) {
                    // Failed to add service.
                    Log.d(TAG, "Failed to add service: " + service!!.uuid)
                    reset()
                    onBluetoothStateChanged()
                } else {
                    // Service added, start advertising.
                    Log.d(TAG, "Service added: " + service!!.uuid)
                    mAdvertiser!!.startAdvertising(mAdvertiseSettings, mAdvertiseData,
                            mAdvertiseCallback)
                }
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            mMainHandler.post {
                if (newState == BluetoothAdapter.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to ${device!!.address}")
                    mStateView?.textResource = R.string.state_connected
                    mAdvertiser!!.stopAdvertising(mAdvertiseCallback)
                } else if (newState == BluetoothAdapter.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from ${device!!.address}")
                    reset()
                    onBluetoothStateChanged()
                }
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int,
                offset: Int, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (device!!.bondState != BluetoothDevice.BOND_BONDED) {
                mStateView?.post { mStateView?.textResource = R.string.state_read_unbonded }
                Log.d(TAG, "Unbonded read request: FAIL")
                mGattServer!!.sendResponse(device, requestId, GATT_INSUFFICIENT_AUTHENTICATION,
                        0 /* offset */, null /* value */)
            } else {
                mStateView?.post { mStateView?.textResource = R.string.state_read_bonded }
                Log.d(TAG, "Bonded read request: SUCCESS")
                mGattServer!!.sendResponse(device, requestId, GATT_SUCCESS, 0 /* offset */,
                        byteArrayOf(1))
            }
        }
    }
}
