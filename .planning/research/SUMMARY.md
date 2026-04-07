# Project Research Summary

**Project:** Android Audio Balance Controller — v1.1 milestone
**Domain:** Android Bluetooth audio processing (DynamicsProcessing, Jetpack Compose)
**Researched:** 2026-04-07
**Confidence:** HIGH

> NOTE: This supersedes the v1.0 summary (2026-04-01). v1.0 is shipped and validated.
> This summary covers only the three new v1.1 features: per-device gain offset, FAQ screen, GitHub open source.

## Executive Summary

This is an incremental v1.1 milestone on top of a fully shipped and validated v1.0 Android app. The existing stack (Kotlin 2.0.21, Compose BOM 2024.12.01, navigation-compose 2.8.5, DataStore, a ForegroundService driving DynamicsProcessing on session 0) requires zero new dependencies for any of the three new features. Two library version upgrades are recommended (Compose BOM to 2026.03.00, navigation-compose to 2.9.7) but neither is blocking. All v1.1 work is additive: extend existing patterns, do not replace them.

The three new features break cleanly by implementation complexity. Gain offset is the most technically demanding because it must be composed with the existing balance values in a single DynamicsProcessing API call — separate calls silently overwrite each other, breaking balance entirely. The FAQ screen is a stateless Compose leaf screen with no ViewModel, no new dependencies, and a single new navigation route. GitHub repo setup is pure documentation work with no code changes, but carries a non-obvious security risk: the git history must be audited for secrets before the first public push.

The recommended build order mirrors the data-dependency chain: data layer first (BalanceRepository + DeviceEntry), then service layer (applyGains helper + seed_gain_offset intent), then ViewModel/state layer, then UI (DeviceCard second slider + FaqScreen + AppNavigation route), and finally the GitHub repo (documents the completed feature set). The critical invariant throughout is that all setInputGainbyChannel calls go through one function — this is the single biggest pitfall in this milestone and cannot be worked around later without a full refactor.

## Key Findings

### Recommended Stack

The v1.0 stack is unchanged and proven. No new libraries are needed. The only recommended changes are two version bumps in `gradle/libs.versions.toml`: Compose BOM from 2024.12.01 to 2026.03.00 and navigation-compose from 2.8.5 to 2.9.7. Both are low-risk stable upgrades with no API changes, and they align the project with Android 16 (Pixel 10 test device). Navigation 3 (1.0.0-alpha10) is explicitly not recommended — it is alpha, API-unstable, and adds zero value for a 3-screen app.

**Core technologies:**
- `DynamicsProcessing` (API 28+): audio effects engine — already proven in v1.0; `setInputGainbyChannel` is the only apply path; no accumulation, absolute setter only
- `DataStore Preferences`: per-device persistence — extend with `gain_offset_XX_XX_XX` key; same store, no migration, default 0f
- `navigation-compose 2.9.7`: routing — add one `"faq"` composable route to existing NavHost; no navigation framework change
- `Compose BOM 2026.03.00`: UI framework — all UI is Compose; FaqScreen is a stateless composable with zero new dependencies
- `ForegroundService + BtA2dpReceiver`: lifecycle backbone — no lifecycle changes; only audio apply logic extended

### Expected Features

**Must have (table stakes):**
- Gain offset slider in DeviceCard (−12 dB to +6 dB or −12 dB to 0 dB — see Gaps) with real-time feedback
- Gain persisted per MAC address in DataStore — default 0f; same pattern as balance
- Gain applied on BT device connect alongside balance — applying only one is a regression
- Gain reset on disconnect — mirrors existing balance reset behavior
- Balance + gain composed atomically in a single DP write per channel — correctness requirement, not optional
- Center snap at 0 dB on gain slider release — same quality bar as balance slider
- FAQ/About screen accessible via info icon in DeviceListScreen TopAppBar
- GitHub public repo with README, MIT LICENSE, and current codebase

**Should have (differentiators):**
- Notification text includes gain when non-zero — user can verify effect state without opening app
- FAQ explains session-0 conflict behavior — sets user expectations about competing audio apps
- `DeviceEntry` data class replacing `Triple` in repository — enables clean fourth field addition

**Defer (v1.2+):**
- Export/import JSON settings
- Notification "Apply now" action
- Per-device nicknames
- Quick Settings Tile
- i18n (French or other)
- Boost above 0 dB (amplification strategy not yet designed)

### Architecture Approach

The existing MVVM + ForegroundService architecture is extended, not restructured. The key structural change is replacing the `Triple<String, Float, Boolean>` return type of `getAllDevicesFlow()` with a `DeviceEntry` data class carrying a fourth `gainOffset: Float` field — this is a compile-forced change that touches BalanceRepository, DeviceListViewModel, and DeviceUiState, but nothing outside the data flow chain. All gain math is centralized in a new private `applyGains(balance: Float, gainOffset: Float)` helper in AudioBalanceService — this is the architectural invariant that prevents the overwrite pitfall. The FAQ screen adds one leaf composable and one NavHost route. The data flow for gain offset mirrors balance exactly.

**Major components and what changes:**
1. `BalanceRepository` — add `DeviceEntry` data class; add `gainOffsetKey`, `getGainOffset`, `saveGainOffset`; update `getAllDevicesFlow()`
2. `AudioBalanceService` — extract `applyGains(balance, offset)` helper; add `seed_gain_offset` intent handler; update both existing DP call sites to use the helper
3. `DeviceListViewModel` — update combine lambda for `DeviceEntry`; add `_gainOffsetOverrides` StateFlow; add `onGainOffsetChange` / `onGainOffsetFinished` callbacks
4. `DeviceUiState` + `ServiceState` — add `gainOffset: Float = 0f` to both
5. `DeviceCard` — add gain offset slider + dB label; two new callback parameters
6. `FaqScreen` (new file) — stateless composable; `LocalUriHandler.current.openUri()` for GitHub link; no ViewModel
7. `AppNavigation` — add `"faq"` composable route with `launchSingleTop = true`; thread `onFaqClick` lambda into device_list route

### Critical Pitfalls

1. **Separate setInputGainbyChannel calls for balance and gain offset** — the second call overwrites the first silently. The only fix is one `applyGains(balance, gainOffset)` function that computes the composed per-channel dB value before any API call. There is no acceptable workaround; this must be the first implementation decision in Phase 1.

2. **Gain offset causing silent clipping** — positive gain boosts the signal above DAC ceiling without warning or exception. Constrain the slider range in the UI; display the numeric dB value always; consider a warning for positive values. Decide the upper bound (0 dB or +6 dB) before implementing the slider.

3. **Git history secrets before going public** — `local.properties`, `.jks`, `keystore.properties` may be in history. Recovery after a public push requires force-rewrite, secret rotation, and notifying all cloners. Prevention: run `git log --all --full-history -- "*.jks" "*.keystore" "keystore.properties" local.properties` before creating the repo; create as private first, verify with GitHub secret scanning, then make public.

4. **ServiceState not extended for gain offset** — if `currentGainOffset` is not added to `ServiceState`, the UI slider shows 0 after BT reconnect while audio has the correct offset. Extend `ServiceState` in Phase 1 as part of service work, not as a UI afterthought.

5. **FAQ navigation back stack growth** — `navigate("faq")` without `launchSingleTop = true` stacks duplicate FAQ entries; multiple Back presses needed to exit. Apply `launchSingleTop = true` from day one; this is a leaf screen, it should never stack on itself.

## Implications for Roadmap

Based on research, suggested 3-phase structure follows data-dependency order, with GitHub last.

### Phase 1: Gain Offset (Data + Service + ViewModel + UI)
**Rationale:** All gain-related layers (data, service, ViewModel, UI) are tightly coupled and should be implemented together to avoid a half-baked state where the apply function can overwrite itself. Implementing in dependency order within the phase (data layer → service layer → ViewModel → UI) is essential. Define `computeChannelGains()` and the slider range constraint as the very first steps before writing any UI or service code.
**Delivers:** Per-device gain offset fully functional — persisted, applied on connect, reset on disconnect, real-time slider feedback, combined with balance in a single DP write, center snap, numeric dB label.
**Addresses:** All P1 gain features from FEATURES.md.
**Avoids:** Pitfalls 1 (separate calls overwriting), 2 (clipping range), 4 (ServiceState), 5 (DataStore key collision), 8 (UnsupportedOperationException on DP recreation).

### Phase 2: FAQ Screen
**Rationale:** No code dependencies on Phase 1 (FAQ is purely navigation + static content). Logically, however, the FAQ should link to the live GitHub repo URL, which requires Phase 3 to be complete or nearly complete. The recommended approach: implement FAQ in Phase 2 with a placeholder URL, and insert the real URL as the final step of Phase 3.
**Delivers:** Static FAQ/About screen accessible from DeviceListScreen TopAppBar; explains app behavior, session-0 conflict risk, open source intent; clickable GitHub link.
**Uses:** Existing navigation-compose, `LocalUriHandler.current.openUri()`, `Icons.Outlined.Info` (all already on classpath).
**Avoids:** Pitfall 6 (back stack accumulation — use `launchSingleTop = true`).

### Phase 3: GitHub Public Repo
**Rationale:** Pure documentation; no code dependencies. Must follow Phases 1 and 2 so the README documents the actual completed feature set. History audit is the mandatory first step — before creating the GitHub repository, not after.
**Delivers:** Public repo at `github.com/Benibur/android-audio-balance` with README (what/why/build/ADB install/known limitations), MIT LICENSE, complete source; FAQ screen URL updated to the live repo.
**Avoids:** Pitfall 3 (git history secrets — audit before pushing), Pitfall 4 (README build instructions — verify against a hypothetical fresh clone).

### Phase Ordering Rationale

- Phase 1 has the highest complexity and the most pitfall surface area; doing it first lets Phase 2 be a clean, low-risk finish.
- Phase 2 is independent of Phase 1 at the code level but logically depends on Phase 1 being feature-complete (FAQ describes the full app including gain offset).
- Phase 3 is strictly last because the README should document the final feature set and the FAQ screen needs the live repo URL.
- Within Phase 1, build order is enforced by compile-time dependencies: `DeviceEntry` before service changes, service changes before ViewModel changes, ViewModel before UI.

### Research Flags

No phase requires a `/gsd:research-phase` call during planning. Research files contain sufficient implementation detail to proceed directly to roadmap and plan creation.

Phases with standard patterns (all phases):
- **Phase 1 (Gain Offset):** All APIs already in use in v1.0. Composition math is standard dB arithmetic. Implementation patterns are fully documented in STACK.md and ARCHITECTURE.md with code-level detail.
- **Phase 2 (FAQ):** Stateless Compose screen + one navigation route. Completely standard patterns. No unknowns.
- **Phase 3 (GitHub):** File creation + git audit commands. All steps documented in PITFALLS.md. No research needed.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Direct codebase audit of all 21 source files + official Android API docs; version compatibility confirmed |
| Features | HIGH | Gain API proven in v1.0 POC; feature set is minimal and fully scoped; FAQ and GitHub patterns are standard |
| Architecture | HIGH | Composition math is sound dB arithmetic; DP setter semantics confirmed in AOSP source and v1.0 POC |
| Pitfalls | HIGH | DynamicsProcessing setter semantics confirmed; git secrets workflow from GitHub official docs; navigation pitfalls from official Compose nav docs |

**Overall confidence:** HIGH

### Gaps to Address

- **Gain slider upper bound:** STACK.md recommends −12 dB to +10 dB; FEATURES.md recommends −12 dB to 0 dB (attenuation only). These differ on whether positive gain (boost) is exposed at all. The 0 dB cap is safer and matches the stated use case (normalizing loud headphones). Resolve as a design decision at the start of Phase 1 planning before implementing the slider range. Recommendation: start with 0 dB cap; can be extended to +6 dB in a future release if users request amplification.
- **UnsupportedOperationException on DP recreation:** The VLC real-world reference provides MEDIUM confidence. The existing service already catches RuntimeException on DP creation; extending that catch to the `applyGains()` call sites is low-cost and should be included in Phase 1. Validate during Phase 1 implementation by testing the recovery path with a competing audio app.
- **FAQ URL timing:** If Phase 2 (FAQ) is coded before Phase 3 (GitHub) is live, the GitHub URL must be a placeholder. Track this explicitly to avoid shipping a 404 link. Insert the real URL as the final step of Phase 3.

## Sources

### Primary (HIGH confidence)
- [DynamicsProcessing API reference](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing) — setInputGainbyChannel signature, absolute setter semantics
- [DynamicsProcessing.java source (AOSP)](https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/audiofx/DynamicsProcessing.java) — confirmed no Java-level clamping; setInputGainAllChannelsTo exists
- [Navigation release notes](https://developer.android.com/jetpack/androidx/releases/navigation) — 2.9.7 confirmed stable (January 2026)
- [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) — 2026.03.00 confirmed latest stable
- [GitHub Docs: Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository) — git filter-repo workflow
- [Android Developers: Navigation with Compose](https://developer.android.com/develop/ui/compose/navigation) — launchSingleTop flag documentation
- Project codebase direct audit (all 21 source files, 2026-04-07) — existing patterns, key naming, service architecture, BalanceMapper, AppNavigation
- Project `POC-RESULTS.md` — confirmed setInputGainbyChannel is an absolute setter on target device; all-stages-false config mandatory

### Secondary (MEDIUM confidence)
- [VLC commit: DynamicsProcessing UnsupportedOperationException catch](https://www.mail-archive.com/vlc-commits@videolan.org/msg67101.html) — real-world DP recreation race condition on new instances
- [Navigation 3 announcement](https://android-developers.googleblog.com/2025/05/announcing-jetpack-navigation-3-for-compose.html) — confirmed alpha-only (1.0.0-alpha10); do not migrate
- [Droidcon 2025: Common pitfalls in Jetpack Compose navigation](https://www.droidcon.com/2025/07/04/common-pitfalls-in-jetpack-compose-navigation/) — back stack growth patterns
- [DEV Community: Android open source app secure build config](https://dev.to/ivanshafran/android-open-source-app-secure-build-config-38gi) — contributor build config patterns, local.properties documentation
- [dr-lex.be: Programming Volume Controls](https://www.dr-lex.be/info-stuff/volumecontrols.html) — logarithmic vs linear gain perception reference

### Tertiary (LOW confidence)
- [GitHub open source license discussion](https://github.com/orgs/community/discussions/131758) — MIT as standard for personal Android apps (community consensus, not authoritative source)

---
*Research completed: 2026-04-07*
*Ready for roadmap: yes*
