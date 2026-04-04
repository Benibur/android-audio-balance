---
phase: 00-dev-environment
plan: 02
subsystem: infra
tags: [android, emulator, adb, avd, physical-device, apk-deployment]

# Dependency graph
requires:
  - phase: 00-dev-environment/00-01
    provides: Debug APK (app/build/outputs/apk/debug/app-debug.apk) and Android SDK toolchain
provides:
  - Android emulator AVD (dev_phone, Pixel 6, API 35) created and booted
  - Debug APK deployed and running on emulator
  - Physical device ADB deployment (pending user connection)
affects: [01-audio-control, 02-bt-service, 03-automation]

# Tech tracking
tech-stack:
  added:
    - Android Emulator (SDK package, emulator-linux_x64)
    - system-images;android-35;google_apis;x86_64 (emulator system image)
    - AVD dev_phone (Pixel 6 profile, API 35, Google APIs)
  patterns:
    - Emulator started with -accel off -gpu swiftshader_indirect (no KVM on this host)
    - APK deployed via adb install -r for both emulator and physical device

key-files:
  created: []
  modified:
    - .planning/config.json (gsd-tools auto-updated _auto_chain_active field)

key-decisions:
  - "Emulator runs in software mode (no KVM available on host); boot takes ~9 min but functional for pre-physical-device testing"
  - "Physical device is the primary target for AudioEffect validation (Phase 1 hard gate)"

patterns-established:
  - "Deployment: adb -s emulator-5554 install -r <apk> targets emulator explicitly; adb -s <serial> for physical device"
  - "Boot check: poll sys.boot_completed via adb shell getprop until '1'; bootanim=stopped is near-ready indicator"

requirements-completed: []

# Metrics
duration: ~25min (Task 1 complete; Task 2 awaiting human verification)
completed: 2026-04-04
---

# Phase 00 Plan 02: Emulator and Physical Device Deployment Summary

**Android emulator AVD dev_phone (Pixel 6, API 35) created and booted in software mode; Hello World APK deployed and running — physical device deployment awaiting USB connection and USB debugging enable**

## Performance

- **Duration:** ~25 min (Task 1 complete; Task 2 is a human-verify checkpoint)
- **Started:** 2026-04-04T17:23:22Z
- **Completed:** 2026-04-04 (Task 1), Task 2 pending user action
- **Tasks:** 1 of 2 complete (Task 2 is checkpoint:human-verify)
- **Files modified:** 1 (config.json by gsd-tools)

## Accomplishments

- Android emulator SDK package and API 35 google_apis x86_64 system image installed via sdkmanager
- AVD dev_phone created with Pixel 6 hardware profile and Android API 35
- Emulator booted successfully (software rendering, ~9 min boot time without KVM)
- app-debug.apk (9MB, from Plan 01) installed and MainActivity launched on emulator — verified via dumpsys

## Task Commits

Each task was committed atomically:

1. **Task 1: Create emulator AVD and deploy Hello World app** - `8ffd7d7` (chore)
2. **Task 2: Physical device deployment** - awaiting human-verify checkpoint

**Plan metadata:** (docs commit pending after Task 2)

## Files Created/Modified

- `.planning/config.json` - gsd-tools added `_auto_chain_active: false` field during init

## Decisions Made

- Emulator runs without KVM (`-accel off -gpu swiftshader_indirect`) because `/dev/kvm` is absent on this host. Boot is slow (~9 min) but fully functional for emulator-based iteration. KVM installation would require `sudo` + logout/login which wasn't feasible here.
- Physical device is the critical target for Phase 1 AudioEffect validation — the emulator alone is insufficient because Android audio HAL behavior differs significantly.

## Deviations from Plan

None - plan executed exactly as written. KVM unavailability was pre-noted in the plan ("if KVM is not available") and handled per that guidance.

## Issues Encountered

- KVM not available (`/dev/kvm` missing): emulator runs in full software emulation (QEMU without hardware virtualization). First boot takes ~9 minutes instead of ~30 seconds. Installation of qemu-kvm requires sudo+fingerprint which isn't available in bash subshell. Pre-noted in plan as expected condition.
- `sys.boot_completed` property takes longer to appear than `bootanim=stopped`: both are boot indicators but `boot_completed=1` lags behind by ~2 min in software mode.

## User Setup Required

**Task 2 requires manual setup before physical device deployment:**

1. Enable Developer Options on your Android phone:
   - Settings > About phone > tap **Build number** 7 times
   - "You are now a developer!" message appears

2. Enable USB Debugging:
   - Settings > Developer Options > **USB Debugging** > ON

3. Connect phone via USB cable to this computer

4. Accept the **"Allow USB debugging?"** prompt on the phone when it appears

5. Verify connection: `adb devices` should show a non-emulator line ending in `device`

## Next Phase Readiness

- Emulator pipeline: fully operational for rapid iteration
- Physical device pipeline: ready once USB debugging is enabled (user action required)
- Phase 01 (AudioEffect POC) requires physical device — this is the hard gate noted in STATE.md
- No blockers for Phase 01 once physical device is confirmed

---
*Phase: 00-dev-environment*
*Completed: 2026-04-04*
