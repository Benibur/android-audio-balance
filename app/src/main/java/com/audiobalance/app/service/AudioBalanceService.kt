package com.audiobalance.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.audiobalance.app.MainActivity
import com.audiobalance.app.data.BalanceRepository
import com.audiobalance.app.ui.state.ServiceState
import com.audiobalance.app.util.BalanceMapper
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AudioBalanceService"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "audio_balance_service"

@RequiresApi(Build.VERSION_CODES.P)
class AudioBalanceService : LifecycleService() {

    companion object {
        private val _stateFlow = MutableStateFlow(ServiceState())
        val stateFlow: StateFlow<ServiceState> = _stateFlow.asStateFlow()
    }

    private var dp: DynamicsProcessing? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var btReceiver: BtA2dpReceiver? = null
    private var disconnectJob: Job? = null
    private var reconnectJob: Job? = null
    private lateinit var balanceRepository: BalanceRepository
    private var currentDeviceMac: String? = null
    private var currentDeviceName: String? = null

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

        balanceRepository = BalanceRepository(applicationContext)
        registerBtReceiver()
        checkCurrentlyConnectedDevices()

        updateNotification("En attente de connexion BT")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // intent can be null when START_STICKY restarts the service after being killed
        super.onStartCommand(intent, flags, startId)

        if (intent?.getStringExtra("action") == "seed_balance") {
            val balance = intent.getFloatExtra("balance", 0f)
            val mac = currentDeviceMac
            if (mac != null) {
                serviceScope.launch {
                    balanceRepository.saveBalance(mac, balance)
                    // Apply immediately without requiring BT reconnect
                    val (leftDb, rightDb) = BalanceMapper.toGainDb(balance.roundToInt())
                    dp?.let {
                        it.setInputGainbyChannel(0, leftDb)
                        it.setInputGainbyChannel(1, rightDb)
                    }
                    _stateFlow.value = _stateFlow.value.copy(currentBalance = balance)
                    updateNotification(formatNotificationText(currentDeviceName, balance.roundToInt()))
                    Log.d(TAG, "Applied balance=$balance for mac=$mac (L=${leftDb}dB R=${rightDb}dB)")
                }
            } else {
                Log.w(TAG, "No device connected — cannot apply balance")
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        unregisterBtReceiver()
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
    // Bluetooth receiver
    // ================================================================

    private fun registerBtReceiver() {
        val filter = IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        btReceiver = BtA2dpReceiver { device, state -> handleBtEvent(device, state) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(btReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(btReceiver, filter)
        }
        Log.d(TAG, "BT A2DP receiver registered")
    }

    private fun unregisterBtReceiver() {
        btReceiver?.let {
            try { unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
        }
        btReceiver = null
    }

    // ================================================================
    // BT event handling
    // ================================================================

    private fun handleBtEvent(device: BluetoothDevice, state: Int) {
        val mac = device.address
        val name = if (hasBluetoothConnectPermission()) device.name else null
        Log.d(TAG, "BT event: state=$state mac=$mac name=$name")

        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                disconnectJob?.cancel()   // cancel pending reset (micro-disconnect protection)
                reconnectJob?.cancel()
                currentDeviceMac = mac
                currentDeviceName = name
                _stateFlow.value = ServiceState(connectedDeviceMac = mac, connectedDeviceName = name, currentBalance = 0f)
                reconnectJob = serviceScope.launch {
                    delay(1000L)  // 1s delay — let BT audio routing stabilize
                    applyDeviceBalance(device)
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                reconnectJob?.cancel()
                disconnectJob?.cancel()
                disconnectJob = serviceScope.launch {
                    delay(2000L)  // 2s delay — cancel if reconnect arrives
                    resetBalanceToCenter()
                    currentDeviceMac = null
                    currentDeviceName = null
                    _stateFlow.value = ServiceState()  // back to defaults
                    updateNotification("No device connected")
                    Log.d(TAG, "Balance reset to center after disconnect timeout")
                }
            }
        }
    }

    private suspend fun applyDeviceBalance(device: BluetoothDevice) {
        val mac = device.address
        val deviceName = if (hasBluetoothConnectPermission()) device.name else null

        val autoApply = balanceRepository.getAutoApply(mac)
        if (!autoApply) {
            // Register device as known (save name + preserve balance), but skip DP gain
            val balance = balanceRepository.getBalance(mac)
            balanceRepository.saveBalance(mac, balance)
            deviceName?.let { balanceRepository.saveDeviceName(mac, it) }
            _stateFlow.value = ServiceState(connectedDeviceMac = mac, connectedDeviceName = deviceName, currentBalance = 0f)
            updateNotification(formatNotificationText(deviceName, 0))
            Log.d(TAG, "AutoApply disabled for mac=$mac — skipping balance")
            return
        }

        val balance = balanceRepository.getBalance(mac)  // 0f for unknown
        // Save unknown devices with balance 0 to make them "known"
        balanceRepository.saveBalance(mac, balance)
        deviceName?.let { balanceRepository.saveDeviceName(mac, it) }

        val (leftDb, rightDb) = BalanceMapper.toGainDb(balance.toInt())
        dp?.let {
            it.setInputGainbyChannel(0, leftDb)
            it.setInputGainbyChannel(1, rightDb)
            Log.d(TAG, "Balance applied: mac=$mac balance=${balance.toInt()} L=${leftDb}dB R=${rightDb}dB")
        }

        _stateFlow.value = ServiceState(connectedDeviceMac = mac, connectedDeviceName = deviceName, currentBalance = balance)
        updateNotification(formatNotificationText(deviceName, balance.toInt()))
    }

    private fun resetBalanceToCenter() {
        dp?.let {
            try {
                it.setInputGainbyChannel(0, 0f)
                it.setInputGainbyChannel(1, 0f)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Reset to center failed: ${e.message}")
            }
        }
    }

    private fun checkCurrentlyConnectedDevices() {
        if (!hasBluetoothConnectPermission()) {
            Log.d(TAG, "No BLUETOOTH_CONNECT permission — skipping connected device check")
            return
        }
        val bluetoothManager = getSystemService(android.bluetooth.BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter ?: return
        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.A2DP) return
                val connected = proxy.connectedDevices
                connected.firstOrNull()?.let { device ->
                    Log.d(TAG, "Already connected: ${device.address}")
                    currentDeviceMac = device.address
                    currentDeviceName = if (hasBluetoothConnectPermission()) device.name else null
                    _stateFlow.value = ServiceState(
                        connectedDeviceMac = device.address,
                        connectedDeviceName = if (hasBluetoothConnectPermission()) device.name else null,
                        currentBalance = 0f
                    )
                    serviceScope.launch {
                        delay(1000L)  // same 1s delay for routing stability
                        applyDeviceBalance(device)
                    }
                }
                adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
            }
            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
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
}
