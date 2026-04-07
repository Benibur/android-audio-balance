---
phase: 04-gain-offset
plan: "01"
subsystem: data-layer, service-layer, unit-tests
tags: [gain-offset, datastore, dynamics-processing, notification, tdd]
one_liner: "DeviceEntry data class, gainOffset DataStore persistence, single applyGains() DP entry point with seed_gain_offset intent handler, and Wave 0 contract tests"

dependency_graph:
  requires: []
  provides:
    - BalanceRepository.getGainOffset / saveGainOffset
    - BalanceRepository.getAllDevicesFlow (returns DeviceEntry)
    - AudioBalanceService.applyGains (exclusive DP writer)
    - AudioBalanceService.seed_gain_offset intent handler
    - AudioBalanceService.formatNotificationText (with optional gainOffset)
    - ServiceState.currentGainOffset
    - DeviceUiState.gainOffset
  affects:
    - DeviceListViewModel (updated Triple -> DeviceEntry)
    - All BT reconnect and balance-apply paths

tech_stack:
  added:
    - junit:junit:4.13.2 (testImplementation)
  patterns:
    - Wave 0 TDD: standalone pure-Kotlin test functions define contract before production code
    - Single DP entry point: applyGains() is the exclusive owner of setInputGainbyChannel calls
    - Composition formula: finalGain = balanceGain + gainOffset (per channel)

key_files:
  created:
    - app/src/test/java/com/audiobalance/app/ApplyGainsTest.kt
    - app/src/test/java/com/audiobalance/app/NotificationTextTest.kt
  modified:
    - app/src/main/java/com/audiobalance/app/data/BalanceRepository.kt
    - app/src/main/java/com/audiobalance/app/ui/state/DeviceUiState.kt
    - app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt
    - app/src/main/java/com/audiobalance/app/ui/viewmodel/DeviceListViewModel.kt
    - app/build.gradle.kts
    - gradle/libs.versions.toml

decisions:
  - "Wave 0 tests use standalone helper functions defined in the test file — no mocking needed, pure math/string functions"
  - "applyGains() wraps both setInputGainbyChannel calls in a try/catch for RuntimeException, matching existing pattern"
  - "seed_balance handler now reads gainOffset from DataStore and composes with balance before applying"

metrics:
  duration_seconds: 226
  completed_date: "2026-04-07"
  tasks_completed: 3
  tasks_total: 3
  files_created: 2
  files_modified: 6
---

# Phase 04 Plan 01: Data + Service Layer for Gain Offset Summary

Data layer and service layer for gain offset: DeviceEntry data class with gainOffset field replaces Triple, repository persistence via DataStore key `gain_offset_{mac}`, single `applyGains(balance, gainOffset)` helper consolidating all 4 former DP call sites, `seed_gain_offset` intent handler, updated notification formatting, and Wave 0 contract tests covering both the composition formula and notification format.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Wave 0 tests — gain composition and notification formatting | c93132d | ApplyGainsTest.kt, NotificationTextTest.kt |
| 2 | Data layer — DeviceEntry, repository methods, ServiceState update | c55564b | BalanceRepository.kt, DeviceUiState.kt |
| 3 | Service layer — applyGains helper, seed_gain_offset, notification update | ef1ee73 | AudioBalanceService.kt, DeviceListViewModel.kt |

## Verification Results

- `./gradlew testDebugUnitTest --tests "com.audiobalance.app.*" -x lint` — BUILD SUCCESSFUL, 10 tests pass
- `./gradlew compileDebugKotlin` — BUILD SUCCESSFUL, no compile errors
- `grep -c "setInputGainbyChannel" AudioBalanceService.kt` — 3 occurrences, all inside `applyGains()` (2 actual calls + 1 log string)
- `data class DeviceEntry(` present in BalanceRepository.kt
- `gain_offset_` key pattern present in BalanceRepository.kt

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Missing Dependency] Added JUnit 4 test dependency**
- **Found during:** Task 1
- **Issue:** `testImplementation` for JUnit was not present in `app/build.gradle.kts` — tests would fail to compile
- **Fix:** Added `junit = "4.13.2"` to `gradle/libs.versions.toml` and `testImplementation(libs.junit)` to `build.gradle.kts`
- **Files modified:** `app/build.gradle.kts`, `gradle/libs.versions.toml`
- **Commit:** c93132d

## Self-Check: PASSED

All 6 key files verified present. All 3 task commits (c93132d, c55564b, ef1ee73) confirmed in git log.
