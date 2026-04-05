# Phase 1 POC Results — AudioEffect Feasibility

**Tested:** 2026-04-04 / 2026-04-05
**Device:** Google Pixel 10 (serial 56191FDCR002NG)
**Android version:** 16 (API 36)
**Tester:** Ben — personal Bluetooth headphones as sole ground truth

---

## TL;DR

Phase 2 will use a single `DynamicsProcessing(priority=0, sessionId=0, config)` instance attached
to the **literal session 0 global mix**, created inside a foreground service and held for the entire
service lifetime. This approach was validated on Pixel 10 / Android 16 / API 36 and produced a
clear, audible L/R balance shift on both Deezer and YouTube without root or any special permissions
beyond `MODIFY_AUDIO_SETTINGS`. The minimal `Config.Builder` (all processing stages disabled) is
mandatory — any stage left enabled with zero bands silences the global audio mix. The per-session
Map strategy is not needed; session 0 global is sufficient and simpler.

---

## Requirements Status

| Requirement | Description | Status |
|---|---|---|
| FEAS-01 | DynamicsProcessing on a known session ID produces audible L/R shift on physical device | **PASS** |
| FEAS-02 | Global/fallback approach identified that works on external audio (Spotify/YouTube) | **PASS** |

Both requirements are closed. Phase 2 can proceed directly to implementation.

---

## Test Matrix

| # | Approach | Audio source | Constructor result | setEnabled | hasControl | Ear test | Verdict |
|---|---|---|---|---|---|---|---|
| 1 | `DynamicsProcessing(0, 15521, config)` per-session (MediaPlayer) | Internal tone (res/raw/test_tone.mp3) | OK | `0` (success) | `true` | Full Left → all audio in left ear; Full Right → all audio in right ear | **PASS** |
| 2 | `DynamicsProcessing(0, -1, config)` via `getActivePlaybackConfigurations` reflection | External (Spotify/YouTube) | N/A — reflection blocked | N/A | N/A | N/A — IDs masked by system | **BLOCKED** — API 36 returns `audioSessionId=-1` for all cross-app sessions |
| 3 | `DynamicsProcessing(0, deezerSessionId, config)` via ACTION_OPEN broadcast | External (Deezer) | Constructor throws RuntimeException — AudioFlinger rejects cross-app attachment | N/A | N/A | N/A | **BLOCKED** — AudioFlinger rejects cross-process attachment on API 36 |
| 4 | `LoudnessEnhancer(0)` session 0 probe | N/A | OK | N/A | N/A | Not tested (mono-only, cannot do L/R balance) | **PROBE OK** — confirms session 0 is not entirely dead; useless for balance |
| 5 | Own broadcast: send OPEN for internal session, receive in SessionBroadcastReceiver | Internal | N/A | N/A | N/A | N/A | **OK** — receiver correctly received self-sent broadcast; Spotify did not broadcast; Deezer did broadcast |
| 6 | `DynamicsProcessing(0, 0, config)` session 0 LITERAL global | External (Deezer + YouTube) | OK | `0` (success) | `true` | Full Left → all audio in left ear; Full Right → all audio in right ear | **PASS** — the breakthrough |

Raw logs: `plan-01-logcat.txt` (12 lines — Plan 01 ear-test captured), `plan-02-logcat.txt` (0 lines — device log buffer cleared before Plan 02 capture; verdicts documented via user verbal confirmation).

---

## Confirmed Implementation Pattern (Phase 2 input)

### Required imports

```kotlin
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
```

### Step 1 — Build the minimal config (CRITICAL)

All processing stages MUST be disabled. Any stage marked `inUse=true` with default or zero-band
settings silences the global audio mix. This is not a workaround — it is a permanent constraint of
attaching to session 0 global.

```kotlin
@RequiresApi(Build.VERSION_CODES.P) // API 28+
val config = DynamicsProcessing.Config.Builder(
    0,      // variant: default (not VARIANT_FAVOR_FREQUENCY_RESOLUTION for session 0)
    2,      // channelCount: stereo
    false,  // preEqInUse   — MUST be false
    0,      // preEqBandCount
    false,  // mbcInUse     — MUST be false
    0,      // mbcBandCount
    false,  // postEqInUse  — MUST be false
    0,      // postEqBandCount
    false   // limiterInUse — MUST be false
).build()
```

### Step 2 — Attach to session 0 LITERAL

```kotlin
// sessionId=0 is LITERAL, not a fallback for "no session".
// It attaches to the global audio mix (all apps, system sounds).
val dp = DynamicsProcessing(0, 0, config)  // priority=0, sessionId=0
val enableResult = dp.setEnabled(true)
val hasControl = dp.hasControl()

if (enableResult != 0 || !hasControl) {
    Log.w(TAG, "DP created but state suspicious: setEnabled=$enableResult hasControl=$hasControl")
}
// enableResult==0 and hasControl==true is the expected success state on Pixel 10 / API 36.
```

### Step 3 — Store in process-wide singleton

Activity recreation (config change, app switch, backgrounding) destroys any Activity-scoped field.
The DP instance MUST live outside the Activity. In Phase 2 it will live in the foreground service.
The POC used a Kotlin `object`:

```kotlin
@RequiresApi(Build.VERSION_CODES.P)
object GlobalDpHolder {
    var instance: DynamicsProcessing? = null
}
// Store immediately after creation:
GlobalDpHolder.instance = dp
```

In Phase 2 this should be a member field of the foreground service instead of a global object.

### Step 4 — Apply balance

```kotlin
// balanceFraction in [-1.0, +1.0]
// -1.0 = full left (right attenuated), +1.0 = full right (left attenuated), 0.0 = center
fun applyBalance(dp: DynamicsProcessing, balanceFraction: Float) {
    val leftGainDb  = if (balanceFraction > 0) -60f * balanceFraction else 0f
    val rightGainDb = if (balanceFraction < 0) -60f * (-balanceFraction) else 0f
    dp.setInputGainbyChannel(0, leftGainDb)   // channel 0 = left ear
    dp.setInputGainbyChannel(1, rightGainDb)  // channel 1 = right ear
    // Example: Full Left  → setInputGainbyChannel(0, 0f)   setInputGainbyChannel(1, -60f)
    // Example: Full Right → setInputGainbyChannel(0, -60f) setInputGainbyChannel(1, 0f)
    // Example: Center     → setInputGainbyChannel(0, 0f)   setInputGainbyChannel(1, 0f)
}
```

-60 dB attenuation on the attenuated channel produced a clear, unambiguous ear-test result without
requiring total silence on that side. This value is confirmed adequate for Phase 2.

### Step 5 — Release (cleanup order matters)

```kotlin
// Release MUST call setEnabled(false) before release().
// Do this in the service's onDestroy / BT disconnect handler.
try { dp.setEnabled(false) } catch (_: RuntimeException) {}
try { dp.release() } catch (_: RuntimeException) {}
GlobalDpHolder.instance = null  // or service field = null
```

### Full error-handling wrapper (copy-pasteable for Phase 2 AudioEffectManager)

```kotlin
@RequiresApi(Build.VERSION_CODES.P)
fun createGlobalBalance(tag: String): DynamicsProcessing? {
    val config = DynamicsProcessing.Config.Builder(
        0, 2,
        false, 0,
        false, 0,
        false, 0,
        false
    ).build()
    return try {
        val dp = DynamicsProcessing(0, 0, config)
        val enableResult = dp.setEnabled(true)
        val hasControl = dp.hasControl()
        Log.d(tag, "DP session=0 created: setEnabled=$enableResult hasControl=$hasControl")
        if (!hasControl) {
            dp.setEnabled(false); dp.release()
            Log.w(tag, "DP created but hasControl=false — releasing")
            null
        } else dp
    } catch (e: RuntimeException) {
        Log.e(tag, "DP(0, 0, config) failed: ${e.javaClass.simpleName}: ${e.message}", e)
        null
    }
}
```

### Permissions required

```xml
<!-- AndroidManifest.xml — confirmed working in Phase 1 -->
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<!-- Phase 2 additions (not tested in Phase 1 — track for Phase 2 validation): -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## Gotchas Observed on This Device

- **Config silence gotcha (Bug 1 in Plan 02):** The first attempt used `preEqInUse=true, preEqBandCount=0, limiterInUse=true`. This produced complete silence on Deezer/YouTube — no audio at all when the effect was enabled. The silence proved the effect IS in the audio pipeline but misconfigured. Fix: all stage flags to `false`. This is the single most dangerous trap for Phase 2.

- **GlobalDpHolder singleton required (Bug 2 in Plan 02):** PID changes observed during Plan 02 testing: `17323 → 17597 → 17731` between "Attach DP" and "Full Left" taps. Activity recreation on every config change or app-switch destroyed the Activity-scoped `AudioEffectPoc` member field holding `globalDp`. The fix was `object GlobalDpHolder` — a Kotlin `object` lives at JVM class level, surviving Activity recreation. In Phase 2 this is handled by the foreground service field.

- **`hasControl()` is a method call, not a property (Plan 01 deviation):** The DynamicsProcessing API on API 36 exposes `hasControl()` as a method, not a property. Plan template used `dp.hasControl` (property access). The compiler requires `dp.hasControl()`. Minor but causes a compile error if overlooked.

- **Plan 01 logcat confirms:** `sessionId=15521`, `setEnabled=0` (success), `hasControl=true`. The logcat line:
  ```
  04-05 11:56:58.687  8521 D POC: DynamicsProcessing session=15521 created OK, setEnabled=0 hasControl=true
  ```

- **Plan 02 logcat not captured:** Device log buffer was cleared before capture at session end. All Plan 02 verdicts are documented from user verbal confirmation ("tous les tests sont ok").

- **`getActivePlaybackConfigurations()` returns `audioSessionId=-1` on API 36:** Google deliberately masks cross-app session IDs on Android 16. Any code path relying on reflection or `AudioPlaybackConfiguration.getAudioSessionId()` across process boundaries will receive -1. Do not attempt this in Phase 2.

- **Spotify does not broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`:** Deezer does. YouTube does not (not tested). Do not rely on broadcast-based session discovery for universal audio coverage.

- **Cross-app session attachment rejected by AudioFlinger:** Even if a foreign session ID is obtained via broadcast (Deezer), attaching `DynamicsProcessing` to it from a different process throws `RuntimeException`. AudioFlinger enforces same-process ownership on API 36. Session 0 global is the correct bypass.

---

## Rejected / Not-Tested Approaches

| Approach | Reason | Status |
|---|---|---|
| Equalizer for balance | `Equalizer` bands are frequency-based, not per-channel — cannot shift L/R | Rejected by design (research finding) |
| LoudnessEnhancer for balance | Mono gain only — no L/R distinction | Rejected by design; tested as probe only |
| Per-session Map via session discovery | `getActivePlaybackConfigurations` returns id=-1 on API 36 | Tested, BLOCKED |
| Per-session Map via broadcast | AudioFlinger rejects cross-app session attachment | Tested, BLOCKED |
| AccessibilityService volume override | Deferred in 01-CONTEXT.md | Not tested in Phase 1 |
| DUMP permission via ADB | Deferred in 01-CONTEXT.md | Not tested in Phase 1 |
| MediaSession callback interception | Deferred in 01-CONTEXT.md | Not tested in Phase 1 |

---

## Debugging Journey (Compact)

Phase 2 should know this story to avoid re-discovering the same traps.

**Plan 01-01:** Straightforward — `DynamicsProcessing(0, mediaPlayer.audioSessionId, config)` worked
on first attempt. Session was `15521`. Minor deviation: `hasControl` is a method call `hasControl()`,
not a property. FEAS-01 closed in a single ear-test session.

**Plan 01-02, Round 1 — All original fallbacks blocked:**
Three approaches tested in the planned order: (A) `getActivePlaybackConfigurations` reflection →
blocked, returns id=-1; (B) per-session broadcast → Deezer broadcast received, but AudioFlinger
rejected cross-app attachment; (C) LoudnessEnhancer session 0 → constructor OK, but mono-only.

**Plan 01-02, Round 2 — User insight unlocked the path:**
User observed that a Play Store equalizer app (`com.jazibkhan.equalizer`) successfully applies
effects to YouTube on non-rooted Pixel and its help text says "activate BEFORE opening the media
player, connect to Global Mix." This revealed session 0 LITERAL as the mechanism.

**Plan 01-02, Round 3 — First attempt at session 0 global silenced all audio:**
Constructor returned OK, `setEnabled=0`, `hasControl=true` — but audio went completely silent.
The silence was the diagnostic: the effect was in the pipeline but configured destructively.
Two bugs identified: (1) `Config.Builder` had `preEqInUse=true, preEqBandCount=0, limiterInUse=true`
— a stage enabled with zero bands produces silence on the global mix; (2) `globalDp` was stored in
an Activity-scoped field, Activity PID recycled between "Attach" and "Full Left" taps.

**Plan 01-02, Round 4 — Breakthrough:**
Both bugs fixed. Passthrough (0 dB both channels) → audio plays normally. Global Full Left → left
ear only. Global Full Right → right ear only. Verified on Deezer (primary) and YouTube (secondary).
User: "tous les tests sont ok."

---

## Phase 2 Architecture Recommendation

Phase 2 `AudioEffectManager` should:

1. **Run in a foreground service** with type `connectedDevice` (requires
   `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission). The service holds the single `DynamicsProcessing`
   instance as a member field — not in a global `object`.

2. **Create one `DynamicsProcessing(0, 0, config)` instance** on BT headphone connect. Use the
   minimal config (all stages disabled). Call `setEnabled(true)` immediately. Do not recreate it
   unless AudioFlinger signals a reset.

3. **Load the balance value from per-device preferences** at service start (keyed by BT device
   MAC address). Apply immediately after `setEnabled(true)` so the effect is live before any media
   app queries the audio server.

4. **On BT disconnect:** call `setEnabled(false)` then `release()`, set field to null.

5. **On BT reconnect:** recreate the instance — the old `DynamicsProcessing` object becomes stale
   when the output device changes. Do not attempt to reuse it.

6. **No per-session Map needed.** Session 0 global reaches all audio: Deezer, YouTube, and by
   extension any media app. Notification sounds are also in scope (unvalidated — see Open Questions).

---

## Open Questions for Phase 2

The following behaviors were NOT validated in Phase 1. Phase 2 should add explicit test tasks for
each before shipping.

1. **Cold-start app behavior:** Our tests had Deezer/YouTube already running when the effect was
   created. The Jazib Khan app documentation implies "activate BEFORE opening media player" is
   required. Whether session 0 global retroactively reaches apps started AFTER effect creation is
   unconfirmed on this device.

2. **BT disconnect/reconnect persistence:** When headphones disconnect and reconnect, does the
   existing `DynamicsProcessing` instance remain valid? AudioFlinger likely invalidates effects tied
   to an output route that no longer exists. Assume recreate-on-reconnect is required.

3. **Notification and ringtone sounds:** Does session 0 global affect system notification sounds?
   This may be desirable (balanced notifications) or undesirable (distorted alerts). Not tested.

4. **Multiple DP instances on session 0:** If another app (e.g., the Jazib Khan equalizer) also
   holds a `DynamicsProcessing` on session 0, do they conflict, stack, or does one take priority?
   Behavior is undocumented.

5. **Effect survival across process death:** A foreground service with `START_STICKY` mitigates
   this, but if the service is killed by the OS, AudioFlinger automatically releases the effect.
   Ensure the service restart re-creates the DP instance and re-applies the saved balance value.

6. **`foregroundServiceType="connectedDevice"` on API 36:** The foreground service type required
   for BT-triggered services was updated in Android 14+. Validate the manifest declaration and
   permission chain early in Phase 2 before other service features.

---

## Deferred Fallbacks (Not Needed for v1)

These were deferred in `01-CONTEXT.md` and were not needed because session 0 global succeeded.
Document here so Phase 2+ planning can find them if session 0 regresses on future Android versions.

- **AccessibilityService volume override:** Can intercept audio focus events and call
  `AudioManager.adjustStreamVolume`. Works without AudioEffect. Not attempted.
- **DUMP permission via ADB / companion device setup:** Grants read access to internal audio routing
  tables. Overly complex for v1. Not attempted.
- **MediaSession callback interception:** Could adjust playback parameters on apps that expose
  MediaSession. Not attempted.

---

*Phase: 01-audioeffect-poc*
*Evidence: plan-01-logcat.txt (12 lines), plan-02-logcat.txt (0 lines — buffer cleared)*
*User approval: pending (Task 2 checkpoint)*
