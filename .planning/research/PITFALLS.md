# Pitfalls Research

**Domain:** Android background service — Bluetooth A2DP monitoring + system-wide AudioEffect balance control
**Researched:** 2026-04-01
**Confidence:** MEDIUM-HIGH (core Android API facts are HIGH; OEM-specific behaviour is MEDIUM due to lack of official OEM documentation)

---

## Critical Pitfalls

### Pitfall 1: AudioEffect Session 0 Is Silently Ignored on Many Devices

**What goes wrong:**
`AudioEffect` constructed with `audioSession = 0` attaches to the global output mix. On paper it still compiles and does not throw. In practice it silently does nothing on a significant fraction of devices running Android 10+, and its behaviour varies by OEM audio HAL implementation. Apps that test only on Pixel devices (where it often still works) ship code that breaks on Samsung One UI, MIUI/HyperOS, and others.

**Why it happens:**
Google deprecated session-0 global effects shortly after Android 2.3 but never removed the API. OEMs progressively cut support through their audio HALs without announcement. The Wavelet equalizer maintainer confirmed in 2023 (GitHub issue #312) that on MIUI 14 / Android 13, legacy session-0 mode no longer reaches apps that are not using `AudioTrack` directly.

**How to avoid:**
- Treat session 0 as a best-effort path only. Build the POC/feasibility phase as a real device matrix test: Pixel (stock), Samsung One UI, Xiaomi MIUI/HyperOS.
- If session 0 fails on the target device, the fallback is `Equalizer` attached per audio-session-ID (received via `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcasts from media player apps). This covers most music apps but not all system sounds.
- Do not rely on session 0 producing any result without confirming on the exact target hardware.
- The `MODIFY_AUDIO_SETTINGS` permission is required regardless and must be declared in the manifest.

**Warning signs:**
- Balance change has no audible effect during development on non-Pixel device.
- No exception is thrown — silence is the only signal.
- Effect `getEnabled()` returns `true` but audio is unmodified.

**Phase to address:**
Phase 1 (Feasibility / POC) — must be validated before committing the architecture. This is the project's single highest-risk unknown.

---

### Pitfall 2: Wrong Foreground Service Type Causes ForegroundServiceStartNotAllowedException

**What goes wrong:**
Developers building an audio-adjacent app default to `foregroundServiceType="mediaPlayback"`. This app does NOT play media — it monitors Bluetooth connections and applies effects. Using `mediaPlayback` is semantically incorrect and additionally triggers the Android 15 restriction that bans `mediaPlayback` services from starting via `BOOT_COMPLETED`.

**Why it happens:**
"It touches audio, so mediaPlayback seems right." The distinction between playing audio and modifying audio routing is subtle and poorly documented in tutorials.

**How to avoid:**
Use `foregroundServiceType="connectedDevice"` — this type covers "interactions with external devices requiring Bluetooth connection." It is NOT restricted from `BOOT_COMPLETED` receivers in Android 15. Required permissions:
```xml
<!-- Manifest -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<!-- Runtime (Android 12+) — already needed for BT -->
<!-- BLUETOOTH_CONNECT satisfies the connectedDevice prerequisite -->
```
Call `startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)` explicitly from the service.

**Warning signs:**
- `ForegroundServiceStartNotAllowedException` in logs on Android 14+ or 15+ devices at boot.
- Google Play review rejection citing incorrect service type (not applicable here — sideload only — but good to know).

**Phase to address:**
Phase 2 (Foreground Service + Boot) — manifest and service declaration.

---

### Pitfall 3: BLUETOOTH_CONNECT Permission Not Requested at Runtime Causes Silent Failure on Android 12+

**What goes wrong:**
Pre-Android 12 code only declares `BLUETOOTH` and `BLUETOOTH_ADMIN` in the manifest. On Android 12+ (API 31+), querying paired devices, reading device names, or interacting with `BluetoothA2dp` profile raises `SecurityException` without `BLUETOOTH_CONNECT` being granted at runtime.

**Why it happens:**
Android 12 completely overhauled Bluetooth permissions. `BLUETOOTH` and `BLUETOOTH_ADMIN` no longer grant device access. `BLUETOOTH_CONNECT` is a new `PROTECTION_DANGEROUS` (runtime) permission requiring explicit user grant. Many older code samples and Stack Overflow answers predate this change.

**How to avoid:**
- Declare `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` in the manifest with `android:maxSdkVersion` guards where appropriate.
- Request `BLUETOOTH_CONNECT` at runtime before calling ANY `BluetoothAdapter` or profile proxy method on API 31+.
- Use `ActivityCompat.checkSelfPermission` gating before any BT operation.
- The permission is also required to read device names in `BluetoothDevice.getName()` — omitting it produces null names, not a crash.

**Warning signs:**
- `SecurityException: Need android.permission.BLUETOOTH_CONNECT` in logcat.
- Device name shows as `null` in the UI despite device being paired.
- BT profile proxy callbacks never fire.

**Phase to address:**
Phase 2 (Bluetooth detection) — implement the runtime permission flow in the same phase as BT monitoring.

---

### Pitfall 4: A2DP BroadcastReceiver Registered in Manifest Misses Boot-Time Connection Events

**What goes wrong:**
A manifest-declared `BroadcastReceiver` for `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` misses events that happen before the system finishes booting, or events that occur before the app process has started after `BOOT_COMPLETED`. If the device was already connected to BT headphones before the service started, the initial state is never received.

**Why it happens:**
`ACTION_CONNECTION_STATE_CHANGED` is not a sticky broadcast. A receiver that registers after the connection state changed will never see the prior state transition. Also, on Android 8+ background execution limits mean a manifest receiver for non-exempt implicit broadcasts may not be delivered while the app is fully stopped.

`BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` is on the implicit broadcast exception list, so manifest registration does work — but the receiver must proactively query current connection state at startup instead of waiting for the next event.

**How to avoid:**
1. In `onStartCommand` of the foreground service, proactively query current A2DP state via `BluetoothA2dp` profile proxy immediately after the service starts.
2. Register the receiver dynamically (`Context.registerReceiver`) inside the service for runtime connection events.
3. On `BOOT_COMPLETED`, start the foreground service, then query initial BT state — do not rely solely on broadcast delivery ordering.

**Warning signs:**
- Balance is not applied if the headphones were already connected before the phone booted.
- Balance is not applied on the very first connection after app install (service not yet running).

**Phase to address:**
Phase 2 (Bluetooth detection) — service startup logic must include proactive state query.

---

### Pitfall 5: OEM Battery Optimisation Kills the Foreground Service Anyway

**What goes wrong:**
Despite being a foreground service with a persistent notification, the service is killed by OEM battery-management layers on Xiaomi (MIUI/HyperOS), Huawei (EMUI), Samsung (One UI before 6.0), and Oppo/Vivo/Realme devices. The service is stopped silently — no exception, no log entry visible to the app.

**Why it happens:**
These OEMs layer proprietary background-kill mechanics on top of standard Android foreground service guarantees. Xiaomi in particular requires a separate "Autostart" permission that is off by default. Samsung before One UI 6.0 restricted wake locks in foreground services. None of these have public APIs.

**How to avoid:**
- **Xiaomi**: There is no programmatic fix. Use the library at `github.com/XomaDev/MIUI-autostart` to detect whether Autostart is enabled and show an in-app prompt directing the user to `Settings > Apps > [App] > App permissions > Background autostart`. Also prompt the user to add the app to the "lock" list in the recent apps tray.
- **Samsung One UI 6.0+**: Targeting Android 14 (`targetSdk=34`) with correct `connectedDevice` type gives the OS-level guarantee introduced in One UI 6.0.
- **All OEMs**: On first launch, prompt the user to disable battery optimisation via `Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)`. This is allowed for sideloaded apps (Play Store restricts this permission request).
- Include in-app guidance referencing `dontkillmyapp.com` per-manufacturer steps.
- Implement `onTaskRemoved` and a restart `BroadcastReceiver` as a last-resort self-restart mechanism (unreliable on Xiaomi but better than nothing).

**Warning signs:**
- Balance stops applying after the phone screen is off for 10–30 minutes.
- Notification disappears without user dismissal.
- No crash log — service simply stops.

**Phase to address:**
Phase 3 (Hardening / OEM compatibility) — after core functionality works, add OEM-specific onboarding flows.

---

### Pitfall 6: AudioEffect Instance Is Silently Destroyed After Audio Server Restart

**What goes wrong:**
The Android `mediaserver`/`audioserver` process can crash and restart (rare but happens on some devices after phone calls, BT reconnections, or system updates). When this occurs, all native `AudioEffect` handles are invalidated. The Java object still exists but all operations become no-ops or throw `IllegalStateException`. The balance silently reverts to 0 until the app is restarted.

**Why it happens:**
`AudioEffect` wraps a native resource tied to the audio server process. There is a known bug (Android platform commit `2fb43ef8`) where re-constructing an `AudioEffect` during `mediaserver` restart while in JNI critical state causes a crash. Even without that crash, the effect object is orphaned.

**How to avoid:**
- Register an `AudioEffect.OnControlStatusChangeListener` — the callback fires when the effect loses control or the server restarts.
- On receiving `controlGranted = false`, schedule re-application: tear down the existing `AudioEffect` instance, call `release()`, recreate and re-enable.
- Always call `release()` on the existing instance before creating a new one to avoid native resource leaks.
- Wrap AudioEffect construction in try-catch for `RuntimeException` (the constructor throws this, not a checked exception, if the effect cannot be created).

**Warning signs:**
- `IllegalStateException` in `AudioEffect` method calls appearing in crash logs.
- Balance silently drops to centre after a phone call or BT reconnect.
- Logcat shows `audioserver` restarting: `"AudioFlinger serverDied()"`

**Phase to address:**
Phase 2 (AudioEffect integration) — implement the listener and recovery path during initial implementation, not as an afterthought.

---

### Pitfall 7: Android 13 Restricts Accessibility Service for Sideloaded Apps Requiring Extra User Step

**What goes wrong:**
If the AccessibilityService approach is chosen as the balance implementation method (fallback when AudioEffect fails), Android 13+ marks the service as "restricted" for apps installed via non-session-based package installers (direct APK sideload via file manager). The Accessibility settings entry is greyed out with "for your security, this setting is currently unavailable."

**Why it happens:**
Android 13 introduced "Restricted settings" to prevent malware installed via APK from abusing Accessibility APIs. Apps installed via ADB (`adb install`) are NOT subject to this restriction — only apps installed by tapping an APK file directly.

**How to avoid:**
- **Use ADB for installation**: `adb install app.apk` bypasses the restricted-settings flag. This is already the stated deployment method (USB direct). Document this requirement explicitly.
- **Do not use file-manager install**: Even for personal use, installing via file manager triggers the restriction.
- Note: even with ADB install, the user must still manually enable the Accessibility Service in Settings — there is no programmatic way to enable it.
- Also note: Google Play policy (January 2026 enforcement) tightened Accessibility API restrictions, but this is irrelevant for sideloaded apps.

**Warning signs:**
- Accessibility service entry appears greyed out in Settings after install.
- User reports "I can't enable it" — the restriction is not visible as an error in the app itself.

**Phase to address:**
Phase 1 (Feasibility) — the deployment method (ADB vs. file sideload) must be decided and documented before committing to AccessibilityService as a fallback.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Assume session 0 always works without device matrix test | Saves POC time | Ships broken on half of real-world devices | Never — test first |
| Use `mediaPlayback` service type because it "sounds right" | No immediate issue on Android < 15 | `ForegroundServiceStartNotAllowedException` on Android 15+ at boot | Never |
| Skip runtime permission check for BLUETOOTH_CONNECT on < API 31 | Simpler code path | `SecurityException` crash on API 31+ | Never — use `Build.VERSION.SDK_INT` guard |
| Register BT receiver in manifest only, no proactive state query | Less code | Initial state missed on every boot | Never |
| Skip `AudioEffect.release()` call | Slightly simpler lifecycle | Native audio server resource leak, potential crash on reconnect | Never |
| Skip OEM battery optimisation guidance in onboarding | Faster first launch | Service silently dies on Xiaomi/Huawei; user thinks app is broken | Only acceptable in early POC, not in any shipped build |
| Hard-code balance coefficient without per-MAC-address storage | Simpler MVP | Only one device supported; core requirement violated | Never |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| `BluetoothA2dp` profile proxy | Querying profile state immediately in `onReceive` before proxy is bound | Use `getProfileProxy()` callback; cache the proxy reference after `onServiceConnected` |
| `AudioEffect` constructor | Not catching `RuntimeException` — it throws this (not a checked exception) if effect creation fails | Wrap in `try { ... } catch (RuntimeException e)` and handle gracefully |
| `startForeground()` on Android 12+ | Calling without the `foregroundServiceType` parameter in the method call | Always pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` as third argument |
| `BOOT_COMPLETED` receiver | Starting service synchronously without checking if it is already running | Check `isServiceRunning()` or use `startForegroundService()` idempotently |
| `BluetoothDevice.getName()` | Called without `BLUETOOTH_CONNECT` grant on API 31+ — returns null silently | Gate all `BluetoothDevice` name reads behind permission check |
| `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent | Forgetting to declare `REQUEST_INSTALL_PACKAGES` or check current exemption state first | Check `PowerManager.isIgnoringBatteryOptimizations(packageName)` before requesting |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Polling Bluetooth connection state in a loop | Battery drain, wakeups, OEM aggressive-kill trigger | Use event-driven `BroadcastReceiver` only — no polling | Immediately at scale of 1 device |
| Creating a new `AudioEffect` on every BT connection event without releasing the old one | Audio glitches, native memory growth, eventual crash | Keep a single `AudioEffect` instance; release and recreate only when needed | After ~10 reconnect cycles |
| Holding `BluetoothA2dp` profile proxy open after use and never closing it | Minor resource leak, possible stale state | Call `adapter.closeProfileProxy(PROFILE, proxy)` when no longer needed or in service `onDestroy` |  Long-running sessions |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Storing per-device balance config in shared external storage | Another app reads/modifies balance coefficients | Use `Context.getFilesDir()` (internal storage) or `SharedPreferences` — not `Environment.getExternalStorageDirectory()` |
| Exporting JSON config without input validation on import | Malformed import crashes app or injects unexpected values | Validate all fields on import: MAC address format, balance value range [-1.0, 1.0] |
| Broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` receiver without permission check | Any app can spoof session IDs and trigger effect re-application | This is a standard Android broadcast — not a security risk in this context since no sensitive data is exposed |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No onboarding for OEM battery optimisation | Service gets killed; user thinks app is broken, uninstalls | Show a one-time prompt on first launch for Xiaomi/Samsung/Huawei detected devices with direct deep-link to the relevant settings screen |
| Applying balance immediately on slider drag (no confirmation) | Accidental extreme L/R balance with no way to recover without app open | Apply balance only on slider release (`onStopTrackingTouch`); add "Reset all" action |
| Silent failure when AudioEffect session 0 does nothing | User configures balance, hears no change, doesn't know why | Include a "test balance" button that plays a short sine wave through the AudioTrack used to verify the effect chain works |
| Notification with no actionable content | User dismisses notification, service moves to background (not possible, but feels intrusive) | Notification should show current active device name and balance value; include a "Disable" quick action |
| No indication that the service is active and monitoring | User can't tell if auto-apply is working | Show current BT device in notification; update notification text when device connects/disconnects |

---

## "Looks Done But Isn't" Checklist

- [ ] **AudioEffect balance**: Effect is "enabled" but verify with actual audio output on the physical target device — not just emulator or Pixel
- [ ] **Boot persistence**: Test by fully rebooting the device with BT headphones already paired; service must apply balance without any user interaction
- [ ] **Permission flow on Android 12+**: Test fresh install with all permissions denied, then grant one by one — no crash at any step
- [ ] **Foreground service type**: Verify `adb shell dumpsys activity services [package]` shows `type=connectedDevice` not `type=none`
- [ ] **OEM kill resilience on Xiaomi**: Test with Autostart disabled — app must detect this and prompt the user
- [ ] **AudioEffect release on service stop**: Verify no `AudioEffect` native leak after `onDestroy` via `adb shell dumpsys media.audio_flinger`
- [ ] **JSON import validation**: Import a file with an out-of-range balance value (e.g., 5.0) — must be rejected gracefully
- [ ] **Multiple BT devices**: Connect device A (balance set), disconnect, connect device B (different balance), disconnect, reconnect A — verify correct balance each time

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Session 0 AudioEffect confirmed non-functional on target device | HIGH | Pivot to per-session-ID approach: register for `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`, apply effect to each received session. Covers music apps but not system sounds. |
| Wrong foreground service type deployed | LOW | Change manifest `foregroundServiceType`, update `startForeground()` call, rebuild, redeploy via ADB |
| BLUETOOTH_CONNECT permission crash on Android 12 | LOW | Add runtime permission request before BT operations; rebuild |
| Service killed on Xiaomi despite foreground | MEDIUM | Add OEM detection + user guidance screen; this cannot be fixed programmatically — requires user action |
| AudioEffect not releasing, audio server crash | MEDIUM | Add `OnControlStatusChangeListener`, implement full release/recreate cycle, add try-catch around constructor |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Session 0 AudioEffect unreliable across OEMs | Phase 1 — Feasibility POC | Test on Pixel + Samsung + Xiaomi physical devices; confirm audible effect before proceeding |
| Wrong foreground service type | Phase 2 — Foreground Service implementation | `adb shell dumpsys activity services` shows correct type; test boot on Android 15 device |
| BLUETOOTH_CONNECT missing runtime request | Phase 2 — Bluetooth detection | Test fresh install on API 31+ device with all permissions denied |
| A2DP receiver misses boot-time state | Phase 2 — Bluetooth detection | Reboot test with headphones pre-connected |
| OEM battery kill (Xiaomi/Samsung) | Phase 3 — Hardening | Test on physical Xiaomi with Autostart disabled; verify prompt is shown |
| AudioEffect destroyed on server restart | Phase 2 — AudioEffect integration | Implement `OnControlStatusChangeListener` before considering feature complete |
| Accessibility restriction for sideloaded APK | Phase 1 — Feasibility (if AccessibilityService chosen as fallback) | Install via `adb install`, verify accessibility entry is not greyed out |

---

## Sources

- [Esper: Why Android Equalizer Apps Don't Work with All Media Players](https://www.esper.io/blog/android-equalizer-apps-inconsistent) — session 0 deprecation history
- [Google Issue Tracker #36936557: Equalizer on audio session 0 should not be deprecated](https://issuetracker.google.com/issues/36936557) — community position
- [Wavelet GitHub Issue #312: Legacy Mode and Enhanced session detection not working on Android 13](https://github.com/Pittvandewitt/Wavelet/issues/312) — real-world failure confirmation on MIUI 14
- [Android Developers: Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) — `connectedDevice` type documentation (HIGH confidence)
- [Android Developers: Changes to foreground service types for Android 15](https://developer.android.com/about/versions/15/changes/foreground-service-types) — BOOT_COMPLETED restrictions list (HIGH confidence)
- [Android Developers: Restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) — exceptions list (HIGH confidence)
- [Android Developers: Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) — BLUETOOTH_CONNECT requirement (HIGH confidence)
- [Don't Kill My App — Xiaomi](https://dontkillmyapp.com/xiaomi) — OEM kill mechanics (MEDIUM confidence — community-maintained)
- [Don't Kill My App — Samsung](https://dontkillmyapp.com/samsung) — One UI foreground service history
- [Esper: Android 13 sideloading restriction and Accessibility APIs](https://www.esper.io/blog/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis) — sideload + AccessibilityService restriction
- [Android AOSP commit 2fb43ef8: AudioEffect native crash on mediaserver restart](https://android.googlesource.com/platform/frameworks/base/+/2fb43ef8c0b922c1bd0d7cb6867e30d702d4bdb8%5E!/) — native resource lifecycle bug
- [Android Developers: Implicit broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions) — A2DP broadcast exemption status

---
*Pitfalls research for: Android Bluetooth audio balance controller*
*Researched: 2026-04-01*
