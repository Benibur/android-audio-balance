# Feature Research

**Domain:** Android Bluetooth audio balance controller — v1.1 milestone (gain offset + FAQ + open source)
**Researched:** 2026-04-07
**Confidence:** HIGH for gain offset (same DynamicsProcessing API already proven in v1.0), MEDIUM for FAQ UX patterns (standard Material3 patterns, no deep research needed), HIGH for open source setup (well-established GitHub norms)

---

## Scope

This research covers only the **new features for v1.1**. The v1.0 feature landscape (balance slider, auto-apply, device list, foreground service, boot receiver) is already built and validated. See git history for prior research.

**Three new features:**
1. Per-device gain offset (volume attenuation/boost via DynamicsProcessing inputGain)
2. FAQ / About screen
3. GitHub public repo (README + MIT license)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features the new milestone must deliver to feel complete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Gain offset slider per device | The feature is the milestone — users with loud/quiet headphones need volume normalization | MEDIUM | Uses `setInputGainAllChannelsTo(dB)` on the existing DP instance; same auto-apply, persist-by-MAC pattern as balance |
| Gain persisted per MAC address | Mirroring balance behavior — users expect settings to survive reconnects | LOW | New DataStore key per MAC (`gain_offset_XX_XX_XX_XX_XX_XX`); default 0.0f dB |
| Gain applied on device connect | Auto-apply at connect must include both balance AND gain — applying one but not the other would be a regression | LOW | Service `applyDeviceBalance()` must also call `setInputGainAllChannelsTo(gainDb)` |
| Gain reset to 0 dB on disconnect | Mirror the balance reset behavior: no audio effect should persist after disconnect | LOW | `resetBalanceToCenter()` equivalent for gain: call `setInputGainAllChannelsTo(0f)` |
| Real-time slider feedback | User expects to hear gain change while dragging, not just on release | MEDIUM | Same throttled service-intent pattern as balance slider (50ms debounce); requires new service action `seed_gain` |
| Gain label showing dB value | Users need to see the current value in dB (e.g., "-6 dB", "+3 dB", "0 dB") | LOW | Display as float with one decimal, center shows "0 dB"; label above or below slider |
| FAQ/About screen accessible from UI | Users expect a way to understand the app and find the source link | LOW | Single static screen; info icon in TopAppBar actions of DeviceListScreen |
| GitHub repo URL visible in FAQ | Open source credibility — users expect a link to source code | LOW | Hardcoded URL string in FAQ screen; use `Intent(ACTION_VIEW)` to open browser |
| MIT LICENSE file in repo | Standard open source expectation — no LICENSE = ambiguous rights | LOW | Single file at repo root; GitHub README badge |
| README with build instructions | Contributors need to know how to build; visitors need to understand the project | LOW | Standard Android project README: what it is, prerequisites, how to build, how to install |

### Differentiators (Competitive Advantage)

Features that go beyond the baseline and add real value for the v1.1 scope.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Gain + balance combined in a single DP apply | Attenuation and stereo correction applied atomically — no audio glitches from partial applies | LOW | Set both channels simultaneously using `setInputGainbyChannel(0, leftDb + gainOffset)` and `setInputGainbyChannel(1, rightDb + gainOffset)` — a single DP write vs two writes |
| Gain with center snap (0 dB magnetic) | Prevents accidental tiny gain deviations from center — same quality as balance slider | LOW | Reuse existing snap logic: `if (abs(rawGain) <= 0.5f) snap to 0f` |
| Notification text includes gain info | Users can verify gain is applied without opening the app | LOW | Extend `formatNotificationText()` to include gain if non-zero: "Bose QC35 • Balance: L+20% • -3 dB" |
| FAQ explains the AudioEffect session 0 approach | Unique technical transparency — explains why the app works system-wide without root | LOW | One paragraph in FAQ; sets user expectations about potential conflicts with other audio apps |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Separate gain + balance DP writes | Seems simpler to implement independently | Two sequential DP writes can create audible glitches or partial states; the combined write avoids this | Combine balance + gain into a single `setInputGainbyChannel()` call per channel |
| Boost above 0 dB (amplification) | "Make it louder" is the most natural user request | DynamicsProcessing inputGain has no enforced max but Android audio routing will clip if the signal exceeds the DAC ceiling; Bluetooth A2DP codecs also clip; headphone damage risk | Cap slider max at 0 dB (attenuation only, no boost) — the use case is normalizing loud devices, not boosting quiet ones |
| Separate gain screen / modal | Seems like clean separation of controls | Creates navigation friction; gain and balance are both per-device audio parameters that belong together | Add gain slider directly in DeviceCard below the balance slider — no new screen needed |
| i18n FAQ content | Full localization seems professional | Scope creep for a personal tool; only one user; adds maintenance overhead | Write FAQ in French (Ben's language) or English only — no i18n infrastructure needed |
| Fancy GitHub Actions CI in repo | Looks professional | Time-consuming setup, no real benefit for a personal USB-deployed app | Simple repo: code + README + LICENSE, no CI |
| Contributing guide with PR templates | Standard open source hygiene | Overkill for a personal project unlikely to receive external contributions | Single CONTRIBUTING.md is optional; a note in README suffices |

---

## Feature Dependencies

```
[Gain offset slider UI]
    └──requires──> [Gain DataStore key in BalanceRepository]
                       └──requires──> [DataStore already set up (v1.0)]

[Gain auto-apply on connect]
    └──requires──> [Gain DataStore key in BalanceRepository]
    └──requires──> [Service applyDeviceBalance() updated to read gain]
    └──requires──> [Balance apply already working (v1.0)]

[Combined gain+balance DP write]
    └──requires──> [BalanceMapper.toGainDb() updated to accept gain offset param]
    └──requires──> [DynamicsProcessing instance (v1.0)]

[Gain real-time slider feedback]
    └──requires──> [New service intent action "seed_gain"]
    └──requires──> [DeviceListViewModel: new onGainSliderChange() + onGainSliderFinished()]
    └──requires──> [DeviceUiState: new gainOffsetDb: Float field]

[Notification with gain info]
    └──requires──> [ServiceState: new currentGainDb: Float field]
    └──requires──> [formatNotificationText() updated]

[FAQ screen]
    └──requires──> [Navigation: new "faq" route added to AppNavigation]
    └──requires──> [DeviceListScreen: info IconButton in TopAppBar actions]
    └──enhances──> [GitHub repo URL] (links to actual repo)

[GitHub public repo]
    └──independent──> (no code dependencies; purely external)
    └──enhances──> [FAQ screen] (FAQ can link to real URL)
```

### Dependency Notes

- **Gain and balance share the same DP instance.** Applying both correctly requires that `leftGainDb` and `rightGainDb` passed to `setInputGainbyChannel()` already incorporate both the balance attenuation AND the global gain offset. This means `BalanceMapper.toGainDb()` needs an additional `gainOffsetDb: Float` parameter (or gain is added after the fact), and the service must read both values before each DP apply.
- **FAQ screen needs navigation plumbing.** `AppNavigation.kt` currently has two routes (`permissions`, `device_list`). Adding `faq` as a third route is straightforward. The info icon action in the `DeviceListScreen` TopAppBar triggers `navController.navigate("faq")`.
- **GitHub repo is infrastructure, not code.** It has no build or runtime dependency on the app code. It can be set up independently and in parallel with code changes.
- **Gain reset on disconnect is a service concern.** `resetBalanceToCenter()` must be extended (or a new `resetAllEffectsToCenter()` function created) to call `setInputGainAllChannelsTo(0f)` in addition to setting balance channels to 0f.

---

## MVP Definition

### v1.1 Launch With

These are the three features of the milestone — all are required.

- [ ] **Gain offset per device** — slider in DeviceCard showing dB value; persisted by MAC; applied on connect; reset on disconnect; real-time feedback while dragging; combined with balance in a single DP write; center snap at 0 dB
- [ ] **FAQ / About screen** — static Compose screen explaining what the app does, why AudioEffect session 0 is used, mention of open source and link to GitHub repo; accessible via info icon in TopAppBar
- [ ] **GitHub public repo** — `github.com/Benibur/android-audio-balance` with README (what it is, how to build, how to install via ADB, known limitations), MIT LICENSE file, and the current code

### Add After Validation (v1.2+)

Deferred per PROJECT.md — do not implement in v1.1.

- [ ] Export/import JSON — useful once config grows; DataStore migration needed first
- [ ] Notification "Apply now" action — add if race condition at connect is observed in real use
- [ ] Per-device nicknames — add if BT device names prove confusing
- [ ] Quick Settings Tile — add after v1 is stable
- [ ] i18n — deferred explicitly in PROJECT.md

### Future Consideration (v2+)

- [ ] Boost above 0 dB — requires clipping/limiting strategy; likely not needed for the target use case (factory-imbalanced headphones)
- [ ] Test balance/gain button — preview effect without reconnecting

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Gain offset slider in DeviceCard | HIGH | MEDIUM | P1 |
| Gain persisted in DataStore | HIGH | LOW | P1 |
| Gain applied on BT connect | HIGH | LOW | P1 |
| Gain reset on disconnect | MEDIUM | LOW | P1 |
| Combined balance+gain DP write | HIGH | LOW | P1 |
| Real-time slider feedback | MEDIUM | LOW | P1 |
| Center snap at 0 dB | LOW | LOW | P1 |
| Notification includes gain | LOW | LOW | P2 |
| FAQ / About screen | MEDIUM | LOW | P1 |
| GitHub repo + README + LICENSE | MEDIUM | LOW | P1 |

---

## Technical Notes for Implementation

### Gain Offset API

`DynamicsProcessing.setInputGainAllChannelsTo(float gainDb)` — sets the same inputGain on all channels simultaneously (convenience wrapper). Available since API 28.

`DynamicsProcessing.setInputGainbyChannel(int channel, float gainDb)` — sets per-channel. The current balance implementation already uses this.

**Combining balance + gain correctly:**
The existing `BalanceMapper.toGainDb(balance)` returns `(leftDb, rightDb)` where one channel gets `0f` and the other gets up to `-60f`. To add gain offset, the cleanest approach is to add `gainOffsetDb` to both outputs:
```
leftFinal  = leftDb  + gainOffsetDb
rightFinal = rightDb + gainOffsetDb
```
Then call `setInputGainbyChannel(0, leftFinal)` and `setInputGainbyChannel(1, rightFinal)`. This is one combined DP write per channel, not two separate operations.

**Gain range recommendation:** -12 dB to 0 dB. Rationale: the use case is normalizing headphones that play too loud (attenuation), not amplification. -12 dB is a practical floor (half perceived loudness). Capping at 0 dB avoids clipping. The API accepts any float; we impose the constraint in the UI slider and the service clamp logic.

**No explicit dB clamp in the Java API** — constraints are enforced at the native engine level. The Java wrapper accepts any float. Community precedent (VLC source) uses `20 * log10(volume)` for conversion.

### FAQ Screen Content

Recommended content:

1. **What this app does** — "Automatically applies your saved stereo balance and volume offset each time you connect a Bluetooth audio device."
2. **Why a persistent service** — "A foreground service keeps the app running in the background so it can detect Bluetooth connections. You will see a persistent notification."
3. **Why it may conflict with other audio apps** — "This app uses Android's DynamicsProcessing on session 0 (the global audio mix). Only one app can control session 0 at a time. If another audio effect app is active, this app may be overridden."
4. **Open source** — "This app is open source under the MIT license." + clickable link to GitHub repo.

Navigation pattern: info icon (`Icons.Outlined.Info`) in `actions` of the `TopAppBar` in `DeviceListScreen`. Navigates to `"faq"` route. FAQ screen has a back navigation arrow (`navigationIcon` in its TopAppBar).

### GitHub Repo Minimal Structure

```
android-audio-balance/
├── README.md          (what + why + build instructions + ADB install)
├── LICENSE            (MIT)
├── app/               (existing Android project)
└── .gitignore         (existing)
```

README sections: What it is (1 paragraph), Why it exists (factory imbalance problem), Requirements (Android 8+, Bluetooth), How to build (Android Studio or Gradle), How to install (ADB command), Known limitations (session 0 conflicts), License.

---

## Sources

- [DynamicsProcessing API reference — Android Developers](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing)
- [DynamicsProcessing.java source — Android Open Source Project](https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/audiofx/DynamicsProcessing.java)
- [The New Dynamics Processing Effect in AOSP — Google Research](https://research.google/pubs/pub47502/)
- [Display a top app bar — Jetpack Compose Developers](https://developer.android.com/develop/ui/compose/quick-guides/content/display-top-app-bar)
- [VLC Android DynamicsProcessing usage — vlc-commits mailing list](https://www.mail-archive.com/vlc-commits@videolan.org/msg67101.html)
- [Wavelet open source repo — GitHub](https://github.com/Pittvandewitt/Wavelet)
- [Make a README — makeareadme.com](https://www.makeareadme.com/)
- Existing v1.0 codebase: `AudioBalanceService.kt`, `BalanceRepository.kt`, `BalanceMapper.kt`, `DeviceCard.kt`, `AppNavigation.kt`

---
*Feature research for: Android BT audio balance controller — v1.1 milestone*
*Researched: 2026-04-07*
