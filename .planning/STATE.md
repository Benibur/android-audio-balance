---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: "Gain Offset + FAQ + Open Source"
status: ready_to_plan
stopped_at: null
last_updated: "2026-04-07"
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-07)

**Core value:** Quand je connecte mes écouteurs Bluetooth, la balance stéréo que j'ai configurée s'applique automatiquement — sans intervention manuelle.
**Current focus:** Phase 4 — Gain Offset

## Current Position

Phase: 4 of 6 (v1.1) — Gain Offset
Plan: 0 of ? in current phase
Status: Ready to plan
Last activity: 2026-04-07 — v1.1 roadmap created; phases 4-6 defined

Progress (v1.1): [░░░░░░░░░░] 0% (0/3 phases complete)

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [v1.1 research]: Gain slider upper bound — start with 0 dB cap (attenuation only); extend to +6 dB later if needed
- [v1.1 research]: All setInputGainbyChannel calls must go through a single `applyGains(balance, gainOffset)` helper — separate calls silently overwrite each other
- [v1.1 research]: Replace `Triple` return type with `DeviceEntry` data class to carry the fourth `gainOffset` field
- [v1.1 research]: FAQ GitHub URL can be a placeholder during Phase 5; real URL inserted as final step of Phase 6

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 6]: Git history must be audited for secrets before repo goes public — run audit as the first step of Phase 6, not the last
- [Phase 4]: Extend existing RuntimeException catch to `applyGains()` call sites to handle DP recreation race; validate by testing with a competing audio app

## Session Continuity

Last session: 2026-04-07
Stopped at: v1.1 roadmap created; ready to plan Phase 4 (Gain Offset)
Resume file: None
