---
phase: 5
slug: faq-screen
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-12
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 (Android local unit tests) |
| **Config file** | `app/build.gradle.kts` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest :app:lintDebug` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest :app:lintDebug`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | FAQ-01..FAQ-04 | manual + compile | `./gradlew :app:testDebugUnitTest` | ✅ (regression only) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements. Phase 5 is a static UI screen with no business logic, math helpers, or data transformations warranting new unit tests. Regression coverage provided by existing tests: `GainOffsetSliderTest`, `ApplyGainsTest`, `NotificationTextTest`.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Info icon navigates to FAQ; Back returns without back-stack duplication | FAQ-01 | Navigation back-stack behavior is UI/runtime concern — no unit-testable logic | Launch app → tap info icon → confirm FAQ opens → tap Back → confirm returns to device list → repeat tap info 3× rapidly → confirm only one FAQ instance on back stack |
| FAQ content renders explanations of app/DynamicsProcessing/sliders | FAQ-02 | Static string content — verification is reading-based | Open FAQ → expand all 6 accordion items → confirm each Q/A pair is present and readable |
| GitHub link opens repo in external browser | FAQ-03 | Requires Android Intent dispatch to system browser | Open FAQ → scroll to footer → tap "View on GitHub" → confirm browser opens to the configured GitHub URL |
| Troubleshooting section warns about session 0 conflicts | FAQ-04 | Static content verification | Open FAQ → locate troubleshooting Q → confirm copy explicitly mentions AudioEffect session 0 conflicts with other audio apps |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies (N/A — manual UI verification documented above)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (compile+lint runs after every task)
- [ ] Wave 0 covers all MISSING references (no new tests needed)
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter (pending planner task mapping)

**Approval:** pending
