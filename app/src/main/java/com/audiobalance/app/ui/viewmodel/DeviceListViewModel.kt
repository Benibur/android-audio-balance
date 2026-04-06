package com.audiobalance.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audiobalance.app.data.BalanceRepository
import com.audiobalance.app.service.AudioBalanceService
import com.audiobalance.app.ui.state.DeviceListUiState
import com.audiobalance.app.ui.state.DeviceUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class DeviceListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BalanceRepository(application)
    private val _sliderOverrides = MutableStateFlow<Map<String, Float>>(emptyMap())

    val uiState: StateFlow<DeviceListUiState> = combine(
        AudioBalanceService.stateFlow,
        repository.getAllDevicesFlow(),
        _sliderOverrides
    ) { serviceState, devices, overrides ->
        val deviceList = devices.map { (mac, balance, autoApply) ->
            val isConnected = mac == serviceState.connectedDeviceMac
            val displayBalance = overrides[mac] ?: balance
            DeviceUiState(
                mac = mac,
                name = repository.getDeviceName(mac) ?: mac,
                balance = displayBalance,
                autoApplyEnabled = autoApply,
                isConnected = isConnected
            )
        }.sortedWith(compareByDescending<DeviceUiState> { it.isConnected }.thenBy { it.name })

        DeviceListUiState(devices = deviceList, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceListUiState())

    private var lastSendTimestamp = 0L

    private fun isConnectedDevice(mac: String): Boolean {
        return AudioBalanceService.stateFlow.value.connectedDeviceMac == mac
    }

    fun onSliderChange(mac: String, value: Float) {
        // Update local override immediately for smooth UI
        _sliderOverrides.value = _sliderOverrides.value + (mac to value)

        // Only send to service if this is the currently connected device
        if (isConnectedDevice(mac)) {
            val now = System.currentTimeMillis()
            if (now - lastSendTimestamp >= 50) {
                lastSendTimestamp = now
                sendBalanceToService(value)
            }
        }
    }

    fun onSliderFinished(mac: String, rawValue: Float) {
        // Magnetic snap: if within +/-3 of center, snap to 0
        val snappedValue = if (abs(rawValue) <= 3f) 0f else rawValue

        // Clear override, let repository flow take over
        _sliderOverrides.value = _sliderOverrides.value - mac

        viewModelScope.launch {
            repository.saveBalance(mac, snappedValue)
            // Only apply audio if this is the currently connected device
            if (isConnectedDevice(mac)) {
                sendBalanceToService(snappedValue)
            }
        }
    }

    fun onAutoApplyToggle(mac: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.saveAutoApply(mac, enabled)
            // Only affect audio if this is the currently connected device
            if (isConnectedDevice(mac)) {
                if (enabled) {
                    val balance = repository.getBalance(mac)
                    sendBalanceToService(balance)
                } else {
                    sendResetToService()
                }
            }
        }
    }

    private fun sendBalanceToService(balance: Float) {
        val context = getApplication<Application>()
        android.util.Log.d("DeviceListViewModel", "sendBalanceToService: balance=$balance")
        val intent = Intent(context, AudioBalanceService::class.java).apply {
            putExtra("action", "seed_balance")
            putExtra("balance", balance)
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            android.util.Log.e("DeviceListViewModel", "startForegroundService failed: ${e.message}")
        }
    }

    private fun sendResetToService() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioBalanceService::class.java).apply {
            putExtra("action", "reset_audio_only")
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            android.util.Log.e("DeviceListViewModel", "startForegroundService failed: ${e.message}")
        }
    }
}
