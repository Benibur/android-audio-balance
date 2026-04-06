---
phase: 2
slug: service-persistence
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Phase 2 is a service/backend phase — most validation is automated (build, grep, adb shell) with manual BT connect/disconnect checkpoints.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build + adb shell commands + manual BT pairing |
| **Config file** | `app/build.gradle.kts` |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug && adb -s 56191FDCR002NG install -r app/build/outputs/apk/debug/app-debug.apk` |
| **Estimated runtime** | ~30s build, ~5s install, manual BT test ~2min |

---

## Sampling Rate

- **After every task commit:** `./gradlew assembleDebug` must succeed
- **After every plan wave:** Deploy to device, verify service starts via `adb shell dumpsys activity services`
- **Before phase verification:** Full BT connect/disconnect/reconnect manual test cycle
- **Max feedback latency:** ~30s for build

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Human Verify | Status |
|---------|------|------|-------------|-----------|-------------------|--------------|--------|
| 02-*-01 | 01 | 1 | SVC-01 | build + adb | `./gradlew assembleDebug && adb shell dumpsys activity services \| grep BalanceService` | No | ⬜ pending |
| 02-*-02 | 01 | 1 | SVC-02 | build + adb | `adb shell dumpsys notification \| grep audiobalance` | No | ⬜ pending |
| 02-*-* | 02 | 2 | BT-01, BT-02 | build + logcat | `adb logcat -s BalanceService:D \| grep "A2DP"` | Yes — BT pair | ⬜ pending |
| 02-*-* | 02 | 2 | BT-03, AUDIO-02 | build + ear-test | Same + ear-test balance on BT connect | Yes — BT pair + ear | ⬜ pending |
| 02-*-* | 03 | 3 | DATA-01, DATA-02 | adb + kill | `adb shell am force-stop com.audiobalance.app && adb shell am start ...` | Yes — BT reconnect after kill | ⬜ pending |

---

## Wave 0 Requirements

- [ ] `BLUETOOTH_CONNECT` permission temporarily granted via `adb shell pm grant` (until Phase 3 adds runtime permission flow)
- [ ] DataStore dependency added to `gradle/libs.versions.toml`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| BT A2DP connection detected | BT-01 | Requires physical BT headphones pairing | Connect BT headphones, check logcat for MAC address log |
| BT A2DP disconnection detected | BT-02 | Same | Disconnect BT headphones, check logcat for disconnect log |
| Balance auto-applied on known device connect | BT-03 | Ear-test required | Store coefficient via adb/temp UI, reconnect BT, listen for balance shift |
| Notification shows device name + balance | SVC-02 | Visual check on notification shade | Pull down notification shade, verify text matches connected device |
| Balance survives app kill + restart | DATA-02 | Requires manual app kill | Force-stop app, reconnect BT, verify stored balance is reapplied |
| 2s disconnect delay works | BT-02 | Timing-dependent BT behavior | Disconnect BT, reconnect within 2s, verify balance not reset |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter after sign-off

**Approval:** pending
