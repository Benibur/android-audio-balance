# Architecture Research

**Domain:** Android background service app — Bluetooth audio balance controller
**Researched:** 2026-04-01
**Confidence:** MEDIUM-HIGH (component boundaries HIGH, AudioEffect session approach MEDIUM due to deprecation ambiguity)

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          UI Layer (Main Process)                     │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  MainActivity  →  Jetpack Compose UI                         │   │
│  │  - DeviceListScreen (device rows + balance sliders)          │   │
│  │  - SettingsScreen (global toggle, export/import)             │   │
│  └──────────────────┬───────────────────────────────────────────┘   │
│                     │ observes StateFlow                             │
│  ┌──────────────────▼───────────────────────────────────────────┐   │
│  │  MainViewModel                                               │   │
│  │  - uiState: StateFlow<UiState>                               │   │
│  │  - handles user intent (slider moved, toggle, export)        │   │
│  │  - delegates persistence to DeviceSettingsRepository         │   │
│  │  - binds to AudioControlService to get live status           │   │
│  └──────────┬────────────────────────┬──────────────────────────┘   │
└─────────────┼────────────────────────┼──────────────────────────────┘
              │ Repository calls        │ ServiceConnection (bind)
┌─────────────▼────────────────────────▼──────────────────────────────┐
│                      Domain / Service Layer                          │
├──────────────────────────────────┬──────────────────────────────────┤
│  DeviceSettingsRepository        │  AudioControlService             │
│  - read/write per-MAC settings   │  (Foreground Service)            │
│  - exposes Flow<List<Device>>    │                                  │
│  - backed by DataStore           │  ┌────────────────────────────┐  │
│                                  │  │  BTConnectionReceiver      │  │
│  ┌───────────────────────────┐   │  │  (manifest-registered)     │  │
│  │  DataStore (Preferences)  │   │  │  ACTION_CONNECTION_STATE   │  │
│  │  Key: "balance_<MAC>"     │   │  │  → notifies service        │  │
│  │  Key: "enabled_<MAC>"     │   │  └────────────┬───────────────┘  │
│  └───────────────────────────┘   │               │                  │
│                                  │  ┌────────────▼───────────────┐  │
│                                  │  │  AudioEffectManager        │  │
│                                  │  │  - holds AudioEffect refs  │  │
│                                  │  │  - apply/release effects   │  │
│                                  │  │  - session 0 primary path  │  │
│                                  │  │  - session scan fallback   │  │
│                                  │  └────────────────────────────┘  │
└──────────────────────────────────┴──────────────────────────────────┘
              │
┌─────────────▼────────────────────────────────────────────────────────┐
│                       Android OS / Boot Layer                         │
├──────────────────────────────────────────────────────────────────────┤
│  BootReceiver                                                         │
│  ACTION_BOOT_COMPLETED → startForegroundService(AudioControlService)  │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Communicates With |
|-----------|----------------|-------------------|
| `MainActivity` | Compose host, permission requests | `MainViewModel` |
| `MainViewModel` | UI state aggregation, user intent handling | `DeviceSettingsRepository`, `AudioControlService` (via binder) |
| `DeviceSettingsRepository` | Read/write per-device balance settings, expose Flow | DataStore, `MainViewModel`, `AudioControlService` |
| `AudioControlService` | Foreground service: monitors BT state, applies audio effects | `BTConnectionReceiver`, `AudioEffectManager`, `DeviceSettingsRepository` |
| `BTConnectionReceiver` | Receives `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` | `AudioControlService` (via intent or direct call) |
| `AudioEffectManager` | Creates/destroys AudioEffect instances, applies balance | Android AudioEffect API, `AudioControlService` |
| `BootReceiver` | Receives `ACTION_BOOT_COMPLETED`, starts foreground service | `AudioControlService` |

## Recommended Project Structure

```
app/src/main/
├── java/com/example/btbalance/
│   ├── MainActivity.kt               # Single activity, Compose host
│   │
│   ├── ui/
│   │   ├── MainViewModel.kt          # UI state + user intent handler
│   │   ├── UiState.kt                # Data classes for UI state
│   │   ├── DeviceListScreen.kt       # Compose screen: device list + sliders
│   │   └── SettingsScreen.kt         # Compose screen: global toggle, export
│   │
│   ├── service/
│   │   ├── AudioControlService.kt    # Foreground service, orchestrator
│   │   ├── AudioEffectManager.kt     # AudioEffect lifecycle management
│   │   └── ServiceBinder.kt          # Binder for UI→service communication
│   │
│   ├── receiver/
│   │   ├── BTConnectionReceiver.kt   # BluetoothA2dp state change handler
│   │   └── BootReceiver.kt           # BOOT_COMPLETED → start service
│   │
│   ├── data/
│   │   ├── DeviceSettingsRepository.kt  # Single source of truth for settings
│   │   ├── DeviceSettings.kt            # Data class: MAC, balance, enabled
│   │   └── DataStoreManager.kt          # DataStore read/write helpers
│   │
│   └── model/
│       └── BluetoothDevice.kt        # Domain model for a known BT device
│
└── res/
    ├── values/strings.xml
    └── xml/
        └── data_extraction_rules.xml # DataStore backup rules
```

### Structure Rationale

- **`service/`**: Groups all background work. `AudioControlService` owns effect lifecycle; `AudioEffectManager` is extracted so it can be unit-tested without a live service context.
- **`receiver/`**: Manifest-registered receivers are isolated. Both are thin wrappers — they delegate to the service immediately and return.
- **`data/`**: Repository pattern isolates DataStore details from the rest of the app. ViewModels and the Service both access settings through `DeviceSettingsRepository`, never DataStore directly.
- **`ui/`**: Each Compose screen gets its own file. All state comes from `MainViewModel` via `collectAsStateWithLifecycle()`.

## Architectural Patterns

### Pattern 1: Foreground Service as Audio Effect Orchestrator

**What:** `AudioControlService` owns the `AudioEffectManager` and is the sole component that creates or destroys `AudioEffect` instances. The service lifecycle dictates effect lifecycle — effects are created on BT connect and released on BT disconnect or service stop.

**When to use:** Always. Audio effects tied to a service that outlives the UI ensure effects keep applying when the screen is off.

**Trade-offs:** Service must call `startForeground()` within 5 seconds of `onStartCommand()`. Effects are only valid while the service is alive; if the OS kills the service, effects disappear.

**Example:**
```kotlin
class AudioControlService : Service() {
    private val binder = ServiceBinder(this)
    private lateinit var effectManager: AudioEffectManager

    override fun onCreate() {
        super.onCreate()
        effectManager = AudioEffectManager()
        startForeground(NOTIF_ID, buildNotification())
    }

    fun onDeviceConnected(macAddress: String) {
        val balance = repository.getBalance(macAddress)
        effectManager.applyBalance(balance)
    }

    fun onDeviceDisconnected() {
        effectManager.release()
    }

    override fun onBind(intent: Intent) = binder
    override fun onDestroy() { effectManager.release(); super.onDestroy() }
}
```

### Pattern 2: Manifest-Registered BroadcastReceiver Delegating to Service

**What:** `BTConnectionReceiver` is declared in the manifest so it fires even when the app is not running. Because `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` is on the implicit broadcast exemption list (API 26+), manifest registration is allowed and works from background.

**When to use:** For this project exclusively — it is one of the few remaining implicit broadcasts that can be manifest-registered.

**Trade-offs:** `onReceive()` runs on the main thread with a 10-second hard limit. No coroutines, no suspend functions. The receiver must start the foreground service and return immediately; all real work happens in the service.

**Example:**
```kotlin
class BTConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) return
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        val device = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else
            @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

        val serviceIntent = Intent(context, AudioControlService::class.java).apply {
            putExtra(EXTRA_BT_STATE, state)
            putExtra(EXTRA_MAC, device?.address)
        }
        context.startForegroundService(serviceIntent)
    }
}
```

### Pattern 3: Repository as Single Source of Truth, Consumed by Both Service and ViewModel

**What:** `DeviceSettingsRepository` wraps DataStore (Preferences) and exposes a `Flow<List<DeviceSettings>>`. Both `MainViewModel` (for UI) and `AudioControlService` (to know what balance to apply) read from it. Writes go through the repository only.

**When to use:** Any time two components need the same data. Avoids the service and UI getting out of sync.

**Trade-offs:** The service needs a `CoroutineScope` (use `lifecycleScope` or a manually managed scope) to call suspend functions and collect flows. SharedPreferences would be simpler but is not coroutine-native and has no Flow support.

### Pattern 4: Bound Service for UI ↔ Service Bidirectional Communication

**What:** `MainViewModel` binds to `AudioControlService` using `ServiceConnection`. The `ServiceBinder` exposes the service instance directly (same process, no IPC needed). This allows the UI to query live service state (e.g. "which device is currently connected") and the service to post state updates back.

**When to use:** When the UI needs real-time status from a running service. Preferred over `LocalBroadcastManager` (deprecated) or sticky intents.

**Trade-offs:** `bindService()` is asynchronous — the binder is not available immediately. ViewModel must handle the null case. Unbind in `onCleared()`.

## Data Flow

### BT Connection → Effect Applied

```
Physical BT connect event (OS)
    ↓
BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED (implicit broadcast)
    ↓
BTConnectionReceiver.onReceive()          [main thread, <10s]
    ↓ startForegroundService(intent with MAC + state)
AudioControlService.onStartCommand()      [handles START_REDELIVER_INTENT]
    ↓ read settings (suspend, coroutine scope)
DeviceSettingsRepository.getSettings(mac)
    ↓ balance value
AudioEffectManager.applyBalance(balance)  [creates/updates AudioEffect]
    ↓
Android AudioFlinger (OS audio routing)
```

### User Changes Balance Slider

```
Compose Slider onValueChange
    ↓
MainViewModel.onBalanceChanged(mac, value)
    ↓ coroutine launch
DeviceSettingsRepository.saveBalance(mac, value)  [DataStore write]
    ↓ Flow emission
AudioControlService (collecting Flow)
    ↓ if device currently connected
AudioEffectManager.applyBalance(value)    [live update]
    ↓
StateFlow<UiState> updated
    ↓
Compose recompose (slider reflects saved value)
```

### Boot Sequence

```
Device reboots
    ↓
ACTION_BOOT_COMPLETED (exempt from background restrictions)
    ↓
BootReceiver.onReceive()
    ↓ startForegroundService(AudioControlService)
AudioControlService.onCreate()
    ↓ startForeground() (must occur within 5s)
Persistent notification shown
    ↓ query current BT state via BluetoothManager
If A2DP device already connected: applyBalance()
Service waits for future BT events via BTConnectionReceiver
```

### State Management

```
DataStore (persistent on-disk)
    ↓ Flow<Preferences>
DeviceSettingsRepository
    ↓ Flow<List<DeviceSettings>>
    ├── MainViewModel._uiState (StateFlow)  →  Compose UI
    └── AudioControlService (collect)       →  AudioEffectManager
```

## AudioEffect: Session Approach Decision Tree

This is the critical technical decision. The architecture must accommodate two approaches:

```
Service starts, BT device connected
    ↓
TRY: AudioEffect(Equalizer.EFFECT_TYPE_EQUALIZER, ..., sessionId = 0)
    ↓ success?
    ├── YES → apply balance via left/right gain on EQ bands
    │         flag: SESSION_0_APPROACH_ACTIVE
    └── NO  → FALLBACK: Listen for ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
                        broadcast from cooperating media players
              OR use AudioManager.getActivePlaybackConfigurations()
                        to enumerate live session IDs
              Apply effect per-session
              flag: PER_SESSION_APPROACH_ACTIVE
```

**Session 0 status (MEDIUM confidence):** Deprecated since ~Android 2.3 but never removed. Works on many devices / OEM skins as of 2024. Will not work on all devices. Must be wrapped in try/catch and tested on physical hardware.

**Per-session fallback:** `AudioManager.getActivePlaybackConfigurations()` returns `List<AudioPlaybackConfiguration>`, each with an audio session ID. Create an `AudioEffect` per session. This is more reliable but requires querying on each BT connect, and newly started players after connect won't be caught without polling or the `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast.

**Architecture implication:** `AudioEffectManager` must be written to support both approaches behind a single interface. The Phase 1 POC should determine which path works; Phase 2 builds the full manager around the validated approach.

## Foreground Service Type Declaration

For Android 14+ (API 34+), foreground service type must be declared. For this app:

```xml
<service
    android:name=".service.AudioControlService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false">
</service>
```

`connectedDevice` is the correct type: the service's purpose is managing interactions with an external Bluetooth device. It requires `BLUETOOTH_CONNECT` runtime permission (already needed). It does NOT require `mediaPlayback` because this app is not a media player.

## Permissions Architecture

| Permission | Why | When Required |
|------------|-----|---------------|
| `BLUETOOTH_CONNECT` | Query connected devices, receive A2DP state events | API 31+ (runtime) |
| `BLUETOOTH_SCAN` | Not strictly needed here; omit unless discovery required | — |
| `MODIFY_AUDIO_SETTINGS` | Required for `AudioEffect` on session 0 | All APIs |
| `FOREGROUND_SERVICE` | Start a foreground service | All APIs |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Declare `connectedDevice` FGS type | API 34+ |
| `RECEIVE_BOOT_COMPLETED` | Register for `ACTION_BOOT_COMPLETED` | All APIs |

## Anti-Patterns

### Anti-Pattern 1: Creating AudioEffect in BroadcastReceiver

**What people do:** Instantiate `AudioEffect` directly inside `BTConnectionReceiver.onReceive()`.

**Why it's wrong:** `onReceive()` returns and the receiver is destroyed. The `AudioEffect` is released. The effect is never actually applied for more than a few milliseconds. Additionally, effect creation may be slow and trigger ANR.

**Do this instead:** Start the foreground service from `onReceive()`; create the `AudioEffect` in the service.

### Anti-Pattern 2: Polling for BT Connection State

**What people do:** Use a `Timer` or `Handler.postDelayed()` to periodically call `BluetoothA2dp.getConnectedDevices()`.

**Why it's wrong:** Wastes battery. Introduces latency (up to poll interval). Entirely unnecessary because `ACTION_CONNECTION_STATE_CHANGED` fires immediately on connect/disconnect.

**Do this instead:** React to the broadcast. Optionally query current state once at service start (to handle "already connected at boot" case).

### Anti-Pattern 3: Storing Settings in SharedPreferences with String Concatenation Keys

**What people do:** `prefs.putFloat("balance_" + mac, value)` using SharedPreferences directly in the ViewModel.

**Why it's wrong:** SharedPreferences writes are synchronous on the caller thread (blocking UI), or if using `apply()` the write isn't guaranteed before a crash. No Flow support means the service can't react to changes.

**Do this instead:** Use `DataStore<Preferences>` with a typed key (`floatPreferencesKey("balance_$mac")`). DataStore is coroutine-safe, crash-safe, and emits a `Flow` on change.

### Anti-Pattern 4: Accessing Service State via Static Variables or Singletons

**What people do:** Hold `AudioEffectManager` in a companion object or Application class so ViewModel can reach it directly.

**Why it's wrong:** Service may be killed and restarted; the static reference becomes stale or points to a dead effect. Testing becomes impossible.

**Do this instead:** Use the bound service pattern. `MainViewModel` binds to `AudioControlService` and calls methods on the live service instance via `ServiceBinder`.

### Anti-Pattern 5: Launching Media Playback FGS Type from BOOT_COMPLETED on API 34+

**What people do:** Declare `foregroundServiceType="mediaPlayback"` (seems logical for audio), then try to start it from `BootReceiver`.

**Why it's wrong:** Android 14 explicitly forbids starting `mediaPlayback` FGS from a `BOOT_COMPLETED` receiver. This throws `ForegroundServiceStartNotAllowedException`.

**Do this instead:** Use `connectedDevice` type, which is not restricted. `BOOT_COMPLETED` → foreground service start is allowed for `connectedDevice`.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Android Bluetooth stack | Broadcast receiver for state changes | `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` is exempt from implicit broadcast restrictions |
| Android AudioFlinger | `AudioEffect` constructor with session ID | Session 0 deprecated but often functional; must verify on physical device |
| Android OS boot | `ACTION_BOOT_COMPLETED` broadcast | Exempt from background restrictions; works with `connectedDevice` FGS type |
| DataStore (Jetpack) | Repository wrapping `dataStore` extension | Coroutine-native; no SharedPreferences needed |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `BTConnectionReceiver` → `AudioControlService` | `startForegroundService(Intent)` with extras | Receiver must not do work; relay MAC + state in intent extras |
| `BootReceiver` → `AudioControlService` | `startForegroundService(Intent)` | Simple start, service queries BT state itself on create |
| `MainViewModel` → `AudioControlService` | Bound service (`ServiceBinder.getService()`) | Bind in `init`, unbind in `onCleared()` |
| `MainViewModel` → `DeviceSettingsRepository` | Direct calls + `Flow` collection | Repository injected via constructor (or Hilt) |
| `AudioControlService` → `DeviceSettingsRepository` | Direct calls + `Flow` collection | Service maintains its own `CoroutineScope` (cancel in `onDestroy()`) |
| `AudioControlService` → `MainViewModel` | Via `StateFlow` in repository (shared reactive stream) | Service writes state; ViewModel observes the same Flow |

## Suggested Build Order

Dependencies flow bottom-up. Build in this order to avoid mocking incomplete layers:

1. **DataStore + Repository** (`data/`)
   — No Android service dependencies. Can be unit-tested with in-memory DataStore.

2. **`AudioEffectManager`** (POC phase)
   — Isolated class. Validates whether session 0 approach works. No UI, no service needed.
   — This is the highest-risk component. Must be validated before building anything else.

3. **`AudioControlService`** (`service/`)
   — Depends on Repository (step 1) and AudioEffectManager (step 2).
   — Build with hardcoded settings first; integrate repository second.

4. **`BTConnectionReceiver` + `BootReceiver`** (`receiver/`)
   — Thin. Depends only on Service being available to start.

5. **`MainViewModel`** (`ui/`)
   — Depends on Repository (step 1) and Service (step 3, via binder).

6. **Compose UI** (`ui/` screens)
   — Depends only on ViewModel state. Last layer; no logic here.

## Scaling Considerations

This is a single-user personal app. Scaling is not relevant. However:

| Concern | Approach |
|---------|----------|
| Multiple BT devices simultaneously | `AudioEffectManager` holds a `Map<String, AudioEffect>` keyed by MAC; apply highest-priority (most recently connected) or merge |
| Large number of known devices in DataStore | DataStore handles this fine up to hundreds of entries; no concern |
| Memory leak from AudioEffect | Must call `release()` explicitly; manager tracks all live instances and releases in `onDestroy()` |

## Sources

- [BluetoothA2dp API reference — Android Developers](https://developer.android.com/reference/android/bluetooth/BluetoothA2dp)
- [Implicit broadcast exceptions (includes BT + BOOT_COMPLETED) — Android Developers](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
- [Foreground service types — Android Developers](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Restrictions on starting FGS from background — Android Developers](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Why Android equalizer apps don't work with all media players — Esper](https://www.esper.io/blog/android-equalizer-apps-inconsistent)
- [AudioEffect session 0 deprecation issue — Google Issue Tracker #36936557](https://issuetracker.google.com/issues/36936557)
- [StateFlow and SharedFlow — Android Developers](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [Services overview — Android Developers](https://developer.android.com/develop/background-work/services)
- [Foreground service types required (API 34) — Android Developers](https://developer.android.com/about/versions/14/changes/fgs-types-required)

---
*Architecture research for: Android Bluetooth Audio Balance Controller*
*Researched: 2026-04-01*
