---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: unknown
stopped_at: Completed 03-04-PLAN.md
last_updated: "2026-04-06T21:55:03.839Z"
progress:
  total_phases: 4
  completed_phases: 4
  total_plans: 12
  completed_plans: 12
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-01)

**Core value:** Quand je connecte mes écouteurs Bluetooth, la balance stéréo que j'ai configurée s'applique automatiquement — sans intervention manuelle
**Current focus:** Phase 03 — ui

## Current Position

Phase: 03 (ui) — EXECUTING
Plan: 1 of 3

## Performance Metrics

**Velocity:**

- Total plans completed: 2
- Average duration: ~79 min (67 min + 90 min / 2)
- Total execution time: ~2.6 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 00-dev-environment | 1/2 | 67 min | 67 min |

**Recent Trend:**

- Last 5 plans: 67 min
- Trend: —

*Updated after each plan completion*
| Phase 00-dev-environment P02 | 25 | 2 tasks | 1 files |
| Phase 01-audioeffect-poc P01 | 90 | 3 tasks | 6 files |
| Phase 01-audioeffect-poc P02 | 180 | 5 tasks | 5 files |
| Phase 01-audioeffect-poc P03 | checkpoint | 2 tasks | 1 files |
| Phase 02-service-persistence P01 | 595s | 3 tasks | 6 files |
| Phase 02-service-persistence P02 | 60 | 3 tasks | 4 files |
| Phase 02-service-persistence P03 | 90 | 2 tasks | 3 files |
| Phase 03-ui P01 | 3 | 2 tasks | 8 files |
| Phase 03-ui P02 | 2 | 2 tasks | 3 files |
| Phase 03-ui P03 | 15 | 2 tasks | 3 files |
| Phase 03-ui P04 | 5 | 1 tasks | 1 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Roadmap: Phase 1 is a hard gate — AudioEffect session 0 must be validated on physical hardware before Phase 2 service work begins. If session 0 is silently blocked, Phase 2 `AudioEffectManager` switches to per-session-ID Map approach instead of single instance.
- Stack: KSP version must exactly match Kotlin version (`2.3.10-2.0.0`); AGP 9.1.0 requires Android Studio Otter 3 Feature Drop or later.
- 00-01: AGP 8.7.3 + Gradle 8.9 chosen over AGP 9.x — stable with Kotlin 2.0.21, avoids Android Studio Otter 3 requirement
- 00-01: Version catalog (gradle/libs.versions.toml) established as single source of truth for all dep versions — all future phases add deps here
- 00-01: minSdk=26 (Android 8.0+), targetSdk=35, package com.audiobalance.app established as project constants
- [Phase 00-02]: 00-02: Emulator runs without KVM (software mode, ~9min boot); physical device remains primary target for Phase 1 AudioEffect validation
- [Phase 00-02]: Emulator runs in software mode (no KVM); ~9min boot; physical device 56191FDCR002NG is primary target for Phase 1 AudioEffect validation
- [Phase 00-02]: adb -s <serial> pattern established as standard for all multi-device targeting
- [Phase 01-01]: FEAS-01 VALIDATED on Pixel 10 / Android 16 / API 36: DynamicsProcessing on mediaPlayer.audioSessionId produces audible balance shift
- [Phase 01-01]: Session 15521 (mediaPlayer.audioSessionId) was used, not global session 0 — session 0 for external apps (Spotify) is a separate code path tested in Plan 01-02
- [Phase 01-01]: Balance -60dB on attenuated channel is clearly audible; this is the validated pattern for Phase 2 AudioEffectManager
- [Phase 01-02]: FEAS-02 VALIDATED: DynamicsProcessing(0, 0, config) on session 0 LITERAL shifts audio to L or R ear on Deezer/YouTube — Phase 2 uses single-instance global approach
- [Phase 01-02]: Minimal DP Config mandatory for session 0 global: all stages (preEq, mbc, postEq, limiter) must be false — any enabled stage with 0 bands silences the global mix
- [Phase 01-02]: GlobalDpHolder singleton required for Activity-recreation-safe effect lifetime — store in object or foreground service, never in Activity member field
- [Phase 01-audioeffect-poc]: POC-RESULTS.md approved by user — Phase 1 deliverable sealed and ready for Phase 2 consumption
- [Phase 01-audioeffect-poc]: Phase 2 architecture: single DynamicsProcessing(0, 0, config) in foreground service — per-session Map not needed
- [Phase 02-01]: ServiceInfo constant FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE is in android.content.pm.ServiceInfo, not android.app.ServiceInfo
- [Phase 02-01]: android.R.drawable.ic_media_play used for notification icon — project has no drawable resources; Phase 3 will add proper icon
- [Phase 02-01]: BLUETOOTH_CONNECT is a runtime permission on API 33+ — must be granted before startForeground(connectedDevice) or SecurityException is thrown; Phase 3 must implement permission flow before startForegroundService()
- [Phase 02-02]: RECEIVER_EXPORTED required for system BT A2DP broadcasts — RECEIVER_NOT_EXPORTED silently blocks them on API 33+
- [Phase 02-02]: resetBalanceToCenter() sets both DP channels to 0f but does not release DynamicsProcessing — DP stays alive between devices
- [Phase 02-02]: New unknown BT devices saved with balance 0f immediately on first connect — no separate registration step needed
- [Phase 02-service-persistence]: AudioBalanceService android:exported=true required on Android 12+ for adb am startservice — Phase 3 should review for distribution hardening
- [Phase 02-service-persistence]: checkCurrentlyConnectedDevices() must set currentDeviceMac from proxy callback — required for seed commands and any startup-time logic that checks currentDeviceMac
- [Phase 02-service-persistence]: seed_balance handler applies DP gain immediately (setInputGainbyChannel + notification update) — saves to DataStore and applies at once, no BT reconnect needed
- [Phase 03-ui]: Intent-based (not bound service) communication from ViewModel to AudioBalanceService — consistent with Phase 2 seed_balance pattern
- [Phase 03-ui]: seed_balance handler accepts Float via getFloatExtra instead of Int — enables slider precision without rounding loss
- [Phase 03-ui]: Companion object StateFlow on AudioBalanceService as singleton — ViewModel collects directly without binding
- [Phase 03-ui]: Permission launchers declared inside composable using rememberLauncherForActivityResult — not in Activity or ViewModel
- [Phase 03-ui]: NavHost start destination computed via remember block from ContextCompat.checkSelfPermission at launch time — avoids recomputation on recomposition
- [Phase 03-ui]: Plan 03-02 pre-created DeviceCard.kt with correct content in parallel execution — no re-work needed
- [Phase 03-ui]: Slider value conversion pattern: sliderValue = (balance + 100f) / 200f, reversed in onValueChange
- [Phase 03-ui]: autoApply guard placed before DP gain application but after device registration: device appears connected in UI at center balance when disabled
- [Phase 03-ui]: seed_balance handler remains ungated by autoApply — explicit user slider actions always apply regardless of toggle state

### Pending Todos

None yet.

### Blockers/Concerns

- **Phase 1 risk**: AudioEffect session 0 is silently blocked on a significant fraction of OEM devices (Samsung One UI, Xiaomi MIUI). Physical device testing is the only way to resolve this. The fallback strategy (per-session-ID broadcasts) changes the `AudioEffectManager` architecture.
- **Phase 2 risk**: Android 15 BOOT_COMPLETED + connectedDevice FGS interaction has nuanced restrictions — verify on Android 15 device early in Phase 2.

## Session Continuity

Last session: 2026-04-06T16:46:52.180Z
Stopped at: Completed 03-04-PLAN.md
Resume file: None
