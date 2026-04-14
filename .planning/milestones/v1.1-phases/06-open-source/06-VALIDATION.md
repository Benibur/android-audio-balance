---
phase: 6
slug: open-source
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-13
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for publishing the repo publicly.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 (unit tests) + shell/git commands (infra tasks) |
| **Config file** | `app/build.gradle.kts` + `.github/workflows/build.yml` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` |
| **Estimated runtime** | ~90 seconds local |

---

## Sampling Rate

- **After every code task commit** (lint fix, build config): `./gradlew :app:lintDebug`
- **After workflow file task**: YAML lint via `yq eval` or `actionlint` (best-effort)
- **After git history rewrite**: re-run audit grep patterns to confirm cleanliness
- **Before `/gsd:verify-work`**: full suite green + audit clean
- **Max feedback latency**: ~90s

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|-------------------|--------|
| TBD | lint-fix | 1 | OSS prereq | compile+lint | `./gradlew :app:lintDebug` | ⬜ pending |
| TBD | filter-repo | 1 | OSS-03 | shell audit | `git log --all --diff-filter=A --name-only \| grep -iE 'local\\.properties\|\\.jks\|keystore\\.properties'` → expect empty | ⬜ pending |
| TBD | readme+license+meta | 2 | OSS-01, OSS-02 | file existence + content grep | `test -f README.md LICENSE CHANGELOG.md CONTRIBUTING.md .github/ISSUE_TEMPLATE/bug_report.md` | ⬜ pending |
| TBD | CI workflow | 2 | OSS-02 | YAML + actionlint | `yq eval '.jobs.build.steps' .github/workflows/build.yml` | ⬜ pending |
| TBD | repo create + push | 3 | OSS-01 | gh CLI | `gh repo view Benibur/android-audio-balance --json visibility,name` | ⬜ pending |
| TBD | tag + release + APK | 3 | OSS-01 | gh CLI | `gh release view v1.1 --json assets` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

Unit test regression coverage provided by existing tests: `GainOffsetSliderTest`, `ApplyGainsTest`, `NotificationTextTest`. No new unit test files needed — phase 6 is publishing/infra, not new code features. The lint fix itself is verified by `./gradlew :app:lintDebug` completing without the `IncompatibleClassChangeError` crash.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Repo is publicly accessible | OSS-01 | Requires external check via incognito browser | Open `https://github.com/Benibur/android-audio-balance` in a private window → confirm repo loads without login |
| README is readable and covers all 4 sections | OSS-02 | Human reading | Load README on GitHub → confirm What/Requirements/Build/Limitations sections render correctly, screenshots display |
| Debug APK in release installs + runs | OSS-01 | Requires device install | Download APK from GitHub Release v1.1 → `adb install` → launch → smoke test balance slider |
| CI workflow runs green on first push | OSS-02 | GitHub-side execution | Check Actions tab after push → confirm build job passes (green checkmark), badge in README shows passing |
| Git history reveals no secrets | OSS-03 | Human confirmation of scan output | Run audit grep commands → inspect zero-match results manually to confirm no false negatives |

---

## Validation Sign-Off

- [ ] Lint fix verified (`./gradlew :app:lintDebug` succeeds)
- [ ] Filter-repo cleanup verified (audit greps return empty for `local.properties`, `*.jks`, keystores)
- [ ] Files present: README.md, LICENSE, CHANGELOG.md, CONTRIBUTING.md, .github/ISSUE_TEMPLATE/bug_report.md, .github/workflows/build.yml
- [ ] Repo public and accessible on GitHub
- [ ] Release v1.1 tagged with debug APK asset
- [ ] CI run green on first push
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
