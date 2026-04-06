---
phase: 03-ui
plan: 01
subsystem: ui
tags: [kotlin, compose, viewmodel, stateflow, datastore, navigation-compose, material3]

# Dependency graph
requires:
  - phase: 02-service-persistence
    provides: AudioBalanceService with seed_balance handler and BalanceRepository with DataStore

provides:
  - DeviceListViewModel with merged StateFlow<DeviceListUiState> combining service + repository state
  - ServiceState companion StateFlow on AudioBalanceService for UI observation
  - BalanceRepository extensions: getAllDevicesFlow, saveAutoApply, getAutoApply, saveDeviceName, getDeviceName
  - DeviceUiState, DeviceListUiState, ServiceState data classes
  - PermissionUiState data class
  - All 23 string resources from UI-SPEC copywriting contract in strings.xml
  - Navigation Compose, lifecycle-viewmodel-compose, material-icons-extended dependencies declared

affects: [03-02-PLAN, 03-03-PLAN]

# Tech tracking
tech-stack:
  added:
    - androidx.navigation:navigation-compose 2.8.5
    - androidx.lifecycle:lifecycle-viewmodel-compose 2.8.7
    - androidx.compose.material:material-icons-extended (BOM-versioned)
  patterns:
    - Companion object StateFlow for service-to-UI state sharing (no bound service needed)
    - combine() to merge service StateFlow and repository Flow into ViewModel uiState
    - _sliderOverrides MutableStateFlow for immediate UI feedback before DataStore persistence
    - Slider throttling at 50ms via timestamp check in ViewModel
    - Magnetic snap via abs(value) <= 3 check in onSliderFinished

key-files:
  created:
    - app/src/main/java/com/audiobalance/app/ui/state/DeviceUiState.kt
    - app/src/main/java/com/audiobalance/app/ui/state/PermissionUiState.kt
    - app/src/main/java/com/audiobalance/app/ui/viewmodel/DeviceListViewModel.kt
  modified:
    - app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt
    - app/src/main/java/com/audiobalance/app/data/BalanceRepository.kt
    - app/src/main/res/values/strings.xml
    - gradle/libs.versions.toml
    - app/build.gradle.kts

key-decisions:
  - "Intent-based (not bound service) communication from ViewModel to AudioBalanceService — consistent with Phase 2 seed_balance pattern"
  - "seed_balance handler accepts Float via getFloatExtra instead of Int — enables slider precision without rounding loss"
  - "Companion object StateFlow on AudioBalanceService as singleton — ViewModel collects directly without binding"
  - "getAllDevicesFlow uses DataStore asMap() filtered by balance_ prefix — devices auto-discovered as they are saved"
  - "autoApply defaults to true for all devices — opt-out UX rather than opt-in"

patterns-established:
  - "State pattern: ViewModel combines multiple flows via combine() operator into a single uiState StateFlow"
  - "Slider pattern: local _sliderOverrides override DataStore values during drag, cleared on finger release"
  - "Service pattern: companion object MutableStateFlow updated at every state transition in service lifecycle"

requirements-completed: [AUDIO-01, UI-01, UI-03]

# Metrics
duration: 3min
completed: 2026-04-06
---

# Phase 03 Plan 01: ViewModel Infrastructure Summary

**Companion StateFlow on AudioBalanceService, BalanceRepository extensions for autoApply and device names, DeviceListViewModel merging both streams with 50ms slider throttle and magnetic snap**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-06T13:30:58Z
- **Completed:** 2026-04-06T13:34:05Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- AudioBalanceService now exposes a `companion object StateFlow<ServiceState>` updated at all BT lifecycle points (connect, disconnect, applyDeviceBalance, checkCurrentlyConnectedDevices, seed_balance)
- BalanceRepository extended with getAllDevicesFlow(), saveAutoApply(), getAutoApply(), saveDeviceName(), getDeviceName() — all backed by DataStore Preferences
- DeviceListViewModel created with merged uiState combining service state and repository flow, including 50ms slider throttle and magnetic snap at abs(value) <= 3
- All 23 string resources from the UI-SPEC copywriting contract added to strings.xml
- Navigation Compose, lifecycle-viewmodel-compose, and material-icons-extended declared in version catalog and build.gradle.kts
- `./gradlew compileDebugKotlin` succeeds with all new code

## Task Commits

1. **Task 1: Dependencies + state data classes + string resources** - `5e67a33` (feat)
2. **Task 2: Service StateFlow + Repository extensions + ViewModel** - `8d8f300` (feat)

## Files Created/Modified
- `app/src/main/java/com/audiobalance/app/ui/state/DeviceUiState.kt` - DeviceUiState, DeviceListUiState, ServiceState data classes
- `app/src/main/java/com/audiobalance/app/ui/state/PermissionUiState.kt` - PermissionUiState data class
- `app/src/main/java/com/audiobalance/app/ui/viewmodel/DeviceListViewModel.kt` - ViewModel with merged StateFlow, slider throttle, magnetic snap
- `app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt` - Added companion StateFlow, updated all BT lifecycle transitions, changed seed_balance to Float
- `app/src/main/java/com/audiobalance/app/data/BalanceRepository.kt` - Added 5 new methods: getAllDevicesFlow, saveAutoApply, getAutoApply, saveDeviceName, getDeviceName
- `app/src/main/res/values/strings.xml` - All 23 UI strings including permission screen, device list, balance labels, error states
- `gradle/libs.versions.toml` - Added navigationCompose 2.8.5, lifecycleViewmodelCompose 2.8.7, material-icons-extended
- `app/build.gradle.kts` - Added implementation lines for three new dependencies

## Decisions Made
- Intent-based communication from ViewModel to AudioBalanceService was kept (no bound service) — consistent with Phase 2 pattern, sufficient for real-time slider feedback
- `seed_balance` handler switched from `getIntExtra` to `getFloatExtra` — enables the ViewModel to pass the exact Float slider value without rounding on send
- `autoApply` defaults to `true` — opt-out UX means new devices immediately apply balance without user needing to toggle on
- Service state companion `MutableStateFlow` initialized with `ServiceState()` (no connected device) so ViewModel has a valid initial value before any BT event

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## Self-Check

## Self-Check: PASSED
- `app/src/main/java/com/audiobalance/app/ui/state/DeviceUiState.kt` — FOUND
- `app/src/main/java/com/audiobalance/app/ui/state/PermissionUiState.kt` — FOUND
- `app/src/main/java/com/audiobalance/app/ui/viewmodel/DeviceListViewModel.kt` — FOUND
- Commit `5e67a33` — FOUND
- Commit `8d8f300` — FOUND
- `./gradlew compileDebugKotlin` — BUILD SUCCESSFUL

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 03-02 (PermissionScreen) can now import `PermissionUiState` and use navigation-compose
- Plan 03-03 (DeviceListScreen) can now import `DeviceListViewModel` and `DeviceUiState` and observe `uiState`
- Both plans can use all string resources via `stringResource(R.string.*)`
- Material Icons Extended and navigation-compose are declared and ready to use

---
*Phase: 03-ui*
*Completed: 2026-04-06*
