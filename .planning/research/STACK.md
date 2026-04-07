# Stack Research

**Domain:** Android Bluetooth audio processing app — v1.1 additions only
**Researched:** 2026-04-07
**Confidence:** HIGH

> NOTE: This file supersedes the v1.0 stack research (2026-04-01) for the v1.1 milestone.
> The existing stack is already proven in production. This document covers ONLY what changes
> or needs attention for gain offset, FAQ screen, and GitHub repo setup.

---

## What Already Exists (Verified from Codebase Audit)

```
gradle/libs.versions.toml shows:
  agp = "8.7.3"
  kotlin = "2.0.21"
  composeBom = "2024.12.01"
  navigationCompose = "2.8.5"
  datastore = "1.2.1"
  lifecycleService = "2.10.0"
  lifecycleViewmodelCompose = "2.8.7"
  activityCompose = "1.9.3"
  coreKtx = "1.15.0"

All three v1.1 features are served by the existing stack.
Zero new library dependencies are required.
```

---

## New Features vs Stack Impact

| Feature | New deps needed | Version changes needed | Code integration points |
|---------|----------------|----------------------|------------------------|
| Gain offset (DynamicsProcessing inputGain) | None | None | `AudioBalanceService`, `BalanceRepository`, `BalanceMapper` |
| FAQ screen (Compose) | None | None | `AppNavigation`, new `FaqScreen.kt` |
| GitHub repo (README + MIT license) | None | None | Repo root files only |

---

## Recommended Stack Changes

### Version Upgrades (Low Risk, Recommended)

Two libraries lag behind current stable. Neither blocks v1.1, but upgrading before writing new code reduces future debt and ensures compatibility with Android 16 (API 36) on the Pixel 10 test device.

| Library | Current | Latest Stable | Upgrade Risk |
|---------|---------|--------------|-------------|
| Compose BOM | 2024.12.01 | 2026.03.00 | Low — no breaking changes in stable BOM upgrades |
| navigation-compose | 2.8.5 | 2.9.7 (Jan 2026) | Low — same composable() API |

**How to upgrade:** Change two version strings in `gradle/libs.versions.toml`. No code changes required.

```toml
# gradle/libs.versions.toml — only these two lines change
composeBom = "2026.03.00"      # was "2024.12.01"
navigationCompose = "2.9.7"    # was "2.8.5"
```

Everything else stays unchanged.

### No New Libraries to Add

All three features use APIs already present:

- **Gain offset** — `DynamicsProcessing.setInputGainbyChannel()` already called in `AudioBalanceService`. Same method, same DP instance. No new dependency.
- **FAQ screen** — Compose + `navigation-compose` already declared. Add one `composable("faq")` route. `LocalUriHandler` for the GitHub link is in `androidx.compose.ui.platform` (already on classpath via BOM). No new dependency.
- **GitHub repo** — File creation only (`README.md`, `LICENSE`). No code changes.

---

## Feature 1: Gain Offset — DynamicsProcessing Technical Detail

### API (already in use)

`DynamicsProcessing.setInputGainbyChannel(channelIndex: Int, inputGain: Float)` sets gain in dB on a single channel. The existing balance code calls this asymmetrically (different value per channel). Gain offset calls it symmetrically (same value both channels).

A convenience method also exists in the SDK (undocumented in the v1.0 codebase but confirmed in AOSP source):
```kotlin
dp.setInputGainAllChannelsTo(gainDb: Float)  // sets all channels in one call
```

Either works. Using two explicit `setInputGainbyChannel` calls is preferable here because gain offset must be composed with the existing balance values.

### Input Gain Range

The Java wrapper applies no clamping. The native engine enforces limits silently (no exception thrown for out-of-range values — they are clamped internally). Confirmed from AOSP source: no `MIN_INPUT_GAIN` / `MAX_INPUT_GAIN` constants; value is passed directly to the native layer.

| Range | Behavior |
|-------|----------|
| -60 dB | Effectively silent (already used for full balance attenuation) |
| 0 dB | No change (default) |
| +12 dB | Moderate boost |
| > +15 dB | Potential audible clipping on loud sources; native engine clips at DAC level silently |

**Recommended UI slider range: -20 dB to +10 dB.** This covers all practical use cases (compensating for quiet headphones, taming harsh output) without exposing users to clipping risk.

### Composing Balance + Gain Offset

Balance and gain offset both work through `setInputGainbyChannel`. They must be added together before the call — not applied as two separate calls (the second call would overwrite the first):

```kotlin
// In AudioBalanceService — applyDeviceBalance() and seed_balance action
val (leftBalanceDb, rightBalanceDb) = BalanceMapper.toGainDb(balance.toInt())
val gainDb = gainRepository.getGain(mac)  // stored per MAC, default 0f

dp?.setInputGainbyChannel(0, leftBalanceDb + gainDb)
dp?.setInputGainbyChannel(1, rightBalanceDb + gainDb)
```

`BalanceMapper.toGainDb()` returns either `(0f, -60f * fraction)` or `(-60f * fraction, 0f)` — adding a symmetric gainDb offset preserves the balance ratio correctly.

### DataStore Persistence

Add one new key type to `BalanceRepository`. Pattern is identical to the existing `balanceKey`:

```kotlin
// BalanceRepository.kt — add these
private fun gainKey(mac: String) = floatPreferencesKey("gain_${mac.replace(":", "_")}")

suspend fun getGain(mac: String): Float =
    context.dataStore.data.map { it[gainKey(mac)] ?: 0f }.first()

suspend fun saveGain(mac: String, gain: Float) =
    context.dataStore.edit { it[gainKey(mac)] = gain }
```

No new DataStore dependency. Same `device_balance` store. No migration needed (new key, default 0f = no behavior change for existing devices).

### State and UI Surface

Extend existing state classes:

```kotlin
// DeviceUiState — add field
data class DeviceUiState(
    val mac: String,
    val name: String,
    val balance: Float,
    val gainDb: Float,           // NEW — -20f to +10f, default 0f
    val autoApplyEnabled: Boolean,
    val isConnected: Boolean
)

// ServiceState — add field
data class ServiceState(
    val connectedDeviceMac: String? = null,
    val connectedDeviceName: String? = null,
    val currentBalance: Float = 0f,
    val currentGainDb: Float = 0f    // NEW
)
```

Surface as a second slider in `DeviceCard.kt`, directly below the balance slider. Same UX pattern: drag to adjust, service intent sent on change.

---

## Feature 2: FAQ Screen — Navigation Pattern

### Add Route to AppNavigation.kt

```kotlin
// AppNavigation.kt
NavHost(navController = navController, startDestination = startDestination) {
    composable("permissions") { /* existing */ }
    composable("device_list") {
        DeviceListScreen(
            onNavigateToFaq = { navController.navigate("faq") }  // ADD
        )
    }
    composable("faq") {                                            // ADD
        FaqScreen(onBack = { navController.popBackStack() })
    }
}
```

### FAQ Screen Content

Static screen — no ViewModel, no state, no coroutines. Single `@Composable`:

```kotlin
// ui/screens/FaqScreen.kt
@Composable
fun FaqScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current  // from androidx.compose.ui.platform — zero new deps
    Scaffold(
        topBar = { /* TopAppBar with back arrow */ }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // App explanation text
            // Open source mention
            // GitHub link button:
            TextButton(onClick = {
                uriHandler.openUri("https://github.com/Benibur/android-audio-balance")
            }) {
                Text("github.com/Benibur/android-audio-balance")
            }
        }
    }
}
```

`LocalUriHandler.current.openUri()` opens the system browser. No `Intent` boilerplate, no new dependency — it is part of `androidx.compose.ui.platform` which is already on the classpath via the BOM.

### Navigation Entry Point

Add an icon button in the `DeviceListScreen` top app bar using `Icons.Default.Info` or `Icons.Default.HelpOutline` (both available via `material-icons-extended` already declared in `libs.versions.toml`).

### Do NOT Migrate to Navigation 3

Navigation 3 (`androidx.navigation3`) was announced in May 2025 and is at `1.0.0-alpha10`. It is alpha — API is not stable. The existing `navigation-compose` 2.9.7 is stable and serves this app's simple linear navigation perfectly. Adding a FAQ screen does not justify any navigation library migration.

---

## Feature 3: GitHub Repo — Required Files

No code changes. Files to create in repo root:

| File | Content notes |
|------|--------------|
| `README.md` | App name, one-line description, screenshot, what it does and why, install via ADB, build instructions (Android Studio / `./gradlew assembleRelease`), license badge |
| `LICENSE` | MIT license full text, `Copyright (c) 2026 Benibur` |

MIT is the right choice for a personal open-source app: permissive, no copyleft, no attribution burden on users or forks. Standard choice for personal Android projects per GitHub community discussion.

The FAQ screen should display the GitHub URL and mention the MIT license — this is the only code touch point for the open source feature.

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Navigation | navigation-compose 2.9.7 (existing) | Navigation 3 (1.0.0-alpha10) | Alpha — API not stable; migration cost for zero benefit on a 3-screen app |
| Gain composition | Additive dB sum per channel | Separate GainMapper utility class | Unnecessary abstraction for a single scalar offset |
| GitHub link | `LocalUriHandler.current.openUri()` | Manual `Intent(Intent.ACTION_VIEW, Uri.parse(url))` | LocalUriHandler is idiomatic Compose, zero extra code, already on classpath |
| Gain UI | Slider -20 to +10 dB | Slider -60 to +20 dB | Impractical extremes; -60 is the full-mute value for balance, not a user-facing gain knob |
| DataStore for gain | Same `device_balance` store, new key | Separate DataStore instance | Single store is simpler; no migration; no code structure change |

---

## What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Navigation 3 | Alpha, API liable to change | Existing navigation-compose stable |
| Room database | Codebase already uses DataStore and it works; adding Room is a rewrite of the persistence layer | DataStore Preferences — extend with new gain key |
| Hilt / Koin | Over-engineering for a single-service app; manual injection (constructor params, applicationContext) is already working | Keep existing manual wiring |
| Equalizer / EQ bands | Out of scope per PROJECT.md | Nothing — DP inputGain is the only audio change for v1.1 |
| Accompanist | Archived/deprecated library | Built-in Compose APIs |
| Gson / Moshi | Not needed; no JSON required in v1.1 | Nothing — JSON export deferred to post-v1.1 |

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|----------------|-------|
| navigation-compose 2.9.7 | Compose BOM 2026.03.00 | Both are stable Jan/Feb 2026 releases |
| Compose BOM 2026.03.00 | Kotlin 2.0.21, AGP 8.7.3 | No Kotlin 2.1+ requirement for this BOM range |
| DynamicsProcessing inputGain | API 28+ (Android P) | Already gated with `@RequiresApi(Build.VERSION_CODES.P)` in service |
| `LocalUriHandler` | Compose UI 1.0+ | In BOM, no version constraint |

---

## Sources

- [DynamicsProcessing API reference](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing) — inputGain dB field, setInputGainbyChannel signature — HIGH confidence
- [DynamicsProcessing.java source (AOSP)](https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/audiofx/DynamicsProcessing.java) — confirmed no Java-level clamping, `setInputGainAllChannelsTo` exists — HIGH confidence
- [Navigation release notes](https://developer.android.com/jetpack/androidx/releases/navigation) — 2.9.7 confirmed stable as of January 2026 — HIGH confidence
- [Navigation 3 announcement (Android Developers Blog)](https://android-developers.googleblog.com/2025/05/announcing-jetpack-navigation-3-for-compose.html) — confirmed alpha-only status — HIGH confidence
- [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) — 2026.03.00 confirmed latest stable — HIGH confidence
- [GitHub open source license discussion](https://github.com/orgs/community/discussions/131758) — MIT as standard choice for personal Android apps — MEDIUM confidence
- Codebase direct audit (`AudioBalanceService.kt`, `BalanceRepository.kt`, `BalanceMapper.kt`, `DeviceUiState.kt`, `AppNavigation.kt`, `libs.versions.toml`) — HIGH confidence

---

*Stack research for: Android Bluetooth audio balance app — v1.1 (gain offset + FAQ + open source)*
*Researched: 2026-04-07*
