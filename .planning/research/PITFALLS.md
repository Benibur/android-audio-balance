# Pitfalls Research

**Domain:** Android audio balance app — v1.1 milestone: per-device gain offset, FAQ screen, GitHub open source
**Researched:** 2026-04-07
**Confidence:** HIGH for DynamicsProcessing math and open-source secrets; MEDIUM for Compose navigation integration

---

> **Scope note:** This file covers pitfalls specific to the v1.1 milestone additions. Pitfalls from v1.0
> (session 0 AudioEffect reliability, foreground service type, OEM battery kill, etc.) remain valid —
> see the v1.0 PITFALLS.md for those. This file does not repeat them.

---

## Critical Pitfalls

### Pitfall 1: Gain Offset Applied Additively to Balance Gains — Wrong Channel Values

**What goes wrong:**
The gain offset is naively stored as a scalar dB value and applied by adding it uniformly to both
`setInputGainbyChannel(0, ...)` and `setInputGainbyChannel(1, ...)`. This produces wrong results:

Example — device has balance L+50% and gain offset −6 dB:
- Balance alone: L=0dB, R=−30dB (half attenuation)
- Gain offset alone: both channels −6dB
- Wrong naive sum: L=−6dB, R=−36dB
- Correct combined: L=−6dB, R=−36dB ← identical in this case
- BUT when balance is R+50% and offset is +3dB: L=−30dB+3dB=−27dB, R=0dB+3dB=+3dB
  The right channel boosts above 0dB, which may clip or distort unexpectedly.

The deeper problem: `setInputGainbyChannel` sets the *absolute* input gain, not a relative delta.
Calling it multiple times for the same channel replaces, not accumulates, the value. So if the
`applyDeviceBalance` function and a separate `applyGainOffset` function each call `setInputGainbyChannel`,
only the last call wins — the other is silently discarded.

**Why it happens:**
The current service has separate balance and gain offset values. Developers split them into
separate apply calls (one for balance, one for offset), not realising `setInputGainbyChannel`
is a setter, not an accumulator. The second call overwrites the first.

**How to avoid:**
Compute the final per-channel gain as a single combined value before any `setInputGainbyChannel`
call. The formula for a single merged apply call:

```kotlin
fun computeChannelGains(balance: Float, gainOffsetDb: Float): Pair<Float, Float> {
    // balance: -100f (full left) to +100f (full right)
    // gainOffsetDb: e.g. -6f to +6f, applied equally to both channels
    val fraction = balance.coerceIn(-100f, 100f) / 100f
    val balanceLeftDb  = if (fraction > 0) -60f * fraction else 0f
    val balanceRightDb = if (fraction < 0) -60f * (-fraction) else 0f
    return Pair(
        balanceLeftDb  + gainOffsetDb,
        balanceRightDb + gainOffsetDb
    )
}
// Single apply — never call setInputGainbyChannel twice for the same channel
val (leftDb, rightDb) = computeChannelGains(balance, gainOffsetDb)
dp?.setInputGainbyChannel(0, leftDb)
dp?.setInputGainbyChannel(1, rightDb)
```

This is the only correct architecture: one function that owns all gain computation, called once
per apply event.

**Warning signs:**
- Changing balance slider has no audible effect after gain offset was applied (offset call
  overwrote balance).
- Gain offset appears to apply correctly in isolation but breaks when balance is non-zero.
- Audio at `+3dB` gain offset + center balance sounds correct, but at `+3dB` + full-right
  balance the left channel is unexpectedly audible.

**Phase to address:**
Phase 1 (Gain offset implementation) — define `computeChannelGains()` as the canonical single
source of truth for all gain math before writing any UI or service integration.

---

### Pitfall 2: Gain Offset Range Causes Clipping or Silent Distortion

**What goes wrong:**
`setInputGainbyChannel` accepts decibel values with no documented maximum. Positive gain values
above 0dB boost the input signal. If the incoming audio is already near full scale (0dBFS),
boosting by +6dB causes clipping distortion — typically heard as crackling or harsh distortion
at high listening volumes. The effect does not warn, does not clamp, and does not show an error.

**Why it happens:**
Developers familiar with hardware equalizers assume "gain" means perceived loudness, not signal
amplitude. Boosting by +6dB on a full-scale signal doubles the amplitude beyond the DAC's range.
The DynamicsProcessing documentation says nothing about clipping behavior on session 0.

**How to avoid:**
- Cap the gain offset slider to a range that is conservative in the positive direction.
  Recommended: **−12dB to +6dB**. Beyond +6dB positive gain, clipping risk is too high for
  general use on unknown source material.
- Display the dB value numerically next to the slider so the user understands the unit.
- Consider showing a warning label for positive values: "Boosting may cause distortion at
  high volumes."
- Do NOT allow the slider to reach values that produce a combined channel gain above +6dB.
  If balance = center and offset = +10dB, both channels are at +10dB — clearly dangerous.

**Warning signs:**
- User reports crackling or distortion at certain settings.
- Distortion only at high system volume + positive gain offset.
- No error in logcat — the effect clamps silently at the hardware level.

**Phase to address:**
Phase 1 (Gain offset) — define dB range and slider constraints as a design decision before
implementation, not after.

---

### Pitfall 3: Git History Contains Build Secrets or Signing Config Before Going Public

**What goes wrong:**
The existing local project has a git history that may contain sensitive information. Before
pushing to a public GitHub repository, the entire commit history must be clean. Common sensitive
items in Android projects:

- `local.properties` — contains `sdk.dir` (usually harmless) but sometimes also API keys
  added manually by developers
- `keystore.jks` / `*.keystore` — signing key files. If committed, anyone can re-sign APKs
  with the same identity
- `keystore.properties` — contains `storePassword`, `keyAlias`, `keyPassword` in plaintext
- Hardcoded API keys in `BuildConfig`, `strings.xml`, or `gradle.properties` (not applicable
  to this project currently, but worth auditing)
- Debug SHA-1 fingerprints, package names, or internal server URLs in comments

The risk: once pushed public, GitHub's secret scanning and web crawlers index the history
immediately. Even if files are deleted in a later commit, the secret persists in the git
object store and is visible via `git log --all`.

**Why it happens:**
Developers working alone on local projects never configure `.gitignore` carefully because
"it's just local." When going public, they `git push --all` without auditing history first.

**How to avoid:**
1. Before creating the public repo, audit the full history:
   ```bash
   git log --all --full-history -- "*.jks" "*.keystore" "keystore.properties"
   git log --all --full-history -- "local.properties"
   git grep -i "password\|apikey\|secret\|token" $(git log --all --pretty=format:"%H")
   ```
2. Ensure `.gitignore` contains at minimum:
   ```
   local.properties
   *.jks
   *.keystore
   keystore.properties
   .gradle/
   build/
   ```
3. This project does not have API keys. The app is sideloaded, not Play Store signed. The
   main risk is `local.properties` (sdk.dir) and any signing config if release builds were set up.
4. If any sensitive file was ever committed, use `git filter-repo` (the modern replacement for
   `git filter-branch`) to rewrite history before the first push.
5. Create the GitHub repo as private first, push, verify history with GitHub's secret scanning
   alerts, then make public.

**Warning signs:**
- `local.properties` appears in `git log --all` output.
- Any `.jks` or `.keystore` file appears in `git log --all` output.
- GitHub sends a security alert email immediately after going public (GitHub secret scanning
  triggers on known patterns).

**Phase to address:**
Phase 3 (GitHub open source) — explicit pre-push history audit is the first step, before
creating the public repository.

---

### Pitfall 4: README Build Instructions Are Wrong for Contributors — Missing .gitignored Files

**What goes wrong:**
The README says "clone and build" but the project requires `local.properties` (pointing to the
Android SDK path) and possibly a signing config that are excluded from the repository. A fresh
clone fails with a cryptic Gradle error like:
```
SDK location not found. Define a valid SDK location with an sdk.dir key in local.properties file...
```
Contributors and the user themselves (on a fresh machine) cannot build without knowing this.

**Why it happens:**
The developer knows `local.properties` is auto-generated by Android Studio and doesn't think
of it as something to document. Contributors without Android Studio experience don't know
where to create it.

**How to avoid:**
- README must include a "Prerequisites / Build" section listing every file that must be
  created manually:
  ```
  # local.properties (not in repo)
  sdk.dir=/path/to/Android/Sdk
  ```
- Include the Android Studio version and minSdk/targetSdk in the README.
- Since there is no Play Store signing for this project (sideload only), release signing
  complexity does not apply. Document that `debug` build is the correct build variant.

**Warning signs:**
- First comment on the GitHub repo is "I can't build this."
- Gradle sync fails on fresh clone without helpful error message.

**Phase to address:**
Phase 3 (GitHub open source) — verify that a hypothetical fresh clone produces a buildable
project by checking the README against the actual `.gitignore`.

---

## Moderate Pitfalls

### Pitfall 5: Gain Offset DataStore Key Collision With Existing Keys

**What goes wrong:**
The current `BalanceRepository` uses MAC address-derived keys: `balance_AA_BB_CC_DD_EE_FF`,
`auto_apply_AA_BB_CC_DD_EE_FF`, `name_AA_BB_CC_DD_EE_FF`. Adding gain offset as a new key
with the wrong prefix pattern (e.g., `gain_AA:BB:CC:DD:EE:FF` with colons instead of
underscores) will not collide but will create a silent inconsistency: `getAllDevicesFlow()`
filters on `balance_` prefix to enumerate known devices. If `getAllBalances()` or similar
functions are used to discover devices, the gain offset key pattern must match or be independent.

A subtler bug: if the gain offset key accidentally starts with `balance_`, the device
enumeration logic in `getAllDevicesFlow()` will try to parse it as a balance value, likely
casting a Float as a Float (coincidentally works), but producing a ghost entry with a mangled
MAC address in the device list.

**How to avoid:**
Use a key function that mirrors the existing pattern exactly:
```kotlin
private fun gainOffsetKey(mac: String) = floatPreferencesKey("gain_offset_${mac.replace(":", "_")}")
```
The prefix `gain_offset_` does not start with `balance_`, so existing filtering is unaffected.
Add a `getGainOffset(mac)` / `saveGainOffset(mac, dB)` pair to `BalanceRepository` following
the same pattern as `getBalance` / `saveBalance`. Default is `0f` (no gain change).

**Warning signs:**
- Device list shows a mysterious extra entry with garbled name.
- `getAllDevicesFlow()` emits more entries than there are known devices.

**Phase to address:**
Phase 1 (Gain offset) — data layer addition is the first implementation step; get the key
naming right before writing service integration.

---

### Pitfall 6: Compose Navigation Back Stack Grows Without Bound When Adding FAQ Screen

**What goes wrong:**
The current `AppNavigation.kt` has two routes: `permissions` and `device_list`. Adding a
third route `faq` with a navigation button on `DeviceListScreen` is straightforward, but the
back stack behaviour of the `NavHost` means pressing "Back" from the FAQ screen returns to
`device_list`, which is correct. However, if the FAQ screen is navigated to multiple times
without `popUpTo`, the back stack accumulates duplicate `faq` entries. Pressing Back repeatedly
cycles through `faq` → `faq` → `faq` → `device_list` instead of going directly to `device_list`.

**How to avoid:**
Use `launchSingleTop = true` when navigating to the FAQ screen:
```kotlin
navController.navigate("faq") {
    launchSingleTop = true
}
```
This prevents duplicate entries. The FAQ screen is a leaf — it has no nested navigation and
should never stack on itself.

**Warning signs:**
- Pressing Back from FAQ takes multiple presses to return to device list.
- `navController.backQueue.size` grows each time FAQ is opened.

**Phase to address:**
Phase 2 (FAQ screen) — apply `launchSingleTop` in the navigate call from day one.

---

### Pitfall 7: Gain Offset Slider State Not Synced on BT Reconnect

**What goes wrong:**
When a BT device reconnects, the service applies the saved balance and (new) gain offset.
The ViewModel that drives the UI reads the `ServiceState` flow, which in v1.0 only carries
`currentBalance`. If `ServiceState` is not extended to include `currentGainOffset`, the UI
shows the slider at its last remembered position (from a previous UI session or zero on fresh
launch) while the audio has the correct offset applied by the service. The user sees a stale
slider that does not reflect actual audio state.

**How to avoid:**
Extend `ServiceState` (or the equivalent data class exposed by the ViewModel) to include
`currentGainOffset: Float = 0f`. The service sets this alongside `currentBalance` when
applying gains on device connect. The ViewModel observes the full state and initialises
both sliders from it.

**Warning signs:**
- Gain slider resets to 0 every time the app is opened, even though audio has the saved offset.
- Balance slider shows correctly (already in `ServiceState`) but gain slider does not.

**Phase to address:**
Phase 1 (Gain offset) — extend `ServiceState` as part of the service integration work, not as
a UI afterthought.

---

### Pitfall 8: DynamicsProcessing `UnsupportedOperationException` on `setInputGainbyChannel` After DP Recreation

**What goes wrong:**
After a DP recreation (triggered by `hasControl()` returning false), the new DP instance
may be in a transitional state where `setInputGainbyChannel` throws
`UnsupportedOperationException` wrapped as `AudioEffect: invalid parameter operation`.
This was observed in VLC's codebase (they added a catch specifically for this). The existing
service code catches `RuntimeException` on creation but not on `setInputGainbyChannel` calls.

**Why it happens:**
The native audio effect framework processes commands asynchronously. Calling
`setInputGainbyChannel` immediately after `setEnabled(true)` on a newly created DP can race
with internal initialization. The first call on a fresh instance occasionally fails.

**How to avoid:**
Wrap `setInputGainbyChannel` calls in try-catch for `RuntimeException` (which is the parent
of `UnsupportedOperationException` in this context):
```kotlin
try {
    dp?.setInputGainbyChannel(0, leftDb)
    dp?.setInputGainbyChannel(1, rightDb)
} catch (e: RuntimeException) {
    Log.e(TAG, "setInputGainbyChannel failed: ${e.message}")
    // Optionally: recreate DP and retry once
}
```
The existing service already wraps creation — extend this pattern to the apply calls.

**Warning signs:**
- `AudioEffect: invalid parameter operation` in logcat, specifically after a DP recreation
  event (hasControl loss due to competing app).
- Gain appears not applied after the auto-recovery path.

**Phase to address:**
Phase 1 (Gain offset) — extend the existing error-handling pattern when refactoring the apply
function to combine balance + gain offset.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Separate balance apply and gain offset apply — two `setInputGainbyChannel` calls | Less refactoring | Second call silently overwrites first; balance broken | Never |
| Linear dB slider (equal pixel spacing across −12..+6 range) | Trivial to implement | Perceived gain changes feel uneven — most drag happens near 0dB | Acceptable for MVP if range is small (−6..+6); avoid for wider ranges |
| Hardcode gain offset range to ±12dB without testing | Saves iteration | Clipping at high positive values; user confusion | Never without a clipping warning |
| Skip `ServiceState` extension for gain offset | Faster service work | UI slider shows wrong value after reconnect | Never |
| Push local project history to GitHub without auditing | Saves 30 minutes | Permanent secret exposure; requires force-push history rewrite | Never |
| Skip `.gitignore` for `local.properties` | One fewer step | Build fails on fresh clone; contributor frustration | Never |
| Create public repo immediately instead of private-first | Slightly faster | Cannot revert if secrets found during verification | Never |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| `BalanceMapper.toGainDb()` + gain offset | Call mapper separately, then add offset in a second `setInputGainbyChannel` call | Replace `BalanceMapper.toGainDb()` call sites with a new `computeChannelGains(balance, offset)` that returns the combined pair |
| `BalanceRepository` + new gain offset key | Use colon-separated MAC in key name | Use `mac.replace(":", "_")` consistently, prefix `gain_offset_`, default 0f |
| `ServiceState` data class | Forget to add `currentGainOffset` field | Extend data class; update every `copy()` call in the service; update ViewModel observation |
| `onStartCommand` `seed_balance` action | Sends balance only; gain offset not re-applied | Either add a `seed_gain` action or rename to `seed_device_settings` and carry both values |
| Compose `NavHost` FAQ route | `navigate("faq")` without `launchSingleTop` | Add `launchSingleTop = true`; FAQ is a leaf screen, never needs stacking |
| GitHub `.gitignore` | Commit `local.properties` assuming it has no secrets | Always exclude; document the required manual creation step in README |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Calling `setInputGainbyChannel` on every slider drag event (continuous) | Excessive JNI calls on slider move; potential audio glitches | Apply gains only on `onValueChangeFinished` / slider release, not `onValueChange` | Immediately — slider drags generate many events per second |
| DataStore write on every slider drag | Disk writes on every frame; ANR risk on slow devices | Write to DataStore only on slider release, same as gain apply | Immediately on fast slider moves |
| Recompute `computeChannelGains` inside composition (no `remember`) | Recomputes on every recomposition | Derive gains in ViewModel, not in `@Composable` | Whenever the parent composable recomposes for unrelated reasons |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| `local.properties` committed to git history | SDK path exposure (low risk) but establishes bad habit; path may reveal username | Add to `.gitignore` before first commit; verify with `git log -- local.properties` |
| Keystore file committed if release signing was ever configured | Anyone can sign APKs with the same certificate identity | `git log --all -- "*.jks" "*.keystore"` before going public; use `git filter-repo` to remove if found |
| Hardcoded debug SHA-1 in source comments | Low risk for this app (no server-side validation) | Grep history for SHA patterns before making repo public |
| `gradle.properties` with signing credentials | Credentials exposed | Exclude from repo; document that this file is not needed for debug builds (sideload only) |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Linear position to linear dB mapping on gain slider | Slider feels coarse at high boost, too sensitive near 0 — but for a ±6dB or ±12dB range this is less noticeable than for wide ranges | For a ±12dB range, linear mapping is acceptable; label tick marks at −12, −6, 0, +6, +12 dB |
| No numeric dB readout next to slider | User does not know what "gain offset" means or what value they set | Show current value as text (e.g. "−3 dB") beside the slider; update in real time on drag |
| Gain slider resets to 0 when navigation returns to device list | User thinks their setting was lost | Read from DataStore / `ServiceState`; initialise slider with saved value on composition |
| Gain offset label says "Volume" without "dB" unit | User expects phone volume control behaviour — confuses offset with absolute volume | Label clearly as "Gain offset" or "Volume trim" with the dB unit always visible |
| FAQ screen accessible only via deep navigation — no obvious entry point | User never discovers FAQ | Add a settings/info icon button in the top app bar of `DeviceListScreen`; do not put FAQ behind a hamburger menu |
| "Open source" link in FAQ opens browser to 404 (repo not yet public when FAQ is coded) | Embarrassing UX on first run | Write the FAQ screen after the GitHub repo is live and verified public, or use a placeholder URL that redirects |

---

## "Looks Done But Isn't" Checklist

- [ ] **Gain combining:** Verify that a device with balance L+50% AND gain −6dB produces L=−6dB, R=−36dB — not two separate applies that overwrite each other.
- [ ] **Gain offset persistence:** Disconnect BT device, close app, reopen app, reconnect device — gain slider must show saved value, audio must have offset applied.
- [ ] **Clipping test:** Set gain to +6dB, play audio at maximum system volume — verify no distortion (or document expected behaviour if distortion occurs).
- [ ] **DataStore key audit:** No `gain_offset_` key starts with `balance_`; `getAllDevicesFlow()` still returns exactly the same device count as before.
- [ ] **Navigation back stack:** Open FAQ 5 times in a row via the nav button — Back must return to device list in one press.
- [ ] **ServiceState sync:** Service applies +3dB gain on reconnect; ViewModel's observed state carries +3dB; UI slider initialises to +3dB position.
- [ ] **Git history clean:** `git log --all --oneline -- local.properties "*.jks" "*.keystore"` returns zero results before the first push.
- [ ] **Fresh clone build:** Delete `local.properties`, run Gradle sync — error message is exactly the sdk.dir error documented in README; no other missing file.
- [ ] **FAQ URL works:** GitHub repo URL in FAQ resolves to a public page, not a 404 or redirect-to-login.
- [ ] **`setInputGainbyChannel` error handling:** Trigger DP recreation (by opening a competing audio effect app), then change balance — verify no crash and gains are applied correctly after recovery.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Separate apply calls discovered after implementation | LOW | Introduce `computeChannelGains()`, replace all call sites; no API or data model change needed |
| Clipping distortion from high gain — user feedback | LOW | Cap slider range in XML/Compose `valueRange`; existing values above cap may need migration |
| Secret found in git history after going public | HIGH | Rotate the secret (if applicable); use `git filter-repo` to rewrite history; force-push; notify all cloners; make repo private during rewrite |
| DataStore key collision creating ghost device entries | MEDIUM | Add a one-time migration to remove malformed keys; update key function; release new build |
| FAQ screen URL broken after repo URL changes | LOW | Update the FAQ string resource; no data migration needed |
| Gain slider shows 0 on reconnect (ServiceState not extended) | LOW | Extend `ServiceState`, rebuild — no persistent data change |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Gain combining (separate calls overwriting) | Phase 1 (gain offset) | Unit test `computeChannelGains(50f, -6f)` == `(−6f, −36f)`; ear test balance + offset together |
| Gain offset causing clipping | Phase 1 (gain offset) | Define slider range constraint; test at max positive gain + max system volume |
| DataStore key collision | Phase 1 (gain offset) | `getAllDevicesFlow()` device count unchanged after adding gain keys |
| `ServiceState` missing gain offset | Phase 1 (gain offset) | Check that ViewModel exposes current gain offset after BT reconnect |
| `setInputGainbyChannel` UnsupportedOperationException | Phase 1 (gain offset) | Verify try-catch wraps all apply calls; test recovery path with competing audio app |
| Git history secrets | Phase 3 (GitHub) | `git log --all -- local.properties "*.jks"` must return empty |
| README build instructions wrong | Phase 3 (GitHub) | Delete `local.properties`, run fresh Gradle sync, follow README |
| Navigation back stack accumulation | Phase 2 (FAQ) | Open FAQ multiple times; Back returns to device list in one press |
| FAQ URL broken | Phase 2 (FAQ) — defer URL insertion | Write FAQ text without hardcoded URL; insert URL as last step of Phase 3 |
| Gain slider showing wrong value on reconnect | Phase 1 (gain offset) + Phase 2 (FAQ/UI) | BT reconnect test with non-zero saved gain offset |

---

## Sources

- [Android Developers: DynamicsProcessing reference](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing) — `setInputGainbyChannel` is a setter, not accumulator (HIGH confidence)
- [VLC commit: catch UnsupportedOperationException in DynamicsProcessing](https://www.mail-archive.com/vlc-commits@videolan.org/msg67101.html) — real-world confirmation of setInputGainbyChannel throwing on recreation (MEDIUM confidence)
- [CodePath: Storing Secret Keys in Android](https://guides.codepath.org/android/Storing-Secret-Keys-in-Android) — `.gitignore` patterns for Android (HIGH confidence)
- [DEV Community: Android open source app secure build config](https://dev.to/ivanshafran/android-open-source-app-secure-build-config-38gi) — contributor build config patterns (MEDIUM confidence)
- [GitHub Docs: Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository) — `git filter-repo` workflow (HIGH confidence)
- [DEV Community: How to think about .gitignore for Android Studio](https://dev.to/vast-cow/how-to-think-about-gitignore-for-android-studio-and-a-standard-practical-setup-9n5) — standard Android `.gitignore` setup (MEDIUM confidence)
- [dr-lex.be: Programming Volume Controls](https://www.dr-lex.be/info-stuff/volumecontrols.html) — logarithmic vs linear gain perception (HIGH confidence — widely cited audio engineering reference)
- [Droidcon 2025: Common pitfalls in Jetpack Compose navigation](https://www.droidcon.com/2025/07/04/common-pitfalls-in-jetpack-compose-navigation/) — `launchSingleTop`, back stack growth (MEDIUM confidence)
- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation) — `launchSingleTop` flag documentation (HIGH confidence)
- Project `POC-RESULTS.md` — confirmed `setInputGainbyChannel` is an absolute setter; all-stages-false config mandatory (HIGH confidence — first-hand validation on target device)
- Project `AudioBalanceService.kt` — current service architecture showing single-value balance apply pattern to extend (first-hand)
- Project `BalanceRepository.kt` — existing DataStore key naming convention (first-hand)
- Project `AppNavigation.kt` — existing two-route navigation structure to extend (first-hand)

---
*Pitfalls research for: Android Bluetooth audio balance controller — v1.1 milestone*
*Researched: 2026-04-07*
