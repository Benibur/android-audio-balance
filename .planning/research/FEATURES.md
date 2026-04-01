# Feature Research

**Domain:** Android Bluetooth audio balance controller (personal use, per-device stereo compensation)
**Researched:** 2026-04-01
**Confidence:** MEDIUM — ecosystem surveyed via WebSearch + official Android docs; competitor features via Play Store listings and app documentation

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Per-device balance profile storage | Core premise — balance must survive app restarts and reconnections | LOW | Stored by MAC address in SharedPreferences or Room; MAC address is stable per device |
| Persistent balance slider (left/right) | The only control users need; range -1.0 to +1.0 | LOW | Single float per device; UI is a slider centered at 0 |
| Auto-apply on Bluetooth A2DP connect | The app's core value — no manual intervention needed | MEDIUM | BroadcastReceiver on `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`; complications with background restrictions on API 31+ |
| Foreground service with persistent notification | Required by Android to stay alive in background; users expect it when auto-apply exists | MEDIUM | Use `connectedDevice` foreground service type (API 34+ requires explicit type); notification must show device name and current balance |
| Auto-start on device boot | Without this, the app fails to apply balance after reboots — users notice | LOW | `BOOT_COMPLETED` receiver; on API 34+ cannot start foreground service directly from `BOOT_COMPLETED` — must use CompanionDeviceManager exemption or alternative pattern |
| Device list with known headphones | Users need to see and manage their configured devices | LOW | Simple list with device name, MAC, balance value, and enable/disable toggle |
| Enable/disable toggle per device | Users may want to temporarily skip auto-apply for a specific device | LOW | Single boolean flag per device profile |
| Global enable/disable | Kill switch to deactivate all auto-apply without uninstalling | LOW | Single SharedPreference flag; notification action to toggle |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Export/import settings as JSON | Survive app reinstalls, share config across devices; Precise Volume and Wavelet don't prominently offer this | LOW | Serialize device profiles to JSON file; use `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` for file picker |
| Quick Settings Tile (QS tile) | Toggle on/off without opening app; Android-Audio-Channel-QS-Tile proves users want this for audio channel toggles | MEDIUM | `TileService` implementation; tile state reflects global enable/disable |
| "Test" balance button on device profile screen | Preview balance immediately without waiting for reconnect | LOW | Apply balance transiently while on the settings screen; restore on exit if user cancels |
| Named device nicknames | Users may pair the same model twice or have confusing default BT names | LOW | Extra text field per profile; display nickname over BT device name |
| "Apply now" action in notification | One-tap re-apply in case auto-apply was missed (e.g. race condition at connect) | LOW | PendingIntent action in the ongoing notification |
| Last-applied timestamp per device | Confirm the auto-apply actually ran; useful for debugging | LOW | Store `lastAppliedAt: Long` per profile |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Volume control | Natural complement to balance; Precise Volume bundles it | Separate Android audio stream concern; adds permission complexity; out of explicit project scope | Defer to a separate project/milestone explicitly noted in PROJECT.md |
| Multi-band equalizer | Power users want full EQ; Wavelet and Poweramp EQ do this | Completely different audio effect API surface; requires per-session EQ management and Wavelet-style DUMP-permission ADB workaround for system-wide coverage; would dwarf the balance use case in complexity | Not built; users who need EQ can use Wavelet alongside this app |
| AutoEQ database integration | Automated headphone correction sounds appealing | Requires shipping or fetching a 5000+ entry database; totally separate value proposition; Wavelet already does this well | Not built; the problem being solved is factory imbalance, not frequency response |
| Cloud sync of profiles | Backup and cross-device sync convenience | Requires auth, backend, privacy policy; complete overkill for a personal-use tool | Use JSON export/import instead |
| Per-app balance profiles | Different balance for Spotify vs YouTube seems useful | AudioEffect session management per app requires the Wavelet DUMP-permission ADB approach and continuous session monitoring; vastly increases complexity | System-wide balance only; single coefficient per device |
| Wired headphone support | Some users have wired headphones with imbalance | Different audio routing detection (`ACTION_HEADSET_PLUG`); different profile key (no MAC address); would blur the Bluetooth-specific scope | Explicitly out of scope; project is BT-only |
| Root-based AudioFlinger patching | Guaranteed system-wide effect | Requires root; disqualifies app from normal deployment; no one runs it | Use AudioEffect session 0 approach (works on most devices without root) |
| Play Store publication | Wider reach | Involves Play Store policies, privacy declarations for Bluetooth permissions, potential review issues with AudioEffect / Accessibility use; adds ongoing maintenance burden | USB direct deployment as explicitly decided in PROJECT.md |

## Feature Dependencies

```
[Global enable/disable toggle]
    └──requires──> [Foreground service]
                       └──requires──> [Auto-apply on BT connect]
                                          └──requires──> [Per-device profile storage]

[Auto-start on boot]
    └──requires──> [Foreground service]
    └──requires──> [Per-device profile storage]

[Quick Settings Tile]
    └──enhances──> [Global enable/disable toggle]
    └──requires──> [Foreground service] (tile needs a service to signal)

[Export/import JSON]
    └──requires──> [Per-device profile storage]

["Apply now" notification action]
    └──requires──> [Foreground service]
    └──requires──> [Per-device profile storage]

[Test balance button]
    └──requires──> [Per-device profile storage]
    └──requires──> [Audio effect application logic] (shared with auto-apply)

[AudioEffect session 0 balance application] ──conflicts──> [Per-app AudioEffect sessions]
    (Session 0 = global mix; cannot combine with per-session approach)
```

### Dependency Notes

- **Auto-apply on BT connect requires per-device profile storage:** The BroadcastReceiver reads the MAC address of the connected device and looks up the stored balance coefficient — no storage means no lookup.
- **Global enable/disable requires foreground service:** The toggle is surfaced in the persistent notification; the service controls whether auto-apply logic runs.
- **Auto-start on boot requires foreground service:** BOOT_COMPLETED receiver starts the foreground service, which then listens for BT events. On API 34+ (Android 14+), starting a foreground service of type `connectedDevice` from a background receiver requires the `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` permission via CompanionDeviceManager.
- **Test balance button shares audio logic with auto-apply:** Both call the same `applyBalance(coefficient)` function — the test button just does it inline in the UI without waiting for BT connect.
- **AudioEffect session 0 conflicts with per-app sessions:** Using session 0 applies to the global output mix. This is intentional for system-wide balance but means you cannot simultaneously run per-session EQ effects independently.

## MVP Definition

### Launch With (v1)

Minimum viable product — what's needed to validate the concept.

- [ ] Per-device profile storage (MAC address → balance float) — core data model, everything else depends on it
- [ ] Persistent balance slider UI per device — the only user-facing control
- [ ] BT A2DP connect/disconnect detection — the trigger for auto-apply
- [ ] AudioEffect session 0 balance application — the effect itself (or validated alternative if session 0 proves unreliable on test device)
- [ ] Foreground service with minimal notification — required to survive background; shows current state
- [ ] Auto-start on boot — without this the app is useless after reboot; must solve API 34+ restriction
- [ ] Device list screen — view and manage known devices
- [ ] Per-device enable/disable toggle — needed to handle devices user doesn't want auto-applied
- [ ] Global enable/disable — essential kill switch

### Add After Validation (v1.x)

Features to add once core is working.

- [ ] Export/import JSON — add once profiles are stable and proven useful; low effort payoff
- [ ] "Apply now" notification action — add if race condition on BT connect is observed in testing
- [ ] Last-applied timestamp — add for debugging during early use, low cost
- [ ] Named device nicknames — add if BT device names prove confusing in practice

### Future Consideration (v2+)

Features to defer until product-market fit is established.

- [ ] Quick Settings Tile — useful but more complex plumbing (TileService lifecycle); defer until v1 is stable
- [ ] "Test balance" button — nice UX addition, defer to keep v1 scope minimal
- [ ] Any volume control features — explicitly out of scope per PROJECT.md

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Per-device profile storage | HIGH | LOW | P1 |
| Balance slider UI | HIGH | LOW | P1 |
| BT A2DP connect detection | HIGH | MEDIUM | P1 |
| AudioEffect balance application | HIGH | MEDIUM (feasibility risk) | P1 |
| Foreground service | HIGH | MEDIUM | P1 |
| Auto-start on boot | HIGH | LOW-MEDIUM (API 34 caveat) | P1 |
| Device list screen | HIGH | LOW | P1 |
| Per-device enable/disable | HIGH | LOW | P1 |
| Global enable/disable | HIGH | LOW | P1 |
| Export/import JSON | MEDIUM | LOW | P2 |
| "Apply now" notification action | MEDIUM | LOW | P2 |
| Named nicknames | LOW | LOW | P2 |
| Last-applied timestamp | LOW | LOW | P2 |
| Quick Settings Tile | MEDIUM | MEDIUM | P3 |
| Test balance button | LOW | LOW | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

## Competitor Feature Analysis

| Feature | Wavelet (headphone EQ) | Precise Volume 2.0 | Our Approach |
|---------|----------------------|-------------------|--------------|
| Per-device profiles | Yes — auto-loads on headphone connect | Yes — device-specific presets | Yes — MAC-keyed balance profiles |
| Balance/pan control | Yes — Channel Balance slider | Yes — L/R Balance slider | Yes — primary and only audio control |
| Auto-apply on BT connect | Yes — automatic profile load on connect | Yes — Bluetooth automation trigger | Yes — core feature |
| System-wide audio effect | Yes — via DUMP permission (ADB step required) | Yes — via Accessibility Service (privacy concern) | Targeting AudioEffect session 0 (no extra permissions needed, but feasibility TBD) |
| Foreground service | Yes | Yes | Yes |
| Boot auto-start | Yes | Yes | Yes |
| Export/import settings | Not prominently documented | Not prominently documented | Yes — JSON, planned for v1.x |
| EQ bands | 9-band graphic EQ + AutoEQ | 10-band + parametric | None — balance only |
| Scope | Full headphone EQ suite | Full audio Swiss Army knife | Single-purpose: per-device stereo balance |
| Quick Settings Tile | Not documented | Not documented | v2+ consideration |
| Notification action | Not documented | Not documented | "Apply now" in v1.x |
| Complexity for user | Medium (DUMP permission setup) | High (many features, accessibility service) | Low (slider + BT connect = done) |

**Competitive position:** Both Wavelet and Precise Volume are feature-heavy general-purpose audio tools. This app is intentionally minimal — one slider per Bluetooth device, auto-applied, nothing else. That simplicity is the differentiator for users who only need balance correction and don't want to configure a full EQ suite.

## Critical Technical Feasibility Note

The most important non-feature to research before building features is **whether AudioEffect session 0 reliably controls stereo balance on Android 10+ without root**. This is explicitly flagged in PROJECT.md as the primary technical risk. Features are moot if the audio effect cannot be applied system-wide. The feasibility research phase must resolve this before any audio-effect-dependent features are scoped in detail.

Known approaches (confidence: MEDIUM, based on community reports):
1. `AudioEffect` on session 0 with a custom effect UUID — works on many devices, deprecated in intent but not removed
2. Wavelet-style DUMP permission via ADB — reliable but requires one-time ADB setup
3. `AccessibilityService` — some apps use this but it's a UX friction point (scary permission dialog) and Google may restrict it further

## Sources

- [Wavelet headphone equalizer — Features documentation](https://pittvandewitt.github.io/Wavelet/Features/)
- [Precise Volume 2.0 — Features documentation](https://precisevolume.phascinate.com/docs/features/)
- [Why Android Equalizer Apps Don't Work with All Media Players — Esper](https://www.esper.io/blog/android-equalizer-apps-inconsistent)
- [Foreground service types — Android Developers](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Restrictions on starting a foreground service from background — Android Developers](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Bluetooth Device Equalizer — Google Play](https://play.google.com/store/apps/details?id=com.cac.bluetoothequalizer)
- [Precise Volume 2.0 + Equalizer — Google Play](https://play.google.com/store/apps/details?id=com.phascinate.precisevolume)
- [Wavelet: headphone equalizer — Google Play](https://play.google.com/store/apps/details?id=com.pittvandewitt.wavelet)
- [XDA Forums — Android Audio Balance Settings discussion](https://xdaforums.com/t/audio-balance-settings.4138405/)
- [Android Authority — Best equalizer apps for Android 2025](https://www.androidauthority.com/best-equalizer-apps-android-761240/)
- [Android-Audio-Channel-QS-Tile — GitHub](https://github.com/VarunS2002/Android-Audio-Channel-QS-Tile)

---
*Feature research for: Android Bluetooth audio balance controller*
*Researched: 2026-04-01*
