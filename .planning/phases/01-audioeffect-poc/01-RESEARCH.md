# Phase 1: AudioEffect POC - Research

**Researched:** 2026-04-04
**Domain:** Android AudioEffect API, stereo balance via per-channel gain, session 0 vs per-session fallbacks
**Confidence:** MEDIUM — core API facts are HIGH (official docs + AOSP source); session 0 behavior on the specific device is UNKNOWN until physical test

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Validation a l'oreille uniquement — pas d'enregistrement loopback
- Critere de succes : difference audible nette quand la balance est poussee a fond d'un cote
- Device de test : 56191FDCR002NG (physique). Ecouteurs BT personnels = ground truth.
- Double mode obligatoire : source audio interne (MediaPlayer/ExoPlayer) ET source externe (Spotify/YouTube en parallele)
- Si session 0 echoue, tester dans l'ordre : (1) per-session via ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, (2) LoudnessEnhancer session 0, (3) autres AudioEffect subclasses
- Fallbacks NON tentes en Phase 1 : AccessibilityService, DUMP permission, MediaSession callback interception

### Claude's Discretion
- Langage UI (Compose vs XML) — Compose recommande (deja configure)
- Source audio embarquee (tone genere vs fichier MP3 dans res/raw)
- Mecanisme de detection "session 0 echoue" (try/catch, test de silence, etc.)
- Structure du code POC (monolithique vs helpers)

### Deferred Ideas (OUT OF SCOPE)
- AccessibilityService fallback
- DUMP permission via ADB (Wavelet-style enhanced session detection)
- MediaSession callback interception
- Enregistrement loopback / mesure quantitative
- Ajustement progressif multi-niveaux (Phase 3)
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| FEAS-01 | AudioEffect session 0 applique un offset de balance stereo gauche/droite mesurable sur le device physique | Approach A (DynamicsProcessing) and Approach B (Equalizer) documented with exact constructors; silent failure detection method provided |
| FEAS-02 | L'approche de fallback est identifiee si session 0 echoue | Per-session broadcast fallback (Approach C) fully documented; LoudnessEnhancer fallback (Approach D) assessed as non-viable for balance |
</phase_requirements>

---

## Summary

Android's AudioEffect API allows attaching audio processing stages to a specific audio session ID. Session 0 is a special "global output mix" session that applies to all audio on the device — it was deprecated in 2012 (shortly after Android 2.3) but was never fully removed. As of 2025, session 0 still works on a significant fraction of Android devices, but behavior is entirely OEM-controlled: some manufacturers silently accept the effect object but route around it at the native layer, others throw exceptions, and a minority still pass audio through it correctly.

The best modern approach for stereo balance is `DynamicsProcessing` (API 28+, Android 9), which exposes per-channel `inputGain` in dB. For session 0 on the global mix, try `DynamicsProcessing(0, 0)` first. The standard `Equalizer` subclass can also try session 0 but cannot do true L/R balance — it only attenuates/boosts all frequencies equally per-band, and its bands are not per-channel. **Neither the Equalizer nor any other standard AudioEffect subclass exposes true per-channel gain — only DynamicsProcessing does this cleanly.**

If session 0 fails (silently or with exception), the per-session fallback intercepts `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcasts sent by the internal MediaPlayer and attaches the effect to that specific session. This works reliably for the internal audio source. For external apps (Spotify, YouTube), it depends entirely on whether those apps send the broadcast — Spotify does; YouTube historically does not.

**Primary recommendation:** Implement Approach A (`DynamicsProcessing` session 0) first. If physical device test confirms audible balance shift, Phase 2 can use the same pattern. If it fails, implement Approach C (per-session broadcast) for the internal source, and document external-app limitation.

**Note for planner:** The device `56191FDCR002NG` is an ADB serial number, not a model number. The planner should add a task to run `adb -s 56191FDCR002NG shell getprop ro.product.model` and `getprop ro.build.version.release` at the start of the POC to identify the OEM and Android version, as this directly determines which AudioEffect behaviors to expect.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `android.media.audiofx.DynamicsProcessing` | API 28+ (built-in, no dep) | Per-channel gain control for stereo balance | Only AudioEffect subclass with true L/R channel independence; used by Wavelet |
| `android.media.audiofx.Equalizer` | API 9+ (built-in) | Fallback test: session 0 accept/reject probe | Oldest and most widely supported subclass; useful to detect if ANY effect works |
| `android.media.audiofx.LoudnessEnhancer` | API 19+ (built-in) | Secondary fallback probe on session 0 | Separate code path in AudioFlinger; sometimes accepted when Equalizer isn't |
| `android.media.MediaPlayer` | API 1+ (built-in) | Internal audio source for reproducible testing | `getAudioSessionId()` gives a valid session ID for per-session approach |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `android.media.AudioManager` | API 1+ (built-in) | `getActivePlaybackConfigurations()` (API 26+) | List active sessions from all apps — no special permission needed on API 26+ |
| `android.media.AudioPlaybackConfiguration` | API 26+ (built-in) | Individual playback session metadata including session ID | Use with `getActivePlaybackConfigurations()` in the per-session fallback |
| `android.media.ToneGenerator` | API 1+ (built-in) | Simple DTMF/supervisory test tones | Quick sanity test; does NOT support stereo channel separation |
| `android.media.AudioTrack` | API 1+ (built-in) | Programmatic stereo tone with L/R separation | Use if a raw PCM stereo test signal is needed; more flexible than ToneGenerator |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `DynamicsProcessing` | `Equalizer` with band gain | Equalizer bands are NOT per-channel; you cannot achieve true L/R balance |
| `DynamicsProcessing` | Custom AudioEffect UUID | Requires OEM-provided native library; not portable |
| Internal MP3 in `res/raw` | `AudioTrack` with generated PCM | MP3 is simpler to implement; AudioTrack gives more control for L/R test signal |
| `MediaPlayer` | `ExoPlayer` (Media3) | MediaPlayer has `getAudioSessionId()` natively; ExoPlayer also has it via `AudioComponent`; both work for this POC |

**Installation:** No additional Gradle dependencies needed. All required classes are in the Android framework (built-in).

---

## Architecture Patterns

### Recommended Project Structure for POC

```
app/src/main/java/com/audiobalance/app/
├── MainActivity.kt               # Replace existing Hello World — single Activity for POC
├── poc/
│   ├── AudioEffectPoc.kt         # Effect lifecycle: create, enable, disable, release
│   ├── InternalAudioSource.kt    # MediaPlayer wrapper: load, play, pause, getSessionId
│   └── SessionBroadcastReceiver.kt  # BroadcastReceiver for ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
app/src/main/res/raw/
└── test_tone.mp3                 # Short stereo audio clip (10-30s loop)
app/src/main/AndroidManifest.xml  # Add MODIFY_AUDIO_SETTINGS permission + receiver declaration
```

### Pattern 1: DynamicsProcessing for Stereo Balance (Approach A — Try First)

**What:** Create a `DynamicsProcessing` effect on audio session 0 (global mix) using only the `inputGain` stage. Set channel 0 (left) gain = 0 dB and channel 1 (right) gain = -60 dB to push audio fully left. Reverse for right.

**When to use:** First attempt on every device. Requires API 28+ (minSdk 26 means this check must be runtime-gated, but since we target a specific physical device we can verify its Android version first).

**Constructor:**
```kotlin
// Source: AOSP DynamicsProcessing.java + Microsoft Learn .NET Android API reference
// Three constructors available:
// DynamicsProcessing(audioSession: Int)
// DynamicsProcessing(priority: Int, audioSession: Int)
// DynamicsProcessing(priority: Int, audioSession: Int, cfg: DynamicsProcessing.Config?)

val config = DynamicsProcessing.Config.Builder(
    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
    2,      // channelCount — stereo = 2
    false, 0,  // preEqInUse=false, preEqBandCount=0
    false, 0,  // mbcInUse=false,   mbcBandCount=0
    false, 0,  // postEqInUse=false, postEqBandCount=0
    false      // limiterInUse=false
).build()

val dp = DynamicsProcessing(0 /* priority */, 0 /* session 0 = global */, config)
dp.setEnabled(true)

// Push balance fully left: left = 0 dB, right = -60 dB
dp.setInputGainbyChannel(0, 0f)   // channel 0 = left, 0 dB
dp.setInputGainbyChannel(1, -60f) // channel 1 = right, -60 dB

// Reset to center:
dp.setInputGainbyChannel(0, 0f)
dp.setInputGainbyChannel(1, 0f)

// Cleanup (ALWAYS call before Activity is destroyed):
dp.setEnabled(false)
dp.release()
```

**Per-session variant (Approach C sub-path):**
```kotlin
// Same constructor, but pass a real session ID instead of 0:
val dp = DynamicsProcessing(0, mediaPlayer.audioSessionId, config)
```

### Pattern 2: Session 0 Silent Failure Detection

**What:** Session 0 can fail in two distinct ways:
1. **Hard failure:** Constructor throws `RuntimeException`, `IllegalArgumentException`, or `UnsupportedOperationException` — easy to catch.
2. **Silent failure:** Constructor returns normally, `setEnabled(true)` returns 0 (SUCCESS), `hasControl()` returns true — but no audio effect is applied. This is the dangerous case.

**Detection strategy:**
```kotlin
fun tryCreateEffect(sessionId: Int): EffectResult {
    return try {
        val config = buildMinimalDpConfig()
        val dp = DynamicsProcessing(0, sessionId, config)
        val enableResult = dp.setEnabled(true)
        val hasControl = dp.hasControl
        Log.d("POC", "Effect created: enabled=$enableResult hasControl=$hasControl session=$sessionId")
        // enableResult == 0 means SUCCESS; hasControl == true means this instance controls the engine
        // BUT: silent failure is still possible even with hasControl=true on some OEMs
        // The only reliable test for silent failure is user ear confirmation
        EffectResult.Created(dp, hasControl, enableResult)
    } catch (e: RuntimeException) {
        Log.e("POC", "Effect creation failed: ${e.message}")
        EffectResult.Failed(e.message ?: "unknown")
    }
}
```

**Important:** `hasControl = true` and `setEnabled` returning SUCCESS does NOT guarantee the effect processes audio. On OEMs that silently block session 0, these calls succeed but audio is routed around the effect chain. There is no programmatic way to distinguish — only the user's ear test can confirm.

### Pattern 3: Per-Session Broadcast Receiver (Approach C — Fallback)

**What:** Register a `BroadcastReceiver` for `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`. When a compatible media player starts playing, it sends this broadcast containing its session ID. Attach the DynamicsProcessing to that session ID.

**When to use:** When session 0 is confirmed to fail (user ear test shows no effect). Reliably works for the internal MediaPlayer source. May work for Spotify. Will NOT work for YouTube (YouTube does not send this broadcast).

```kotlin
// In AndroidManifest.xml — declare receiver (required for Android 8+ for implicit broadcasts 
// from OTHER apps; for our own MediaPlayer we can register programmatically):
// Note: ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION IS exempt from the Android 8 implicit
// broadcast restriction — it is in the explicit whitelist. So manifest declaration works.
<receiver android:name=".poc.SessionBroadcastReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION"/>
    </intent-filter>
</receiver>

// In SessionBroadcastReceiver.kt:
class SessionBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) {
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR)
            val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            Log.d("POC", "Session opened: id=$sessionId pkg=$packageName")
            // Now attach DynamicsProcessing to sessionId
            attachEffectToSession(context, sessionId)
        }
    }
}

// In InternalAudioSource.kt — send the broadcast BEFORE starting playback:
fun openAudioEffectSession(context: Context, sessionId: Int) {
    val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    context.sendBroadcast(intent)
}

// Close when done:
fun closeAudioEffectSession(context: Context, sessionId: Int) {
    val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
    }
    context.sendBroadcast(intent)
}
```

### Pattern 4: AudioManager Session Discovery (Approach C enhancement)

**What:** `AudioManager.getActivePlaybackConfigurations()` (API 26+, no special permission) returns all currently playing sessions. Use this to find session IDs from external apps without relying on their broadcast.

```kotlin
// No special permission required — returns public playback info
val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val configs = audioManager.activePlaybackConfigurations
for (config in configs) {
    val sessionId = config.audioSessionId  // method exists on AudioPlaybackConfiguration
    val attrs = config.audioAttributes
    Log.d("POC", "Active session: id=$sessionId usage=${attrs.usage}")
    // Attempt to attach effect to each active session
}
```

**Limitation:** `AudioPlaybackConfiguration.getAudioSessionId()` is available, but attaching a `DynamicsProcessing` to another app's session ID is not guaranteed to work — the AudioFlinger may reject cross-app effect insertion. This is worth trying in the POC for external apps, but should be logged as LOW confidence.

### Pattern 5: Internal Audio Source (MediaPlayer)

**Use case:** Reproducible L/R test. The internal source MUST work for the POC to pass.

```kotlin
class InternalAudioSource(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun prepare(rawResId: Int): Int {
        val mp = MediaPlayer.create(context, rawResId)
        mp.isLooping = true
        mediaPlayer = mp
        return mp.audioSessionId
    }

    fun play() = mediaPlayer?.start()
    fun pause() = mediaPlayer?.pause()
    fun release() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    val sessionId: Int get() = mediaPlayer?.audioSessionId ?: AudioManager.ERROR
}
```

**Audio file recommendation:** Use a short stereo MP3 or OGG in `res/raw/` with distinct L/R content — either a music clip with obvious stereo separation, or a "dual-mono" test tone where 440 Hz is on L only and 880 Hz is on R only. Either makes it immediately obvious to the ear if balance is shifted. A 15-30 second looping clip is sufficient.

### Anti-Patterns to Avoid

- **Using Equalizer for L/R balance:** Equalizer bands are NOT per-channel. Setting band gain on an Equalizer affects both L and R equally. Cannot produce stereo balance shift.
- **Using BassBoost or Virtualizer for balance:** These are mono effects or spatializers, not per-channel gain controls.
- **Creating AudioEffect without releasing it:** Always call `release()` in `onDestroy()` or the effect engine leaks and future attempts fail.
- **Trusting `setEnabled()` return code alone:** Return code 0 = SUCCESS means the call was accepted, NOT that audio is being processed.
- **Using ToneGenerator for stereo balance test:** ToneGenerator outputs to phone earpiece, does not integrate with AudioEffect session, and is mono. Use MediaPlayer with a stereo audio file instead.
- **Calling `setInputGainbyChannel` before `setEnabled(true)`:** Enable the effect first, then set parameters.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-channel gain | Custom DSP math on PCM buffers | `DynamicsProcessing.setInputGainbyChannel()` | Android framework handles the audio thread safely; custom PCM manipulation requires AudioTrack in write-mode which blocks the main thread |
| Session ID discovery from other apps | Polling process list / parsing /proc | `AudioManager.getActivePlaybackConfigurations()` | Official API, API 26+, no permission needed; handles all sessions system-wide |
| Effect lifecycle management | Manual flag tracking | `AudioEffect.hasControl` + try/catch on constructor | AudioFlinger owns the lifecycle; trust its state |
| Stereo test tone generation | Write PCM byte arrays manually | Embed a stereo MP3/OGG in `res/raw/` | Much simpler; avoids AudioTrack threading issues in a POC |

---

## Common Pitfalls

### Pitfall 1: Session 0 Silent Acceptance

**What goes wrong:** `DynamicsProcessing(0, 0, config)` constructs successfully, `setEnabled(true)` returns 0 (SUCCESS), `hasControl` is true — but no audio effect is applied. Balance slider has no audible effect.

**Why it happens:** The AudioFlinger accepts the effect object but the OEM's audio HAL (Hardware Abstraction Layer) ignores or bypasses effects on the global output mix. Common on Samsung One UI, Xiaomi MIUI, and devices that use the Qualcomm audio HAL with OEM modifications.

**How to avoid:** Cannot be avoided programmatically. Must be tested on the physical device. Log all three indicators (constructor success, `setEnabled` result, `hasControl`) so the POC output shows exactly what happened.

**Warning signs:** No exception is thrown; all API calls return SUCCESS; user reports no audible change.

### Pitfall 2: Exception Swallowing at Construction

**What goes wrong:** Constructor throws `RuntimeException` with message "AudioEffect: error -3" or `UnsupportedOperationException` with "invalid parameter operation". If not caught, app crashes.

**Why it happens:** AudioFlinger rejects the effect request. Error -3 corresponds to `INVALID_OPERATION` in the native layer — often means the audio session does not exist, or the effect type is not available in the device's `audio_effects.xml` config.

**How to avoid:** Always wrap AudioEffect constructors in try/catch:
```kotlin
try {
    val dp = DynamicsProcessing(0, sessionId, config)
    // proceed
} catch (e: RuntimeException) {
    Log.e("POC", "DynamicsProcessing construction failed: ${e.message}")
    // try next approach
} catch (e: UnsupportedOperationException) {
    Log.e("POC", "DynamicsProcessing unsupported: ${e.message}")
}
```

**Warning signs:** LogCat shows "AudioEffect initCheck failed" or "AudioFlinger could not create effect".

### Pitfall 3: Effect Released Before Playback Ends

**What goes wrong:** Effect is `release()`d while MediaPlayer is still playing. Audio continues but effect is removed. Or worse, the MediaPlayer becomes unstable if the session is destroyed.

**How to avoid:** Always call `dp.setEnabled(false)` and `dp.release()` AFTER stopping the MediaPlayer, in `onDestroy()`. Use an ordered teardown:
```kotlin
override fun onDestroy() {
    audioEffectPoc.releaseAll()  // disables + releases effects
    internalSource.release()     // stops + releases MediaPlayer
    super.onDestroy()
}
```

### Pitfall 4: BroadcastReceiver Not Receiving External App Sessions

**What goes wrong:** Spotify or YouTube plays but `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` never fires.

**Why it happens:** Many apps do not send this broadcast. YouTube Music historically doesn't. YouTube (video app) doesn't. Spotify does send it in some versions but may not in others.

**How to avoid:** For the external-app test, supplement the broadcast approach with `AudioManager.getActivePlaybackConfigurations()` polling. If the broadcast never arrives, query active configurations and attempt to attach the effect to each music-type session.

**Warning signs:** No log entries from `SessionBroadcastReceiver.onReceive` while Spotify/YouTube is playing.

### Pitfall 5: DynamicsProcessing Requires API 28 — Device May Be Android 8

**What goes wrong:** `DynamicsProcessing` is not available on Android 8.0 (API 26) or Android 8.1 (API 27). App crashes with `ClassNotFoundException` or `NoSuchMethodError`.

**How to avoid:** Check API level at runtime before constructing `DynamicsProcessing`:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    // API 28+ — use DynamicsProcessing
} else {
    // API 26-27 — fall back to Equalizer (cannot do true L/R balance)
    // Document as "hardware limitation — Phase 2 cannot support this device for balance"
}
```

The physical device 56191FDCR002NG is likely not Android 8 (verify with `getprop ro.build.version.release`), but the POC code should handle this gracefully for completeness.

### Pitfall 6: LoudnessEnhancer Cannot Provide L/R Balance

**What goes wrong:** LoudnessEnhancer is listed as a fallback. Developer assumes it can shift balance by applying different gain to each channel.

**Why it happens:** `LoudnessEnhancer` is a mono gain amplifier. It has a single `setTargetGain(int gainmB)` method that applies the same gain to all channels. It cannot produce stereo balance.

**How to avoid:** Do NOT use LoudnessEnhancer for balance. Its value in this POC is solely as a "does ANY effect work on session 0?" probe — if `LoudnessEnhancer(0, 0)` succeeds when `DynamicsProcessing(0, 0, ...)` fails, it means the session 0 mechanism is partially available and the device has a DynamicsProcessing-specific restriction. Log this finding.

---

## Code Examples

### Minimal DynamicsProcessing for Balance

```kotlin
// Source: AOSP frameworks/base/media/java/android/media/audiofx/DynamicsProcessing.java
// + Microsoft Learn DynamicsProcessing API reference (net-android-35.0)

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log

@RequiresApi(Build.VERSION_CODES.P) // API 28
fun createBalanceEffect(sessionId: Int): DynamicsProcessing? {
    return try {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,           // channelCount = 2 (stereo)
            false, 0,    // preEq disabled
            false, 0,    // mbc disabled
            false, 0,    // postEq disabled
            false        // limiter disabled
        ).build()
        
        DynamicsProcessing(0, sessionId, config).also { dp ->
            dp.setEnabled(true)
            Log.d("POC", "DynamicsProcessing created: session=$sessionId hasControl=${dp.hasControl}")
        }
    } catch (e: RuntimeException) {
        Log.e("POC", "DynamicsProcessing failed for session $sessionId: ${e.message}")
        null
    }
}

// Apply balance: balanceFraction in [-1.0, +1.0]
// -1.0 = full left, 0.0 = center, +1.0 = full right
@RequiresApi(Build.VERSION_CODES.P)
fun applyBalance(dp: DynamicsProcessing, balanceFraction: Float) {
    val leftGainDb = if (balanceFraction > 0) {
        -60f * balanceFraction  // attenuate left when pushing right
    } else 0f
    val rightGainDb = if (balanceFraction < 0) {
        -60f * (-balanceFraction)  // attenuate right when pushing left
    } else 0f
    dp.setInputGainbyChannel(0, leftGainDb)
    dp.setInputGainbyChannel(1, rightGainDb)
    Log.d("POC", "Balance set: L=${leftGainDb}dB R=${rightGainDb}dB")
}
```

### Approach Probe Sequence (full fallback chain)

```kotlin
// Source: pattern from esper.io analysis + AOSP AudioEffect docs
// Try each approach in order, log result, stop at first success confirmed by user

sealed class ApproachResult {
    data class Success(val approach: String, val dp: Any) : ApproachResult()
    data class ApiAccepted(val approach: String, val dp: Any) : ApproachResult() // no exception but ear test needed
    data class Failed(val approach: String, val reason: String) : ApproachResult()
}

fun probeApproaches(mediaSessionId: Int): List<ApproachResult> {
    val results = mutableListOf<ApproachResult>()
    
    // Approach A: DynamicsProcessing session 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val dp = createBalanceEffect(0)
        results += if (dp != null) ApproachResult.ApiAccepted("DynamicsProcessing session 0", dp)
                   else ApproachResult.Failed("DynamicsProcessing session 0", "constructor threw")
    }
    
    // Approach B: DynamicsProcessing on our own MediaPlayer session
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val dp = createBalanceEffect(mediaSessionId)
        results += if (dp != null) ApproachResult.ApiAccepted("DynamicsProcessing session $mediaSessionId", dp)
                   else ApproachResult.Failed("DynamicsProcessing session $mediaSessionId", "constructor threw")
    }
    
    // Approach C probe: does LoudnessEnhancer work on session 0? (indicates partial session 0 support)
    try {
        val le = LoudnessEnhancer(0)
        le.setEnabled(true)
        results += ApproachResult.ApiAccepted("LoudnessEnhancer session 0 (PROBE ONLY — cannot do balance)", le)
    } catch (e: RuntimeException) {
        results += ApproachResult.Failed("LoudnessEnhancer session 0", e.message ?: "unknown")
    }
    
    return results
}
```

### AudioManager Session Discovery

```kotlin
// Source: Android API reference AudioManager.getActivePlaybackConfigurations() API 26+
// No special permission needed

fun discoverActiveSessions(context: Context): List<Int> {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return audioManager.activePlaybackConfigurations
        .also { configs ->
            configs.forEach { c ->
                Log.d("POC", "Active session: id=${c.audioSessionId} usage=${c.audioAttributes?.usage}")
            }
        }
        .map { it.audioSessionId }
        .filter { it != AudioManager.ERROR }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Equalizer session 0 for global EQ | DynamicsProcessing per-session | API 28 (Android 9, 2018) | DynamicsProcessing is the only way to do true per-channel gain; Equalizer remains relevant only as a probe |
| Rely on broadcast for all session detection | `getActivePlaybackConfigurations()` + broadcast | API 26 (Android 8, 2017) | Can discover sessions without broadcast from the app; no DUMP permission needed |
| DUMP permission for full session discovery (Wavelet approach) | Only needed for advanced cases | Android 10+ | For our POC scope (internal + Spotify), broadcast + `getActivePlaybackConfigurations()` is sufficient without ADB-granted permissions |
| Session 0 global effects | Per-session effects | ~2012 (Gingerbread era) | Session 0 is deprecated but still works on some devices; must test empirically |

**Deprecated/outdated:**
- **Equalizer for L/R balance:** Never worked — Equalizer has no per-channel concept. Still useful as a session 0 probe (did it throw or succeed?).
- **Session 0 as primary strategy for new apps:** Deprecated since 2012; still viable as an attempt but must have a fallback.

---

## Fallback Priority Order (PRESCRIPTIVE)

Try in this exact order. Stop at the first approach confirmed by user ear test.

### Approach A: DynamicsProcessing on session 0 (try first)
- **Class:** `android.media.audiofx.DynamicsProcessing`
- **Constructor:** `DynamicsProcessing(0, 0, config)` — priority=0, session=0 (global)
- **Requires:** `MODIFY_AUDIO_SETTINGS` permission in manifest (normal permission, auto-granted at install)
- **API level:** 28+ (must runtime-check; if device is API 26-27, skip to Approach B)
- **Works on external apps?** Yes — if accepted at the native layer, applies to ALL audio on the device
- **Silent failure risk:** HIGH on Samsung One UI and Xiaomi MIUI — constructor succeeds, effect is ignored
- **Detection:** Try/catch on constructor + log `hasControl` + `setEnabled` result + user ear confirmation

### Approach B: DynamicsProcessing on internal MediaPlayer session (always works for internal source)
- **Class:** `android.media.audiofx.DynamicsProcessing`
- **Constructor:** `DynamicsProcessing(0, mediaPlayer.audioSessionId, config)`
- **Requires:** `MODIFY_AUDIO_SETTINGS` (same as A)
- **API level:** 28+
- **Works on external apps?** No — only applies to the app's own MediaPlayer
- **Silent failure risk:** LOW — per-session effects are not deprecated and rarely blocked
- **Note:** This approach ALWAYS satisfies the internal audio source requirement; failure here means a deeper platform problem

### Approach C: Per-session broadcast + DynamicsProcessing (fallback for external apps)
- **Mechanism:** `BroadcastReceiver` for `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`
- **Constructor (when session ID received):** `DynamicsProcessing(0, sessionId, config)`
- **Requires:** `MODIFY_AUDIO_SETTINGS` + receiver in manifest
- **Broadcast whitelist:** `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` is on Android 8's implicit broadcast whitelist — manifest declaration works without Context registration
- **Works on:** Spotify (sends broadcast), VLC (sends broadcast), most dedicated music apps
- **Does NOT work on:** YouTube (does not send broadcast), YouTube Music (does not send broadcast)
- **Supplement with:** `AudioManager.getActivePlaybackConfigurations()` to discover sessions not announced via broadcast

### Approach D: LoudnessEnhancer on session 0 (diagnostic probe, NOT a balance solution)
- **Class:** `android.media.audiofx.LoudnessEnhancer`
- **Constructor:** `LoudnessEnhancer(0)` — session 0 only
- **Purpose:** Determine if session 0 accepts ANY effect when DynamicsProcessing fails. This is a diagnostic, not a solution — LoudnessEnhancer has no per-channel gain control.
- **If this succeeds when Approach A fails:** The device supports session 0 effects but DynamicsProcessing specifically is rejected. This is an edge case — document and move on to Approach C.

### What to do if ALL approaches fail

If Approaches A, B, C, D all fail to produce an audible balance shift (including Approach B which should always work for the internal source):
1. Confirm the MediaPlayer is playing (obvious audio output from device)
2. Confirm the effect is enabled and `hasControl` is true
3. Run `adb shell dumpsys media.audio_flinger` and capture output
4. Check if `DynamicsProcessing` appears in device's `audio_effects.xml`
5. Flag FEAS-01 as BLOCKED and document findings for the project record

---

## Open Questions

1. **Device identity for 56191FDCR002NG**
   - What we know: This is the ADB serial number, not a model number
   - What's unclear: OEM (Samsung? Xiaomi? Generic Android One?), Android version, whether OEM modifies audio HAL
   - Recommendation: First task in the POC plan must identify the device with `adb shell getprop ro.product.model` and `getprop ro.build.version.release`

2. **`AudioPlaybackConfiguration.getAudioSessionId()` availability**
   - What we know: Method appears on the class per Microsoft Learn docs; class is API 26+
   - What's unclear: Whether the method was available from API 26 or added later; the official Android docs did not load clearly
   - Recommendation: Add `@RequiresApi(Build.VERSION_CODES.O)` guard when calling this; the device is API 26+ so it should be fine, but verify in practice

3. **Spotify broadcast behavior (2025)**
   - What we know: Historically Spotify sends `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`; Wavelet docs confirm many players do
   - What's unclear: Whether current Spotify version (2025) still sends the broadcast consistently
   - Recommendation: Test empirically during the POC external-app session; log all received broadcasts

4. **DynamicsProcessing session 0 on this specific device OEM**
   - What we know: Session 0 is deprecated but some devices accept it; OEM-dependent
   - What's unclear: Will this device's AudioFlinger accept DynamicsProcessing on session 0?
   - Recommendation: This is the core question the POC answers — no way to know without running the test

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | None — this phase uses human-in-the-loop validation (ear test) + logcat inspection |
| Config file | n/a |
| Quick run command | `adb -s 56191FDCR002NG logcat -s POC:D` (observe logs while running POC app) |
| Full suite command | Manual test checklist (see below) |

This phase has no automated test suite. The validation is physical/perceptual: the user listens with their BT headphones and confirms an audible balance shift. The "tests" are structured human checkpoints logged to the FINDINGS.md deliverable.

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | How to Verify | Automated? |
|--------|----------|-----------|---------------|-----------|
| FEAS-01 | DynamicsProcessing (or fallback) produces audible L/R balance shift on physical device | Perceptual / manual | User confirms: push to L = audio predominantly in left ear; push to R = audio predominantly in right ear | No — manual |
| FEAS-02 | If session 0 fails, a fallback approach is identified and validated | Perceptual + logcat | Logcat shows which approach succeeded (no constructor exception, `hasControl=true`); user ear confirms | Partial — logcat is automated, ear confirmation is manual |

### Human-Verify Checkpoint Structure

The POC Activity must present these verification steps to the user in sequence:

**Step 1 — Internal source, Approach A (session 0)**
- Press "Play Internal Audio" (confirms MediaPlayer playing)
- Press "Balance LEFT (session 0)" — push to full left
- Question: "Do you hear audio predominantly in the LEFT ear?" (YES / NO / UNSURE)
- Press "Reset Balance"
- Press "Balance RIGHT (session 0)"
- Question: "Do you hear audio predominantly in the RIGHT ear?" (YES / NO / UNSURE)
- Log: approach name, constructor success/fail, `hasControl`, user YES/NO/UNSURE

**Step 2 — Internal source, Approach B (per-session)**
- (Same flow but using `mediaPlayer.audioSessionId` instead of 0)

**Step 3 — External source (Spotify/YouTube)**
- Instruction: "Start Spotify or YouTube on this device now"
- Press "Discover Active Sessions" — shows session IDs found
- Press "Apply Balance LEFT to all discovered sessions"
- Question: "Do you hear audio predominantly in the LEFT ear?" (YES / NO / UNSURE)

**Each step must log:**
```
[APPROACH_A|B|C] constructor=OK|EXCEPTION(msg) enabled=0|-1 hasControl=true|false userResult=YES|NO|UNSURE
```

### What FINDINGS.md Must Contain

The POC deliverable document must include all of the following fields (checklist for validation):

- [ ] Device model (`ro.product.model`) and Android version (`ro.build.version.release`)
- [ ] ADB serial: `56191FDCR002NG`
- [ ] For each approach tried: constructor result (OK / exception type + message), `setEnabled` return code, `hasControl` value
- [ ] For each approach: user ear test result (YES / NO / UNSURE) for LEFT push and RIGHT push
- [ ] The single recommended constructor call for Phase 2 (exact class + parameters + session ID strategy)
- [ ] How to apply and remove the effect cleanly (lifecycle sequence)
- [ ] Any OEM-specific gotchas observed
- [ ] Recommended error-handling pattern for Phase 2

### Sampling Rate

- **Per UI interaction:** LogCat entries via `Log.d("POC", ...)` — visible in real time with `adb logcat -s POC:D`
- **Per approach completion:** User records result in app UI (YES/NO/UNSURE button) — written to in-memory state shown on screen
- **Phase gate:** FINDINGS.md must be created and must contain all required fields before Phase 1 is closed and Phase 2 begins

### Wave 0 Gaps

- [ ] No test infrastructure to set up — this phase has no automated tests
- [ ] `app/src/main/res/raw/test_tone.mp3` — stereo audio file must be added (any short stereo music clip or L/R distinct tone)
- [ ] `MODIFY_AUDIO_SETTINGS` permission — must be added to `AndroidManifest.xml` before any AudioEffect construction

---

## Sources

### Primary (HIGH confidence)
- AOSP `frameworks/base/media/java/android/media/audiofx/DynamicsProcessing.java` — constructor signatures, `setInputGainbyChannel` method, `Config.Builder` parameters
- Microsoft Learn .NET Android API reference (DynamicsProcessing, API net-android-35.0, updated 2024-05-01) — full method and constructor list, inner class structure
- Microsoft Learn .NET Android API reference (AudioPlaybackConfiguration, API net-android-35.0, updated 2025-04-23) — `getAudioSessionId()` confirmed as existing method; class is API 26+
- Microsoft Learn .NET Android API reference (AudioManager.ActivePlaybackConfigurations, API net-android-35.0) — `getActivePlaybackConfigurations()` confirmed at API 26+, no special permission listed

### Secondary (MEDIUM confidence)
- esper.io "Why Many Android Equalizer Apps Don't Work with All Media Players" — Session 0 deprecation history, OEM `/vendor/etc/audio_effects.xml` mechanism, per-session broadcast mechanics, Wavelet's DUMP permission approach
- Wavelet Settings docs (pittvandewitt.github.io/Wavelet/Settings/) — Legacy mode = session 0; Enhanced session detection = `AudioPlaybackConfiguration` + DUMP permission; broadcast approach = default mode
- Wavelet GitHub Issue #312 — confirmed session 0 fails on MIUI 14 (Android 13); effects work on AudioTrack-based players but not "fast mixer thread" players

### Tertiary (LOW confidence — verify on device)
- Google Issue Tracker #36936557 — session 0 deprecation acknowledged by Google; no fix promised
- Medium: "UnsupportedOperationException AudioEffect: invalid parameter operation" — occurs on LG devices; device-specific
- GitHub androidx/media Issue #1261 — error code -3 from AudioFlinger; cross-app session rejection pattern

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — DynamicsProcessing API is documented in AOSP source and Microsoft Learn; constructors verified
- Architecture patterns: HIGH for API usage; MEDIUM for session 0 behavior (device-dependent)
- Pitfalls: MEDIUM — based on multiple community reports and Wavelet issue tracker; not formally documented by Google
- Fallback order: MEDIUM — based on Wavelet's real-world implementation patterns

**Research date:** 2026-04-04
**Valid until:** 2026-07-04 (stable APIs; session 0 behavior on specific device is unknown — POC answers this definitively)
