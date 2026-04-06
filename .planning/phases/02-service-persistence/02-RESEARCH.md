# Phase 2: Service + Persistence - Research

**Researched:** 2026-04-04
**Domain:** Android Foreground Service, Bluetooth A2DP, DynamicsProcessing lifecycle, DataStore persistence
**Confidence:** HIGH (core FGS + BT APIs), MEDIUM (background start exemption nuances on API 36)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Foreground service type:** `connectedDevice` (per REQUIREMENTS.md SVC-01 and CONTEXT.md)
- **Notification format:** `"{device_name} • Balance: L+{value}%"` (or `R+{value}%` or `Center`) — minimal, no quick actions
- **Notification visibility:** Hidden (service stopped) when no BT A2DP is connected; service restarts on next BT connect
- **Disconnect behavior:** 2-second delay after A2DP disconnect broadcast before resetting balance to center. If device reconnects within 2s, cancel reset and keep balance active.
- **Reconnect delay:** 1-second delay after A2DP connect broadcast before applying balance (let BT audio routing stabilize)
- **DP release on disconnect:** Do NOT release DynamicsProcessing on disconnect — reset to center (0dB/0dB). DP is held for the full service lifetime.
- **DP creation:** Create DP instance on service `onCreate()`, not on first BT connect. This ensures the effect exists before any media app queries AudioFlinger.
- **Service restart:** `START_STICKY` — system restarts automatically after kill
- **Boot start:** BOOT_COMPLETED receiver starts the service (LIFE-01 included in Phase 2 by user decision)
- **Unknown device:** New unknown MAC → save with balance 0 (center), apply center balance
- **Balance range:** User-facing -100 to +100 (0 = center); internal dB mapping is Claude's decision
- **Architecture:** Single `DynamicsProcessing(0, 0, config)` held in foreground service member field — confirmed from Phase 1
- **POC code pattern locked:** constructor, Config.Builder params, `setInputGainbyChannel` calls — see POC-RESULTS.md and AudioEffectPoc.kt

### Claude's Discretion
- Choice between DataStore Preferences and SharedPreferences for persistence
- Exact dB mapping formula for -100/+100 → dB
- FGS background start strategy for BT connect broadcast (workaround for non-exempted broadcast)
- Structure of code (packages, classes, interfaces)
- Coroutine scope and delayed-task implementation (Handler.postDelayed vs coroutine Job)

### Deferred Ideas (OUT OF SCOPE)
- Toggle global on/off kill switch (v2, LIFE-02)
- Action rapide dans la notification (v2, UIX-04)
- Quick Settings Tile (v2, ADV-01)
- Export/import JSON (v2, DATA-03, DATA-04)
- POC code refactoring — Phase 2 writes service fresh, POC is read-only reference
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| AUDIO-02 | Balance coefficient applied system-wide when BT device is connected | DynamicsProcessing session 0 pattern from Phase 1 — confirmed working |
| BT-01 | Auto-detect A2DP connections (MAC address) | `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` broadcast + `BluetoothDevice.EXTRA_DEVICE` |
| BT-02 | Auto-detect A2DP disconnections | Same broadcast, `BluetoothProfile.STATE_DISCONNECTED` state constant |
| BT-03 | Auto-apply stored balance on BT device connect | DataStore read by MAC key → `applyBalance()` call after 1s delay |
| SVC-01 | Foreground service (`connectedDevice`) maintains app alive in background | `android:foregroundServiceType="connectedDevice"` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission |
| SVC-02 | Persistent notification shows connected device and active balance | `NotificationCompat.Builder` with `IMPORTANCE_LOW` channel, updated via `notificationManager.notify()` |
| DATA-01 | Balance profiles stored persistently (MAC → coefficient) | DataStore Preferences: `stringPreferencesKey("balance_$mac")`, float stored as string |
| DATA-02 | Profiles survive app restart and phone reboot | DataStore file persists across kills; BOOT_COMPLETED receiver restarts service |
</phase_requirements>

---

## Summary

Phase 2 builds a headless foreground service that monitors Bluetooth A2DP connections, persists per-device balance coefficients using DataStore Preferences, and applies the confirmed `DynamicsProcessing(0, 0, config)` pattern from Phase 1 automatically when a known device connects.

The core challenge is the Android 12+ background start restriction: **Bluetooth A2DP connection broadcasts are NOT listed as an exemption** for starting a foreground service from the background. The solution is to keep the service running persistently (START_STICKY + BOOT_COMPLETED) rather than attempting to restart it from the BT broadcast. The BT receiver is registered dynamically inside the running service, eliminating the background start problem entirely.

The DynamicsProcessing instance is created in `Service.onCreate()` (before any BT event), held for the service lifetime, and never released on BT disconnect — only reset to center. This is the critical architectural decision from Phase 1 findings: the effect must pre-exist before media apps start.

**Primary recommendation:** Use a persistent foreground service registered at boot via BOOT_COMPLETED (which IS an exemption). Register the BT A2DP receiver dynamically inside the service. Use DataStore Preferences 1.2.1 for persistence. Use Kotlin coroutines with a `CoroutineScope(SupervisorJob() + Dispatchers.Main)` manually cancelled in `onDestroy()` for all async work including the 2s/1s delayed handlers.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `android.bluetooth.BluetoothA2dp` | platform | A2DP connection monitoring | Only API for A2DP profile state |
| `android.media.audiofx.DynamicsProcessing` | platform (API 28+) | Global balance effect | Validated in Phase 1 — confirmed working |
| `androidx.datastore:datastore-preferences` | 1.2.1 | Persistent MAC → balance storage | Async, coroutine-native, no UI-thread I/O risk |
| `androidx.lifecycle:lifecycle-service` | 2.10.0 | LifecycleService base class for coroutine scope | Provides `lifecycleScope` in Service context |
| `kotlinx-coroutines-android` | bundled via lifecycle | Coroutine dispatcher + delay | Cancelable async for 2s/1s delays |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.core:core-ktx` | 1.15.0 (already in project) | Notification builder helpers, ContextCompat | Always |
| `NotificationCompat` | via core-ktx | Foreground notification builder | Required for foreground service on all API levels |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| DataStore Preferences | SharedPreferences | SharedPreferences has synchronous disk I/O, ANR risk on slow storage; DataStore is the current Android recommendation |
| LifecycleService | Plain Service | Plain Service works fine but requires manual CoroutineScope setup; LifecycleService provides `lifecycleScope` automatically |
| Dynamic BT receiver (in-service) | Manifest-declared BT receiver | Manifest receiver cannot start FGS from background on Android 12+ — dynamic registration is the required pattern |

**Version verification:** DataStore 1.2.1 confirmed current stable (released March 11, 2026 via official docs). LifecycleService 2.10.0 confirmed current stable (November 2025). DataStore 1.3.0-alpha07 exists as alpha with encryption but is not appropriate for production use here.

**Installation additions (gradle/libs.versions.toml):**
```toml
[versions]
datastore = "1.2.1"
lifecycleService = "2.10.0"

[libraries]
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycleService" }
```

**app/build.gradle.kts additions:**
```kotlin
implementation(libs.androidx.datastore.preferences)
implementation(libs.androidx.lifecycle.service)
```

---

## Architecture Patterns

### Recommended Project Structure
```
com.audiobalance.app/
├── service/
│   ├── AudioBalanceService.kt   # foreground service — DP + BT monitoring
│   └── BtA2dpReceiver.kt        # dynamically-registered BT A2DP receiver
├── data/
│   ├── BalanceRepository.kt     # DataStore read/write, single source of truth
│   └── DevicePreferences.kt     # DataStore file delegate (top-level)
├── receiver/
│   └── BootReceiver.kt          # BOOT_COMPLETED → starts AudioBalanceService
└── util/
    └── BalanceMapper.kt         # -100..+100 to dB conversion
```

Note: `poc/` package and files remain untouched — Phase 2 writes fresh code.

### Pattern 1: Foreground Service with connectedDevice Type

**What:** Service declared with `android:foregroundServiceType="connectedDevice"` that calls `startForeground()` with a notification and the `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` flag.

**When to use:** Always — this is the only valid foreground service type for Bluetooth-triggered background work.

**Manifest declaration:**
```xml
<!-- AndroidManifest.xml additions -->

<!-- Permissions -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Service -->
<service
    android:name=".service.AudioBalanceService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false" />

<!-- Boot receiver -->
<receiver
    android:name=".receiver.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

**Service skeleton:**
```kotlin
// Source: Official Android FGS documentation + lifecycle-service docs
class AudioBalanceService : LifecycleService() {

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "audio_balance_service"

    private var dp: DynamicsProcessing? = null
    private var btReceiver: BtA2dpReceiver? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var disconnectJob: Job? = null
    private var reconnectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("No device"), 
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        createDpInstance()
        registerBtReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBtReceiver()
        releaseDp()
        serviceScope.cancel()
    }
}
```

### Pattern 2: Dynamic BT Receiver Registration (CRITICAL — background start workaround)

**What:** The BT A2DP receiver is registered inside `Service.onCreate()` via `registerReceiver()`, NOT declared in the manifest for this action. This is the only pattern that avoids the Android 12+ background start restriction.

**Why this matters:** `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` is NOT in the Android 12+ exemption list for starting foreground services from the background. If the service is started at boot (BOOT_COMPLETED is exempted), it runs persistently with START_STICKY. When a BT event arrives, the service is already running — it simply handles the intent in-process. No background start is ever attempted.

**When to use:** Always — this replaces any manifest-declared BT receiver that tries to `startForegroundService()`.

```kotlin
// Inside AudioBalanceService
private fun registerBtReceiver() {
    val filter = IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
    btReceiver = BtA2dpReceiver { device, state -> handleBtEvent(device, state) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(btReceiver, filter, RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(btReceiver, filter)
    }
}

private fun unregisterBtReceiver() {
    btReceiver?.let { 
        try { unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
    }
    btReceiver = null
}
```

### Pattern 3: BT Event Handling with Coroutine Delays

**What:** 2-second delay on disconnect (cancel if reconnect arrives), 1-second delay on reconnect before applying balance.

**When to use:** Always — as decided in CONTEXT.md.

```kotlin
// Source: Kotlin coroutines best practices + Android Service coroutine patterns
private fun handleBtEvent(device: BluetoothDevice, state: Int) {
    when (state) {
        BluetoothProfile.STATE_CONNECTED -> {
            disconnectJob?.cancel()  // cancel pending reset if reconnect within 2s
            reconnectJob?.cancel()
            reconnectJob = serviceScope.launch {
                delay(1000L)  // wait 1s for BT audio routing to stabilize
                applyDeviceBalance(device)
            }
        }
        BluetoothProfile.STATE_DISCONNECTED -> {
            reconnectJob?.cancel()
            disconnectJob?.cancel()
            disconnectJob = serviceScope.launch {
                delay(2000L)  // wait 2s before resetting
                resetBalanceToCenter()
                updateNotification("No device")
            }
        }
    }
}
```

### Pattern 4: BluetoothA2dp Extras Extraction

**What:** Correctly reading device MAC and state from the broadcast intent.

```kotlin
// Source: Android BluetoothA2dp API reference
class BtA2dpReceiver(private val onEvent: (BluetoothDevice, Int) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) return
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (device != null && state != -1) {
            onEvent(device, state)
        }
    }
}

// Device name and MAC address (requires BLUETOOTH_CONNECT permission at runtime on API 31+)
// The BLUETOOTH_CONNECT permission is a runtime permission — must be checked before access
val mac: String = device.address    // e.g. "AA:BB:CC:DD:EE:FF"
val name: String? = if (hasBluetoothConnectPermission()) device.name else null
```

### Pattern 5: DataStore Preferences for MAC → Balance Storage

**What:** Single DataStore instance, keyed by MAC address, storing balance as Float (−100f to +100f).

```kotlin
// DevicePreferences.kt — top-level file (not inside a class)
// Source: Official Android DataStore documentation
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_balance")

// BalanceRepository.kt
class BalanceRepository(private val context: Context) {

    private fun balanceKey(mac: String) = floatPreferencesKey("balance_${mac.replace(":", "_")}")

    suspend fun getBalance(mac: String): Float {
        return context.dataStore.data
            .map { prefs -> prefs[balanceKey(mac)] ?: 0f }
            .first()
    }

    suspend fun saveBalance(mac: String, balance: Float) {
        context.dataStore.edit { prefs ->
            prefs[balanceKey(mac)] = balance
        }
    }

    // For Phase 3 UI: observe all known devices
    fun getAllBalances(): Flow<Map<String, Float>> = context.dataStore.data.map { prefs ->
        prefs.asMap()
            .filterKeys { it.name.startsWith("balance_") }
            .mapKeys { (key, _) -> key.name.removePrefix("balance_").replace("_", ":") }
            .mapValues { (_, value) -> (value as? Float) ?: 0f }
    }
}
```

### Pattern 6: DynamicsProcessing Lifecycle in Service

**What:** DP created in `onCreate()`, reset (not released) on disconnect, released only in `onDestroy()`. When service is restarted (START_STICKY), `onCreate()` recreates DP automatically.

```kotlin
// Source: POC-RESULTS.md confirmed pattern — Phase 1 finding
@RequiresApi(Build.VERSION_CODES.P)
private fun createDpInstance() {
    val config = DynamicsProcessing.Config.Builder(
        0, 2,        // variant=default, channelCount=stereo
        false, 0,    // preEqInUse=false, preEqBandCount=0  ← MUST be false or silence
        false, 0,    // mbcInUse=false, mbcBandCount=0      ← MUST be false or silence
        false, 0,    // postEqInUse=false, postEqBandCount=0 ← MUST be false or silence
        false        // limiterInUse=false                  ← MUST be false or silence
    ).build()
    dp = try {
        val instance = DynamicsProcessing(0, 0, config)
        val enableResult = instance.setEnabled(true)
        val hasControl = instance.hasControl()
        Log.d(TAG, "DP session=0: setEnabled=$enableResult hasControl=$hasControl")
        if (!hasControl) { instance.setEnabled(false); instance.release(); null }
        else instance
    } catch (e: RuntimeException) {
        Log.e(TAG, "DP creation failed: ${e.message}")
        null
    }
}

private fun releaseDp() {
    dp?.let {
        try { it.setEnabled(false) } catch (_: RuntimeException) {}
        try { it.release() } catch (_: RuntimeException) {}
    }
    dp = null
}

// Reset to center WITHOUT releasing — called on BT disconnect after 2s delay
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
```

### Pattern 7: Balance Mapping (-100..+100 to dB)

**What:** Convert user-facing integer (-100 to +100) to dB gains for left and right channels.

**Mapping formula:** Same as POC — 0f on the dominant channel, up to -60f on the attenuated channel. The POC used `balanceFraction` in [-1.0, +1.0]. For user-facing [-100, +100]:

```kotlin
// BalanceMapper.kt
// Source: POC-RESULTS.md applyBalance() — adapted for integer input range
object BalanceMapper {
    // balance: -100 (full left) to +100 (full right), 0 = center
    fun toGainDb(balance: Int): Pair<Float, Float> {
        val fraction = balance / 100f  // normalise to [-1.0, +1.0]
        val leftDb  = if (fraction > 0) -60f * fraction else 0f
        val rightDb = if (fraction < 0) -60f * (-fraction) else 0f
        return Pair(leftDb, rightDb)
    }
}

// Usage in service
fun applyBalance(balance: Int) {
    val (leftDb, rightDb) = BalanceMapper.toGainDb(balance)
    dp?.let {
        it.setInputGainbyChannel(0, leftDb)
        it.setInputGainbyChannel(1, rightDb)
    }
}
```

### Pattern 8: Notification Channel and Update

**What:** Create channel once in `onCreate()`. Update content by rebuilding the notification and calling `notificationManager.notify()` with the same ID — the channel is not recreated.

```kotlin
private fun createNotificationChannel() {
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Audio Balance Service",
        NotificationManager.IMPORTANCE_LOW  // silent, no sound/vibration
    ).apply {
        description = "Shows connected Bluetooth device and active balance"
        setShowBadge(false)
    }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

private fun buildNotification(contentText: String): Notification {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    val pendingIntent = PendingIntent.getActivity(
        this, 0, intent,
        PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)  // Phase 3 will update icon
        .setContentTitle("Audio Balance")
        .setContentText(contentText)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .build()
}

// To update notification content (no channel recreation):
private fun updateNotification(contentText: String) {
    val nm = getSystemService(NotificationManager::class.java)
    nm.notify(NOTIFICATION_ID, buildNotification(contentText))
}

// Format: "Sony WH-1000XM5 • Balance: L+30%"
private fun formatNotificationText(deviceName: String?, balance: Int): String {
    val name = deviceName ?: "BT Device"
    val balanceText = when {
        balance > 0 -> "R+${balance}%"
        balance < 0 -> "L+${-balance}%"
        else -> "Center"
    }
    return "$name • Balance: $balanceText"
}
```

### Pattern 9: BOOT_COMPLETED Receiver

**What:** Starts AudioBalanceService after boot. `ACTION_BOOT_COMPLETED` is explicitly an exemption for background FGS start on all Android versions.

**Key finding:** `connectedDevice` type is NOT in the restricted-from-BOOT_COMPLETED list (only camera, dataSync, mediaPlayback, mediaProjection, phoneCall, microphone are restricted). This is confirmed safe.

```kotlin
// BootReceiver.kt
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val serviceIntent = Intent(context, AudioBalanceService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
```

### Pattern 10: Check Currently Connected A2DP Devices at Service Start

**What:** When the service starts (e.g., at boot with headphones already connected), check existing A2DP connections using `BluetoothA2dp` proxy.

**Important:** This requires `BLUETOOTH_CONNECT` runtime permission AND involves async proxy binding.

```kotlin
// Inside AudioBalanceService.onCreate() — after registerBtReceiver()
private fun checkCurrentlyConnectedDevices() {
    if (!hasBluetoothConnectPermission()) return
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
    adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.A2DP) return
            val a2dp = proxy as BluetoothA2dp
            val connected = a2dp.connectedDevices  // requires BLUETOOTH_CONNECT
            connected.firstOrNull()?.let { device ->
                serviceScope.launch {
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
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == 
            PackageManager.PERMISSION_GRANTED
    } else true  // API 26-30: old BLUETOOTH permission, no runtime grant needed
}
```

### Anti-Patterns to Avoid

- **Manifest-declared BT receiver calling `startForegroundService()`:** This will throw `ForegroundServiceStartNotAllowedException` on Android 12+ because BT connection broadcasts are NOT an exemption.
- **Storing DP in a companion object or Application class:** Use the service member field. The service lifecycle is the correct owner.
- **Creating DP on each BT connect instead of once in `onCreate()`:** The POC proved the effect must pre-exist for cold-start apps. Create once in `onCreate()`, never recreate unless `hasControl()` returns false.
- **Enabling any DynamicsProcessing stage (preEq, mbc, postEq, limiter):** Any stage set to `inUse=true` with zero bands silences the global mix. All four MUST be false.
- **Reading DataStore synchronously:** DataStore has no synchronous API. Always use `suspend` or collect as Flow.
- **Starting FGS from BT disconnect/reconnect broadcast receiver declared in manifest:** Unnecessary and broken. The service is already running persistently.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Delayed task cancellation | Custom timer/flag mechanism | `Job` from Kotlin coroutines + `cancel()` | Coroutine Jobs are structurally tied to scope, automatically cleaned up on scope cancel |
| Key-value persistence | File-based storage or SQLite | DataStore Preferences | Handles concurrent writes, async I/O, no ANR risk |
| Foreground service notification | Raw `Notification.Builder` for API split | `NotificationCompat.Builder` | Handles API-level differences, IMPORTANCE constants |
| Bluetooth permission checks | Hardcoded version checks | `ContextCompat.checkSelfPermission()` | Handles both old `BLUETOOTH` and new `BLUETOOTH_CONNECT` |

**Key insight:** The background start restriction is the most dangerous pitfall. Don't try to work around it with timers, JobScheduler or WorkManager — just keep the service persistent via START_STICKY + BOOT_COMPLETED. The restriction is cleanly avoided by never stopping the service in the first place.

---

## Common Pitfalls

### Pitfall 1: BT Broadcast Cannot Restart FGS (Android 12+)

**What goes wrong:** A manifest-declared `BroadcastReceiver` for `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` calls `context.startForegroundService(intent)`. On Android 12+, the OS throws `ForegroundServiceStartNotAllowedException`. The app crashes silently in the background.

**Why it happens:** `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` is not in the Android 12+ exemption list for background FGS starts. Only specific broadcasts (BOOT_COMPLETED, timezone change, etc.) are exempt.

**How to avoid:** Register the BT receiver dynamically inside the service. Keep service running persistently. Service started once at boot; BT events are handled inside it.

**Warning signs:** Silent failures in logcat with `ForegroundServiceStartNotAllowedException`, service not appearing after BT connect.

### Pitfall 2: DynamicsProcessing Config Silence Bug (from Phase 1)

**What goes wrong:** Any of `preEqInUse`, `mbcInUse`, `postEqInUse`, or `limiterInUse` set to `true` (even with 0 bands) silences the global audio mix completely when attached to session 0.

**Why it happens:** AudioFlinger activates the stage chain even with zero bands, producing silence rather than passthrough. This was observed and confirmed in Phase 1 Plan 02 Round 3.

**How to avoid:** All four flags MUST be `false`. Copy the Config.Builder from POC-RESULTS.md verbatim.

**Warning signs:** DP created and `setEnabled=0`, `hasControl=true` but audio is completely silent.

### Pitfall 3: DP Must Exist Before Media Apps Start

**What goes wrong:** DP created on BT connect (not in `onCreate()`). User connects headphones, opens Spotify, and the balance is not applied because the DP was created after Spotify registered its audio session.

**Why it happens:** The "activate BEFORE the media player" principle from Phase 1 — discovered via a third-party equalizer app's documentation. The DP on session 0 needs to exist in the AudioFlinger chain before media sessions are established.

**How to avoid:** Create `DynamicsProcessing(0, 0, config)` in `Service.onCreate()`. Always.

**Warning signs:** Balance works when service restarts but not on first-ever headphone connect after app install.

### Pitfall 4: BLUETOOTH_CONNECT Runtime Permission Not Checked

**What goes wrong:** `device.name` or `a2dp.connectedDevices` called without checking `BLUETOOTH_CONNECT` permission on API 31+. Throws `SecurityException` silently in the background.

**Why it happens:** API 31 introduced `BLUETOOTH_CONNECT` as a runtime permission (not just manifest-declared). Legacy code accessing BT device properties without this check crashes on Android 12+.

**How to avoid:** Always guard with `hasBluetoothConnectPermission()` before accessing `device.name` or `connectedDevices`. Phase 3 will handle the runtime permission UI (UI-05). For Phase 2, service should degrade gracefully if permission is absent (use "BT Device" as fallback name, skip initial connection check).

**Warning signs:** `SecurityException: Need android.permission.BLUETOOTH_CONNECT` in logcat.

### Pitfall 5: DataStore Multiple Instances Crash

**What goes wrong:** `preferencesDataStore(name = "device_balance")` delegate called in multiple files or classes. Throws `IllegalStateException: There are multiple DataStores active for the same file`.

**Why it happens:** The `preferencesDataStore` delegate creates a singleton, but if multiple extension properties are declared, multiple instances compete.

**How to avoid:** Declare the delegate ONCE in a dedicated top-level file (`DevicePreferences.kt`). Inject via `BalanceRepository` everywhere else.

**Warning signs:** `IllegalStateException` crash at `DataStore.data` access.

### Pitfall 6: START_STICKY Re-Delivery with Null Intent

**What goes wrong:** Service was killed by OS (low memory). OS restarts it with `START_STICKY`, calling `onStartCommand(intent=null, ...)`. Code that dereferences `intent` without null check crashes.

**Why it happens:** `START_STICKY` restarts the service without re-delivering the original intent — `intent` is null on automatic restart.

**How to avoid:** Always null-check `intent` in `onStartCommand`. The service re-initializes fully via `onCreate()` (DP, BT receiver), so the null intent is fine.

### Pitfall 7: Android 15 dataSync/mediaPlayback BOOT_COMPLETED Restriction (Not Us, But Adjacent)

**What goes wrong:** Developer reads "Android 15 restricts FGS from BOOT_COMPLETED" and incorrectly applies it to `connectedDevice` type.

**Why it happens:** The restriction in Android 15 targets only: camera, dataSync, mediaPlayback, mediaProjection, phoneCall (and microphone on Android 14). `connectedDevice` is NOT on this list.

**How to avoid:** Use `connectedDevice` type as planned. No workaround needed for BOOT_COMPLETED with this type.

---

## Code Examples

### Complete applyDeviceBalance() flow

```kotlin
// Source: POC-RESULTS.md + DataStore pattern
private suspend fun applyDeviceBalance(device: BluetoothDevice) {
    val mac = device.address
    val deviceName = if (hasBluetoothConnectPermission()) device.name else null

    // Load or create the stored balance
    val balance = balanceRepository.getBalance(mac)  // returns 0f for unknown devices

    // Save unknown devices (new device gets 0 by default)
    // BalanceRepository.getBalance() returns 0f if key absent — save it to make device "known"
    balanceRepository.saveBalance(mac, balance)

    // Apply to DP
    val (leftDb, rightDb) = BalanceMapper.toGainDb(balance.toInt())
    dp?.let {
        it.setInputGainbyChannel(0, leftDb)
        it.setInputGainbyChannel(1, rightDb)
        Log.d(TAG, "Balance applied: mac=$mac balance=$balance L=${leftDb}dB R=${rightDb}dB")
    }

    // Update notification
    updateNotification(formatNotificationText(deviceName, balance.toInt()))
}
```

### Complete manifest additions (copy-pasteable)

```xml
<!-- Add to existing AndroidManifest.xml — inside <manifest>, before <application> -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Add inside <application> -->
<service
    android:name=".service.AudioBalanceService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false" />

<receiver
    android:name=".receiver.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

### startForeground() call (API 29+ type flag required)

```kotlin
// Source: Official Android FGS documentation
// Must be called within 5 seconds of onStartCommand()
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(
        NOTIFICATION_ID,
        buildNotification("Starting..."),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
    )
} else {
    startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SharedPreferences for settings | DataStore Preferences | Android Jetpack 2020, stable 2021+ | No more ANR from disk I/O on main thread |
| Manifest-declared BT receiver → startFGS | Dynamic receiver inside persistent service | Android 12 (2021) | Background start restriction requires persistent service |
| Handler.postDelayed() for delays | CoroutineScope + `delay()` + `Job.cancel()` | Kotlin coroutines became standard ~2020 | Structured concurrency, no callback hell, easy cancellation |
| `BLUETOOTH` permission (API <31) | `BLUETOOTH_CONNECT` runtime permission (API 31+) | Android 12 (2021) | Runtime permission required for device name/address access |

**Deprecated/outdated:**
- `BluetoothAdapter.getDefaultAdapter()`: Deprecated in API 33 — use `context.getSystemService(BluetoothManager::class.java).adapter` instead. However, `getDefaultAdapter()` still works on all supported API levels (26-36). Use the new form for cleanliness.
- `intent.getParcelableExtra(key)` without class param: Deprecated in API 33 — use `getParcelableExtra(key, Class)` form on API 33+ with the old form for below 33.
- `VARIANT_FAVOR_FREQUENCY_RESOLUTION` in Config.Builder: Used in POC Plan 01 but replaced with `0` (default variant) for session 0 global. The default variant is the confirmed working pattern.

---

## Open Questions

1. **DP validity after BT reconnect with audio output route change**
   - What we know: POC-RESULTS.md suggests "recreate on reconnect" may be needed (Open Question #2)
   - What's unclear: Does the DP on session 0 remain valid after a BT audio route change, or does AudioFlinger invalidate it?
   - Recommendation: CONTEXT.md locked decision is to NOT release DP on disconnect (reset to center). But the service architecture (START_STICKY) means a service restart will recreate the DP. If DP becomes stale on reconnect, test by checking `hasControl()` after reconnect and recreating if false.

2. **Cold-start media app behavior (Open Question #1 from Phase 1)**
   - What we know: The Jazib Khan equalizer implies "activate before media app." Whether session 0 reaches apps started AFTER effect creation is unconfirmed.
   - What's unclear: Startup ordering in real use — headphones connect, service applies balance, user opens Spotify. Does session 0 global apply?
   - Recommendation: Test this scenario early in Phase 2 execution. The service architecture (DP created in `onCreate()` at boot) maximizes the chance the effect exists before any music app.

3. **BLUETOOTH_CONNECT runtime permission during Phase 2 (no UI yet)**
   - What we know: Phase 3 handles the permission request UI (UI-05). Phase 2 is headless.
   - What's unclear: In Phase 2 testing, the developer (Ben) will need to manually grant BLUETOOTH_CONNECT via adb or by running the app once from the foreground.
   - Recommendation: Add a `MainActivity` one-liner to request BLUETOOTH_CONNECT in Phase 2 for manual testing. Flag in PLAN.md that this is temporary — Phase 3 replaces it with proper UI.

4. **POST_NOTIFICATIONS runtime on API 33+**
   - What we know: On API 33+, `POST_NOTIFICATIONS` is a runtime permission. The FGS notification will still be posted even without it, but won't appear in the notification drawer.
   - What's unclear: On the Pixel 10 (API 36), will the FGS notification show without the permission?
   - Recommendation: Same approach as BLUETOOTH_CONNECT — request temporarily in MainActivity for Phase 2 testing. Official documentation confirms the FGS still starts without it.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | None currently — Android instrumentation tests not set up |
| Config file | None — Wave 0 gap |
| Quick run command | `adb shell logcat -s AudioBalanceService:D -t 50` (manual log inspection) |
| Full suite command | Manual BT connect/disconnect testing on Pixel 10 + logcat |

This phase is primarily validated via manual physical device testing (headphone connect/disconnect) and logcat inspection. The BT connection lifecycle and AudioFlinger effect behavior cannot be meaningfully unit-tested without hardware.

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BT-01 | A2DP connect broadcast received, MAC logged | manual-only | `adb logcat -s AudioBalanceService:D` | ❌ Wave 0 |
| BT-02 | A2DP disconnect broadcast received, logged | manual-only | `adb logcat -s AudioBalanceService:D` | ❌ Wave 0 |
| BT-03 | Stored balance applied on reconnect (ear test) | manual-only | Ear test with BT headphones | N/A |
| SVC-01 | Foreground service visible in notification shade | manual-only | Check notification + `adb shell dumpsys activity services` | N/A |
| SVC-02 | Notification shows device name + balance value | manual-only | Visual notification inspection | N/A |
| DATA-01 | Balance persists after `adb shell am force-stop` | manual-only | Force stop + reconnect headphones | N/A |
| DATA-02 | Balance survives phone reboot | manual-only | Reboot test + reconnect headphones | N/A |
| AUDIO-02 | Balance applied system-wide (Deezer/YouTube) | manual-only | Ear test on Deezer + YouTube | N/A |

**Justification for manual-only:** All requirements require real Bluetooth hardware (BT events), real AudioFlinger behavior (session 0 global effects), and auditory verification. Instrumented tests on emulators cannot reproduce the A2DP audio stack or DynamicsProcessing behavior. This matches the Phase 1 precedent.

### Sampling Rate
- **Per task commit:** `adb -s 56191FDCR002NG logcat -s AudioBalanceService:D -t 100` — scan for expected log lines
- **Per wave merge:** Full BT connect/disconnect cycle + balance persistence test (force-stop + reconnect)
- **Phase gate:** All 5 success criteria from ROADMAP.md verified before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] No automated test infrastructure needed — this phase is verified manually
- [ ] Ensure logcat TAG constants are defined in each class for easy filtering:
  - `AudioBalanceService` → `private const val TAG = "AudioBalanceService"`
  - `BtA2dpReceiver` → `private const val TAG = "BtA2dpReceiver"`
  - `BalanceRepository` → `private const val TAG = "BalanceRepository"`

---

## Sources

### Primary (HIGH confidence)
- Official Android FGS background start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start — exemption list, BOOT_COMPLETED status
- Official Android FGS service types: https://developer.android.com/develop/background-work/services/fgs/service-types — connectedDevice type requirements and permissions
- Official Android FGS types for Android 15: https://developer.android.com/about/versions/15/changes/foreground-service-types — confirmed connectedDevice NOT restricted from BOOT_COMPLETED
- Official DataStore releases: https://developer.android.com/jetpack/androidx/releases/datastore — version 1.2.1 confirmed stable March 2026
- Official Lifecycle releases: https://developer.android.com/jetpack/androidx/releases/lifecycle — version 2.10.0 confirmed stable November 2025
- Official Bluetooth profiles: https://developer.android.com/develop/connectivity/bluetooth/profiles — A2DP proxy pattern
- Official BluetoothA2dp reference: https://developer.android.com/reference/kotlin/android/bluetooth/BluetoothA2dp — ACTION_CONNECTION_STATE_CHANGED, extras
- POC-RESULTS.md (Phase 1 findings) — DynamicsProcessing pattern, all gotchas — HIGH confidence (physically validated on Pixel 10 / API 36)

### Secondary (MEDIUM confidence)
- DataStore vs SharedPreferences comparison 2025: https://www.atipik.ch/en/blog/android-jetpack-datastore-vs-sharedpreferences — consistent with official recommendation
- Android Notification runtime permission: https://developer.android.com/develop/ui/views/notifications/notification-permission — POST_NOTIFICATIONS for API 33+

### Tertiary (LOW confidence)
- Issue tracker discussion on BT broadcast + FGS: https://issuetracker.google.com/issues/230514922 — confirms Bluetooth broadcasts are not FGS start exemptions (marked Won't Fix by Google)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — DataStore 1.2.1 and LifecycleService 2.10.0 versions verified against official release pages
- Architecture: HIGH — FGS type requirements and BOOT_COMPLETED exemption verified against official docs; BT receiver pattern verified against issuetracker finding
- Pitfalls: HIGH — Config silence bug and GlobalDpHolder pattern are physically confirmed from Phase 1; background start restriction confirmed from official docs

**Research date:** 2026-04-04
**Valid until:** 2026-07-04 (90 days — stable Android APIs, no imminent changes expected for connectedDevice type)
