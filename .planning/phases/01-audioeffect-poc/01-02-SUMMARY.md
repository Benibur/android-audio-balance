---
phase: 01-audioeffect-poc
plan: "02"
subsystem: audio-effects
tags: [android, dynamicsprocessing, session0, global-mix, kotlin, compose, bluetooth, breakthrough]

# Dependency graph
requires:
  - phase: 01-audioeffect-poc
    plan: "01"
    provides: FEAS-01 validated, AudioEffectPoc.kt pattern, InternalAudioSource.kt, Pixel 10 device profile

provides:
  - FEAS-02 validated via unexpected path: DynamicsProcessing(0, 0, config) on session 0 LITERAL produces audible L/R balance on external audio (Deezer, YouTube) on Pixel 10 / Android 16 / API 36
  - GlobalDpHolder singleton pattern for Activity-recreation-safe effect lifetime
  - Minimal DP Config (all stages disabled) required for session 0 global to not silence audio
  - Negative findings: getActivePlaybackConfigurations reflection blocked (id=-1), cross-app session attach rejected, Spotify does not broadcast its session
  - Open questions carried to Phase 2 (cold-start apps, BT reconnect, notification sounds)

affects:
  - 01-03 (documentation plan — primary finding to document is session 0 global approach)
  - 02-audioeffect-service (AudioEffectManager must use single DP instance on session 0, created in foreground service, with GlobalDpHolder or service-scoped singleton)

# Tech tracking
tech-stack:
  added:
    - android.media.audiofx.DynamicsProcessing session=0 LITERAL (global mix)
    - GlobalDpHolder object (process-wide singleton)
    - SessionBroadcastReceiver (ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
    - FallbackProbes (discoverActiveSessions, tryLoudnessEnhancerSession0, tryDynamicsProcessingOnSession)
  patterns:
    - Global session 0 constructor: DynamicsProcessing(priority=0, sessionId=0, config) — session 0 is LITERAL, not symbolic
    - Minimal config: all stages disabled (preEqInUse=false, mbcInUse=false, postEqInUse=false, limiterInUse=false) — enabling ANY stage with 0 bands silences audio
    - Per-channel gain: setInputGainbyChannel(0, leftDb) / setInputGainbyChannel(1, rightDb) — same API as per-session case
    - Singleton required: DynamicsProcessing instance must outlive Activity recreation — store in object GlobalDpHolder or foreground service
    - Timing: effect must be created while device is in active playback state; "create BEFORE media player" may be needed for cold-start apps (unvalidated)

key-files:
  created:
    - app/src/main/java/com/audiobalance/app/poc/SessionBroadcastReceiver.kt
    - app/src/main/java/com/audiobalance/app/poc/FallbackProbes.kt
    - .planning/phases/01-audioeffect-poc/plan-02-logcat.txt (empty — device log buffer cleared before capture)
  modified:
    - app/src/main/java/com/audiobalance/app/poc/AudioEffectPoc.kt (added createGlobalSession0, applyGlobalBalance, applyGlobalPassthrough, releaseGlobal, GlobalDpHolder)
    - app/src/main/java/com/audiobalance/app/MainActivity.kt (added 8 fallback buttons + global session 0 buttons + selectable logs)
    - app/src/main/AndroidManifest.xml (added SessionBroadcastReceiver with OPEN_AUDIO_EFFECT_CONTROL_SESSION intent-filter)

key-decisions:
  - "FEAS-02 VALIDATED: DynamicsProcessing(0, 0, config) on session 0 LITERAL shifts audio to L or R ear on Deezer/YouTube — Phase 2 uses single-instance global approach, not per-session Map"
  - "Minimal DP Config mandatory for session 0 global: all stages (preEq, mbc, postEq, limiter) must be false — any enabled stage with 0 bands silences the global mix"
  - "GlobalDpHolder singleton required: Activity recreation destroys member fields; process-wide object survives config changes"
  - "AudioManager.getActivePlaybackConfigurations reflection returns id=-1 on API 36 — blocked, not usable for cross-app session discovery"
  - "Phase 2 AudioEffectManager architecture: single DynamicsProcessing(0, 0, config) in foreground service; update gain per BT device profile"

requirements-completed: [FEAS-02]

# Metrics
duration: ~3 sessions (multi-iteration debugging journey; breakthrough on session 3)
completed: 2026-04-04
---

# Phase 01 Plan 02: External Audio Balance POC Summary

**DynamicsProcessing on session 0 LITERAL (global mix) produces audible L/R balance shift on Deezer and YouTube on Pixel 10 / Android 16 / API 36 — FEAS-02 VALIDATED via unexpected path**

## Performance

- **Duration:** ~3 sessions (significant debugging journey)
- **Completed:** 2026-04-04
- **Tasks:** 3 (Task 1: fallback infrastructure, Task 2: UI extension, Task 3: ear-test with multi-iteration debugging)
- **Files modified:** 5

## Accomplishments

- FEAS-02 validated with clear audible evidence: Global Full Left shifted external audio (Deezer/YouTube) entirely to left ear; Global Full Right shifted entirely to right ear — confirmed on personal Bluetooth headphones
- Discovered and resolved two critical bugs that caused the first global session 0 attempt to silence all audio
- Established the GlobalDpHolder singleton pattern that survives Activity recreation
- Documented negative findings for three other fallback approaches (reflection blocked, cross-app attach rejected, Spotify broadcast opt-out)
- Left clean diagnostic infrastructure in the POC (selectable log text, Log.d mirroring) for Plan 03

## Task Commits

1. **Task 1: Add SessionBroadcastReceiver + FallbackProbes + manifest entry** — `1881e84` (feat)
2. **Task 2: Extend MainActivity with fallback buttons and external-audio workflow** — `94c8f53` (feat)
3. **Task 3 (extension) — global session 0 buttons added after initial fallbacks failed** — `a53fa2e` (feat)
4. **Task 3 (fix 1) — simplified DP config + GlobalDpHolder singleton** — `7b489d7` (fix)
5. **Task 3 (fix 2) — selectable logs + logcat mirroring** — `70a30b5` (feat)

## Files Created/Modified

- `app/src/main/java/com/audiobalance/app/poc/AudioEffectPoc.kt` — Extended with `createGlobalSession0`, `applyGlobalBalance`, `applyGlobalPassthrough`, `releaseGlobal`, and `GlobalDpHolder` object
- `app/src/main/java/com/audiobalance/app/MainActivity.kt` — Extended with 8 fallback buttons + global session 0 group + selectable on-screen logs
- `app/src/main/java/com/audiobalance/app/poc/SessionBroadcastReceiver.kt` — BroadcastReceiver for OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION; stores observed sessions in companion object map
- `app/src/main/java/com/audiobalance/app/poc/FallbackProbes.kt` — `discoverActiveSessions`, `tryDynamicsProcessingOnSession`, `tryLoudnessEnhancerSession0`, `sendOpenSessionBroadcast`
- `app/src/main/AndroidManifest.xml` — SessionBroadcastReceiver declared with OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION intent-filter

## Debugging Journey (Critical Context for Phase 2)

This plan required three rounds of iteration to reach the breakthrough. The full story is documented here because it contains architectural knowledge not found in any Android documentation.

### Round 1: Original fallback chain tested, all blocked

The original plan tested three fallbacks for external audio (Spotify/YouTube):

**Test A — AudioManager session discovery (reflection):**
`AudioManager.getActivePlaybackConfigurations` returned sessions with `audioSessionId = -1` on API 36. The session IDs are deliberately masked — Google blocked cross-app session discovery via reflection on Android 16. Result: BLOCKED.

**Test B — Per-session DP via broadcast:**
`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcasts were received from Deezer but NOT from Spotify. Spotify does not implement the broadcast protocol. When attaching `DynamicsProcessing` to Deezer's session ID obtained via broadcast, AudioFlinger rejected the cross-app attachment. Result: BLOCKED.

**Test C — LoudnessEnhancer session 0:**
`LoudnessEnhancer(0)` returned OK. This confirmed session 0 is not entirely dead on API 36. However, LoudnessEnhancer cannot do L/R balance — it is mono-only. Result: PROBE ONLY, no balance possible.

**Test D — Own broadcast receiver:**
Our `SessionBroadcastReceiver` correctly received the test broadcast sent from our own app. Receiver infrastructure works. Result: OK (but no external app sent broadcasts except Deezer, which rejected cross-app attachment).

### Round 2: User observes Jazib Khan Equalizer app — key insight

The user reported that a Play Store app (`com.jazibkhan.equalizer`) successfully applies equalizer effects to YouTube on non-rooted Pixel. Its help text says: "activate the equalizer BEFORE opening the media player" and it connects to "Global Mix".

This revealed that session 0 LITERAL (not `mediaPlayer.audioSessionId`) can still reach the global audio mix on Android 16, provided the effect is created at the right moment. The original Plan 01-02 test had not used session 0 for external audio — only for internal audio (where it appeared to have no effect because session 0 was not the same as the MediaPlayer's session).

A new diagnostic button was added: "Attach DP session 0 GLOBAL" using `DynamicsProcessing(0, 0, config)` with a `Config.Builder` that had all processing stages ostensibly disabled.

### Round 3: First global session 0 attempt — audio silenced (proves effect IS in path)

First ear-test: YouTube/Deezer went completely silent when the global effect was enabled. This was alarming but actually proved that session 0 DOES reach the global mix — the silence meant the effect was in the audio pipeline and doing something unintended.

Two bugs were identified:

**Bug 1 — DP Config produced silence:**
The `Config.Builder` had `preEqInUse=true` with `preEqBandCount=0`, `limiterInUse=true` with default settings. A `DynamicsProcessing` stage that is marked "in use" with zero bands or default limiter values produces silence or severe attenuation on the global mix. Fix: set ALL stages to `false` (preEq, mbc, postEq, limiter disabled). Only the implicit input gain stage remains, controlled by `setInputGainbyChannel`.

**Bug 2 — GlobalDpHolder not yet in place:**
The first implementation stored `globalDp` as a member field of `AudioEffectPoc`, which was Activity-scoped. Between first tap ("Attach") and second tap ("Full Left"), the Activity had been recreated (PID changes observed: 17323 → 17597 → 17731), destroying the `globalDp` reference. The fix was `object GlobalDpHolder` — a Kotlin `object` lives at JVM class level, surviving Activity recreation for the entire process lifetime.

### Round 4: Breakthrough — all tests pass

After both fixes were deployed:

- **Passthrough (0 dB on both channels):** Deezer/YouTube audio played normally. Config confirmed non-destructive.
- **Global Full Left:** Audio shifted clearly to left ear on external audio. AUDIBLE SHIFT confirmed.
- **Global Full Right:** Audio shifted clearly to right ear on external audio. Symmetric confirmation.

User verdict: "tous les tests sont ok" — all tests passed.

## Ear-Test Verdicts (Task 3 Final)

| Test | Approach | Result |
|------|----------|--------|
| A | AudioManager.getActivePlaybackConfigurations (reflection) | BLOCKED — returns id=-1 on API 36 |
| B | Per-session DP via ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION broadcast | BLOCKED — Spotify: no broadcast; Deezer: cross-app attachment rejected by AudioFlinger |
| C | LoudnessEnhancer session 0 | OK (constructor accepted) — but mono only, cannot do L/R balance |
| D | Own broadcast receiver sanity | OK — receiver correctly received self-sent broadcast |
| E | DynamicsProcessing(0, 0, config) GLOBAL (unexpected path) | PASS — audible L/R shift on Deezer + YouTube confirmed |

External app tested: Deezer (primary), YouTube (secondary)
Bonus test (cold-start: open app AFTER effect active): NOT TESTED — deferred to Phase 2

## The Winning Pattern (Phase 2 Reference)

```kotlin
// 1. Create minimal config — ALL stages disabled
val config = DynamicsProcessing.Config.Builder(
    0,      // variant (default)
    2,      // channelCount: stereo
    false,  // preEqInUse — MUST be false
    0,      // preEqBandCount
    false,  // mbcInUse — MUST be false
    0,      // mbcBandCount
    false,  // postEqInUse — MUST be false
    0,      // postEqBandCount
    false   // limiterInUse — MUST be false
).build()

// 2. Attach to session 0 LITERAL (not mediaPlayer.audioSessionId)
val dp = DynamicsProcessing(0, 0, config)  // priority=0, sessionId=0
dp.setEnabled(true)

// 3. Store in process-wide singleton (survives Activity recreation)
GlobalDpHolder.instance = dp

// 4. Apply balance (same API as per-session case)
dp.setInputGainbyChannel(0, leftDb)   // channel 0 = left ear
dp.setInputGainbyChannel(1, rightDb)  // channel 1 = right ear
// Full left: leftDb=0f, rightDb=-60f
// Full right: leftDb=-60f, rightDb=0f
// Center: leftDb=0f, rightDb=0f

// 5. Release on service stop
dp.setEnabled(false)
dp.release()
GlobalDpHolder.instance = null
```

## Decisions Made

**Phase 2 architecture: single-instance global approach**
FEAS-02 confirmed that `DynamicsProcessing(0, 0, config)` reaches the global mix. Phase 2 `AudioEffectManager` should create ONE instance on session 0, not a `Map<sessionId, DynamicsProcessing>`. The per-session Map fallback is no longer needed.

**Minimal DP Config is mandatory for session 0 global**
Any processing stage marked `inUse=true` with zero bands or default settings will silence or distort the global audio mix. The safe config has all stages disabled; only `setInputGainbyChannel` is used for balance. This constraint is permanent — it is not a workaround.

**GlobalDpHolder singleton is the correct lifecycle pattern**
Activity recreation (config changes, backgrounding, app switching) destroys Activity-scoped member fields. The DP instance for global session 0 must live in an `object` (Kotlin singleton) or, in Phase 2, in the foreground service. Never store it in a ViewModel or Activity field.

**Session 0 timing requirement (partially validated)**
The Jazib Khan Equalizer help text says "activate before opening media player." Our tests had the effect created while audio was already playing (Deezer/YouTube running), and it worked. Whether it also works for apps started AFTER the effect is created is not yet confirmed — this remains an open question for Phase 2 validation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] DP Config with preEqInUse=true and 0 bands silences global audio**
- **Found during:** Task 3 (first ear-test of global session 0)
- **Issue:** `Config.Builder` had `preEqInUse=true, preEqBandCount=0, limiterInUse=true` which produced silence on the global audio mix when the effect was enabled on session 0
- **Fix:** Set all stage flags to `false`: `preEqInUse=false, mbcInUse=false, postEqInUse=false, limiterInUse=false`
- **Files modified:** `app/src/main/java/com/audiobalance/app/poc/AudioEffectPoc.kt`
- **Commit:** `7b489d7`

**2. [Rule 1 - Bug] globalDp member field lost on Activity recreation**
- **Found during:** Task 3 (observed PID changes 17323 → 17597 → 17731 between taps)
- **Issue:** `globalDp` was stored as a field in Activity-scoped `AudioEffectPoc` instance; Activity was recreated between "Attach" and "Full Left" taps, destroying the reference
- **Fix:** Created `object GlobalDpHolder` Kotlin singleton; moved instance storage there
- **Files modified:** `app/src/main/java/com/audiobalance/app/poc/AudioEffectPoc.kt`
- **Commit:** `7b489d7`

**3. [Rule 2 - Missing functionality] Global session 0 buttons not in original plan**
- **Found during:** Task 3 checkpoint — all original fallback paths blocked, user insight about Jazib Khan app
- **Context:** Plan 01-02 originally only tested getActivePlaybackConfigurations reflection, per-session broadcasts, and LoudnessEnhancer. User observation about a working equalizer app revealed the session 0 global path. Adding diagnostic buttons for this was essential to validate FEAS-02.
- **Addition:** "Attach DP session 0 GLOBAL", "Global: Full Left", "Global: Center", "Global: Full Right", "Global: Passthrough (0dB)", "Release Global DP" buttons
- **Files modified:** `app/src/main/java/com/audiobalance/app/MainActivity.kt`, `app/src/main/java/com/audiobalance/app/poc/AudioEffectPoc.kt`
- **Commits:** `a53fa2e`, `7b489d7`

---

**Total deviations:** 3 (2 Rule 1 bugs auto-fixed, 1 Rule 2 essential addition)
**Impact on plan:** The Rule 2 addition was the breakthrough that validated FEAS-02.

## Logcat Capture

Device log buffer was cleared before logcat capture was attempted at session end.
`plan-02-logcat.txt` is present but empty (0 lines).
The ear-test verdicts are documented in this SUMMARY based on user verbal confirmation during the session.

## Deferred Items (Open Questions for Phase 2)

The following behaviors were not validated in this plan:

1. **Cold-start app behavior:** Does the global DP affect audio from an app opened AFTER the effect was created? Not tested — Deezer/YouTube were running before effect creation in our tests. The Jazib Khan help text implies "before" is required, but this was not confirmed on our device.

2. **System notification sound behavior:** Does the global DP apply to system sounds (notifications, ringtones)? Not tested. May be desirable to suppress or pass through depending on user preference.

3. **BT disconnect/reconnect persistence:** When Bluetooth headphones disconnect and reconnect, does the DP instance remain valid? The effect might become stale when the output device changes. Phase 2 foreground service must handle `ACTION_ACL_DISCONNECTED` + `ACTION_ACL_CONNECTED` events and potentially recreate the DP instance.

4. **Multiple DP instances on session 0:** If another app also creates a DynamicsProcessing on session 0 (e.g., a system equalizer), do they conflict or stack? Behavior is undefined in documentation.

5. **Effect lifetime across process death:** The `object GlobalDpHolder` lives in process memory. If the process is killed by the OS, the effect is released automatically by the audio server. Phase 2 foreground service + `START_STICKY` will mitigate this.

## Next Phase Readiness

**Plan 01-03 (documentation + phase verification) can proceed:**
- Primary finding to document: session 0 global approach with minimal config
- All ear-test verdicts captured
- Negative findings documented (API 36 reflection block, cross-app attach rejection)
- Open questions listed for Phase 2 validation plan

**Phase 2 AudioEffectManager architecture is now decided:**
- Single `DynamicsProcessing(0, 0, config)` instance in a foreground service
- Config: all stages disabled (minimal passthrough)
- Lifecycle: create on service start / BT connect, update gain per device profile, release on service stop / BT disconnect
- Handle BT reconnect events — may need to recreate DP instance
- Add validation early in Phase 2: test cold-start app behavior

---
*Phase: 01-audioeffect-poc*
*Completed: 2026-04-04*
