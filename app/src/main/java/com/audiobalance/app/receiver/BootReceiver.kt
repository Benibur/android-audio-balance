package com.audiobalance.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.audiobalance.app.service.AudioBalanceService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "BOOT_COMPLETED received — starting AudioBalanceService")
        val serviceIntent = Intent(context, AudioBalanceService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
