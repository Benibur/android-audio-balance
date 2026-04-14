# Project Retrospective: Bluetooth Audio Balance Controller

Living document. Each milestone gets its own section. Cross-milestone trends at the bottom.

---

## Milestone: v1.1 — Gain Offset + FAQ + Open Source

**Shipped:** 2026-04-14
**Phases:** 3 (Phase 4, 5, 6)
**Plans:** 8

### What Was Built

- **Phase 4 (Gain Offset):** Per-device dB slider (-12..0) wired into the same DynamicsProcessing call as balance. Persisted via DataStore by MAC. Notification text updated to reflect non-zero gain.
- **Phase 5 (FAQ Screen):** Static 6-item accordion screen with TopAppBar back nav, footer link to GitHub repo, troubleshooting copy on AudioEffect session 0. Reachable via info icon in the device list TopAppBar with `launchSingleTop=true`.
- **Phase 6 (Open Source):** Public repo `Benibur/android-audio-balance` with MIT license, CI workflow (assembleDebug + tests + lint), README/CHANGELOG/CONTRIBUTING, app launcher icon at 5 densities + 512px store icon, release v1.1 with debug APK as asset, history rewritten with `git filter-repo` to remove `local.properties` before first push.

### What Worked

- **UI-SPEC before planning (Phase 5):** the design contract caught accordion-vs-list ambiguity early, kept the planner from re-deciding.
- **Research uncovered 2 bloquant findings before planning Phase 6:** `local.properties` in history + the `NullSafeMutableLiveData` lint crash root cause. Both would have surfaced as broken first-push otherwise.
- **Wave parallelism in Phase 6:** plans 06-01 (lint+CI) and 06-02 (community files) ran in parallel without conflict — no file overlap.
- **Checkpoint discipline in Phase 6:** filter-repo and gh repo create were both gated by human-verify checkpoints. The dry-run audit showed exactly what would be rewritten before the destructive op ran.
- **Inline UI tweaks outside the GSD workflow:** the slider polish + app icon work between phases 5 and 6 was small enough to commit directly without spawning a new phase. Faster than over-formalizing.

### What Was Inefficient

- **Plan 06-01 underestimated lint scope:** plan said "1 detector to disable", reality was 4 detectors + lint-baseline.xml for 9 pre-existing errors. Plan should have included a "run lint to discover all crashes + baseline" exploratory step.
- **Plan 06-03 expected 4 screenshots, reality was 2:** the device card has no expand/collapse mechanic — `device-card-expanded.png` and `device-list.png` would have been identical. Caught at intake checkpoint and renegotiated, but the plan should have read the actual UI before specifying screenshot files.
- **Build cache confusion:** at one point `./gradlew installDebug` reported success but the APK on disk was stale. Required a `--rerun-tasks` to force a fresh build. Worth investigating whether to add a clean step in CI/release flows.
- **Background bash for long builds:** the 6min `--rerun-tasks` build pushed itself to background unexpectedly, breaking the conversational flow. For long Gradle ops, default to `run_in_background` explicitly.

### Patterns Established

- **Discuss → UI-SPEC (when frontend) → Research → Plan → Verify → Execute** sequence works well for app phases. Skip UI-SPEC for pure infra phases like Phase 6.
- **Audit dry-run before any history-rewriting op.** filter-repo gated by a checkpoint that runs the audit greps and presents results — never auto-approved.
- **Two-step plans for irreversible remote ops** (Phase 6 plan 04 split go-public from smoke-test). Both checkpoints, both human-only.
- **Inline UI polish commits between phases** (icon, slider tweaks) instead of forcing them into the next planned phase. Keeps the GSD workflow about substantive work, not micro-tweaks.

### Key Lessons

- **Read the actual UI before specifying screenshot files in a plan.** Plans that hardcode artifact filenames need to verify those artifacts make sense in the current UI state.
- **`git filter-repo` is safe BEFORE the first remote exists, dangerous after.** Phase 6 nailed this timing — never push, always rewrite first if needed, push once, never rewrite again.
- **Lint baselines are acceptable when the underlying issue is upstream.** Disabling 4 detectors with a comment + baseline file is better than blocking the milestone on an AGP/Kotlin version dance.
- **GitHub CI cost is zero for this workload** — full pipeline (build + tests + lint) finishes in <2 min on hosted runners, free for public repos.
- **The 14-day Play Store closed testing requirement** is the real reason to defer Play Store to a separate milestone, not the technical work itself.

### Cost Observations

- **Model mix:** mostly Sonnet for executors and verifiers; Opus for the planner only. Researchers used Sonnet. Felt right — planning is where reasoning compounds, execution is mostly mechanical.
- **Sessions:** ~3-4 active sessions over 2 days, including the inter-phase UI polish work.
- **Notable:** the 06-04 agent hit a 500 API error mid-spawn and had to be retried. Idempotent pre-flight checks (`gh auth status`, `git remote -v`) made the retry trivial — second attempt picked up cleanly.

---

## Cross-Milestone Trends

### Throughput

| Milestone | Phases | Plans | LOC delta | Calendar days |
|-----------|--------|-------|-----------|---------------|
| v1.0 MVP | 4 | 12 | +1718 | ~3 |
| v1.1 GO+FAQ+OSS | 3 | 8 | +659 | ~2 active (spread over 7 calendar) |

### Workflow conventions

- Phases 0-3 (v1.0): used full GSD workflow including Nyquist validation strategy. Effective for foundational architecture decisions.
- Phases 4-6 (v1.1): same workflow + UI-SPEC for the FAQ phase. Validation strategy moved to mostly manual for static UI screens (no math/data transformations to unit-test).
- Inline polish commits between phases: started in v1.1 (slider tweaks, app icon). Worth keeping.

### Tech debt to revisit

- 4 lint detectors disabled + lint-baseline.xml — revisit when AGP publishes a fix for the Kotlin Analysis API mismatch.
- Pre-existing `MissingPermission` and `NewApi` errors grandfathered in baseline — should be reviewed when planning v1.2 or v2.0.
- Foreground service `android:exported="true"` with TODO comment to set false before distribution — still pending.
