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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the foreground service (permissions are now handled by PermissionScreen)
        val serviceIntent = Intent(this, AudioBalanceService::class.java)
        startForegroundService(serviceIntent)

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
