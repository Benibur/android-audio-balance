package com.audiobalance.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.audiobalance.app.service.AudioBalanceService
import com.audiobalance.app.ui.navigation.AppNavigation
import com.audiobalance.app.ui.theme.AudioBalanceTheme

class MainActivity : ComponentActivity() {
    private fun hasBluetoothConnectPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Service is started AFTER permissions are granted — see AppNavigation onAllGranted
        // If permissions are already granted (not first launch), start immediately
        if (hasBluetoothConnectPermission()) {
            startForegroundService(Intent(this, AudioBalanceService::class.java))
        }

        setContent {
            AudioBalanceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
