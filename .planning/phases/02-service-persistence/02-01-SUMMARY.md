---
phase: 02-service-persistence
plan: "01"
subsystem: service
tags: [foreground-service, dynamics-processing, notification, lifecycle]
dependency_graph:
  requires: [01-audioeffect-poc]
  provides: [AudioBalanceService, BalanceMapper]
  affects: [MainActivity]
tech_stack:
  added:
    - "androidx.datastore:datastore-preferences:1.2.1"
    - "androidx.lifecycle:lifecycle-service:2.10.0"
  patterns:
    - "LifecycleService with manual CoroutineScope"
    - "DynamicsProcessing(0,0,config) minimal config pattern from POC"
    - "FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE with BLUETOOTH_CONNECT runtime permission"
key_files:
  created:
    - app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt
    - app/src/main/java/com/audiobalance/app/util/BalanceMapper.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/com/audiobalance/app/MainActivity.kt
decisions:
  - "android.R.drawable.ic_media_play used for notification icon — project has no app drawable resources yet (Phase 3 will add proper icons)"
  - "android.content.pm.ServiceInfo import required for FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE constant (not android.app.ServiceInfo)"
  - "BLUETOOTH_CONNECT must be runtime-granted before first service launch on API 33+ — added to plan notes"
metrics:
  duration_seconds: 595
  completed_date: "2026-04-06"
  tasks_completed: 3
  files_changed: 6
---

# Phase 02 Plan 01: Foreground Service Skeleton Summary

Foreground service (connectedDevice type) with DynamicsProcessing(0,0,config) session 0 global, IMPORTANCE_LOW notification, START_STICKY, and BalanceMapper(-100..+100 to dB pair).

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Add dependencies and manifest declarations | 4ba9e22 | gradle/libs.versions.toml, app/build.gradle.kts, AndroidManifest.xml |
| 2 | Create AudioBalanceService and BalanceMapper | 53f45be | service/AudioBalanceService.kt, util/BalanceMapper.kt |
| 3 | Wire MainActivity to start service and deploy | ecdddf1 | MainActivity.kt |

## Verification Results

All success criteria met and validated on device (Pixel 10, Android 16, API 36):

- `isForeground=true foregroundId=1001 types=0x00000010` — service running as connectedDevice FGS
- `D AudioBalanceService: DP session=0: setEnabled=0 hasControl=true` — DP session 0 global created and enabled
- Notification channel `audio_balance_service` with `mImportance=2` (IMPORTANCE_LOW) confirmed in dumpsys
- Service returns START_STICKY (confirmed in onStartCommand implementation)
- `./gradlew assembleDebug` succeeds

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Wrong ServiceInfo import path**
- **Found during:** Task 2 — compilation failure
- **Issue:** Plan specified `import android.app.ServiceInfo` but `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` constant is in `android.content.pm.ServiceInfo`
- **Fix:** Changed import to `android.content.pm.ServiceInfo`
- **Files modified:** service/AudioBalanceService.kt
- **Commit:** 53f45be

**2. [Rule 1 - Bug] R.drawable.ic_launcher_foreground does not exist**
- **Found during:** Task 2 — compilation failure
- **Issue:** Project has no drawable resources directory or launcher icon files. `R.drawable.ic_launcher_foreground` was an invalid reference
- **Fix:** Used `android.R.drawable.ic_media_play` (system drawable always present). Phase 3 will add a proper app notification icon.
- **Files modified:** service/AudioBalanceService.kt
- **Commit:** 53f45be

**3. [Rule 1 - Bug] Missing android.content.Intent import in MainActivity**
- **Found during:** Task 3 — compilation failure
- **Issue:** MainActivity had no `android.content.Intent` import; it was only used internally inside lambdas before, where Kotlin could infer the type
- **Fix:** Added `import android.content.Intent`
- **Files modified:** MainActivity.kt
- **Commit:** ecdddf1

**4. [Rule 2 - Runtime Permission Gate] BLUETOOTH_CONNECT must be granted before first launch**
- **Found during:** Task 3 — first deploy crash
- **Issue:** On API 33+, `BLUETOOTH_CONNECT` is a runtime permission. Service crashes with SecurityException when `startForeground(FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)` is called without the permission granted. The `requestPermissions()` in MainActivity is async — the service starts before the user dismisses the dialog.
- **Fix:** Granted permissions via `adb pm grant` for Phase 2 testing (per plan's own instructions). Service works correctly once permissions are granted.
- **Note for Phase 3:** The UI phase must implement `ActivityResultLauncher` permission flow before calling `startForegroundService()`. For Phase 2, adb grant is the intended pattern.
- **Commit:** ecdddf1 (documented in commit message)

## Self-Check: PASSED

Files exist:
- FOUND: app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt
- FOUND: app/src/main/java/com/audiobalance/app/util/BalanceMapper.kt

Commits exist:
- FOUND: 4ba9e22
- FOUND: 53f45be
- FOUND: ecdddf1

Device validation:
- FOUND: `D AudioBalanceService: DP session=0: setEnabled=0 hasControl=true` in logcat
- FOUND: `isForeground=true foregroundId=1001` in dumpsys activity services
- FOUND: `audio_balance_service` channel in dumpsys notification
