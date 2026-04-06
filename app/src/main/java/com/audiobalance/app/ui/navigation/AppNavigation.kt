package com.audiobalance.app.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.audiobalance.app.ui.screens.PermissionScreen

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()

    val allPermissionsGranted = remember {
        val btGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        btGranted && notifGranted
    }

    val startDestination = if (allPermissionsGranted) "device_list" else "permissions"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("permissions") {
            PermissionScreen(
                onAllGranted = {
                    navController.navigate("device_list") {
                        popUpTo("permissions") { inclusive = true }
                    }
                }
            )
        }
        composable("device_list") {
            // Placeholder — Plan 03 will replace this with DeviceListScreen
            Text("Device List — loading...")
        }
    }
}
