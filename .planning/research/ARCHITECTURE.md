# Architecture Research

**Domain:** Android Bluetooth audio processing — v1.1 gain offset + FAQ additions
**Researched:** 2026-04-07
**Confidence:** HIGH (direct codebase analysis of all 21 source files)

> This file replaces the v1.0 research (2026-04-01). The architecture is now shipped and
> validated. This document focuses exclusively on what changes for milestone v1.1.

---

## Existing Architecture (v1.0 — Immutable Baseline)

```
┌─────────────────────────────────────────────────────────────┐
│                     UI Layer (Compose)                       │
├──────────────┬──────────────────┬───────────────────────────┤
│ Permissions  │  DeviceList      │  [NEW] FaqScreen           │
│ Screen       │  Screen          │                            │
│              │  + DeviceCard    │                            │
└──────┬───────┴────────┬─────────┴──────────────┬────────────┘
       │                │                         │
┌──────▼────────────────▼─────────────────────────▼──────────┐
│                   ViewModel Layer                            │
│  DeviceListViewModel (AndroidViewModel)                      │
│  — combines: AudioBalanceService.stateFlow                   │
│            + repository.getAllDevicesFlow()                  │
│            + _sliderOverrides (local MutableStateFlow)       │
└────────────────────────┬────────────────────────────────────┘
                         │  Intent ("seed_balance" /
                         │          "reset_audio_only" /
                         │          [NEW] "seed_gain_offset")
┌────────────────────────▼────────────────────────────────────┐
│           AudioBalanceService (ForegroundService)            │
│  — holds DynamicsProcessing instance (session 0 global)      │
│  — handles BT connect/disconnect via BtA2dpReceiver          │
│  — applies gain per-channel to DP                            │
│  — exposes companion object StateFlow for UI                 │
└───────────┬─────────────────────────────┬───────────────────┘
            │                             │
┌───────────▼──────────┐      ┌───────────▼──────────────────┐
│ DynamicsProcessing   │      │    BalanceRepository          │
│ (session 0 global)   │      │    (DataStore Preferences)    │
│ setInputGainby-      │      │    balance_XX_XX_XX           │
│   Channel(ch, dB)    │      │    auto_apply_XX_XX_XX        │
│                      │      │    name_XX_XX_XX              │
│  Called with         │      │  [NEW] gain_offset_XX_XX_XX   │
│  COMPOSED value:     │      └──────────────────────────────┘
│  balance_dB +        │
│  gainOffset_dB       │
└──────────────────────┘
```

---

## Integration Points for New Features

### 1. Gain Offset — DynamicsProcessing Composition

**The core question:** `setInputGainbyChannel(channel, gainDb)` overwrites the value for that
channel. It does not accumulate. Balance and gain offset must be composed into a single dB value
before each API call.

**Composition rule (HIGH confidence — dB arithmetic):**

Balance produces a per-channel attenuation via `BalanceMapper.toGainDb(balance)`:
- `leftDb`  = 0 dB to -60 dB (attenuated when balance shifts right)
- `rightDb` = 0 dB to -60 dB (attenuated when balance shifts left)

Gain offset is a symmetric dB shift applied to both channels:
- `gainOffsetDb` = e.g. -12 dB to +6 dB

The composed values:

```kotlin
val (balanceLeft, balanceRight) = BalanceMapper.toGainDb(balance.roundToInt())
val leftFinal  = balanceLeft  + gainOffsetDb
val rightFinal = balanceRight + gainOffsetDb

dp?.setInputGainbyChannel(0, leftFinal)
dp?.setInputGainbyChannel(1, rightFinal)
```

This is the only correct approach. Both controls are in dB space; dB addition is linear
multiplication of linear gain, which is what the user expects (offset shifts overall volume,
balance shifts relative difference).

**DynamicsProcessing input gain range:** -80 dB to +80 dB per Android API docs. The current
balance uses up to -60 dB. A gain offset range of -12 to +6 dB leaves safe headroom. Exposing
extremes is not needed.

**Where `setInputGainbyChannel` is currently called — all sites must be updated:**

| Location | When called |
|----------|-------------|
| `AudioBalanceService.onStartCommand` — `seed_balance` block | Real-time slider update |
| `AudioBalanceService.applyDeviceBalance()` | BT connect auto-apply |

Both sites must be refactored to call a single `applyGains(balance: Float, gainOffset: Float)`
private function that performs the composition and calls the API.

---

### 2. Gain Offset — Data Persistence

**What to add to `BalanceRepository`:**

```kotlin
private fun gainOffsetKey(mac: String) =
    floatPreferencesKey("gain_offset_${mac.replace(":", "_")}")

suspend fun getGainOffset(mac: String): Float =
    context.dataStore.data.map { it[gainOffsetKey(mac)] ?: 0f }.first()

suspend fun saveGainOffset(mac: String, dB: Float) =
    context.dataStore.edit { it[gainOffsetKey(mac)] = dB }
```

**`getAllDevicesFlow()` must emit gain offset per device.**

Currently returns `Flow<List<Triple<String, Float, Boolean>>>`. The `Triple` approach does not
scale to a fourth field. Replace it with a proper data class:

```kotlin
data class DeviceEntry(
    val mac: String,
    val balance: Float,       // -100f to +100f
    val autoApply: Boolean,
    val gainOffset: Float     // dB, default 0f
)
```

Updated flow query inside `getAllDevicesFlow()` adds one line:

```kotlin
val gainOffset = prefs[gainOffsetKey(mac)] ?: 0f
DeviceEntry(mac, balance, autoApply, gainOffset)
```

**Files modified by this change:**
- `BalanceRepository` — add `DeviceEntry`, update `getAllDevicesFlow()`
- `DeviceListViewModel` — the combine lambda destructures `DeviceEntry` instead of `Triple`
- `DeviceUiState` — add `gainOffset: Float = 0f`

---

### 3. Gain Offset — Service Intent

New intent action mirrors the existing `seed_balance` pattern:

```kotlin
// ViewModel sends:
Intent(context, AudioBalanceService::class.java).apply {
    putExtra("action", "seed_gain_offset")
    putExtra("gain_offset", gainOffsetDb)
}

// Service handles in onStartCommand:
"seed_gain_offset" -> {
    val gainOffsetDb = intent.getFloatExtra("gain_offset", 0f)
    val mac = currentDeviceMac ?: return
    serviceScope.launch {
        balanceRepository.saveGainOffset(mac, gainOffsetDb)
        val balance = balanceRepository.getBalance(mac)
        applyGains(balance, gainOffsetDb)   // shared helper
        _stateFlow.value = _stateFlow.value.copy(currentGainOffset = gainOffsetDb)
    }
}
```

The service already holds `currentDeviceMac` so it can load the stored balance when only
the gain offset changes, and vice versa.

---

### 4. Gain Offset — ViewModel

**New fields and callbacks, parallel to balance:**

```kotlin
private val _gainOffsetOverrides = MutableStateFlow<Map<String, Float>>(emptyMap())

// In combine lambda — DeviceEntry now has gainOffset:
val displayGainOffset = gainOffsetOverrides[mac] ?: deviceEntry.gainOffset
DeviceUiState(..., gainOffset = displayGainOffset)

fun onGainOffsetChange(mac: String, dB: Float) {
    _gainOffsetOverrides.value = _gainOffsetOverrides.value + (mac to dB)
    if (isConnectedDevice(mac)) {
        val now = System.currentTimeMillis()
        if (now - lastSendTimestamp >= 50) {   // same 50ms throttle
            lastSendTimestamp = now
            sendGainOffsetToService(dB)
        }
    }
}

fun onGainOffsetFinished(mac: String, rawDb: Float) {
    val snappedDb = if (kotlin.math.abs(rawDb) <= 0.5f) 0f else rawDb  // snap to 0
    _gainOffsetOverrides.value = _gainOffsetOverrides.value - mac
    viewModelScope.launch {
        repository.saveGainOffset(mac, snappedDb)
        if (isConnectedDevice(mac)) sendGainOffsetToService(snappedDb)
    }
}
```

---

### 5. Gain Offset — UI (DeviceCard)

A second slider below the balance slider. Same structural pattern: L label + Slider + R label
replaced by: a dB label (center-aligned showing current value) + Slider.

**Slider mapping:**

```kotlin
// gainOffset: -12f to +6f dB
// Slider API: 0f to 1f
val sliderValue = (gainOffset - (-12f)) / (6f - (-12f))   // normalize to 0..1

// On change:
val gainOffsetDb = sliderValue * 18f + (-12f)
```

**Label:** "Volume: 0 dB" / "Volume: -3 dB" / "Volume: +2 dB"

**New parameters for `DeviceCard`:**

```kotlin
@Composable
fun DeviceCard(
    device: DeviceUiState,
    onBalanceChange: (Float) -> Unit,
    onBalanceFinished: (Float) -> Unit,
    onAutoApplyToggle: (Boolean) -> Unit,
    onGainOffsetChange: (Float) -> Unit,      // NEW
    onGainOffsetFinished: (Float) -> Unit     // NEW
)
```

---

### 6. FAQ Screen — Navigation Pattern

**Recommended pattern:** Add a third `composable()` route to the existing `NavHost` in
`AppNavigation.kt`. No new nav graph, no nested navigation.

**`AppNavigation.kt` changes:**

```kotlin
// Add route
composable("faq") {
    FaqScreen(onBack = { navController.popBackStack() })
}

// Thread onFaqClick into device_list route
composable("device_list") {
    DeviceListScreen(onFaqClick = { navController.navigate("faq") })
}
```

**`DeviceListScreen` change:** Add `onFaqClick: () -> Unit` parameter. Pass it to the
`TopAppBar` as an `IconButton` with `Icons.Outlined.Info` or `Icons.Outlined.HelpOutline`.

**`FaqScreen` — stateless composable, no ViewModel:**

```kotlin
@Composable
fun FaqScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("À propos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        // Static content: Column with Text blocks + ClickableText for GitHub URL
        // uriHandler.openUri("https://github.com/Benibur/android-audio-balance")
    }
}
```

`LocalUriHandler.current.openUri()` is the Compose-idiomatic way to open a URL. No
`Intent(Intent.ACTION_VIEW)` boilerplate needed.

---

## Component Change Summary

| Component | Status | What Changes |
|-----------|--------|-------------|
| `BalanceRepository` | MODIFIED | Add `DeviceEntry` data class; add `gainOffsetKey`, `getGainOffset`, `saveGainOffset`; update `getAllDevicesFlow` to emit `DeviceEntry` |
| `AudioBalanceService` | MODIFIED | Add `seed_gain_offset` intent handler; extract `applyGains(balance, offset)` private helper; update both `setInputGainbyChannel` call sites to use it |
| `BalanceMapper` | MODIFIED | `toGainDb` still returns `Pair<Float, Float>` — no change to its signature. Composition with gain offset happens in the new `applyGains()` helper in the service |
| `DeviceUiState` | MODIFIED | Add `gainOffset: Float = 0f` field |
| `ServiceState` | MODIFIED | Add `currentGainOffset: Float = 0f` (needed if notification shows gain level) |
| `DeviceListViewModel` | MODIFIED | Update combine lambda for `DeviceEntry`; add `_gainOffsetOverrides`; add `onGainOffsetChange` / `onGainOffsetFinished`; add `sendGainOffsetToService()` |
| `DeviceCard` | MODIFIED | Add gain offset slider + label; add two new callback params |
| `DeviceListScreen` | MODIFIED | Pass new callbacks to `DeviceCard`; accept and thread `onFaqClick` lambda; add info `IconButton` to `TopAppBar` |
| `AppNavigation` | MODIFIED | Add `"faq"` composable route; pass `onFaqClick` lambda to device_list route |
| `FaqScreen` | NEW | Stateless composable, `onBack` lambda, `LocalUriHandler` for GitHub link |

---

## Data Flow

### Real-Time Gain Offset Slider

```
User moves gain offset slider in DeviceCard
    ↓
onGainOffsetChange(mac, dB)
    ↓
DeviceListViewModel._gainOffsetOverrides updated  → immediate UI recompose
    ↓ (only if connected device, throttled 50 ms)
Intent("seed_gain_offset", gain_offset=dB) → AudioBalanceService.onStartCommand
    ↓
save to repo, read current balance from repo
    ↓
applyGains(balance, dB):
    leftFinal  = balanceLeft  + dB
    rightFinal = balanceRight + dB
    ↓
dp?.setInputGainbyChannel(0, leftFinal)
dp?.setInputGainbyChannel(1, rightFinal)
```

### BT Connect Auto-Apply (with gain offset)

```
BtA2dpReceiver → handleBtEvent(STATE_CONNECTED)
    ↓ 1 s delay (audio routing stability)
applyDeviceBalance(device)
    ↓
repo.getBalance(mac)      → balance
repo.getGainOffset(mac)   → gainOffsetDb  [NEW]
repo.getAutoApply(mac)    → autoApply
    ↓ if autoApply
applyGains(balance, gainOffsetDb)          [NEW shared helper]
    dp?.setInputGainbyChannel(0, balanceLeft  + gainOffsetDb)
    dp?.setInputGainbyChannel(1, balanceRight + gainOffsetDb)
```

### FAQ Navigation

```
DeviceListScreen: user taps info icon in TopAppBar
    ↓
onFaqClick() lambda (provided by AppNavigation)
    ↓
navController.navigate("faq")
    ↓
FaqScreen rendered (stateless, static content)
    ↓ user taps back arrow
onBack() → navController.popBackStack()
    ↓
DeviceListScreen restored (back stack state preserved)
```

---

## Recommended Build Order

Build in dependency order — lower layers first:

**Step 1 — Data layer**
`DeviceEntry` data class + `BalanceRepository` additions for gain offset +
update `getAllDevicesFlow()` to emit `DeviceEntry`.
No UI, no service changes. Isolated, testable.

**Step 2 — Service layer**
Extract `applyGains(balance, offset)` helper in `AudioBalanceService`.
Update both existing `setInputGainbyChannel` call sites to use it.
Add `seed_gain_offset` intent handler.
Update `applyDeviceBalance()` to load and pass gain offset.
All audio logic in one pass — no half-measures.

**Step 3 — ViewModel + state layer**
`DeviceUiState` and `ServiceState` field additions.
`DeviceListViewModel` combine lambda update + `_gainOffsetOverrides` + new callbacks.
Depends on Step 1 (repository API) and Step 2 (intent action constant).

**Step 4 — UI layer**
`DeviceCard` second slider.
`DeviceListScreen` new callbacks + FAQ icon.
`AppNavigation` new route.
`FaqScreen` static composable.
Depends on Step 3 (ViewModel + UiState).

**Step 5 — GitHub repo**
README, MIT licence, public repository.
Purely documentation. No code dependencies. Logically last (documents the completed feature set).

---

## Architectural Invariants (Must Not Be Broken)

These constraints made v1.0 work. Every change must respect them.

| Invariant | Why it must hold |
|-----------|-----------------|
| All `setInputGainbyChannel` calls go through one function | Balance and gain offset must be composed together; split call sites create split-brain audio state |
| DP auto-recovery (`hasControl()` check before apply) | Another app can evict our DP at any time; `applyGains()` must include the same null/hasControl check as current `seed_balance` handler |
| `currentDeviceMac` null check before repo writes | Prevents orphaned DataStore entries |
| 50 ms throttle on real-time slider events to service | Gain offset slider must throttle same as balance slider |
| 1 s delay after BT connect before applying audio | Audio routing not stable at connect time; gain offset is included in this delayed apply via `applyDeviceBalance()` |
| DP `setEnabled(true)` after create, check `hasControl()` | The `createDpInstance()` pattern is not touched by these changes |

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Two separate `setInputGainbyChannel` calls for balance + gain offset

**What:** Call the API once for balance, then call it again for gain offset.

**Why wrong:** The second call overwrites the first. The channel ends up with only the gain offset
applied, balance is lost.

**Instead:** One `applyGains(balance, gainOffset)` function composes both to dB before a single
API call per channel.

### Anti-Pattern 2: Reading gain offset inside `seed_balance` handler, balance inside `seed_gain_offset` handler — separately

**What:** Each intent handler reads only its own value from the repo and applies both values
by also calling `getBalance()` or `getGainOffset()` ad-hoc inside each handler.

**Why wrong:** Three code paths reading and applying gain values. Easy to miss updating one;
leads to inconsistent state.

**Instead:** Both handlers call `applyGains(mac)` — a shared suspend function that reads both
values from repo and calls the API once. Single implementation.

### Anti-Pattern 3: Passing NavController directly into FaqScreen

**What:** `FaqScreen(navController = navController)` so it calls `navController.popBackStack()`.

**Why wrong:** Couples a screen composable to the navigation framework. Breaks @Preview, harder
to reuse or test.

**Instead:** Pass `onBack: () -> Unit`. `AppNavigation` provides `{ navController.popBackStack() }`.

### Anti-Pattern 4: Adding a ViewModel to FaqScreen

**What:** `FaqViewModel` created by habit.

**Why wrong:** FAQ content is static string resources. No async loading, no business logic, no
state. ViewModel adds file count and complexity with zero benefit.

**Instead:** Pure stateless `@Composable` with `stringResource()` calls.

### Anti-Pattern 5: Adding a new DataStore file for gain offset

**What:** Create `val Context.gainOffsetDataStore by preferencesDataStore(name = "gain_offset")`.

**Why wrong:** Two DataStore files = two async reads per device per BT connect. Unnecessary.

**Instead:** Add `gain_offset_XX_XX_XX` keys to the existing `device_balance` DataStore. All
device settings in one store, one read path.

---

## Sources

- Direct codebase analysis: `AudioBalanceService.kt`, `BalanceRepository.kt`,
  `DeviceListViewModel.kt`, `BalanceMapper.kt`, `AppNavigation.kt`, `DeviceCard.kt`,
  `DeviceUiState.kt`, `DevicePreferences.kt` (all read 2026-04-07)
- `DynamicsProcessing.setInputGainbyChannel()` semantics: overwrites, does not accumulate
  (HIGH confidence — standard audio effect API semantics; validated in v1.0 POC)
- dB addition for independent gain stages: standard audio signal processing
  (HIGH confidence)
- Jetpack Navigation Compose `composable()` + `popBackStack()` pattern: standard API
  (HIGH confidence)
- `LocalUriHandler` for Compose URL opening: Compose UI standard library
  (HIGH confidence)

---

*Architecture research for: Android BT Audio Balance v1.1 — Gain Offset + FAQ*
*Researched: 2026-04-07*
