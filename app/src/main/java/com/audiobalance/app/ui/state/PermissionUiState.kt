package com.audiobalance.app.ui.state

data class PermissionUiState(
    val bluetoothGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val showDenialState: Boolean = false,
    val allGranted: Boolean = false
)
