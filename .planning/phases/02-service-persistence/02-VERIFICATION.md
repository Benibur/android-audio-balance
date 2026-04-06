---
phase: 02-service-persistence
verified: 2026-04-04T12:00:00Z
status: human_needed
score: 5/5 must-haves verified
re_verification: false
human_verification:
  - test: "Reboot test — service auto-starts at boot and applies stored balance"
    expected: "After phone reboot with BT headphones connected, BootReceiver fires, AudioBalanceService starts, and stored balance is re-applied without the user opening the app. Logcat shows 'BOOT_COMPLETED received' then 'Balance applied: mac=... balance=-50'"
    why_human: "Plan 03 SUMMARY explicitly notes the reboot test (Test 3) was skipped — user chose not to run it. BootReceiver code is correct and wired, but on-device validation has not been performed. This is the only gap between code correctness and confirmed end-to-end behavior."
---

# Phase 02: Service + Persistence — Verification Report

**Phase Goal:** The app silently monitors Bluetooth A2DP connections, persists per-device balance coefficients, and applies the correct balance automatically when a known device connects — all while running as a foreground service.

**Verified:** 2026-04-04
**Status:** human_needed — all automated checks pass; one human verification outstanding (reboot path not tested on device)
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (from Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | When a BT A2DP device connects, the app detects the connection and logs the device MAC address without any user action | VERIFIED | `BtA2dpReceiver.onReceive()` extracts MAC via `device.address`, logs `A2DP state=$state device=${device?.address}`, passes to `handleBtEvent()`. SUMMARY 02-02: "A2DP state=2 detected, MAC=04:52:C7:60:C8:72" confirmed on Pixel 10. |
| 2 | A balance coefficient saved for a device MAC persists after the app is killed and the phone is restarted | VERIFIED (kill only) | DataStore write confirmed in `BalanceRepository.saveBalance()`. SUMMARY 02-03: balance=-50 survived force-stop, verified via logcat + ear test on Deezer/YouTube. **Reboot path: BootReceiver code is correct and wired in manifest, but on-device reboot test was explicitly skipped — see Human Verification section.** |
| 3 | When a known BT device connects, the previously saved balance coefficient is applied automatically to the audio output | VERIFIED | `applyDeviceBalance()` calls `balanceRepository.getBalance(mac)`, maps via `BalanceMapper.toGainDb()`, applies via `dp.setInputGainbyChannel(0, leftDb)` / `setInputGainbyChannel(1, rightDb)`. SUMMARY 02-02: "setInputGainbyChannel (L=0.0dB R=0.0dB)" confirmed. SUMMARY 02-03: balance=-50 confirmed as L=0.0dB R=-30.0dB via ear test. |
| 4 | A persistent foreground notification is visible while the service is running, showing the connected device name and active balance value | VERIFIED | `createNotificationChannel()` creates `IMPORTANCE_LOW` channel. `startForeground(NOTIFICATION_ID, buildNotification(...), FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)`. `updateNotification(formatNotificationText(deviceName, balance))` called on connect. SUMMARY 02-02: "Bose QC35 Ben • Balance: Center" confirmed on device. |
| 5 | When a BT device disconnects, the balance effect is removed and the notification updates accordingly | VERIFIED | `STATE_DISCONNECTED` branch in `handleBtEvent()` launches `disconnectJob` with `delay(2000L)`, calls `resetBalanceToCenter()` (sets both channels to 0f), then `updateNotification("No device connected")`. SUMMARY 02-02: "A2DP state=0 detected, balance reset to center after 2s delay" confirmed on device. |

**Score:** 5/5 truths verified (with reboot sub-path of truth 2 pending human confirmation)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt` | Foreground service with DP instance, notification, START_STICKY, BT handling | VERIFIED | 317 lines; `LifecycleService` subclass; `DynamicsProcessing(0, 0, config)` all-false config; `startForeground` with `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`; `START_STICKY`; `handleBtEvent`, `applyDeviceBalance`, `resetBalanceToCenter`, `registerBtReceiver`, `checkCurrentlyConnectedDevices` all present and substantive. |
| `app/src/main/java/com/audiobalance/app/util/BalanceMapper.kt` | Balance -100..+100 to dB pair conversion | VERIFIED | `toGainDb(balance: Int): Pair<Float, Float>` using -60f attenuation confirmed in file. |
| `app/src/main/java/com/audiobalance/app/data/DevicePreferences.kt` | Single DataStore delegate | VERIFIED | Single `val Context.dataStore by preferencesDataStore(name = "device_balance")` — exactly one delegate in the project. |
| `app/src/main/java/com/audiobalance/app/data/BalanceRepository.kt` | MAC-keyed balance read/write | VERIFIED | `getBalance(mac)` returns 0f for unknown; `saveBalance(mac, balance)` writes float; `getAllBalances()` flow present; uses `floatPreferencesKey("balance_${mac.replace(":", "_")}")`. |
| `app/src/main/java/com/audiobalance/app/service/BtA2dpReceiver.kt` | BroadcastReceiver for A2DP state changes | VERIFIED | Handles `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`; extracts `BluetoothDevice` via API 33+ branch with deprecated fallback; passes `(device, state)` to lambda. |
| `app/src/main/java/com/audiobalance/app/receiver/BootReceiver.kt` | BOOT_COMPLETED receiver | VERIFIED | Guards on `Intent.ACTION_BOOT_COMPLETED`; calls `context.startForegroundService(serviceIntent)` targeting `AudioBalanceService`. |
| `app/src/main/AndroidManifest.xml` | All permissions + service + BootReceiver declarations | VERIFIED | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` all present. Service declared with `foregroundServiceType="connectedDevice"` and `exported="true"`. BootReceiver declared with `exported="true"` and BOOT_COMPLETED intent-filter. |
| `gradle/libs.versions.toml` | DataStore 1.2.1 and lifecycle-service 2.10.0 entries | VERIFIED | `datastore = "1.2.1"`, `lifecycleService = "2.10.0"`, `androidx-datastore-preferences` and `androidx-lifecycle-service` library entries all confirmed. |
| `app/build.gradle.kts` | DataStore and lifecycle-service dependencies | VERIFIED | `implementation(libs.androidx.datastore.preferences)` at line 50; `implementation(libs.androidx.lifecycle.service)` at line 51. |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AudioBalanceService.onCreate()` | `DynamicsProcessing(0, 0, config)` | `createDpInstance()` called in `onCreate` | WIRED | Line 63: `createDpInstance()` called after `startForeground`. Inside: `DynamicsProcessing(0, 0, config)` with all 4 stage flags false. |
| `AudioBalanceService.onCreate()` | `startForeground()` with `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` | API Q branch | WIRED | Lines 53-61: `startForeground(NOTIFICATION_ID, buildNotification("Starting..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)` on SDK >= Q. |
| `MainActivity` | `AudioBalanceService` | `startForegroundService()` | WIRED | Line 61 in MainActivity: `startForegroundService(serviceIntent)` with explicit `Intent(this, AudioBalanceService::class.java)`. |
| `BtA2dpReceiver.onReceive()` | `AudioBalanceService.handleBtEvent()` | callback lambda passed at construction | WIRED | `BtA2dpReceiver { device, state -> handleBtEvent(device, state) }` in `registerBtReceiver()`. Lambda invoked as `onEvent(device, state)` in receiver. |
| `AudioBalanceService.handleBtEvent()` | `BalanceRepository.getBalance(mac)` | suspend call inside coroutine launch | WIRED | `applyDeviceBalance(device)` calls `balanceRepository.getBalance(mac)` — confirmed in source. |
| `AudioBalanceService.applyDeviceBalance()` | `dp.setInputGainbyChannel()` | `BalanceMapper.toGainDb()` then DP channel set | WIRED | `BalanceMapper.toGainDb(balance.toInt())` destructured to `(leftDb, rightDb)`, then `it.setInputGainbyChannel(0, leftDb)` / `it.setInputGainbyChannel(1, rightDb)`. |
| `AudioBalanceService.handleBtEvent(STATE_CONNECTED)` | `delay(1000L)` | reconnectJob coroutine | WIRED | Line 191: `delay(1000L)` inside `reconnectJob = serviceScope.launch { ... }` in STATE_CONNECTED branch. |
| `AudioBalanceService.handleBtEvent(STATE_DISCONNECTED)` | `delay(2000L)` | disconnectJob coroutine, cancelled on reconnect | WIRED | Line 199: `delay(2000L)` inside `disconnectJob = serviceScope.launch { ... }`. Line 186: `disconnectJob?.cancel()` in STATE_CONNECTED branch (micro-disconnect protection). |
| `BootReceiver.onReceive()` | `AudioBalanceService` | `startForegroundService(intent)` | WIRED | Line 19 in BootReceiver: `context.startForegroundService(serviceIntent)` where intent targets `AudioBalanceService::class.java`. |
| `AudioBalanceService.checkCurrentlyConnectedDevices()` | `BalanceRepository.getBalance()` | via `applyDeviceBalance` call from proxy callback | WIRED | Proxy callback sets `currentDeviceMac`, `currentDeviceName`, then calls `applyDeviceBalance(device)` inside `serviceScope.launch`. Fix `3bedc36` confirmed this sets the mac before the coroutine runs. |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| AUDIO-02 | 02-02 | Balance coefficient applied system-wide when BT device connected | SATISFIED | `applyDeviceBalance()` calls `dp.setInputGainbyChannel()` on connect. Device-confirmed PASS in SUMMARY 02-02. |
| BT-01 | 02-02 | App detects A2DP BT connections (MAC address) | SATISFIED | `BtA2dpReceiver` fires on `ACTION_CONNECTION_STATE_CHANGED` STATE_CONNECTED, logs `mac=$mac`. Confirmed on Pixel 10 / Bose QC35. |
| BT-02 | 02-02 | App detects A2DP BT disconnections | SATISFIED | STATE_DISCONNECTED branch in `handleBtEvent()`, 2s delay, `resetBalanceToCenter()`. Confirmed on device. |
| BT-03 | 02-02 | Stored balance auto-applied on BT device connect | SATISFIED | `balanceRepository.getBalance(mac)` read on connect, applied via `BalanceMapper.toGainDb()` + `setInputGainbyChannel()`. Confirmed on device. |
| SVC-01 | 02-01 | Foreground service (connectedDevice type) runs in background | SATISFIED | `startForeground(..., FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)`, `START_STICKY`, manifest service declaration. Confirmed: `isForeground=true types=0x00000010`. |
| SVC-02 | 02-01 | Persistent notification shows service state (device, balance) | SATISFIED | `IMPORTANCE_LOW` channel, `buildNotification()`, `updateNotification(formatNotificationText(...))`. Confirmed: "Bose QC35 Ben • Balance: Center" on device. |
| DATA-01 | 02-03 | Balance profiles stored persistently (MAC → coefficient) | SATISFIED | DataStore `floatPreferencesKey("balance_{mac}")` in `BalanceRepository`. balance=-50 confirmed saved via logcat. |
| DATA-02 | 02-03 | Balance profiles survive app restarts | SATISFIED (kill only) | Confirmed: force-stop + relaunch restores balance=-50, ear test confirmed on Deezer/YouTube. **Reboot path not device-tested — see Human Verification.** |

No orphaned requirements found. All 8 phase-2 requirements are claimed by plans and have implementation evidence.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AudioBalanceService.kt` | 78-96 | `seed_balance` adb testing hook in `onStartCommand()` | Info | Intentionally temporary (Phase 2 testing only) — documented as such with comment and in SUMMARY 03. Phase 3 removes it when real UI is added. No functional impact. |
| `AndroidManifest.xml` | 36 | `android:exported="true"` on `AudioBalanceService` with TODO comment | Info | Added to enable adb testing, acknowledged as temporary. Phase 3 should revert to `exported="false"`. No security issue for personal-use adb-only deployment. |
| `AudioBalanceService.kt` | 295 | `android.R.drawable.ic_media_play` as notification icon | Info | Uses system drawable because project has no app drawable resources yet. SUMMARY 01 documents this. Phase 3 will add a proper icon. |

No blocker anti-patterns found. All three are intentional, documented, and deferred to Phase 3.

---

### Human Verification Required

#### 1. Reboot Persistence Test

**Test:** Reboot the Pixel 10 (`adb -s 56191FDCR002NG reboot`), wait for boot to complete, connect Bose QC35 Ben (MAC 04:52:C7:60:C8:72).

**Expected:** `adb -s 56191FDCR002NG logcat -s BootReceiver:D AudioBalanceService:D BalanceRepository:D` shows:
1. `D BootReceiver: BOOT_COMPLETED received — starting AudioBalanceService`
2. `D AudioBalanceService: Already connected: 04:52:C7:60:C8:72` (if headphones were already connected at boot) or `D AudioBalanceService: BT event: state=2 mac=04:52:C7:60:C8:72` (if connecting after boot)
3. `D AudioBalanceService: Balance applied: mac=04:52:C7:60:C8:72 balance=-50 L=0.0dB R=-30.0dB` (or whichever value was last stored)
4. Ear test on Deezer/YouTube confirms audio is shifted to the expected side.

**Why human:** The Plan 03 SUMMARY explicitly documents this test was skipped: "Reboot test (Test 3 from the checkpoint) was not performed — user chose to skip it as optional." The BootReceiver code is correct and the manifest wiring is verified, but the reboot path has never been validated on the physical device.

---

### Gaps Summary

No blocking gaps. The codebase fully implements the phase goal. The single outstanding item is a human validation on a code path (reboot auto-start) that is correctly implemented but was not exercised on device. The other success criteria were all validated on Pixel 10 / Android 16 / API 36 with Bose QC35 Ben.

---

## Summary

Phase 02 delivers what it promised: a headless foreground service that detects BT A2DP connect/disconnect, reads per-MAC balance from DataStore, applies it via `DynamicsProcessing(0, 0, config)` session 0, and updates a persistent notification — all automatically, without user action. The 5 success criteria from ROADMAP.md and all 8 requirement IDs are implemented, compiled, and 4 of 5 criteria were validated on physical hardware. The reboot path requires one additional on-device confirmation before the phase can be fully signed off.

---

_Verified: 2026-04-04_
_Verifier: Claude (gsd-verifier)_
