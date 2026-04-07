package com.audiobalance.app.ui.state

data class DeviceUiState(
    val mac: String,
    val name: String,
    val balance: Float,          // -100f to +100f
    val autoApplyEnabled: Boolean,
    val isConnected: Boolean,
    val gainOffset: Float = 0f    // dB, -12f to 0f
)

data class DeviceListUiState(
    val devices: List<DeviceUiState> = emptyList(),
    val isLoading: Boolean = true
)

data class ServiceState(
    val connectedDeviceMac: String? = null,
    val connectedDeviceName: String? = null,
    val currentBalance: Float = 0f,
    val currentGainOffset: Float = 0f
)
