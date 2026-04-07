# Phase 4: Gain Offset - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Per-device dB gain offset slider that attenuates audio globally (both channels equally), persisted per MAC address, and auto-applied alongside balance on every BT connect. Covers GAIN-01 through GAIN-05.

</domain>

<decisions>
## Implementation Decisions

### Slider range & scale
- Range: -12 dB to 0 dB (attenuation only, no boost)
- Step size: 1 dB (12 discrete positions)
- No snap behavior needed — 0 dB is already a discrete step
- Label format: "Min volume adjustment: -3 dB" (shows "0 dB" when no attenuation)

### Slider placement & UX
- Below the balance slider in DeviceCard — both visible at once, card gets taller
- Same visual style as balance slider (consistent look), differentiated by label only
- Label: "Min volume adjustment" (not "Volume" or "Gain")

### Gain composition
- Balance and gain offset MUST be composed into a single `setInputGainbyChannel` call per channel
- Formula: `channelGainDb = balanceChannelDb + gainOffsetDb` (gain offset applied equally to both channels)
- All 4 existing call sites (`seed_balance`, `reset_audio_only`, `applyDeviceBalance`, `resetBalanceToCenter`) must use a single `applyGains(balance, gainOffset)` helper
- No separate DP calls — the API overwrites, not accumulates

### Notification format
- Claude's Discretion — show gain offset in notification when non-zero, choose the clearest format

### Data model
- DataStore key: `gain_offset_${mac}` (same pattern as `balance_${mac}`)
- Replace `Triple<String, Float, Boolean>` with `DeviceEntry` data class carrying: mac, balance, autoApply, gainOffset
- New intent action `seed_gain_offset` following the `seed_balance` pattern
- autoApply toggle gates auto-apply of gain (same as balance), but manual slider actions always apply

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### DynamicsProcessing & Audio
- `.planning/phases/01-audioeffect-poc/POC-RESULTS.md` — Validated DP config, session 0 behavior, gain per channel API
- `.planning/research/ARCHITECTURE.md` — Integration points, gain composition formula, component change table
- `.planning/research/PITFALLS.md` — Gain combining overwrite risk, clipping concerns, DataStore key discipline

### Existing Service & UI
- `app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt` — 4 call sites for setInputGainbyChannel (lines 108-109, 123-124, 265-266, 278-279), notification formatting (lines 360-368)
- `app/src/main/java/com/audiobalance/app/data/BalanceRepository.kt` — DataStore key pattern (lines 19-21), getAllDevicesFlow Triple return (line 72)
- `app/src/main/java/com/audiobalance/app/ui/components/DeviceCard.kt` — Slider structure (line 152), where second slider goes
- `app/src/main/java/com/audiobalance/app/ui/viewmodel/DeviceListViewModel.kt` — combine flow (line 25), seed_balance intent (line 100)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DeviceCard` Slider: existing balance slider pattern (normalized 0f..1f, snap center, throttle 50ms) — gain slider follows same Compose Slider API but simpler (no snap, 12 steps)
- `seed_balance` intent pattern: exact template for `seed_gain_offset` action
- `formatNotificationText`: extend to include gain offset parameter

### Established Patterns
- DataStore key naming: `{type}_{mac.replace(":", "_")}` — gain offset follows as `gain_offset_${mac}`
- Intent-based service communication: ViewModel → Intent → Service (not bound service)
- Service StateFlow via companion object: ViewModel collects directly
- Slider override MutableStateFlow for immediate UI feedback before save

### Integration Points
- `AudioBalanceService.applyDeviceBalance()` — must compose balance + gain into single DP call
- `BalanceRepository.getAllDevicesFlow()` — must return gain offset as 4th field
- `DeviceListViewModel.uiState` combine — must include gain offset in flow
- `DeviceCard` — second Slider below balance, same callbacks pattern

</code_context>

<specifics>
## Specific Ideas

- User's primary use case: volume 1 on some BT headphones is too loud — need to attenuate below Android's minimum
- Label "Min volume adjustment" chosen to be clear to non-audio-engineers
- Card layout preview confirmed: balance slider on top, gain slider below, both in same card

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-gain-offset*
*Context gathered: 2026-04-07*
