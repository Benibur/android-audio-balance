package com.audiobalance.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.audiobalance.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val TAG = "AudioBalanceService"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "audio_balance_service"

@RequiresApi(Build.VERSION_CODES.P)
class AudioBalanceService : LifecycleService() {

    private var dp: DynamicsProcessing? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Starting..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
        }

        createDpInstance()

        // TODO Plan 02: check currently connected devices
        updateNotification("En attente de connexion BT")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // intent can be null when START_STICKY restarts the service after being killed
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        releaseDP()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ================================================================
    // DynamicsProcessing lifecycle
    // ================================================================

    private fun createDpInstance() {
        val config = DynamicsProcessing.Config.Builder(
            0, 2,
            false, 0,   // preEqInUse MUST be false
            false, 0,   // mbcInUse MUST be false
            false, 0,   // postEqInUse MUST be false
            false       // limiterInUse MUST be false
        ).build()

        dp = try {
            val instance = DynamicsProcessing(0, 0, config)
            val enableResult = instance.setEnabled(true)
            val hasControl = instance.hasControl()
            Log.d(TAG, "DP session=0: setEnabled=$enableResult hasControl=$hasControl")
            if (!hasControl) {
                instance.setEnabled(false)
                instance.release()
                Log.w(TAG, "DP created but hasControl=false — releasing")
                null
            } else {
                instance
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "DP creation failed: ${e.message}")
            null
        }
    }

    private fun releaseDP() {
        dp?.let {
            try { it.setEnabled(false) } catch (_: RuntimeException) {}
            try { it.release() } catch (_: RuntimeException) {}
        }
        dp = null
    }

    // ================================================================
    // Notification helpers
    // ================================================================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Balance Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Audio Balance")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun updateNotification(contentText: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    fun formatNotificationText(deviceName: String?, balance: Int): String {
        val name = deviceName?.takeIf { it.isNotBlank() } ?: "BT Device"
        val balanceText = when {
            balance > 0  -> "R+${balance}%"
            balance < 0  -> "L+${-balance}%"
            else         -> "Center"
        }
        return "$name \u2022 Balance: $balanceText"
    }

    // TODO Plan 02: register BT receiver
}
