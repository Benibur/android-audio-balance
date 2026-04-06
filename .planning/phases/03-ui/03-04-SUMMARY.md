---
phase: 03-ui
plan: 04
subsystem: ui
tags: [android, bluetooth, kotlin, dynamicsprocessing, datastore]

# Dependency graph
requires:
  - phase: 03-ui
    provides: autoApply toggle persisted to DataStore per device
provides:
  - autoApply guard in AudioBalanceService.applyDeviceBalance() — toggle now prevents DP gain on reconnect
affects: [service, bluetooth, audio-effect]

# Tech tracking
tech-stack:
  added: []
  patterns: [suspend guard pattern: read preference before applying side-effect]

key-files:
  created: []
  modified:
    - app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt

key-decisions:
  - "autoApply guard placed before DP gain application, not before device registration — device still appears in UI as connected with balance 0"
  - "seed_balance handler (explicit user slider action) remains ungated — autoApply only affects automatic BT reconnect application"

patterns-established:
  - "Guard pattern: read DataStore preference, early-return skipping side-effect while still updating StateFlow/notification"

requirements-completed: [UI-03]

# Metrics
duration: 5min
completed: 2026-04-04
---

# Phase 03 Plan 04: Gap Closure Summary

**autoApply toggle now enforced in AudioBalanceService — BT reconnect skips DP gain when disabled while device still registers and appears connected in UI**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-04T00:25:26Z
- **Completed:** 2026-04-04T00:30:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Added `getAutoApply(mac)` guard as first suspension call in `applyDeviceBalance()`
- When disabled: device saved/registered, StateFlow updated (connectedDeviceMac set), notification updated at center balance, DP channels NOT touched
- When enabled: full existing balance application path unchanged
- seed_balance handler (user dragging slider) remains ungated — explicit user actions always apply

## Task Commits

1. **Task 1: Add autoApply guard to AudioBalanceService** - `62695f7` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

- `app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt` — added 12-line autoApply guard block before DP gain application in `applyDeviceBalance()`

## Decisions Made

- Guard placed after device registration intent but before DP gain calls: the early-return path still calls `saveBalance`, `saveDeviceName`, updates `_stateFlow`, and calls `updateNotification` so the UI correctly shows the device as connected at center balance — matches spec requirement that "device still appears in UI as connected even when autoApply is off"
- No changes to `onStartCommand` seed_balance handler: that path represents explicit user action and must always apply

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- UI-03 verification gap is closed: the autoApply toggle now has end-to-end effect from DataStore through to AudioBalanceService DP gain application
- Phase 03 is fully complete — all 4 plans executed
- Ready for Phase 04 (polish / distribution) when planned

---
*Phase: 03-ui*
*Completed: 2026-04-04*
