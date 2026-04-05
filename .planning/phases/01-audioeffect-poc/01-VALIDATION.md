---
phase: 1
slug: audioeffect-poc
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> **Note:** Phase 1 is a POC phase — validation is primarily human ear-test, not automated tests. This VALIDATION.md captures the manual test protocol and the few automated checks that apply (compilation, APK build, logcat assertions).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build + adb logcat filtering + human ear-test |
| **Config file** | `app/build.gradle.kts` (existing) |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug && adb -s 56191FDCR002NG install -r app/build/outputs/apk/debug/app-debug.apk` |
| **Estimated runtime** | ~30-60 seconds per build/deploy cycle |

---

## Sampling Rate

- **After every task commit:** `./gradlew assembleDebug` must succeed (compile gate)
- **After every plan wave:** Deploy to physical device, confirm Activity launches without crash
- **Before phase verification:** Human ear-test executed for each approach tried, results logged in POC-RESULTS.md
- **Max feedback latency:** ~60 seconds for build, human test is manual

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Human Verify | Status |
|---------|------|------|-------------|-----------|-------------------|--------------|--------|
| 01-01-* | 01 | 1 | FEAS-01 | build + logcat | `./gradlew assembleDebug` + `adb logcat \| grep AudioEffect` | Yes — ear test | ⬜ pending |
| 01-02-* | 02 | 2 | FEAS-02 | build + logcat | Same + broadcast capture logs | Yes — ear test on fallbacks | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/main/AndroidManifest.xml` — add `MODIFY_AUDIO_SETTINGS` permission (compile-time requirement)
- [ ] Verify device OEM/Android version before building (informs expected behavior)

*Existing infrastructure (Phase 0) covers Gradle build and ADB deploy. No test framework install needed — this is a POC with manual verification.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| DynamicsProcessing session 0 produces audible L/R balance shift on internal audio | FEAS-01 | Silent failure is undetectable programmatically — only ear confirms | 1. Connect BT headphones. 2. Launch POC Activity. 3. Play internal test tone. 4. Tap "Full Left". 5. User confirms: is right channel quieter or silent? |
| DynamicsProcessing session 0 produces audible L/R balance shift on external audio (Spotify/YouTube) | FEAS-01 | Same reason, different audio source | 1. Start Spotify playing music. 2. Launch POC Activity. 3. Tap "Attach session 0 effect". 4. Tap "Full Left". 5. User confirms audibly. |
| Fallback: per-session approach works when session 0 fails | FEAS-02 | Same | 1. Play internal audio. 2. Tap "Try per-session fallback". 3. Tap "Full Left". 4. User confirms audibly. |
| Documentation of working approach is accurate | FEAS-02 (deliverable) | Requires human judgment on completeness | Review POC-RESULTS.md: constructor signature present, permissions listed, error handling pattern documented, device/Android version recorded |

---

## Validation Sign-Off

- [ ] APK builds successfully after every task
- [ ] Each approach logs attempt + exception status to logcat (grep-verifiable)
- [ ] POC-RESULTS.md contains findings for all approaches tried (session 0 internal, session 0 external, per-session fallback, etc.)
- [ ] Human ear-test results recorded per approach (audible / inaudible / not-attempted)
- [ ] At least ONE approach is marked "audible" — otherwise phase is in gaps_found state and user must decide to explore deferred fallbacks or pivot project
- [ ] `nyquist_compliant: true` set in frontmatter after sign-off

**Approval:** pending
