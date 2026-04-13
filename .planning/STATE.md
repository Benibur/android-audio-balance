---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Gain Offset + FAQ + Open Source
status: unknown
stopped_at: Phase 6 context gathered
last_updated: "2026-04-13T06:58:27.307Z"
progress:
  total_phases: 3
  completed_phases: 2
  total_plans: 4
  completed_plans: 4
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-07)

**Core value:** Quand je connecte mes écouteurs Bluetooth, la balance stéréo que j'ai configurée s'applique automatiquement — sans intervention manuelle.
**Current focus:** Phase 05 — faq-screen

## Current Position

Phase: 05 (faq-screen) — COMPLETE
Plan: 2 of 2 (all plans complete)

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [v1.1 research]: Gain slider upper bound — start with 0 dB cap (attenuation only); extend to +6 dB later if needed
- [v1.1 research]: All setInputGainbyChannel calls must go through a single `applyGains(balance, gainOffset)` helper — separate calls silently overwrite each other
- [v1.1 research]: Replace `Triple` return type with `DeviceEntry` data class to carry the fourth `gainOffset` field
- [v1.1 research]: FAQ GitHub URL can be a placeholder during Phase 5; real URL inserted as final step of Phase 6
- [Phase 04-gain-offset]: Wave 0 tests use standalone helper functions in the test file — no mocking needed for pure math/string contracts
- [Phase 04-gain-offset]: applyGains() is the exclusive owner of all setInputGainbyChannel calls in AudioBalanceService
- [Phase 04-gain-offset plan 02]: Used .value snapshot for _gainOffsetOverrides inside combine lambda — avoids changing combine arity; sufficient for transient UI state
- [Phase 05-faq-screen]: faq_expand_description and faq_collapse_description added as separate strings (21 total) to satisfy UI-SPEC Accessibility Contract per-state contentDescriptions
- [Phase 05-faq-screen]: launchSingleTop = true on FAQ navigate() call prevents duplicate back-stack entries from rapid taps

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 6]: Git history must be audited for secrets before repo goes public — run audit as the first step of Phase 6, not the last
- [Phase 4]: Extend existing RuntimeException catch to `applyGains()` call sites to handle DP recreation race; validate by testing with a competing audio app

## Session Continuity

Last session: 2026-04-13T06:58:27.299Z
Stopped at: Phase 6 context gathered
Resume file: .planning/phases/06-open-source/06-CONTEXT.md
