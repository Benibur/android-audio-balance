package com.audiobalance.app.service

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BtA2dpReceiver(
    private val onEvent: (device: BluetoothDevice, state: Int) -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "BtA2dpReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) return

        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        Log.d(TAG, "A2DP state=$state device=${device?.address}")

        if (device != null && state != -1) {
            onEvent(device, state)
        }
    }
}
