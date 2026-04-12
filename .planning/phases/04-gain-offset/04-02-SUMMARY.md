---
phase: 04-gain-offset
plan: "02"
subsystem: ui, viewmodel, unit-tests
tags: [gain-offset, compose, slider, tdd, viewmodel, stateflow]

dependency_graph:
  requires:
    - phase: 04-01
      provides: "BalanceRepository.getGainOffset/saveGainOffset, DeviceUiState.gainOffset, AudioBalanceService.seed_gain_offset intent handler"
  provides:
    - GainOffsetSliderTest — unit tests for slider normalization math
    - DeviceListViewModel.onGainOffsetChange / onGainOffsetFinished / sendGainOffsetToService
    - DeviceListViewModel._gainOffsetOverrides MutableStateFlow
    - DeviceCard gain offset slider (12 discrete dB steps, -12..0 dB)
    - DeviceListScreen gain offset callback wiring
  affects:
    - Any future UI changes to DeviceCard or DeviceListScreen

tech-stack:
  added: []
  patterns:
    - ".value snapshot inside combine lambda for transient UI overrides (avoids changing combine arity)"
    - "Slider normalization: gainSliderValue = (gainOffset + 12f) / 12f; reverse: gainDb = normalized * 12f + (-12f)"
    - "TDD: pure-arithmetic test file with no Android deps, co-located with implementation"

key-files:
  created:
    - app/src/test/java/com/audiobalance/app/GainOffsetSliderTest.kt
  modified:
    - app/src/main/java/com/audiobalance/app/ui/viewmodel/DeviceListViewModel.kt
    - app/src/main/java/com/audiobalance/app/ui/components/DeviceCard.kt
    - app/src/main/java/com/audiobalance/app/ui/screens/DeviceListScreen.kt
    - app/src/main/res/values/strings.xml

key-decisions:
  - "Used .value snapshot for _gainOffsetOverrides inside combine lambda — avoids changing combine arity; sufficient for transient UI state"
  - "onGainOffsetFinished clears override map entry and persists via repository — mirrors balance slider pattern exactly"
  - "Gain slider reuses existing sliderAlpha variable — both balance and gain sliders dim together when auto-apply is off"

patterns-established:
  - "Slider normalization: normalized = (db + 12f) / 12f; db = normalized * 12f + (-12f)"
  - "Transient UI override pattern: MutableStateFlow<Map<String,T>> snapshot read inside combine lambda"

requirements-completed: [GAIN-01, GAIN-03, GAIN-04, GAIN-05]

duration: ~45min
completed: "2026-04-11"
---

# Phase 04 Plan 02: ViewModel + UI Layer for Gain Offset Summary

**Gain offset slider in DeviceCard with 12 discrete -12..0 dB steps, real-time ViewModel override flow, DataStore persistence on release, and service intent dispatch — all verified on-device including persistence across BT reconnect**

## Performance

- **Duration:** ~45 min
- **Completed:** 2026-04-11
- **Tasks:** 3 (2 auto + 1 human-verify)
- **Files modified:** 5

## Accomplishments

- Gain offset slider visible below balance slider in every device card, labeled "Min volume adjustment: X dB"
- ViewModel override flow with 50ms throttle delivers real-time audio feedback while dragging
- Value persists across slider release, app restart, and Bluetooth reconnect — all 11 human-verify steps passed
- Notification correctly shows gain offset when non-zero and omits it when zero

## Task Commits

Each task was committed atomically:

1. **Task 1: Slider normalization tests + ViewModel gain offset flow** - `ac66c88` (test + feat, TDD)
2. **Task 2: DeviceCard gain slider + DeviceListScreen wiring + strings** - `2cd443b` (feat)
3. **Task 3: Verify gain offset feature on device** - `52f7e85` (human-verify — all 11 steps passed)

## Files Created/Modified

- `app/src/test/java/com/audiobalance/app/GainOffsetSliderTest.kt` — 7 unit tests covering normalizedToDb/dbToNormalized math and step count
- `app/src/main/java/com/audiobalance/app/ui/viewmodel/DeviceListViewModel.kt` — added `_gainOffsetOverrides` flow, `onGainOffsetChange`, `onGainOffsetFinished`, `sendGainOffsetToService`
- `app/src/main/java/com/audiobalance/app/ui/components/DeviceCard.kt` — gain offset label + slider below balance slider, callbacks, dimming tied to sliderAlpha
- `app/src/main/java/com/audiobalance/app/ui/screens/DeviceListScreen.kt` — wired `onGainOffsetChange` and `onGainOffsetFinished` callbacks to ViewModel
- `app/src/main/res/values/strings.xml` — added `gain_offset_label` string with `%1$d dB` format arg

## Decisions Made

- **`.value` snapshot in combine lambda:** `_gainOffsetOverrides.value[mac]` read as a snapshot inside the existing 3-flow `combine(serviceFlow, devicesFlow, balanceOverrides)` rather than adding a 4th flow. This avoids increasing combine arity and is sufficient since gain offset overrides are transient UI state (cleared on `onGainOffsetFinished`).
- **No snap logic in `onGainOffsetFinished`:** Unlike balance (which snaps to center near 0), gain offset has no snap target — 0 dB is already a discrete Slider step (steps=11 gives exactly 12 positions including 0).
- **Shared dimming via `sliderAlpha`:** The existing `sliderAlpha` computed from `autoApplyEnabled` is reused for the gain slider Row, so both sliders dim together when auto-apply is off — consistent UX with no extra state.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Complete gain offset feature is fully working and verified on-device
- Phase 04 (both plans 01 and 02) is complete
- Ready to proceed to Phase 05 (FAQ) or Phase 06 (open source prep)

---
*Phase: 04-gain-offset*
*Completed: 2026-04-11*
