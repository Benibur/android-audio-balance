package com.audiobalance.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.audiobalance.app.R
import com.audiobalance.app.ui.state.PermissionUiState

@Composable
fun PermissionScreen(onAllGranted: () -> Unit) {
    var permissionState by remember { mutableStateOf(PermissionUiState()) }
    val context = LocalContext.current

    // Check initial permission state
    LaunchedEffect(Unit) {
        val btGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        permissionState = PermissionUiState(
            bluetoothGranted = btGranted,
            notificationGranted = notifGranted,
            allGranted = btGranted && notifGranted
        )
        if (btGranted && notifGranted) onAllGranted()
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = permissionState.copy(
            notificationGranted = granted,
            allGranted = permissionState.bluetoothGranted && granted,
            showDenialState = !granted
        )
        if (permissionState.bluetoothGranted && granted) onAllGranted()
    }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = permissionState.copy(
            bluetoothGranted = granted,
            showDenialState = !granted
        )
        if (granted) {
            // BT granted, now request notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                permissionState = permissionState.copy(notificationGranted = true, allGranted = true)
                onAllGranted()
            }
        }
    }

    fun requestPermissions() {
        permissionState = permissionState.copy(showDenialState = false)
        if (!permissionState.bluetoothGranted) {
            btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else if (!permissionState.notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)   // md horizontal
            .padding(top = 48.dp, bottom = 32.dp),  // 2xl top, xl bottom
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Icon: Bluetooth at 64dp, colorScheme.primary
        Icon(
            imageVector = Icons.Outlined.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp)) // lg

        // Title
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp)) // md

        // Body text — normal or denial state
        if (permissionState.showDenialState) {
            Text(
                text = stringResource(R.string.permission_denied_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp)) // lg

        // Permission items
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bluetooth Connect item
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp), // sm-plus
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.BluetoothConnected,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.permission_bluetooth_label),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Notifications item
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp), // sm-plus
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.permission_notification_label),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Push button to bottom
        Spacer(modifier = Modifier.weight(1f))

        // CTA button — normal or denial state
        if (permissionState.showDenialState) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { openAppSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.permission_open_settings))
                }
                OutlinedButton(
                    onClick = { requestPermissions() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.permission_retry))
                }
            }
        } else {
            Button(
                onClick = { requestPermissions() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.permission_cta))
            }
        }
    }
}
