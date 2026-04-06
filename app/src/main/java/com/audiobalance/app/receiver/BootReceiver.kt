package com.audiobalance.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.audiobalance.app.service.AudioBalanceService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Don't start the service if BLUETOOTH_CONNECT hasn't been granted yet (first launch)
        val btGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        if (!btGranted) {
            Log.d(TAG, "BOOT_COMPLETED received but BLUETOOTH_CONNECT not granted — skipping service start")
            return
        }
        Log.d(TAG, "BOOT_COMPLETED received — starting AudioBalanceService")
        val serviceIntent = Intent(context, AudioBalanceService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
