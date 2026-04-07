---
phase: 4
slug: gain-offset
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 (standard Android project; no test files exist yet) |
| **Config file** | None — Wave 0 must create `app/src/test/` directory structure |
| **Quick run command** | `./gradlew testDebugUnitTest --tests "com.audiobalance.app.*"` |
| **Full suite command** | `./gradlew testDebugUnitTest` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew testDebugUnitTest --tests "com.audiobalance.app.*" -x lint`
- **After every plan wave:** Run `./gradlew testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 04-W0-01 | W0 | 0 | GAIN-03 | unit | `./gradlew testDebugUnitTest --tests "*.ApplyGainsTest"` | ❌ W0 | ⬜ pending |
| 04-W0-02 | W0 | 0 | GAIN-05 | unit | `./gradlew testDebugUnitTest --tests "*.NotificationTextTest"` | ❌ W0 | ⬜ pending |
| 04-W0-03 | W0 | 0 | GAIN-01 | unit | `./gradlew testDebugUnitTest --tests "*.GainOffsetSliderTest"` | ❌ W0 | ⬜ pending |
| 04-XX-XX | TBD | TBD | GAIN-02 | unit | `./gradlew testDebugUnitTest --tests "*.BalanceRepositoryTest"` | ❌ W0 | ⬜ pending |
| 04-XX-XX | TBD | TBD | GAIN-04 | manual | BT reconnect test with saved gain offset | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/com/audiobalance/app/` — Create test directory structure
- [ ] `app/src/test/java/com/audiobalance/app/ApplyGainsTest.kt` — GAIN-03 composition formula
- [ ] `app/src/test/java/com/audiobalance/app/NotificationTextTest.kt` — GAIN-05 notification format
- [ ] `app/src/test/java/com/audiobalance/app/GainOffsetSliderTest.kt` — GAIN-01 slider normalization

*Note: ApplyGainsTest, NotificationTextTest, and GainOffsetSliderTest are pure unit tests (no Android dependencies). BalanceRepositoryTest may require Robolectric — defer to plan.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Auto-apply gain on BT connect | GAIN-04 | Requires physical BT device connection | 1. Set gain offset to -6dB on a device. 2. Disconnect BT. 3. Reconnect BT. 4. Verify audio is attenuated. |
| Gain slider real-time audio | GAIN-01 | Requires listening to audio output | 1. Play music. 2. Drag gain slider to -12dB. 3. Verify audio gets quieter. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
