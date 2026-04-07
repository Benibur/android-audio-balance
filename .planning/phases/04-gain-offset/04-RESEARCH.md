# Phase 4: Gain Offset — Research

**Researched:** 2026-04-07
**Domain:** Android DynamicsProcessing gain composition, Jetpack DataStore, Compose Slider
**Confidence:** HIGH — based entirely on direct codebase analysis and previously validated POC results

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Slider range & scale**
- Range: -12 dB to 0 dB (attenuation only, no boost)
- Step size: 1 dB (12 discrete positions)
- No snap behavior needed — 0 dB is already a discrete step
- Label format: "Min volume adjustment: -3 dB" (shows "0 dB" when no attenuation)

**Slider placement & UX**
- Below the balance slider in DeviceCard — both visible at once, card gets taller
- Same visual style as balance slider (consistent look), differentiated by label only
- Label: "Min volume adjustment" (not "Volume" or "Gain")

**Gain composition**
- Balance and gain offset MUST be composed into a single `setInputGainbyChannel` call per channel
- Formula: `channelGainDb = balanceChannelDb + gainOffsetDb` (gain offset applied equally to both channels)
- All 4 existing call sites (`seed_balance`, `reset_audio_only`, `applyDeviceBalance`, `resetBalanceToCenter`) must use a single `applyGains(balance, gainOffset)` helper
- No separate DP calls — the API overwrites, not accumulates

**Data model**
- DataStore key: `gain_offset_${mac}` (same pattern as `balance_${mac}`)
- Replace `Triple<String, Float, Boolean>` with `DeviceEntry` data class carrying: mac, balance, autoApply, gainOffset
- New intent action `seed_gain_offset` following the `seed_balance` pattern
- autoApply toggle gates auto-apply of gain (same as balance), but manual slider actions always apply

### Claude's Discretion

**Notification format**
- Show gain offset in notification when non-zero — choose the clearest format

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| GAIN-01 | User can adjust a per-device gain offset slider (dB) in the device card | DeviceCard.kt slider pattern confirmed reusable; `Slider` API maps -12..0 dB to 0..1f normalized |
| GAIN-02 | Gain offset is persisted per MAC address and restored on reconnect | BalanceRepository DataStore key pattern confirmed; `gain_offset_${mac.replace(":", "_")}` follows exact existing convention |
| GAIN-03 | Gain offset and balance are composed into a single DynamicsProcessing call per channel | `setInputGainbyChannel` is an absolute setter — composition formula validated; all 4 call sites identified in AudioBalanceService.kt |
| GAIN-04 | Gain offset auto-applies on BT connect (same behavior as balance) | `applyDeviceBalance()` confirmed entry point; must read gainOffset from repo and pass to shared `applyGains()` helper |
| GAIN-05 | Notification displays gain offset when non-zero | `formatNotificationText` at lines 360-368 confirmed; signature must accept gainOffset parameter |
</phase_requirements>

---

## Summary

Phase 4 is a focused extension of the existing v1.0 architecture. The core technical challenge — composing balance and gain offset into a single `setInputGainbyChannel` call — is already solved in the architecture research and has a validated formula. The codebase is well-structured for this addition: the repository, service, ViewModel, and UI each have a single well-defined place to add the gain offset dimension.

The implementation is a clean four-layer pass: (1) data model — add `DeviceEntry` data class and `gain_offset` DataStore keys to `BalanceRepository`; (2) service — extract `applyGains(balance, gainOffset)` helper and update all four DP call sites; (3) ViewModel — add `_gainOffsetOverrides` flow and new callbacks mirroring the balance pattern exactly; (4) UI — add a second `Slider` below the balance slider in `DeviceCard`. No new architectural patterns are needed; every piece mirrors an existing pattern.

The single highest-risk item is the `setInputGainbyChannel` composition correctness: calling it twice for the same channel (once for balance, once for gain) produces wrong audio because the second call silently overwrites the first. This is a silent failure with no runtime error. The `applyGains()` helper must be the exclusive owner of all DP channel writes.

**Primary recommendation:** Build data layer first (DeviceEntry + repository), then service (applyGains helper), then ViewModel, then UI. Never write two `setInputGainbyChannel` calls for the same channel.

---

## Standard Stack

### Core (all already in the project — no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `android.media.audiofx.DynamicsProcessing` | API 28+ (P) | Per-channel dB gain control | Only API that provides independent L/R channel input gain on session 0 global |
| `androidx.datastore:datastore-preferences` | already in project | Persist gainOffset per MAC | Same store as balance/autoApply; single read path per connect |
| Jetpack Compose `Slider` | already in project | Gain offset slider UI | Same component as balance slider; already themed and accessible |
| Kotlin `StateFlow` / `MutableStateFlow` | already in project | Real-time slider override and service state | Existing ViewModel pattern; gain offset adds one more override map |

**No new dependencies required for Phase 4.** All needed libraries are already declared.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Composing gains in service `applyGains()` | Compose in BalanceMapper | BalanceMapper.toGainDb() only knows balance — it has no gain offset parameter. The service is the right place: it holds both values at apply time |
| `DeviceEntry` data class | Quadruple (extending Triple) | Kotlin has no Quadruple; adding a 4th positional field to Triple breaks all destructuring in the ViewModel. Data class wins |

---

## Architecture Patterns

### Recommended Project Structure (changes only)

```
com.audiobalance.app/
├── data/
│   └── BalanceRepository.kt       MODIFIED — add DeviceEntry, gainOffsetKey, getGainOffset, saveGainOffset, update getAllDevicesFlow()
├── service/
│   └── AudioBalanceService.kt     MODIFIED — add applyGains() helper, seed_gain_offset handler, update all 4 DP call sites
├── ui/
│   ├── state/
│   │   └── DeviceUiState.kt       MODIFIED — add gainOffset: Float = 0f to DeviceUiState and ServiceState
│   ├── viewmodel/
│   │   └── DeviceListViewModel.kt MODIFIED — add _gainOffsetOverrides, onGainOffsetChange, onGainOffsetFinished, sendGainOffsetToService
│   └── components/
│       └── DeviceCard.kt          MODIFIED — add gain slider + label, add 2 callback params
```

### Pattern 1: DeviceEntry replaces Triple

**What:** A data class replaces the `Triple<String, Float, Boolean>` returned by `getAllDevicesFlow()`.

**When to use:** Any time a 4th field needs to be added to per-device data.

```kotlin
// Source: .planning/research/ARCHITECTURE.md
data class DeviceEntry(
    val mac: String,
    val balance: Float,       // -100f to +100f
    val autoApply: Boolean,
    val gainOffset: Float     // dB, default 0f
)
```

The ViewModel's `combine` lambda currently destructures `(mac, balance, autoApply)` from Triple — this becomes `deviceEntry.mac`, `deviceEntry.balance`, etc. All call sites are in `DeviceListViewModel.kt` lines 30-39.

### Pattern 2: applyGains() — single DP call owner

**What:** A private suspend function in `AudioBalanceService` that is the only place `setInputGainbyChannel` is called. All four existing call sites are replaced.

**When to use:** Any time audio gains change (slider move, reconnect, reset, seed_gain_offset intent).

```kotlin
// Source: .planning/research/ARCHITECTURE.md + PITFALLS.md
private fun applyGains(balance: Float, gainOffsetDb: Float) {
    val (balanceLeft, balanceRight) = BalanceMapper.toGainDb(balance.roundToInt())
    val leftFinal  = balanceLeft  + gainOffsetDb
    val rightFinal = balanceRight + gainOffsetDb
    try {
        dp?.setInputGainbyChannel(0, leftFinal)
        dp?.setInputGainbyChannel(1, rightFinal)
    } catch (e: RuntimeException) {
        Log.e(TAG, "setInputGainbyChannel failed: ${e.message}")
    }
}
```

The four existing call sites to replace:
1. `seed_balance` handler (lines 108-109): becomes `applyGains(balance, storedGainOffset)`
2. `reset_audio_only` handler (lines 123-124): becomes `applyGains(0f, 0f)` (reset both)
3. `applyDeviceBalance()` (lines 264-266): becomes `applyGains(balance, gainOffset)` after loading both from repo
4. `resetBalanceToCenter()` (lines 278-279): becomes `applyGains(0f, 0f)`

### Pattern 3: Gain offset slider in DeviceCard

**What:** A second Slider below the balance slider, using the same Compose Slider API but simpler (no snap, integer steps via valueRange and steps).

**When to use:** Second slider below existing balance slider in same Card Column.

```kotlin
// Source: direct codebase analysis, DeviceCard.kt line 152 pattern
// Range: -12f..0f dB, mapped to 0f..1f for Slider API
val gainSliderValue = (device.gainOffset - (-12f)) / (0f - (-12f))  // = (gainOffset + 12f) / 12f

Slider(
    value = gainSliderValue.coerceIn(0f, 1f),
    onValueChange = { normalized ->
        val gainDb = normalized * 12f + (-12f)   // map back to -12..0
        onGainOffsetChange(gainDb)
    },
    onValueChangeFinished = {
        onGainOffsetFinished(device.gainOffset)
    },
    enabled = device.autoApplyEnabled,
    steps = 11,              // 12 positions = 11 steps between them
    valueRange = 0f..1f,
    modifier = Modifier.weight(1f)
)
```

Label text: `"Min volume adjustment: ${gainOffset.roundToInt()} dB"` (shows "0 dB" when no attenuation).

### Pattern 4: seed_gain_offset intent (mirrors seed_balance exactly)

**What:** New intent action sent by ViewModel to service when gain offset slider changes.

```kotlin
// Source: .planning/research/ARCHITECTURE.md — mirrors existing seed_balance at ViewModel line 100
// ViewModel sends:
fun sendGainOffsetToService(gainOffsetDb: Float) {
    val context = getApplication<Application>()
    val intent = Intent(context, AudioBalanceService::class.java).apply {
        putExtra("action", "seed_gain_offset")
        putExtra("gain_offset", gainOffsetDb)
    }
    try { context.startForegroundService(intent) } catch (e: Exception) { /* log */ }
}

// Service handles in onStartCommand:
"seed_gain_offset" -> {
    val gainOffsetDb = intent.getFloatExtra("gain_offset", 0f)
    val mac = currentDeviceMac ?: return START_STICKY
    serviceScope.launch {
        balanceRepository.saveGainOffset(mac, gainOffsetDb)
        val balance = balanceRepository.getBalance(mac)
        applyGains(balance, gainOffsetDb)
        _stateFlow.value = _stateFlow.value.copy(currentGainOffset = gainOffsetDb)
        updateNotification(formatNotificationText(currentDeviceName, balance.roundToInt(), gainOffsetDb))
    }
}
```

### Pattern 5: ViewModel gain offset overrides (mirrors balance overrides exactly)

**What:** A second `MutableStateFlow<Map<String, Float>>` for immediate UI feedback before DataStore save.

```kotlin
// Source: .planning/research/ARCHITECTURE.md — mirrors _sliderOverrides at ViewModel line 23
private val _gainOffsetOverrides = MutableStateFlow<Map<String, Float>>(emptyMap())

// In combine lambda — add to existing 3-flow combine (must become 4-flow or compute inside):
val displayGainOffset = _gainOffsetOverrides.value[mac] ?: deviceEntry.gainOffset

fun onGainOffsetChange(mac: String, dB: Float) {
    _gainOffsetOverrides.value = _gainOffsetOverrides.value + (mac to dB)
    if (isConnectedDevice(mac)) {
        val now = System.currentTimeMillis()
        if (now - lastSendTimestamp >= 50) {
            lastSendTimestamp = now
            sendGainOffsetToService(dB)
        }
    }
}

fun onGainOffsetFinished(mac: String, rawDb: Float) {
    _gainOffsetOverrides.value = _gainOffsetOverrides.value - mac
    viewModelScope.launch {
        repository.saveGainOffset(mac, rawDb)
        if (isConnectedDevice(mac)) sendGainOffsetToService(rawDb)
    }
}
```

Note: combining 4 flows requires switching from `combine(f1, f2, f3)` to `combine(f1, f2, f3, f4)` — Kotlin Flow supports up to 5 flows natively. Alternatively, include `_gainOffsetOverrides` in the existing 3-flow combine as a 4th argument.

### Pattern 6: Notification with gain offset

**What:** Extend `formatNotificationText` to include gain offset when non-zero.

```kotlin
// Source: AudioBalanceService.kt lines 360-368 — current signature
// Current: formatNotificationText(deviceName: String?, balance: Int): String
// New:
fun formatNotificationText(deviceName: String?, balance: Int, gainOffsetDb: Float = 0f): String {
    val name = deviceName?.takeIf { it.isNotBlank() } ?: "BT Device"
    val balanceText = when {
        balance > 0  -> "R+${balance}%"
        balance < 0  -> "L+${-balance}%"
        else         -> "Center"
    }
    val gainText = if (gainOffsetDb != 0f) " • Vol: ${gainOffsetDb.roundToInt()} dB" else ""
    return "$name • Balance: $balanceText$gainText"
}
```

Default parameter `gainOffsetDb = 0f` keeps all existing call sites (that don't pass gain) unchanged — backward compatible.

### Anti-Patterns to Avoid

- **Two separate DP calls for balance + gain:** `setInputGainbyChannel(0, balanceLeft)` then `setInputGainbyChannel(0, gainOffset)` — the second call overwrites the first. The result: balance is silently lost. Use `applyGains()` exclusively.
- **Separate `applyGainOffset()` function that also reads balance:** Multiple functions each reading both values from repo and calling the API = split-brain. Use one shared `applyGains(balance, gainOffset)` that is the only DP writer.
- **Adding a second DataStore for gain offset:** Two async reads per connect event. Add `gain_offset_` keys to the existing `device_balance` DataStore.
- **Forgetting `steps = 11` on the Compose Slider:** Without `steps`, the slider is continuous (infinite positions). With `steps = 11`, the Compose Slider snaps to 12 discrete positions (-12, -11, ..., 0 dB). This provides the "1 dB step" behavior without custom snapping logic.
- **Using `Triple` as `Quadruple`:** Kotlin has no Quadruple type. Adding a 4th field to Triple by type aliasing is fragile. Use the `DeviceEntry` data class.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Integer dB steps on slider | Custom step-snapping logic in onValueChange | `Slider(steps = 11, valueRange = 0f..1f)` | Compose Slider `steps` parameter handles discrete positions natively; hardware-accelerated and accessible |
| Throttle slider events to service | Manual time-check in onValueChange | The 50ms `lastSendTimestamp` guard already exists in ViewModel — copy the exact same pattern for gain offset | Reinventing produces subtle timing bugs; the existing pattern is proven |
| dB ↔ slider normalization | Custom mapping DSL | One-line formula: `gainDb = normalized * 12f + (-12f)` and inverse | Trivial formula; no abstraction needed |
| Backward-compatible notification | New notification builder | Default parameter `gainOffsetDb = 0f` in `formatNotificationText` | Existing call sites (that don't know about gain) continue to work without modification |

**Key insight:** Every building block for Phase 4 already exists in the v1.0 codebase. The work is wiring, not invention.

---

## Common Pitfalls

### Pitfall 1: `setInputGainbyChannel` is a setter, not an accumulator

**What goes wrong:** A "balance apply" and a "gain offset apply" each call `setInputGainbyChannel` for the same channel. Only the last call's value survives in the DP hardware. The first is silently discarded.

**Why it happens:** The API name "set" should be a clue, but developers unfamiliar with the AudioEffect framework assume effects accumulate like mixer faders.

**How to avoid:** One function (`applyGains`) exclusively owns all `setInputGainbyChannel` calls. No other function in the service touches the DP channel values directly.

**Warning signs:** Balance appears to work in isolation but has no effect when gain offset is non-zero. Or gain offset appears correct but balance is always center regardless of slider position.

### Pitfall 2: `getAllDevicesFlow()` currently filters on `balance_` prefix

**What goes wrong:** `BalanceRepository.getAllDevicesFlow()` enumerates devices by scanning for DataStore keys whose name starts with `"balance_"`. A gain offset key that accidentally starts with `"balance_"` (e.g., a typo `"balance_gain_offset_..."`) would create a ghost device entry in the UI with a garbled MAC.

**How to avoid:** The key function must use the prefix `"gain_offset_"` — confirmed not to collide. Verify: `"gain_offset_AA_BB_CC_DD_EE_FF".startsWith("balance_")` is false.

**Warning signs:** Device list shows extra entries with malformed names after first BT connect with gain offset enabled.

### Pitfall 3: ServiceState missing currentGainOffset

**What goes wrong:** The ViewModel combines `AudioBalanceService.stateFlow` (a `ServiceState`) with the repository flow. If `ServiceState` does not include `currentGainOffset`, the gain offset slider in the UI will not reflect the applied value after BT reconnect. The slider shows 0 (default) while the audio has the stored offset applied.

**How to avoid:** Add `currentGainOffset: Float = 0f` to `ServiceState` at the same time as the service work. The service must set this field in every `_stateFlow.value = ...` update that involves gain.

**Warning signs:** Gain slider shows 0 after reconnect even though gain was previously set to -6 dB and audio sounds attenuated.

### Pitfall 4: `steps` parameter off by one

**What goes wrong:** For 12 discrete positions (-12, -11, ..., 0), the Compose `Slider` needs `steps = 11` (not 12). `steps` is the number of gaps between positions, not the number of positions. Using `steps = 12` produces 13 positions.

**How to avoid:** Count: N positions = N-1 steps. 12 positions = 11 steps.

**Warning signs:** Slider settles on non-integer dB values like -11.5 dB.

### Pitfall 5: combine() needs 4-flow overload

**What goes wrong:** The current ViewModel uses `combine(serviceFlow, devicesFlow, overridesFlow)` — a 3-argument overload. Adding `_gainOffsetOverrides` as a 4th argument requires switching to the 4-argument overload: `combine(f1, f2, f3, f4) { a, b, c, d -> ... }`.

**How to avoid:** Kotlin Flow provides `combine` overloads for 2–5 flows. Switch from the 3-argument lambda to the 4-argument lambda. The lambda signature changes from `{ serviceState, devices, overrides -> }` to `{ serviceState, devices, balanceOverrides, gainOverrides -> }`.

**Warning signs:** Compile error "None of the following candidates is applicable" when adding 4th flow to `combine`.

### Pitfall 6: reset_audio_only handler resets both balance and gain to 0

**What goes wrong:** `reset_audio_only` currently sets both channels to 0f (lines 123-124). This is the correct behavior for the "toggle off" path — it resets the audio to neutral without touching stored values. With gain offset, this behavior is unchanged: both `applyGains(0f, 0f)` or equivalent direct `setInputGainbyChannel(0, 0f)` / `setInputGainbyChannel(1, 0f)` is correct for the audio-only reset path, since the intent means "apply nothing (toggle is off)."

**How to avoid:** `reset_audio_only` should call `applyGains(0f, 0f)` — silences both balance and gain offset from DP. This is correct because the user toggled auto-apply off; no audio effect should be applied until toggle is re-enabled.

---

## Code Examples

### Verified current state: all 4 DP call sites to be replaced

```kotlin
// Source: AudioBalanceService.kt — lines 108-109 (seed_balance handler)
dp?.setInputGainbyChannel(0, leftDb)
dp?.setInputGainbyChannel(1, rightDb)

// Source: AudioBalanceService.kt — lines 123-124 (reset_audio_only handler)
it.setInputGainbyChannel(0, 0f)
it.setInputGainbyChannel(1, 0f)

// Source: AudioBalanceService.kt — lines 264-266 (applyDeviceBalance)
it.setInputGainbyChannel(0, leftDb)
it.setInputGainbyChannel(1, rightDb)

// Source: AudioBalanceService.kt — lines 278-279 (resetBalanceToCenter)
it.setInputGainbyChannel(0, 0f)
it.setInputGainbyChannel(1, 0f)
```

All 4 sites are replaced by calls to `applyGains(balance, gainOffset)`.

### Verified current state: Triple destructuring to replace in ViewModel

```kotlin
// Source: DeviceListViewModel.kt — lines 30-39 (current Triple destructuring)
val deviceList = devices.map { (mac, balance, autoApply) ->
    val isConnected = mac == serviceState.connectedDeviceMac
    val displayBalance = overrides[mac] ?: balance
    DeviceUiState(
        mac = mac,
        name = repository.getDeviceName(mac) ?: mac,
        balance = displayBalance,
        autoApplyEnabled = autoApply,
        isConnected = isConnected
    )
}
```

After: `devices` is `List<DeviceEntry>`; destructuring changes to `deviceEntry.mac`, `deviceEntry.balance`, etc. (or `(mac, balance, autoApply, gainOffset)` positional destructuring on `DeviceEntry`).

### Verified current DataStore key pattern (to follow exactly)

```kotlin
// Source: BalanceRepository.kt — lines 19-21
private fun balanceKey(mac: String) = floatPreferencesKey("balance_${mac.replace(":", "_")}")
private fun autoApplyKey(mac: String) = booleanPreferencesKey("auto_apply_${mac.replace(":", "_")}")
private fun deviceNameKey(mac: String) = stringPreferencesKey("name_${mac.replace(":", "_")}")

// New (same pattern):
private fun gainOffsetKey(mac: String) = floatPreferencesKey("gain_offset_${mac.replace(":", "_")}")
```

### Verified BalanceMapper.toGainDb signature (not modified, used inside applyGains)

```kotlin
// Source: BalanceMapper.kt — full file
fun toGainDb(balance: Int): Pair<Float, Float> {
    val fraction = balance.coerceIn(-100, 100) / 100f
    val leftDb  = if (fraction > 0) -60f * fraction else 0f
    val rightDb = if (fraction < 0) -60f * (-fraction) else 0f
    return Pair(leftDb, rightDb)
}
// Note: takes Int, not Float — caller must .roundToInt() the balance Float
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Triple<String, Float, Boolean>` per device | `DeviceEntry` data class with named fields | Phase 4 | Adds gainOffset field; eliminates positional confusion; named destructuring |
| 4 independent `setInputGainbyChannel` call sites | Single `applyGains(balance, gainOffset)` helper | Phase 4 | Guarantees composition correctness; single point of DP channel writes |
| `formatNotificationText(name, balance)` | `formatNotificationText(name, balance, gainOffset=0f)` | Phase 4 | Backward-compatible; gain shown only when non-zero |
| `ServiceState(mac, name, balance)` | `ServiceState(mac, name, balance, gainOffset=0f)` | Phase 4 | Slider sync on reconnect |

---

## Open Questions

1. **`combine` with 4 flows — lambda parameter clarity**
   - What we know: Kotlin Flow `combine(f1, f2, f3, f4)` is supported; `_gainOffsetOverrides` needs to be a 4th combined flow
   - What's unclear: The current ViewModel uses the 3-arg lambda directly inside `stateIn`; adding a 4th flow requires no architecture change but the lambda body grows
   - Recommendation: Add `_gainOffsetOverrides` as the 4th flow. Alternatively, compute `displayGainOffset` inside the existing lambda by reading `_gainOffsetOverrides.value[mac]` directly (using `.value` snapshot instead of `combine`), matching the same correctness level since overrides are transient UI state. Both approaches are valid; the `.value` snapshot is simpler and avoids changing the combine arity.

2. **`seed_gain_offset` handler needs stored balance — one extra repo read**
   - What we know: When the service receives `seed_gain_offset`, it knows the new gain offset from the intent, but must also know the current balance to call `applyGains(balance, newGain)`. It can read this from `balanceRepository.getBalance(currentDeviceMac)`.
   - What's unclear: Race condition if balance was recently changed via `seed_balance` intent and not yet persisted. In practice: `seed_balance` saves to repo before applying, so a subsequent `seed_gain_offset` intent will read the already-saved balance. Risk is low. Same holds for `seed_balance` reading gain offset.
   - Recommendation: Each handler reads the other value from repo. This is the ARCHITECTURE.md recommended pattern and is sufficient for this use case.

3. **`steps` parameter interaction with `enabled = false`**
   - What we know: The balance slider uses `enabled = device.autoApplyEnabled` to visually dim when toggle is off. The gain slider should follow the same pattern.
   - What's unclear: Nothing material — this is a confirmed pattern. Included for completeness.
   - Recommendation: Apply `enabled = device.autoApplyEnabled` to the gain slider, same as balance.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 (standard Android project; no test files exist yet) |
| Config file | None — Wave 0 must create `app/src/test/` directory structure |
| Quick run command | `./gradlew testDebugUnitTest --tests "com.audiobalance.app.*"` |
| Full suite command | `./gradlew testDebugUnitTest` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| GAIN-01 | Slider maps -12..0 dB range to 0..1f normalized correctly | unit | `./gradlew testDebugUnitTest --tests "*.GainOffsetSliderTest"` | ❌ Wave 0 |
| GAIN-02 | DataStore key `gain_offset_AA_BB_CC_DD_EE_FF` round-trips correctly | unit | `./gradlew testDebugUnitTest --tests "*.BalanceRepositoryTest"` | ❌ Wave 0 |
| GAIN-03 | `applyGains(balance=50f, gainOffset=-6f)` produces left=-6f, right=-36f | unit | `./gradlew testDebugUnitTest --tests "*.ApplyGainsTest"` | ❌ Wave 0 |
| GAIN-03 | `applyGains(balance=-50f, gainOffset=-6f)` produces left=-36f, right=-6f | unit | `./gradlew testDebugUnitTest --tests "*.ApplyGainsTest"` | ❌ Wave 0 |
| GAIN-03 | `applyGains(balance=0f, gainOffset=0f)` produces left=0f, right=0f | unit | `./gradlew testDebugUnitTest --tests "*.ApplyGainsTest"` | ❌ Wave 0 |
| GAIN-04 | Auto-apply on BT connect calls applyGains with stored values | integration / manual | BT reconnect with device having saved gainOffset=-6f → verify audio | manual-only (requires physical BT device) |
| GAIN-05 | Notification text includes gain offset when non-zero | unit | `./gradlew testDebugUnitTest --tests "*.NotificationTextTest"` | ❌ Wave 0 |
| GAIN-05 | Notification text omits gain offset when 0f | unit | `./gradlew testDebugUnitTest --tests "*.NotificationTextTest"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew testDebugUnitTest --tests "com.audiobalance.app.*" -x lint`
- **Per wave merge:** `./gradlew testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `app/src/test/java/com/audiobalance/app/ApplyGainsTest.kt` — covers GAIN-03 (gain composition math, most critical correctness test)
- [ ] `app/src/test/java/com/audiobalance/app/NotificationTextTest.kt` — covers GAIN-05 (notification format with/without gain)
- [ ] `app/src/test/java/com/audiobalance/app/GainOffsetSliderTest.kt` — covers GAIN-01 (slider normalization math)
- [ ] `app/src/test/java/com/audiobalance/app/BalanceRepositoryTest.kt` — covers GAIN-02 (DataStore key naming; may require Robolectric)
- [ ] Create directory: `app/src/test/java/com/audiobalance/app/`

Note: `ApplyGainsTest` and `NotificationTextTest` are pure unit tests (no Android dependencies). `BalanceRepositoryTest` requires Robolectric or an in-memory DataStore; if too heavy, test key naming logic in isolation (string functions only). `GainOffsetSliderTest` is pure arithmetic.

---

## Sources

### Primary (HIGH confidence)
- Direct codebase analysis: `AudioBalanceService.kt`, `BalanceRepository.kt`, `DeviceListViewModel.kt`, `BalanceMapper.kt`, `DeviceCard.kt`, `DeviceUiState.kt` — all read 2026-04-07; current v1.0 production code
- `.planning/phases/01-audioeffect-poc/POC-RESULTS.md` — validated `setInputGainbyChannel` as absolute setter (confirmed on Pixel 10 / Android 16)
- `.planning/research/ARCHITECTURE.md` — gain composition formula, `DeviceEntry` data class, all integration patterns (all from direct codebase analysis, 2026-04-07)
- `.planning/research/PITFALLS.md` — gain combining overwrite risk, DataStore key collision, `steps` parameter, ServiceState sync (all from codebase analysis + POC results)
- `.planning/phases/04-gain-offset/04-CONTEXT.md` — locked decisions (all constraints come from here)

### Secondary (MEDIUM confidence)
- Android Developers reference for `DynamicsProcessing.setInputGainbyChannel` — confirmed absolute setter semantics, -80 to +80 dB range (cited in ARCHITECTURE.md)
- Kotlin Flow `combine` 4-flow overload — standard Kotlin coroutines API

### Tertiary (LOW confidence)
- None for this phase — all findings are directly verifiable from codebase

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all libraries confirmed present in project
- Architecture: HIGH — based on direct codebase reads; all call sites identified by line number
- Pitfalls: HIGH — overwrite risk validated by POC; DataStore key collision from code analysis; steps parameter from Compose API
- Gain composition formula: HIGH — dB arithmetic is deterministic; formula validated in ARCHITECTURE.md

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable Android APIs, no fast-moving components)
