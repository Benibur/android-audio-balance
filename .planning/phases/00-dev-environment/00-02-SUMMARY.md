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
  - Debug APK deployed and running on emulator (emulator-5554)
  - Physical device ADB deployment confirmed (serial 56191FDCR002NG)
  - Full dev-to-device pipeline validated for both emulator and physical hardware
affects: [01-audio-control, 02-bt-service, 03-automation]

# Tech tracking
tech-stack:
  added:
    - Android Emulator (SDK package, emulator-linux_x64)
    - system-images;android-35;google_apis;x86_64 (emulator system image)
    - AVD dev_phone (Pixel 6 profile, API 35, Google APIs)
  patterns:
    - Emulator started with -accel off -gpu swiftshader_indirect (no KVM on this host)
    - APK deployed via adb install for both emulator and physical device
    - Physical device targeted explicitly via adb -s <serial>

key-files:
  created: []
  modified:
    - .planning/config.json (gsd-tools auto-updated _auto_chain_active field)

key-decisions:
  - "Emulator runs in software mode (no KVM available on host); boot takes ~9 min but functional for pre-physical-device testing"
  - "Physical device (56191FDCR002NG) is the primary target for AudioEffect validation (Phase 1 hard gate)"
  - "adb -s <serial> pattern established for all multi-device targeting in future phases"

patterns-established:
  - "Deployment: adb -s emulator-5554 targets emulator explicitly; adb -s <serial> for physical device"
  - "Boot check: poll sys.boot_completed via adb shell getprop until '1'; bootanim=stopped is near-ready indicator"
  - "Verification: dumpsys activity activities | grep com.audiobalance confirms Resumed state"

requirements-completed: []

# Metrics
duration: ~25min (plus human-verify checkpoint wait)
completed: 2026-04-04
---

# Phase 00 Plan 02: Emulator and Physical Device Deployment Summary

**Android emulator AVD dev_phone (Pixel 6, API 35, software mode) and physical device 56191FDCR002NG both run the Hello World APK via ADB — full dev-to-device pipeline validated**

## Performance

- **Duration:** ~25 min active execution (plus checkpoint wait for physical device connection)
- **Started:** 2026-04-04T17:23:22Z
- **Completed:** 2026-04-04T18:10:00Z
- **Tasks:** 2 of 2 complete
- **Files modified:** 1 (config.json by gsd-tools)

## Accomplishments

- Android emulator SDK package and API 35 google_apis x86_64 system image installed via sdkmanager
- AVD dev_phone created with Pixel 6 hardware profile and Android API 35
- Emulator booted successfully (software rendering, ~9 min boot time without KVM)
- app-debug.apk installed and MainActivity launched on emulator — verified via dumpsys
- Physical device 56191FDCR002NG detected by ADB, APK installed via USB, MainActivity confirmed Resumed on device

## Task Commits

Each task was committed atomically:

1. **Task 1: Create emulator AVD and deploy Hello World app** - `8ffd7d7` (chore)
2. **Task 2: Physical device ADB verification and APK deployment** - `a89e845` (chore)

**Plan metadata:** TBD (docs commit after SUMMARY/STATE/ROADMAP update)

## Files Created/Modified

- `.planning/config.json` - gsd-tools added `_auto_chain_active: false` field during init

## Decisions Made

- Emulator runs without KVM (`-accel off -gpu swiftshader_indirect`) because `/dev/kvm` is absent on this host. Boot is slow (~9 min) but fully functional for emulator-based iteration.
- Physical device (serial `56191FDCR002NG`) is the critical target for Phase 1 AudioEffect validation — the emulator audio HAL behavior differs significantly from real hardware.
- `adb -s <serial>` pattern established as the standard for all multi-device targeting going forward.

## Deviations from Plan

None — plan executed exactly as written. KVM unavailability was pre-noted in the plan ("if KVM is not available") and handled per that guidance. Physical device connection was a human-verify checkpoint as planned.

## Issues Encountered

- KVM not available (`/dev/kvm` missing): emulator runs in full software emulation. First boot takes ~9 minutes. Pre-noted in plan as expected condition.
- `sys.boot_completed` property takes longer to appear than `bootanim=stopped`: both are boot indicators but `boot_completed=1` lags by ~2 min in software mode.

## User Setup Required

None — physical device setup (Developer Options, USB Debugging) was completed by the user before Task 2 continuation.

## Next Phase Readiness

- Emulator pipeline: fully operational for rapid iteration
- Physical device pipeline: fully operational (serial `56191FDCR002NG`, USB connected)
- Phase 01 (AudioEffect POC) can begin immediately — physical device hard gate is cleared
- Risk remains: AudioEffect session 0 may be silently blocked by OEM audio stack on this device — Phase 01 plan 01 will surface this immediately

## Self-Check: PASSED

- Task 1 commit `8ffd7d7` exists in git log
- Task 2 commit `a89e845` exists in git log
- Physical device `56191FDCR002NG` confirmed present via `adb devices`
- MainActivity confirmed Resumed on physical device via `dumpsys activity activities`

---
*Phase: 00-dev-environment*
*Completed: 2026-04-04*
