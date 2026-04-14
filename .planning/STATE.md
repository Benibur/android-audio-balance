---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Gain Offset + FAQ + Open Source
status: unknown
stopped_at: Completed 06-04 fully — smoke test approved by user, README icon follow-up committed (4edfc95, 6c4913b). Phase 06 complete. Milestone v1.1 shipped.
last_updated: "2026-04-14T16:58:52.871Z"
progress:
  total_phases: 3
  completed_phases: 3
  total_plans: 8
  completed_plans: 8
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-07)

**Core value:** Quand je connecte mes écouteurs Bluetooth, la balance stéréo que j'ai configurée s'applique automatiquement — sans intervention manuelle.
**Current focus:** Phase 06 — open-source

## Current Position

Phase: 06 (open-source) — COMPLETE
Plan: 4 of 4 (all plans complete)

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
- [Phase 06]: docs/screenshots/.gitkeep placeholder committed so README image references are tracked before Plan 03 delivers real screenshots
- [Phase 06-open-source]: Disabled 4 Compose/lifecycle lint checks (NullSafeMutableLiveData, FrequentlyChangingValue, RememberInComposition, AutoboxingStateCreation) — all share AGP 8.7.3 + Kotlin 2.x KaCallableMemberCall incompatibility
- [Phase 06-open-source]: Added lint-baseline.xml for 9 pre-existing MissingPermission/NewApi errors — keeps lint signal for new code while CI starts green
- [Phase 06 plan 03]: Screenshots reduced from 4 to 2 — device-card-expanded dropped (no expand/collapse UI), permissions dropped (redundant with Android conventions); user decision
- [Phase 06 plan 03]: git filter-repo --force rewrote all 134 commits to remove local.properties; 133 commits remain on branch main
- [Phase 06 plan 04]: APK built from detached HEAD at v1.1 tag before gh release create — guarantees upload matches tagged source (SHA256 verified)
- [Phase 06 plan 04]: git push --tags origin used explicitly alongside git push --all origin — both v1.0 and v1.1 now on origin
- [Phase 06 plan 04]: Full CHANGELOG.md used as GitHub Release notes body for v1.1

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 6]: Git history must be audited for secrets before repo goes public — run audit as the first step of Phase 6, not the last
- [Phase 4]: Extend existing RuntimeException catch to `applyGains()` call sites to handle DP recreation race; validate by testing with a competing audio app

## Session Continuity

Last session: 2026-04-14T16:30:00Z
Stopped at: Completed 06-04 fully — smoke test approved by user, README icon follow-up committed (4edfc95, 6c4913b). Phase 06 complete. Milestone v1.1 shipped.
Resume file: None
