---
phase: 02-service-persistence
plan: "02"
subsystem: service
tags: [bluetooth, a2dp, datastore, coroutines, broadcast-receiver, dynamics-processing]

# Dependency graph
requires:
  - phase: 02-01
    provides: AudioBalanceService, BalanceMapper, DynamicsProcessing session 0 in foreground service
provides:
  - BtA2dpReceiver (dynamic broadcast receiver for A2DP connect/disconnect)
  - BalanceRepository (MAC-keyed DataStore read/write)
  - DevicePreferences (single DataStore delegate)
  - AudioBalanceService with handleBtEvent, applyDeviceBalance, resetBalanceToCenter
affects: [02-03, 03-ui]

# Tech tracking
tech-stack:
  added:
    - "androidx.datastore:datastore-preferences (already in build, first use)"
  patterns:
    - "Dynamic BroadcastReceiver registration with RECEIVER_EXPORTED for system BT broadcasts on API 33+"
    - "Coroutine Job cancel/relaunch pattern for micro-disconnect protection (2s disconnect, 1s reconnect)"
    - "MAC-keyed DataStore float preference: floatPreferencesKey(balance_XX_XX_XX_XX_XX_XX)"
    - "getProfileProxy for checking already-connected A2DP devices at service start"

key-files:
  created:
    - app/src/main/java/com/audiobalance/app/data/DevicePreferences.kt
    - app/src/main/java/com/audiobalance/app/data/BalanceRepository.kt
    - app/src/main/java/com/audiobalance/app/service/BtA2dpReceiver.kt
  modified:
    - app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt

key-decisions:
  - "RECEIVER_EXPORTED required for system BT A2DP broadcasts — RECEIVER_NOT_EXPORTED silently blocks them on API 33+"
  - "resetBalanceToCenter() sets both channels to 0f but does NOT release DynamicsProcessing — DP stays alive between devices"
  - "New unknown devices saved with balance 0f immediately on first connect — no separate registration step"

patterns-established:
  - "BT receiver pattern: dynamic registration in onCreate, unregister in onDestroy, RECEIVER_EXPORTED for system broadcasts"
  - "Delay pattern: 1000L for connect (audio routing stabilization), 2000L for disconnect (micro-disconnect protection)"
  - "DataStore key pattern: floatPreferencesKey(balance_ + mac.replace(:, _))"

requirements-completed: [BT-01, BT-02, BT-03, AUDIO-02]

# Metrics
duration: ~60min
completed: 2026-04-04
---

# Phase 02 Plan 02: BT Detection + Balance Auto-Apply Summary

**BT A2DP connect/disconnect wired to DataStore-backed balance auto-apply via coroutine delays, with notification updates and micro-disconnect protection.**

## Performance

- **Duration:** ~60 min
- **Started:** ~2026-04-04T00:00:00Z
- **Completed:** 2026-04-04
- **Tasks:** 3 (2 auto + 1 checkpoint)
- **Files modified:** 4

## Accomplishments

- DataStore persistence layer created: single `preferencesDataStore` delegate + `BalanceRepository` with MAC-keyed float prefs
- `BtA2dpReceiver` registers dynamically for `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` and passes `(device, state)` to service callback
- `AudioBalanceService` wired with full BT lifecycle: 1s connect delay, 2s disconnect delay, coroutine job cancel on reconnect (micro-disconnect protection), `checkCurrentlyConnectedDevices()` on start
- Physically validated on Pixel 10 / Android 16 / API 36 with Bose QC35 Ben (MAC: 04:52:C7:60:C8:72)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create DataStore persistence layer and BT receiver** - `0d4c31f` (feat)
2. **Task 2: Integrate BT detection and auto-apply into AudioBalanceService** - `d780f45` (feat)
3. **Task 2 bug fix: RECEIVER_EXPORTED for BT A2DP broadcast receiver** - `0788eec` (fix)
4. **Task 3: Checkpoint — human verification on physical device** - passed (no commit)

## Files Created/Modified

- `app/src/main/java/com/audiobalance/app/data/DevicePreferences.kt` — Single `preferencesDataStore(name = "device_balance")` delegate
- `app/src/main/java/com/audiobalance/app/data/BalanceRepository.kt` — `getBalance(mac)`, `saveBalance(mac, balance)`, `getAllBalances()` flow
- `app/src/main/java/com/audiobalance/app/service/BtA2dpReceiver.kt` — `BroadcastReceiver` for A2DP state changes, API 33+ parcelable handling
- `app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt` — Added `handleBtEvent`, `applyDeviceBalance`, `resetBalanceToCenter`, `registerBtReceiver`, `checkCurrentlyConnectedDevices`, `hasBluetoothConnectPermission`

## Decisions Made

- `RECEIVER_EXPORTED` required for system BT broadcasts — `RECEIVER_NOT_EXPORTED` silently blocks `ACTION_CONNECTION_STATE_CHANGED` on API 33+ (see Deviations)
- `resetBalanceToCenter()` does not release the `DynamicsProcessing` instance — only resets gain to 0dB on both channels, keeping DP alive for the next device
- New unknown devices receive balance 0f immediately on first connect — they are written to DataStore so subsequent connects are treated as "known"

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] RECEIVER_NOT_EXPORTED blocks system BT broadcasts on API 33+**
- **Found during:** Task 2 (physical device testing — BT connect not detected)
- **Issue:** Plan specified `RECEIVER_NOT_EXPORTED` flag for the dynamic receiver registration on API 33+. System-broadcast actions like `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` are sent by the Android OS (external process), so `RECEIVER_NOT_EXPORTED` silently prevents receipt. No error is thrown — the broadcast simply never arrives.
- **Fix:** Changed flag to `RECEIVER_EXPORTED` in `registerBtReceiver()`. The receiver is protected by Android's permission system (`BLUETOOTH_CONNECT` is required to receive A2DP broadcasts), so exporting is safe here.
- **Files modified:** `app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt`
- **Verification:** After fix, BT-01 PASS — A2DP state=2 detected in logcat immediately on headphone connect
- **Committed in:** `0788eec`

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug)
**Impact on plan:** Critical fix — without it no BT event would ever be received. No scope creep.

## Issues Encountered

None beyond the deviation documented above.

## Verification Results

All success criteria validated on device (Pixel 10, Android 16, API 36) with Bose QC35 Ben:

| Test | Result | Evidence |
|------|--------|----------|
| BT-01: A2DP connect detected | PASS | `A2DP state=2 detected, MAC=04:52:C7:60:C8:72, name="Bose QC35 Ben"` |
| BT-02: Disconnect resets balance | PASS | `A2DP state=0 detected, balance reset to center after 2s delay (08.234→10.253)` |
| BT-03: Balance auto-applied on connect | PASS | `Balance auto-applied 1s after connect (balance=0 for new device)` |
| AUDIO-02: DP gain applied | PASS | `setInputGainbyChannel (L=0.0dB R=0.0dB for center)` |
| SVC-02: Notification updates | PASS | Shows "Bose QC35 Ben • Balance: Center" on connect, "No device connected" on disconnect |
| Micro-disconnect protection | SKIPPED | Impossible to reproduce manually — mechanism in code, cancel/relaunch pattern verified in review |

## User Setup Required

None — no new external service configuration required. Permissions were pre-granted in Plan 01.

## Next Phase Readiness

- DataStore persistence layer ready — `BalanceRepository` available for Plan 03 boot receiver work
- `getAllBalances()` flow available for Phase 3 UI device list
- `checkCurrentlyConnectedDevices()` handles service restart after kill — Plan 03 verifies after reboot
- Plan 03 goal: `BOOT_COMPLETED` receiver + DataStore persistence survives kill/reboot (DATA-01, DATA-02)

---
*Phase: 02-service-persistence*
*Completed: 2026-04-04*
