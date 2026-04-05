---
phase: 01-audioeffect-poc
plan: "01"
subsystem: audio-effects
tags: [android, dynamicsprocessing, mediaplayer, audiofx, kotlin, compose, bluetooth]

# Dependency graph
requires:
  - phase: 00-dev-environment
    provides: build toolchain, physical device adb access, Compose project scaffold

provides:
  - FEAS-01 validated: DynamicsProcessing on mediaPlayer.audioSessionId produces audible L/R balance shift
  - Working AudioEffectPoc.kt with exact constructor, enable, applyBalance, release lifecycle
  - Working InternalAudioSource.kt wrapping MediaPlayer and exposing audioSessionId
  - Pixel 10 / Android 16 / API 36 device profile recorded
  - Distinction established: session-ID attachment (WORKS) vs session 0 global (NOT YET TESTED)

affects:
  - 01-02 (external audio / session 0 test — must use this session-ID nuance)
  - 02-audioeffect-service (AudioEffectManager architecture depends on session 0 verdict from 01-02)

# Tech tracking
tech-stack:
  added:
    - android.media.audiofx.DynamicsProcessing (API 28+)
    - android.media.MediaPlayer (internal audio source)
  patterns:
    - DynamicsProcessing lifecycle: Config.Builder → constructor(priority=0, sessionId, config) → setEnabled(true) → setInputGainbyChannel → setEnabled(false) → release()
    - Balance math: leftGainDb = if (fraction > 0) -60f * fraction else 0f; rightGainDb = if (fraction < 0) -60f * (-fraction) else 0f
    - AudioEffect always attached to known audioSessionId, never to an assumed session ID
    - Throwaway POC UI: Compose Column with plain Material3 buttons + on-screen log text

key-files:
  created:
    - app/src/main/java/com/audiobalance/app/poc/AudioEffectPoc.kt
    - app/src/main/java/com/audiobalance/app/poc/InternalAudioSource.kt
    - app/src/main/res/raw/test_tone.mp3
    - .planning/phases/01-audioeffect-poc/DEVICE-INFO.md
  modified:
    - app/src/main/AndroidManifest.xml (added MODIFY_AUDIO_SETTINGS)
    - app/src/main/java/com/audiobalance/app/MainActivity.kt (replaced Hello World with POC UI)

key-decisions:
  - "FEAS-01 VALIDATED on Pixel 10 / Android 16 / API 36: DynamicsProcessing on mediaPlayer.audioSessionId produces audible balance shift"
  - "Session ID used was 15521 (mediaPlayer.audioSessionId), not hardcoded session 0 — these are different code paths"
  - "Session 0 global attachment for external apps (Spotify/YouTube) is NOT yet validated — deferred to Plan 01-02"
  - "Balance at -60dB on attenuated channel provides clear audible shift without requiring total silence"
  - "POC code is intentionally throwaway — Phase 2 re-implements from validated patterns, not from this code"

patterns-established:
  - "DynamicsProcessing pattern: always wrap constructor in try/catch RuntimeException; log hasControl() after setEnabled"
  - "Session-ID pattern: pass mediaPlayer.audioSessionId explicitly, never assume session 0 for internal audio"
  - "Balance math: -60dB on attenuated side, 0dB on dominant side — validated as audible on Pixel 10"

requirements-completed: [FEAS-01]

# Metrics
duration: ~2 sessions (device not connected in session 1; ear-test completed in session 2)
completed: 2026-04-04
---

# Phase 01 Plan 01: Internal Audio Balance POC Summary

**DynamicsProcessing on MediaPlayer session ID (15521) produces audible L/R balance shift on Pixel 10 / Android 16 / API 36 — FEAS-01 VALIDATED**

## Performance

- **Duration:** 2 sessions (device connectivity issue between sessions)
- **Started:** 2026-04-05T06:28:08Z (plan start)
- **Completed:** 2026-04-04
- **Tasks:** 3 (Task 1: device info, Task 2: APK build, Task 3: ear-test)
- **Files modified:** 6

## Accomplishments

- FEAS-01 validated with clear audible evidence: Full Left shifted all audio to left ear, Full Right shifted all audio to right ear, as confirmed by user on personal Bluetooth headphones
- Working debug APK built and installed on Pixel 10 (serial 56191FDCR002NG) with POC Activity providing Play/Stop/Full Left/Center/Full Right controls and on-screen log
- Exact DynamicsProcessing API pattern validated: Config.Builder with no EQ/MBC/PostEQ/Limiter, `setInputGainbyChannel` with -60dB attenuation math, attached to MediaPlayer's own `audioSessionId`

## Task Commits

1. **Task 1: Discover device info and record in DEVICE-INFO.md** - `b54df4d` (chore)
2. **Task 2: Add permission + test_tone.mp3 + POC classes + wire MainActivity** - `161e7cf` (feat)
3. **Task 3: Human ear-test — internal audio balance shift (FEAS-01)** - `4b1b446` (chore)

## Files Created/Modified

- `app/src/main/java/com/audiobalance/app/poc/AudioEffectPoc.kt` — DynamicsProcessing lifecycle: create, enable, applyBalance, release
- `app/src/main/java/com/audiobalance/app/poc/InternalAudioSource.kt` — MediaPlayer wrapper exposing audioSessionId
- `app/src/main/java/com/audiobalance/app/MainActivity.kt` — POC Compose UI with 5 buttons and on-screen log
- `app/src/main/AndroidManifest.xml` — Added MODIFY_AUDIO_SETTINGS permission
- `app/src/main/res/raw/test_tone.mp3` — 440 Hz (L) / 880 Hz (R) stereo tone for ear-test
- `.planning/phases/01-audioeffect-poc/DEVICE-INFO.md` — Physical device profile + session ID nuance

## Decisions Made

**Session ID nuance — critical for Phase 2 architecture:**
The on-screen log showed `Session ID: 15521`, not 0. The POC attached DynamicsProcessing to `mediaPlayer.audioSessionId` (15521). This is fundamentally different from attaching to global session 0 (which would affect all audio system-wide, including Spotify/YouTube). Two code paths exist:

1. **Known session ID (this plan):** `DynamicsProcessing(0, mediaPlayer.audioSessionId, config)` → VALIDATED WORKS
2. **Session 0 global (Plan 01-02):** `DynamicsProcessing(0, 0, config)` → NOT YET TESTED

Phase 2 architecture (AudioEffectManager) must wait for Plan 01-02 verdict before committing to session 0 vs per-session-ID approach.

**Balance math confirmed effective:**
`leftGainDb = if (fraction > 0) -60f * fraction else 0f` — a -60 dB reduction on the attenuated channel is clearly audible without requiring complete silence. This is the pattern Phase 2 should adopt.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `hasControl` property vs method call**
- **Found during:** Task 2 (AudioEffectPoc.kt implementation)
- **Issue:** Plan template specified `val hasControl: Boolean = dp.hasControl` (property access) but the actual DynamicsProcessing API on API 36 exposes it as `hasControl()` (method call)
- **Fix:** Used `created.hasControl()` in the implementation
- **Files modified:** `app/src/main/java/com/audiobalance/app/poc/AudioEffectPoc.kt`
- **Verification:** APK compiled successfully, BUILD SUCCESSFUL
- **Committed in:** `161e7cf` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 Rule 1 bug — API signature mismatch)
**Impact on plan:** Minor; required for compilation. No scope creep.

## Issues Encountered

- **Device not connected during Task 1:** Physical device `56191FDCR002NG` was not available when Task 1 ran. DEVICE-INFO.md was created with placeholder values and emulator data as reference. Physical values were filled in after the ear-test session (Task 3 completion).
- **Session ID expectation:** Plan objective stated "validates DynamicsProcessing on audio session 0" but the actual working session was 15521 (the MediaPlayer's own session). The POC code used `mediaPlayer.audioSessionId` rather than hardcoded 0, which is why it worked. Session 0 global attachment remains to be tested in Plan 01-02.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

**Plan 01-02 (external audio / session 0 test) can proceed:**
- Physical device confirmed available and authorized
- APK installed, POC Activity working
- Key question for 01-02: does `DynamicsProcessing(0, 0, config)` (session 0) affect audio from external apps (Spotify/YouTube), or is it silently ignored on this device?

**Phase 2 readiness depends on 01-02 verdict:**
- If session 0 works for external apps → `AudioEffectManager` uses a single DynamicsProcessing instance on session 0
- If session 0 silent-fails → `AudioEffectManager` must use `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast + per-session-ID Map

**Validated pattern for Phase 2 (regardless of session 0 outcome):**
```kotlin
// Exact constructor that works on Pixel 10 / API 36:
val config = DynamicsProcessing.Config.Builder(
    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
    2, false, 0, false, 0, false, 0, false
).build()
val dp = DynamicsProcessing(0, sessionId, config)
dp.setEnabled(true)
// Balance: attenuate one channel by -60dB
dp.setInputGainbyChannel(0, leftGainDb)  // channel 0 = L
dp.setInputGainbyChannel(1, rightGainDb) // channel 1 = R
// Cleanup:
dp.setEnabled(false)
dp.release()
```

---
*Phase: 01-audioeffect-poc*
*Completed: 2026-04-04*
