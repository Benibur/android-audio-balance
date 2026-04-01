# Project Research Summary

**Project:** Android Bluetooth Audio Balance Controller
**Domain:** Android background service app — per-device stereo balance correction via AudioEffect
**Researched:** 2026-04-01
**Confidence:** MEDIUM (core Android APIs verified; AudioEffect session 0 feasibility is the critical unknown)

## Executive Summary

This is a single-purpose Android utility that monitors Bluetooth A2DP connections and automatically applies per-device stereo balance corrections using Android's `AudioEffect` API. The architecture is well-understood: a `connectedDevice` foreground service listens for A2DP connection events, looks up a per-device balance coefficient from DataStore, and applies it via `AudioEffect`. The Kotlin/Compose/Hilt stack is modern, version constraints are tight (AGP 9.1.0 + Kotlin 2.3.10 + KSP), and all framework APIs are well-documented.

The project's single highest-risk unknown is whether `AudioEffect` with session 0 (global output mix) reliably applies stereo balance on the target hardware. This API is deprecated since Android 2.3 but never removed, and it works on many devices — including Pixel — but is silently blocked on a significant fraction of OEM devices (Samsung One UI, Xiaomi MIUI/HyperOS). The entire AudioEffect portion of the implementation must be validated on physical target hardware before the rest of the feature set is built. A three-tier fallback strategy exists: session 0 → per-session-ID broadcasts → AudioRecord/AudioTrack re-injection.

The recommended mitigation is to phase the build: a dedicated POC phase first to validate the audio effect approach, followed by standard service/persistence/UI phases that build on the confirmed implementation. OEM battery optimisation killing the foreground service is a secondary risk that requires user-facing onboarding (not code fixes) for Xiaomi and older Samsung devices.

## Key Findings

### Recommended Stack

The stack is Kotlin 2.3.10 with AGP 9.1.0, Jetpack Compose (BOM 2026.03.00), Hilt 2.57.1, Room 2.8.4, and DataStore Preferences 1.1.2. KSP replaces KAPT for annotation processing. The version constraints are tightly coupled: KSP version must exactly match Kotlin version in `{kotlin}-{ksp}` format, and AGP 9.1.0 requires Android Studio Otter 3 Feature Drop or later. The only deprecated dependency in the recommended stack is `AudioEffect` session 0, which is kept because it remains the only viable no-root path for system-wide balance.

**Core technologies:**
- Kotlin 2.3.10 + Coroutines 1.10.2: primary language with coroutine-first async — required for modern AGP and Compose BOM
- Jetpack Compose BOM 2026.03.00 + Material3: UI toolkit — manages all Compose library versions coherently
- `android.media.audiofx.Equalizer` (session 0): balance simulation via EQ band gain — only viable no-root path for global effect
- `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`: A2DP state broadcast — exempt from background restrictions, manifest-registerable
- Foreground service (`connectedDevice` type): background persistence — required type for API 34+ and BOOT_COMPLETED compatibility
- Room 2.8.4 + DataStore 1.1.2: persistence — Room for per-device records, DataStore for global app settings
- Hilt 2.57.1 + KSP: dependency injection — first-class Android scopes (Service, ViewModel, BroadcastReceiver)

**Critical version constraints:**
- KSP: `2.3.10-2.0.0` (must match Kotlin 2.3.10 exactly)
- AGP 9.1.0 requires Gradle 9.3.1+ and Android Studio Otter 3 Feature Drop+
- `accompanist-permissions` is deprecated and must NOT be used — use `activity-compose:1.10+` instead

### Expected Features

**Must have (table stakes — v1):**
- Per-device balance profile storage (MAC address → balance float) — everything else depends on this
- Persistent balance slider UI per device — the only user-facing audio control
- BT A2DP connect/disconnect detection — triggers auto-apply
- AudioEffect session 0 balance application (or validated fallback) — the core effect
- Foreground service with persistent notification — required for background survival
- Auto-start on device boot — without this the app fails after every reboot
- Device list screen with per-device enable/disable toggle — device management
- Global enable/disable kill switch — surfaced in notification

**Should have (v1.x after validation):**
- JSON export/import — survive reinstalls; neither Wavelet nor Precise Volume prominently offer this
- "Apply now" notification action — one-tap re-apply for race-condition cases
- Last-applied timestamp per device — debug aid
- Named device nicknames — handle confusing BT default names

**Defer (v2+):**
- Quick Settings Tile — TileService lifecycle complexity; defer until v1 stable
- "Test balance" button — nice UX, not essential for launch
- Volume control, multi-band EQ, wired headphone support, cloud sync — explicitly out of scope

### Architecture Approach

The app follows a clean layered architecture with a foreground service as the central orchestrator. `AudioControlService` owns all `AudioEffect` lifecycle; it is the only component that creates or destroys effect instances. A `BTConnectionReceiver` (manifest-declared, exempt from implicit broadcast restrictions) receives A2DP state changes and delegates to the service via `startForegroundService()`. `DeviceSettingsRepository` wraps DataStore and exposes `Flow<List<DeviceSettings>>` consumed by both `MainViewModel` (UI) and `AudioControlService` (effect application). The ViewModel binds to the service via `ServiceBinder` for real-time status. Build order must follow dependency direction: DataStore/Repository → AudioEffectManager (POC) → AudioControlService → Receivers → ViewModel → Compose UI.

**Major components:**
1. `AudioControlService` (foreground, `connectedDevice` type) — BT monitoring, effect orchestration, coroutine scope
2. `AudioEffectManager` — creates/destroys `AudioEffect` instances; session 0 primary path with per-session fallback; supports `OnControlStatusChangeListener` for server-restart recovery
3. `DeviceSettingsRepository` — single source of truth via DataStore; exposes Flow to both service and ViewModel
4. `BTConnectionReceiver` + `BootReceiver` — thin manifest-declared receivers; no logic, relay to service only
5. `MainViewModel` — UI state aggregation via `StateFlow`; binds to service for live status
6. Compose UI (`DeviceListScreen`, `SettingsScreen`) — stateless presentation driven by ViewModel

### Critical Pitfalls

1. **AudioEffect session 0 silently does nothing on OEM devices** — test on physical target hardware (Pixel + Samsung + Xiaomi) in Phase 1 before building any dependent features; if blocked, pivot to per-session-ID approach using `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcasts

2. **Wrong foreground service type (`mediaPlayback` instead of `connectedDevice`)** — `mediaPlayback` is banned from `BOOT_COMPLETED` on Android 15; `connectedDevice` is correct and unrestricted; declare `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission in manifest and pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` to `startForeground()`

3. **BLUETOOTH_CONNECT not requested at runtime on Android 12+** — declare in manifest AND request at runtime before any BluetoothAdapter call; omission causes silent `null` device names or `SecurityException` crash

4. **A2DP receiver misses boot-time connection state** — `ACTION_CONNECTION_STATE_CHANGED` is not sticky; proactively query `BluetoothA2dp` profile proxy state in `onStartCommand` rather than waiting for the next event; covers "already connected at boot" case

5. **OEM battery optimisation kills the foreground service** — no programmatic fix for Xiaomi/Huawei; add user-facing onboarding on first launch prompting for battery optimisation exemption and Xiaomi Autostart permission; implement `onTaskRemoved` restart receiver as last resort

6. **AudioEffect invalidated on audio server restart** — register `OnControlStatusChangeListener`; on `controlGranted=false`, release and recreate the effect; always wrap `AudioEffect` constructor in `try/catch(RuntimeException)`

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: AudioEffect Feasibility POC
**Rationale:** The entire project hinges on whether `AudioEffect` session 0 produces an audible balance shift on the target hardware. This is the single highest-risk unknown; all other phases are moot if this fails. Architecture research explicitly flags this as the first build step.
**Delivers:** Validated audio effect approach (session 0 confirmed, or fallback strategy selected); `AudioEffectManager` skeleton with session 0 + per-session-ID fallback code paths
**Addresses:** AudioEffect feasibility risk (FEATURES.md critical note; PITFALLS.md Pitfall 1)
**Avoids:** Building a full service + UI on a foundation that may not work on the target device

### Phase 2: Core Data + Persistence Layer
**Rationale:** `DeviceSettingsRepository` is a dependency of both the service and ViewModel; building it early enables all other phases to be built in correct dependency order. No Android service dependencies — testable in isolation.
**Delivers:** Room database schema (or DataStore-only if Room feels like overengineering), `DeviceSettings` data model (MAC, balance float, enabled bool), Repository with `Flow<List<DeviceSettings>>`, `DataStoreManager`
**Uses:** Room 2.8.4 + DataStore 1.1.2 + KSP (STACK.md)
**Implements:** `data/` layer (ARCHITECTURE.md)

### Phase 3: Foreground Service + Bluetooth Detection
**Rationale:** With AudioEffect validated (Phase 1) and persistence in place (Phase 2), the service can be built against real dependencies. The receiver, boot logic, and permission flow are tightly coupled and should be built together to test the full connect → effect-applied path.
**Delivers:** `AudioControlService` with `connectedDevice` foreground type; `BTConnectionReceiver`; `BootReceiver`; BLUETOOTH_CONNECT runtime permission flow; proactive A2DP state query on service start; notification with device name and balance
**Uses:** `connectedDevice` FGS type, `ServiceCompat.startForeground()`, `BluetoothA2dp` API (STACK.md)
**Implements:** Full BT connection → effect applied data flow (ARCHITECTURE.md)
**Avoids:** Pitfalls 2, 3, 4, 6 (service type, BT permission, missed boot state, AudioEffect server restart)

### Phase 4: UI Layer
**Rationale:** UI depends on ViewModel which depends on Repository (Phase 2) and Service (Phase 3); build last. Compose screens are presentation-only and have no blocking unknowns.
**Delivers:** `MainActivity`, `DeviceListScreen` (device list + balance sliders), `SettingsScreen` (global toggle), `MainViewModel` with `StateFlow<UiState>`, bound-service connection to `AudioControlService`
**Uses:** Jetpack Compose BOM 2026.03.00, Material3, Hilt navigation-compose (STACK.md)
**Implements:** UI layer + ViewModel (ARCHITECTURE.md)

### Phase 5: Polish + OEM Hardening
**Rationale:** OEM battery optimisation issues (Xiaomi/Samsung) and the secondary feature set (JSON export, notification actions, timestamps) should be added after core functionality is validated end-to-end.
**Delivers:** OEM battery optimisation onboarding flow (Xiaomi Autostart prompt, battery exemption request); JSON export/import with input validation; "Apply now" notification action; last-applied timestamp; `onTaskRemoved` restart receiver
**Addresses:** v1.x features from FEATURES.md; Pitfall 5 (OEM kill); PITFALLS.md security note on JSON import validation
**Avoids:** Shipping to Xiaomi/Samsung users with silent service death and no guidance

### Phase Ordering Rationale

- Phase 1 must come first because the audio effect approach is the project's single architectural decision point; the two fallback strategies (session 0 vs per-session) require different code in `AudioEffectManager`
- Phase 2 before Phase 3 because the service needs a working repository to look up balance coefficients; building with hardcoded values first is a false start
- Phase 3 before Phase 4 because ViewModel binds to the service — it needs a real service to bind to
- Phase 5 last because OEM hardening is additive and does not change core architecture; export/import requires stable data model from Phase 2

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1 (AudioEffect POC):** Session 0 behavior is device-specific and under-documented; hands-on physical device testing is the only reliable source of truth; may need to research `AudioManager.getActivePlaybackConfigurations()` API for per-session fallback implementation details
- **Phase 3 (Foreground Service + BT):** BOOT_COMPLETED + foreground service interaction on Android 15 has nuanced restrictions; `CompanionDeviceManager` exemption path may need investigation if `connectedDevice` type proves insufficient

Phases with standard patterns (skip research-phase):
- **Phase 2 (Data layer):** Room + DataStore patterns are extremely well documented; no unknowns
- **Phase 4 (UI):** Jetpack Compose + ViewModel + StateFlow is the canonical Android UI pattern; no architectural unknowns
- **Phase 5 (Hardening):** OEM-specific guidance is fully documented in PITFALLS.md; JSON export/import is straightforward

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM-HIGH | All library versions verified against official release notes; version compatibility matrix is confirmed; AudioEffect API status is LOW confidence due to OEM variability |
| Features | MEDIUM | Table stakes derived from competitor analysis (Wavelet, Precise Volume) and user expectations; AudioEffect feasibility flag is well-reasoned |
| Architecture | MEDIUM-HIGH | Component boundaries and data flows are HIGH confidence; AudioEffect session approach decision is MEDIUM due to deprecation ambiguity |
| Pitfalls | MEDIUM-HIGH | Core Android API pitfalls are HIGH (official docs); OEM-specific kill mechanics are MEDIUM (community-maintained sources, no official OEM docs) |

**Overall confidence:** MEDIUM

### Gaps to Address

- **AudioEffect session 0 on target hardware**: Cannot be resolved by research alone — requires physical device testing in Phase 1. Decision point: if session 0 fails, Phase 3 service architecture changes (must maintain a `Map<Int, AudioEffect>` per session instead of a single instance)
- **Android 15 BOOT_COMPLETED + connectedDevice interaction**: Research indicates `connectedDevice` is unrestricted, but this should be verified on an Android 15 device early in Phase 3
- **Per-channel EQ manipulation**: Research flagged that achieving true left/right balance via public `Equalizer` bands is not straightforward; the POC must determine the exact band manipulation strategy that produces audible balance (not just `setEnabled(true)`)
- **Multiple simultaneous BT devices**: Architecture suggests a `Map<String, AudioEffect>` keyed by MAC; the policy for handling simultaneous connections (apply most-recently-connected, apply all, etc.) is not resolved in research

## Sources

### Primary (HIGH confidence)
- [Android Bluetooth permissions docs](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) — BLUETOOTH_CONNECT runtime requirement for API 31+
- [Foreground service types reference](https://developer.android.com/develop/background-work/services/fgs/service-types) — `connectedDevice` type confirmed for BT monitoring
- [Foreground service types required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required) — MissingForegroundServiceTypeException behavior
- [Android 15 foreground service types changes](https://developer.android.com/about/versions/15/changes/foreground-service-types) — BOOT_COMPLETED restrictions
- [Implicit broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions) — A2DP broadcast exemption confirmed
- [BluetoothA2dp API reference](https://developer.android.com/reference/android/bluetooth/BluetoothA2dp) — ACTION_CONNECTION_STATE_CHANGED, EXTRA_STATE, EXTRA_DEVICE
- [Compose BOM reference](https://developer.android.com/develop/ui/compose/bom) — BOM 2026.03.00 confirmed latest stable
- [AGP release notes](https://developer.android.com/build/releases/about-agp) — AGP 9.1.0 + Kotlin 2.3.10 confirmed stable
- [Room release notes](https://developer.android.com/jetpack/androidx/releases/room) — Room 2.8.4 confirmed latest stable

### Secondary (MEDIUM confidence)
- [Esper: Why Android Equalizer Apps Don't Work with All Media Players](https://www.esper.io/blog/android-equalizer-apps-inconsistent) — session 0 vs per-session tradeoffs
- [Wavelet GitHub Issue #312](https://github.com/Pittvandewitt/Wavelet/issues/312) — real-world session 0 failure on MIUI 14 / Android 13
- [Equalizer broadcast tracker](https://github.com/pinpong/equalizer-issue-tracker/blob/master/EQUALIZER_BROADCAST.md) — per-session session ID broadcast protocol
- [Don't Kill My App — Xiaomi](https://dontkillmyapp.com/xiaomi) — OEM kill mechanics and Autostart requirement
- [Don't Kill My App — Samsung](https://dontkillmyapp.com/samsung) — One UI foreground service history
- [Wavelet features documentation](https://pittvandewitt.github.io/Wavelet/Features/) — competitor feature reference
- [Precise Volume 2.0 features documentation](https://precisevolume.phascinate.com/docs/features/) — competitor feature reference

### Tertiary (LOW confidence)
- [Google Issue Tracker #36936557](https://issuetracker.google.com/issues/36936557) — session 0 deprecation history; issue status unclear, no clean migration path
- [androidx/media GitHub issue #310](https://github.com/androidx/media/issues/310) — ChannelMixingAudioProcessor as balance alternative; only for apps controlling their own playback, not applicable here
- [Android AOSP commit 2fb43ef8](https://android.googlesource.com/platform/frameworks/base/+/2fb43ef8) — AudioEffect native crash on mediaserver restart; existence confirms recovery listener is needed

---
*Research completed: 2026-04-01*
*Ready for roadmap: yes*
