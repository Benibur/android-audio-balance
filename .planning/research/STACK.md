# Stack Research

**Domain:** Android Bluetooth audio balance controller
**Researched:** 2026-04-01
**Confidence:** MEDIUM — core Android framework APIs verified via official docs; AudioEffect session 0 behavior carries LOW confidence due to device-specific variability and deliberate deprecation without a clean replacement

---

## Critical Context: The Global Audio Balance Problem

This is the most important technical question for the entire project. Android does NOT have a first-class public API for system-wide stereo balance post-Android 10. Three approaches exist with very different tradeoffs:

| Approach | Global Coverage | API Status | Root Required | Reliability |
|----------|----------------|------------|---------------|-------------|
| AudioEffect session 0 | Yes (theoretically) | **Deprecated** since ~Android 2.4, still present | No | LOW — device-specific, may be silenced by OEM |
| Equalizer broadcast (per-session) | Partial — only cooperative apps | Supported | No | MEDIUM — misses apps that don't broadcast session IDs |
| AudioRecord + screen capture (PhantomAmp model) | Full | API 29+ only | No | HIGH but intrusive |

**Recommendation:** Start with AudioEffect session 0 (deprecated but still functional on most devices as of Android 14). The project context explicitly acknowledges this risk and calls for a POC first. Session 0 works by attaching an Equalizer to the global output mix and using band gain manipulation to simulate balance. No single API call maps to "stereo balance" — it must be implemented by boosting one channel and cutting the other via EQ bands.

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.3.10 | Primary language | Official Android language, coroutines-first, required for modern Compose and AGP 9.x |
| Android Gradle Plugin (AGP) | 9.1.0 | Build system | Latest stable; requires Gradle 9.3.1+ and Kotlin 2.3.x |
| Gradle | 9.3.1+ | Build toolchain | Required by AGP 9.1.0 minimum |
| Jetpack Compose BOM | 2026.03.00 | UI toolkit versioning | Latest stable BOM; manages all Compose library versions coherently |
| Material Design 3 | via BOM (1.4.x) | UI component library | Standard Compose UI kit; 1.4 is stable as of Dec 2025 |
| Android min SDK | 26 (Android 8.0) | Compatibility floor | Foreground service APIs stable here; BLUETOOTH_CONNECT not yet required below API 31 |
| Android target SDK | 35 (Android 15) | Feature targeting | Current SDK; must declare foreground service types per API 34 requirement |

### Audio Effect Layer

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `android.media.audiofx.Equalizer` | Platform API | Balance simulation via EQ bands | Only viable no-root path for global balance; session 0 still functional despite deprecation notice |
| `android.media.audiofx.AudioEffect` | Platform API | Base class, session 0 constant | `AudioEffect.ERROR_BAD_VALUE` from session 0 constructor = device blocks global effects; must handle gracefully |
| `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast | Platform API | Detect audio sessions from cooperative apps | Fallback: Spotify, YouTube Music, and other major apps do broadcast session IDs; attach per-session effects |

**How balance is simulated with Equalizer:**
Equalizer has no "balance" parameter. Balance is achieved by:
- Left-heavy: boost low-to-mid bands on L channel, cut on R (not directly accessible per-band per-channel without lower-level API)
- In practice with session 0 Equalizer: apply overall gain offset using `setGain()` is not available — the workaround is using two `Equalizer` instances (one per channel) which requires the audio framework to support per-channel session attachment (device-specific)

**LOW CONFIDENCE NOTE:** The per-channel EQ manipulation path is not reliably supported via public APIs. The POC phase must validate whether session 0 + gain manipulation achieves audible balance shifts. If not, fallback to `AudioRecord` + `AudioTrack` re-injection (screen capture model) is the alternative.

### Bluetooth Detection Layer

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` | Platform API | Detect headphone connect/disconnect | Standard broadcast for A2DP profile state changes; includes `EXTRA_DEVICE` (MAC address) and `EXTRA_STATE` |
| `BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED` | Platform API | Detect when audio actually starts streaming | Finer-grained than connection state; useful for knowing when to apply effects |
| `android.intent.action.BOOT_COMPLETED` | Platform API | Auto-start service on device boot | Requires `RECEIVE_BOOT_COMPLETED` permission; works on API 26+ |
| `android.permission.BLUETOOTH_CONNECT` | Runtime permission (API 31+) | Required to receive BT intents on Android 12+ | Without this, BluetoothA2dp broadcasts are silently dropped on API 31+ |

### Background Service Layer

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `android.app.Service` (Foreground) | Platform API | Keep app alive while audio effects active | Required for persistence; `connectedDevice` type is correct for this use case (not `mediaPlayback` — app is not playing audio, it's monitoring a BT device) |
| `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` | API 34 requirement | Required type declaration for API 34+ targeting | Omitting causes `MissingForegroundServiceTypeException` on Android 14 devices |
| `ServiceCompat.startForeground()` | androidx-core 1.16.x | API-safe foreground service start | Handles API version differences; avoids crash on API 34+ |
| `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` | Normal permission | Required companion to `connectedDevice` type | Must be declared in manifest; automatically granted, no user prompt |

### Persistence Layer

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Room | 2.8.4 | Per-device balance settings storage | Best fit for structured data (device MAC, name, balance coefficient, enabled flag); KSP-native, coroutines-first; overkill for simple prefs but right for per-device keyed data |
| DataStore Preferences | 1.1.2 | Global app settings (enable/disable toggle, export flag) | Replaces SharedPreferences; coroutine/Flow-based; correct for simple key-value global settings |

**Why Room over pure DataStore:** The core data is a table — device address → balance coefficient. Room's query model (`getByMac()`, `getAll()`) is cleaner than encoding a map in DataStore. Use both: Room for device records, DataStore for app-wide settings.

### Dependency Injection

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Hilt | 2.57.1 | DI framework | Google-official; first-class Android integration (ViewModel, Service, BroadcastReceiver injection scopes); KSP-native with Kotlin 2.x |
| KSP | 2.3.10-2.0.0 | Annotation processor | Required for Room and Hilt; replaces KAPT; 2x faster compilation |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.core:core-ktx` | 1.16.x (via BOM) | Kotlin extensions for Android framework | Always — cleaner Bluetooth intent parsing, service management |
| `androidx.lifecycle:lifecycle-service` | 2.9.x | `LifecycleService` base class for foreground service | Use instead of raw `Service` to get lifecycle-aware coroutine scopes |
| `kotlinx.coroutines:kotlinx-coroutines-android` | 1.10.2 | Coroutines on Android | Required for Room queries, Flow collection from DataStore, service coroutine scope |
| `kotlinx.serialization:kotlinx-serialization-json` | 1.7.x | JSON export/import of device settings | Use instead of Gson/Moshi — pure Kotlin, no reflection, Kotlin 2.x compatible |
| `androidx.activity:activity-compose` | 1.10.x | Compose entrypoint, `rememberLauncherForActivityResult` | Runtime permission requests for BLUETOOTH_CONNECT |
| `accompanist-permissions` (now deprecated) | N/A | See note below | DO NOT USE — see What NOT to Use |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Android Studio Otter 3 Feature Drop or later | IDE | Required for AGP 9.0+; earlier versions fail to open AGP 9.x projects |
| KSP plugin `com.google.devtools.ksp` | Annotation processing | Version must match Kotlin version: `2.3.10-2.0.0` format |
| Android Emulator (API 26+) | Local testing | BT simulation is limited in emulator — most BT audio testing requires physical device |
| `adb shell dumpsys media.audio_flinger` | Debug audio sessions | Inspect active audio sessions and effects from CLI |
| `adb shell dumpsys audio` | Debug Bluetooth audio state | Verify A2DP connection state and active output device |

---

## Build Configuration (build.gradle.kts)

```kotlin
// Project-level build.gradle.kts
plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
    id("com.google.devtools.ksp") version "2.3.10-2.0.0" apply false
    id("com.google.dagger.hilt.android") version "2.57.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10" apply false
}

// App-level build.gradle.kts
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Lifecycle + service
    implementation("androidx.lifecycle:lifecycle-service:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Core
    implementation("androidx.core:core-ktx:1.16.0")

    // Room
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-android-compiler:2.57.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Serialization (for JSON export/import)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```

---

## Manifest Permissions

```xml
<!-- Bluetooth: legacy for API 26-30 -->
<uses-permission android:name="android.permission.BLUETOOTH"
                 android:maxSdkVersion="30" />

<!-- Bluetooth: runtime permission for API 31+ -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Audio effects -->
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<!-- Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<!-- Boot start -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Service declaration -->
<service
    android:name=".service.BtAudioBalanceService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false" />
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| AudioEffect session 0 (POC first) | Per-session via broadcast (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`) | If session 0 is blocked by device OEM; provides partial coverage for cooperative apps (Spotify, YT Music) |
| AudioEffect session 0 | Screen capture audio re-injection (PhantomAmp model) | If session 0 AND per-session both fail; requires API 29+, blocks concurrent screen recording, high battery cost |
| Room for device storage | Pure DataStore with serialized list | Only if adding a database feels like overengineering for a personal tool; DataStore is simpler but queries are awkward |
| `connectedDevice` foreground type | `mediaPlayback` | Never — app is not the media player; using wrong type causes rejection on API 34+ |
| Hilt | Koin | If developer prefers pure-Kotlin DI with no KSP annotation processing; Koin has less boilerplate but weaker compile-time safety |
| kotlinx.serialization | Gson / Moshi | If working with a Java-based existing codebase; Gson has reflection issues with Kotlin data classes, Moshi requires extra adapters |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `accompanist-permissions` | Deprecated and archived; Google transferred permission handling to `androidx.activity` 1.9+ and official Compose APIs | `rememberLauncherForActivityResult` + `ActivityResultContracts.RequestPermission` from `androidx.activity:activity-compose` |
| KAPT | Slower than KSP by 2x; deprecated direction for Kotlin 2.x projects; some libraries have dropped KAPT support | KSP for all annotation processing (Hilt, Room) |
| `SharedPreferences` | Synchronous, no coroutine support, no type safety, deprecated in favor of DataStore | `DataStore<Preferences>` |
| `AudioEffect` with session 0 + no error handling | Session 0 effects fail silently or throw on some OEM ROMs (Samsung, Xiaomi); an unchecked `IllegalStateException` will crash the service | Always wrap `AudioEffect` construction in try-catch; check `getEnabled()` after `setEnabled(true)` |
| `startService()` without `startForeground()` within 5 seconds | Android 8+ kills background services that don't call `startForeground()` within 5 seconds of start | Call `startForeground()` as first operation in `onStartCommand()` |
| `MediaRouter` for balance | MediaRouter controls routing (which output device receives audio), not audio processing parameters | AudioEffect APIs for processing |
| `AccessibilityService` for audio balance | Accessibility APIs have no audio effect capabilities; this misconception likely comes from some apps using AccessibilityService to intercept UI events, not audio | AudioEffect session 0 or per-session approach |
| Implicit `BroadcastReceiver` in manifest for `BluetoothA2dp` changes | Android 8+ does not deliver implicit broadcasts to manifest-declared receivers; the A2DP state change broadcast is explicit per `BluetoothDevice` but requires runtime-registered receiver inside a running component | Register receiver programmatically inside the foreground service's `onCreate()` |

---

## Stack Patterns by Variant

**If session 0 AudioEffect works on target device (happy path):**
- Single `Equalizer(0, 0)` instance in foreground service
- Use band gain manipulation to simulate balance coefficient
- Reapply on every A2DP connect event

**If session 0 is blocked by OEM (common on Samsung/Xiaomi):**
- Register `BroadcastReceiver` for `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`
- On receipt, attach `Equalizer` to the received session ID
- Maintain a `Map<Int, Equalizer>` of active sessions
- Clean up on `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION`
- Coverage: Spotify, YouTube Music, VLC, Poweramp, most major players

**If both session approaches fail:**
- `MediaProjectionManager` to capture audio (API 29+, `RECORD_AUDIO` permission)
- Re-inject via `AudioTrack` with channel mixing applied in software
- High battery/CPU cost; blocks screen recording; treat as last resort

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| AGP 9.1.0 | Gradle 9.3.1+, Kotlin 2.3.10 | Incompatible with Android Studio < Otter 3 Feature Drop |
| KSP 2.3.10-2.0.0 | Kotlin 2.3.10 | KSP version must match Kotlin version exactly (format: `{kotlin}-{ksp}`) |
| Hilt 2.57.1 | KSP (not KAPT) | Uses KSP2 processing; verify with `room.generateKotlin=true` option |
| Room 2.8.4 | KSP, kotlinx-coroutines 1.10.x | `room-ktx` required for coroutine suspend functions |
| Compose BOM 2026.03.00 | Kotlin 2.3.x, Activity 1.10+ | Includes Compose UI 1.10, Material3 1.4 |
| DataStore 1.1.2 | kotlinx-coroutines 1.7+ | Not compatible with main-thread access |

---

## Sources

- [Android AudioEffect API reference](https://developer.android.com/reference/kotlin/android/media/audiofx/AudioEffect) — session 0 deprecation note, MODIFY_AUDIO_SETTINGS requirement
- [Android AOSP audio-effects docs](https://source.android.com/docs/core/audio/audio-effects) — device effect constructor (AudioEffect + AudioDeviceAttributes, API 31+ SystemAPI)
- [Google Issue Tracker #36936557](https://issuetracker.google.com/issues/36936557) — session 0 deprecation history; no clean migration path provided by Google (LOW confidence: issue status unclear)
- [Esper: Why Android equalizer apps don't work](https://www.esper.io/blog/android-equalizer-apps-inconsistent) — MEDIUM confidence; explains session broadcast mechanism vs session 0 tradeoffs
- [BluetoothA2dp API reference](https://developer.android.com/reference/android/bluetooth/BluetoothA2dp) — ACTION_CONNECTION_STATE_CHANGED, EXTRA_STATE, EXTRA_DEVICE
- [Android Bluetooth permissions docs](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) — BLUETOOTH_CONNECT requirement for API 31+
- [Foreground service types required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required) — MissingForegroundServiceTypeException behavior
- [Foreground service types reference](https://developer.android.com/develop/background-work/services/fgs/service-types) — `connectedDevice` type confirmed for Bluetooth monitoring use case
- [Compose BOM reference](https://developer.android.com/develop/ui/compose/bom) — BOM 2026.03.00 confirmed as latest stable
- [AGP release notes](https://developer.android.com/build/releases/about-agp) — AGP 9.1.0 + Kotlin 2.3.10 confirmed stable
- [Room release notes](https://developer.android.com/jetpack/androidx/releases/room) — Room 2.8.4 confirmed latest stable (Nov 2025)
- [kotlinx.coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases) — 1.10.2 confirmed stable
- [androidx/media GitHub issue #310](https://github.com/androidx/media/issues/310) — ChannelMixingAudioProcessor as alternative balance approach for Media3 (LOW confidence: only for apps controlling their own playback)
- [Equalizer broadcast tracker](https://github.com/pinpong/equalizer-issue-tracker/blob/master/EQUALIZER_BROADCAST.md) — Full protocol for session ID broadcast mechanism

---

*Stack research for: Android Bluetooth Audio Balance Controller*
*Researched: 2026-04-01*
