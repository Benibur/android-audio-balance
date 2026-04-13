# Phase 6: Open Source - Research

**Researched:** 2026-04-12
**Domain:** GitHub repository publication, Android CI, git history audit
**Confidence:** HIGH (most findings verified against official docs)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**README**
- Language: English only
- Mandatory sections: What it does (BT left/right imbalance + screenshot gallery), Requirements (Android 8+, BT A2DP only, USB debugging), Build & sideload (`./gradlew assembleDebug` + `adb install`), Known limitations (AudioEffect session 0 conflicts, no wired/speaker, no Play Store)
- Screenshots: 4+ screens from user's Pixel, placed in `docs/screenshots/`
- Badges: 3 at top — MIT license, Android 8+ platform, GitHub Actions CI build status

**Repo metadata**
- Description/tagline: "Fix left/right audio imbalance on your Bluetooth headphones"
- Topics: `android`, `bluetooth`, `audio`, `accessibility`, `hearing`, `audioeffect`, `dynamicsprocessing`
- Issues and PRs: enabled

**License**
- MIT, copyright holder "Benibur", year 2026

**CONTRIBUTING.md**
- Build steps (gradle + adb) + Conventional Commits convention + "open an issue before a large PR"

**Additional meta files**
- `.github/ISSUE_TEMPLATE/bug_report.md` — device model, Android version, app version, steps, expected vs actual
- `CHANGELOG.md` — starting with `v1.1 — Initial public release`

**CI (GitHub Actions)**
- Scope: build debug APK + unit tests + lint
- Triggers: push on `main` + PRs
- Blocking prerequisite: fix lint bug BEFORE activating lint in CI
- Workflow file: `.github/workflows/build.yml`

**Git history audit**
- Scan + manual review before ANY push
- Three audit commands specified in CONTEXT.md
- Expected result: no match → safe to push; any match → present to user before deciding

**First push strategy**
- Full history preserved (`git push --all`)
- `.planning/` stays public (no secrets, demonstrates GSD workflow)
- Tag `v1.1` on HEAD after push
- Release: attach `app-debug.apk` to GitHub release v1.1

### Claude's Discretion
- Exact README wording (tone, sentences)
- Detailed CHANGELOG content (exhaustiveness)
- Screenshot gallery layout (markdown table vs HTML)
- Exact job/step names in CI workflow
- Badge order in README

### Deferred Ideas (OUT OF SCOPE)
- CODE_OF_CONDUCT.md
- Signing of release APK (production keystore)
- Emulator screenshots
- i18n of README
- Play Store listing
- GitHub Wiki / Discussions
- Release automation (semantic-release, changelog auto)
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| OSS-01 | GitHub repo `Benibur/android-audio-balance` created with MIT license | gh CLI one-liner creates repo and pushes; MIT text from opensource.org |
| OSS-02 | README with app description, Android 8+ requirement, build instructions, session 0 warning | Standard markdown README structure; badge shield.io URLs documented below |
| OSS-03 | Git history audited for secrets before first public push | **BLOCKING FINDING**: `local.properties` was committed in phase 00 commit `8244194` — it only contains `sdk.dir=/home/ben/Android/Sdk` (no password), but IS tracked and in HEAD; must decide: git-filter-repo removal vs accept as non-secret; audit commands documented below |
</phase_requirements>

---

## Summary

Phase 6 is a documentation and DevOps phase — no new app code. The deliverables are: GitHub repository creation, community files (README, LICENSE, CHANGELOG, CONTRIBUTING, issue template), a CI workflow, and a tagged release with APK attached.

The critical technical blocker is the pre-existing lint crash (`NonNullableMutableLiveDataDetector` / `IncompatibleClassChangeError`). This crash appears when AGP 8.7.x runs lint against lifecycle library versions whose lint jar was compiled against an older Kotlin Analysis API. The fix is a one-liner in `app/build.gradle.kts` disabling the specific check. Do not attempt to resolve by upgrading AGP or lifecycle versions — those upgrades risk breaking the working app in scope.

A secondary blocker was discovered during research: `local.properties` is tracked in git history (committed in phase 00 as `sdk.dir=/home/ben/Android/Sdk`). The file only contains a local SDK path — not a password or secret — but it leaks the user's home directory path (`/home/ben/Android/Sdk`). The recommended decision is documented in the pitfalls section. The file should be removed from HEAD and from history before pushing publicly.

**Primary recommendation:** Fix lint in `app/build.gradle.kts`, remove `local.properties` from HEAD and history with git-filter-repo, create and push the repo with `gh repo create --source=. --public --push`, then attach the APK via `gh release create v1.1`.

---

## Standard Stack

### Core Actions (GitHub Actions workflow)

| Action | Version | Purpose | Why Standard |
|--------|---------|---------|--------------|
| actions/checkout | v4 | Checks out the repository | Official GitHub action, v4 required for Git 2.18+ |
| actions/setup-java | v4 | Installs JDK (Temurin/Zulu distribution) | Official; v4 added native Gradle cache support |
| gradle/actions/setup-gradle | v4 | Gradle wrapper + dependency caching | Official Gradle org action; supercedes gradle-build-action |
| actions/upload-artifact | v4 | Upload built APK as workflow artifact | Official; v4 required (v3 deprecated end of 2024) |
| softprops/action-gh-release | v2 | Create GitHub release and attach files | Most widely used release action; v3 released 2026-04-12 (Node 24), v2 stable |

**Version verification (2026-04-12):**
- `actions/setup-java` latest: v4 (v5.2.0 tag available, `@v4` resolves to latest v4.x)
- `gradle/actions/setup-gradle` latest: v4 (v6.1.0 tag available — note: `gradle/actions` repo uses v6 tags but the action path is `gradle/actions/setup-gradle@v4`)
- `softprops/action-gh-release` latest stable: v2 (v2.2.1); v3.0.0 released 2026-04-12 (Node 24 runtime, breaking if org has Node 20 policy)

**Recommendation: use `softprops/action-gh-release@v2`** until v3 stabilizes.

### CLI Tools (local execution)

| Tool | Install | Purpose |
|------|---------|---------|
| gh CLI | already installed (verify: `gh --version`) | Create repo, create release |
| git-filter-repo | `pip3 install git-filter-repo` | Remove `local.properties` from full history |

**Installation:**
```bash
# Verify gh CLI
gh --version

# Install git-filter-repo if needed
pip3 install git-filter-repo
```

---

## Architecture Patterns

### Workflow File Structure

```
.github/
├── workflows/
│   └── build.yml          # CI: build + test + lint
└── ISSUE_TEMPLATE/
    └── bug_report.md      # Bug report template

docs/
└── screenshots/           # 4+ PNG screenshots from user's Pixel

README.md                  # Root
LICENSE                    # MIT text verbatim
CHANGELOG.md               # Starts with v1.1
CONTRIBUTING.md            # Build steps + commit convention + PR policy
```

### Pattern 1: GitHub Actions Android Build Workflow

**What:** Checkout → JDK → Gradle setup (with caching) → unit tests → lint → assemble APK → upload artifact

**When to use:** Every push to main and every PR

**Verified configuration (2026-04-12):**
```yaml
# Source: official gradle/actions docs + actions/setup-java v4 docs
name: Android CI

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Make gradlew executable
        run: chmod +x ./gradlew

      - name: Run unit tests
        run: ./gradlew test

      - name: Run lint
        run: ./gradlew lint

      - name: Build debug APK
        run: ./gradlew assembleDebug

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

**JDK note:** Project uses `JavaVersion.VERSION_17` in `compileOptions` and `jvmTarget = "17"`. Temurin (Eclipse Adoptium) is the standard free OpenJDK distribution for CI.

**Gradle cache note:** `gradle/actions/setup-gradle@v4` automatically handles caching of `~/.gradle/caches` and `~/.gradle/wrapper`. Do NOT add a separate `actions/cache` step for Gradle — it creates redundancy and cache key conflicts. The `cache: gradle` option in `actions/setup-java` is also redundant when using `setup-gradle`.

### Pattern 2: GitHub Release with APK Attachment (tag-triggered)

**What:** Create a GitHub release when a tag is pushed; attach the debug APK.

**Two approaches — for this project, use gh CLI (simpler, one manual v1.1 release):**

```bash
# Source: gh CLI manual
# After git push --all and git push --tags:

# Build the APK locally first
./gradlew assembleDebug

# Create release with APK attached
gh release create v1.1 \
  app/build/outputs/apk/debug/app-debug.apk \
  --title "v1.1 — Initial Public Release" \
  --notes-file CHANGELOG.md
```

**If adding a tag-triggered CI release job (optional enhancement for later PRs):**
```yaml
# Source: softprops/action-gh-release v2 docs
- name: Release
  uses: softprops/action-gh-release@v2
  if: github.ref_type == 'tag'
  with:
    files: app/build/outputs/apk/debug/app-debug.apk
    body_path: CHANGELOG.md
```

### Pattern 3: Create GitHub Repo from Existing Local Repo

```bash
# Source: GitHub Docs + gh CLI manual
# Run from project root
gh repo create android-audio-balance \
  --public \
  --source=. \
  --remote=origin \
  --description "Fix left/right audio imbalance on your Bluetooth headphones" \
  --push

# Push all branches and tags (--push only pushes current branch)
git push --all origin
git push --tags origin
```

**Note:** `gh repo create --push` only pushes the current branch. Use `git push --all origin` afterward to push ALL branches (including `master` if the default isn't `main`). Then `git push --tags origin` for the v1.1 tag.

**Note on branch name:** The repo currently has a `master` branch. GitHub defaults to `main`. The repo description in CONTEXT.md references pushing to `main`. Clarify: either rename local branch `master` → `main` before push, or configure GitHub repo default branch to `master` after creation.

### Pattern 4: Shields.io Badge URLs

```markdown
[![MIT License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Android 8+](https://img.shields.io/badge/Android-8%2B-brightgreen.svg?logo=android)](https://developer.android.com/about/versions/oreo)
[![CI Build](https://github.com/Benibur/android-audio-balance/actions/workflows/build.yml/badge.svg)](https://github.com/Benibur/android-audio-balance/actions/workflows/build.yml)
```

The CI badge URL must match the exact workflow filename (`build.yml`) and repo path.

### Anti-Patterns to Avoid

- **Using `actions/cache` for Gradle alongside `gradle/actions/setup-gradle`:** Causes redundant cache entries and key collisions. Use only `setup-gradle`.
- **Using `gradle/gradle-build-action`:** Deprecated alias; now delegates to `gradle/actions/setup-gradle`. Use the canonical path directly.
- **Using `softprops/action-gh-release@v3` immediately:** Released 2026-04-12 (same day as this research); requires Node 24 runtime; not yet battle-tested.
- **Running `./gradlew lint` before fixing the lint crash:** CI will be permanently red. Fix first.
- **Pushing before git history audit:** Once pushed public, history is indexed by crawlers within minutes.

---

## BLOCKING: Lint Crash Fix

### Root Cause (HIGH confidence — verified against multiple sources)

`NonNullableMutableLiveDataDetector` (also surfaced as `NullSafeMutableLiveData` lint check ID) crashes with `IncompatibleClassChangeError` when:
- AGP 8.7+ runs the lint jar from `lifecycle-livedata-core-ktx-lint`
- The lint jar was compiled against an older Kotlin Analysis API where `KaCallableMemberCall` was a class
- JetBrains changed `KaCallableMemberCall` from a class to an interface (binary incompatibility)

**Current project versions:**
- AGP: `8.7.3` (from `libs.versions.toml`)
- lifecycle: `2.8.7` (from `libs.versions.toml` — `lifecycleRuntimeKtx` and `lifecycleViewmodelCompose`)
- Kotlin: `2.0.21`

The issue was introduced with lifecycle 2.9.0-rc01's interaction with Kotlin 2.x analysis API changes. The project uses `2.8.7` which should be stable, but AGP 8.7.x's lint runner still triggers the crash because it loads the lint jar from lifecycle-livedata-core-ktx.

### Fix: Disable the Specific Lint Check

The targeted fix — no version upgrades, no risk to working app:

```kotlin
// In app/build.gradle.kts — add inside android { } block
android {
    // ... existing config ...

    lint {
        disable += "NullSafeMutableLiveData"
    }
}
```

**Why not upgrade lifecycle to 2.10.0?** The fix would require testing the app end-to-end after the upgrade. That's out of scope for a documentation/release phase. The lint disable is the correct minimal fix here.

**Why not upgrade AGP?** AGP upgrades often require Gradle version bumps and plugin compatibility checks. Risky in scope.

---

## BLOCKING: `local.properties` in Git History

### Finding (CRITICAL)

Running the audit commands from CONTEXT.md reveals:

```
git log --all --diff-filter=A --name-only | sort -u | grep -iE "local\.properties|\.jks|keystore\.properties|\.env"
→ MATCH: local.properties
```

`local.properties` was committed in commit `8244194` ("chore(00-01): install JDK 17 and Android SDK, add local.properties") and has never been removed. It is still present in `HEAD`.

**Content of the committed `local.properties`:**
```
sdk.dir=/home/ben/Android/Sdk
```

**Risk assessment:** This is NOT a secret (no password, no API key). It is a local machine path. However:
1. It leaks the user's home directory structure (`/home/ben/`)
2. It makes the build fail for other developers who clone (they get a wrong SDK path)
3. It violates Android best practices (local.properties is in .gitignore by convention for a reason)
4. It IS listed in `.gitignore` as `/local.properties` — meaning it was intentionally added with `git add -f` at some point

**Recommended action:** Remove from history using git-filter-repo + remove from HEAD + add to .gitignore cleanly.

### Removal Procedure

```bash
# Step 1: Install git-filter-repo
pip3 install git-filter-repo

# Step 2: Verify what will be removed (dry run)
git filter-repo --path local.properties --invert-paths --dry-run

# Step 3: Remove from all history (REWRITES ALL 121 COMMITS — local only, repo not yet pushed)
git filter-repo --path local.properties --invert-paths

# Step 4: Verify removal
git log --all --full-history -- local.properties
# Should return empty

# Step 5: Verify file is gone from HEAD
git ls-tree -r --name-only HEAD | grep local.properties
# Should return empty

# Step 6: .gitignore already has /local.properties — confirm it's there
grep local.properties .gitignore
# Should show: /local.properties
```

**IMPORTANT:** `git filter-repo` rewrites all commit SHAs. Since this repo has never been pushed to a remote yet (first push is part of this phase), there are NO remote refs to reconcile. This is the ideal moment to clean history.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Gradle caching in CI | Custom `actions/cache` Gradle steps | `gradle/actions/setup-gradle@v4` | Gradle org's own action has smarter cache key logic, deduplication, and wrapper checksum validation |
| GitHub release creation | curl to GitHub API manually | `gh release create` CLI | Authentication, asset upload, rate limiting already handled |
| File-from-history removal | `git filter-branch` script | `git filter-repo` | filter-branch is deprecated, 10-100x slower, has known safety issues |
| Secret scanning | grep on HEAD only | `git log --all -p | grep` | HEAD-only scan misses files deleted in earlier commits |

---

## Common Pitfalls

### Pitfall 1: Lint CI Red from Day One (KNOWN BLOCKER)
**What goes wrong:** CI runs `./gradlew lint` → `IncompatibleClassChangeError` in `NonNullableMutableLiveDataDetector` → CI job fails permanently
**Why it happens:** AGP 8.7.x lint runner loads lifecycle's lint jar compiled against an older Kotlin Analysis API where `KaCallableMemberCall` was a class; JetBrains changed it to an interface
**How to avoid:** Add `lint { disable += "NullSafeMutableLiveData" }` to `app/build.gradle.kts` BEFORE enabling CI
**Warning signs:** Any local `./gradlew lint` run that crashes with `UNEXPECTED TOP-LEVEL EXCEPTION` or `IncompatibleClassChangeError`

### Pitfall 2: `local.properties` Leaked in History
**What goes wrong:** Public repo exposes `sdk.dir=/home/ben/Android/Sdk` — leaks home directory path, breaks clones for contributors
**Why it happens:** File was committed in phase 00 before `.gitignore` was effective for it
**How to avoid:** Run `git filter-repo --path local.properties --invert-paths` before first push; only safe to do pre-push since all SHAs are rewritten
**Warning signs:** `git ls-tree -r --name-only HEAD | grep local.properties` returns a match

### Pitfall 3: `git push --all` Not Pushing Tags
**What goes wrong:** `git push --all` pushes branches but NOT tags; v1.1 release tag is missing from remote
**Why it happens:** Tags require explicit push with `--tags` or individual tag name
**How to avoid:** Always follow `git push --all origin` with `git push --tags origin`

### Pitfall 4: Branch Name Mismatch (master vs main)
**What goes wrong:** Local repo uses `master` (visible in git status output), but CI workflow and GitHub defaults target `main`; CI never triggers
**Why it happens:** Git default branch was `master` historically; GitHub now defaults to `main`
**How to avoid:** Before push, decide: rename `master` → `main` (`git branch -m master main`), OR update CI workflow `on.push.branches` to list `master`, OR configure GitHub repo default branch after creation
**Recommendation:** Rename to `main` before first push — cleaner for a new public repo

### Pitfall 5: CI Badge URL Wrong on Day 1
**What goes wrong:** Badge shows "no status" or broken image if workflow hasn't run yet or branch name mismatches
**Why it happens:** Badge URL is branch-sensitive; if repo branch is `master` but badge references `main`, it shows nothing
**How to avoid:** Trigger a CI run immediately after first push; update badge URL to match actual branch name

### Pitfall 6: gh repo create `--push` Only Pushes Current Branch
**What goes wrong:** Only the current branch is pushed; other branches missing; tags not pushed
**Why it happens:** `--push` flag in `gh repo create` is equivalent to `git push origin HEAD`, not `git push --all --tags`
**How to avoid:** After `gh repo create`, run `git push --all origin && git push --tags origin`

### Pitfall 7: APK Not Built Before `gh release create`
**What goes wrong:** `gh release create v1.1 app/build/outputs/apk/debug/app-debug.apk` fails — file doesn't exist
**Why it happens:** APK is in `.gitignore`/build output, not in the repo; must be built fresh
**How to avoid:** Run `./gradlew assembleDebug` immediately before `gh release create`

---

## Code Examples

### Verified: Disable Lint Check in build.gradle.kts
```kotlin
// app/build.gradle.kts
// Source: Android Lint documentation + community verified workaround
android {
    namespace = "com.audiobalance.app"
    compileSdk = 35

    // ... existing config ...

    lint {
        disable += "NullSafeMutableLiveData"
    }
}
```

### Verified: Complete CI Workflow
```yaml
# .github/workflows/build.yml
# Source: gradle/actions docs (v6.1.0), actions/setup-java v4 docs
name: Android CI

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Make gradlew executable
        run: chmod +x ./gradlew

      - name: Run unit tests
        run: ./gradlew test

      - name: Run lint
        run: ./gradlew lint

      - name: Build debug APK
        run: ./gradlew assembleDebug

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
```

### Verified: Bug Report Issue Template
```markdown
<!-- .github/ISSUE_TEMPLATE/bug_report.md -->
---
name: Bug report
about: Report something that isn't working
title: '[BUG] '
labels: bug
assignees: ''
---

**Device model:**
(e.g. Pixel 6, Samsung Galaxy S23)

**Android version:**
(e.g. Android 13 / API 33)

**App version:**
(e.g. v1.1 — visible in Settings > Apps)

**Steps to reproduce:**
1.
2.
3.

**Expected behavior:**

**Actual behavior:**

**Additional context:**
(logcat output, other audio apps running, etc.)
```

### Verified: Git History Audit Commands
```bash
# Source: CONTEXT.md + verified as standard git forensics pattern

# 1. Scan commit CONTENTS for sensitive values
git log --all -p | grep -iE "keystore|\.jks|password=|secret=|api[_-]?key|token=" | head -50

# 2. Scan for sensitive FILES ever added (even if later deleted)
git log --all --diff-filter=A --name-only --pretty=format:"" | sort -u | grep -iE "local\.properties|\.jks|keystore\.properties|\.env"

# 3. List all files currently tracked (visual validation)
git ls-tree -r --name-only HEAD
```

### Verified: local.properties Removal
```bash
# Source: git-filter-repo official docs (newren/git-filter-repo)
pip3 install git-filter-repo
git filter-repo --path local.properties --invert-paths
```

### Verified: Repo Creation + Full History Push
```bash
# Source: gh CLI manual + GitHub Docs
gh repo create android-audio-balance \
  --public \
  --source=. \
  --remote=origin \
  --description "Fix left/right audio imbalance on your Bluetooth headphones"

# Push all branches (gh repo create --push only does current branch)
git push --all origin
git push --tags origin
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `gradle/gradle-build-action@v3` | `gradle/actions/setup-gradle@v4` | 2023 (v3 became alias) | Same outcome, but canonical path going forward |
| `actions/setup-java` with `cache: gradle` | `gradle/actions/setup-gradle` for caching | 2023 | setup-gradle has smarter cache logic |
| `git filter-branch` for history rewrite | `git filter-repo` | ~2020 | filter-branch deprecated; filter-repo is 10-100x faster |
| `actions/upload-artifact@v3` | `actions/upload-artifact@v4` | 2024 (v3 deprecated) | Breaking API changes in v4 — do not mix v3/v4 in same workflow |
| `softprops/action-gh-release@v1` | `softprops/action-gh-release@v2` | 2023 | Node 16 → Node 20 runtime |

---

## Open Questions

1. **Branch name: master vs main**
   - What we know: local repo uses `master` (per git status)
   - What's unclear: whether user wants to rename to `main` or keep `master`
   - Recommendation: rename to `main` before first push — standard convention for new OSS repos; CI workflow will reference `main`

2. **local.properties decision**
   - What we know: committed in phase 00, only contains SDK path (not a password), still in HEAD and history
   - What's unclear: whether user wants to clean history (rewrites all SHAs, changes commit IDs) or accept it
   - Recommendation: clean with git-filter-repo before push — this is the only safe moment to do so (no remote exists yet); the path leak is minor but the build-failure-for-contributors issue is real

3. **APK to attach to v1.1 release**
   - What we know: user wants "the APK currently running on the Pixel 10" attached
   - What's unclear: whether user needs to build fresh or already has the APK file
   - Recommendation: task should build fresh with `./gradlew assembleDebug` immediately before `gh release create` to ensure it's the exact build from the audited, tagged commit

---

## Validation Architecture

`nyquist_validation` is enabled (no explicit `false` in config.json).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 (junit 4.13.2) |
| Config file | none — standard Android test runner |
| Quick run command | `./gradlew test` |
| Full suite command | `./gradlew test lint assembleDebug` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Notes |
|--------|----------|-----------|-------------------|-------|
| OSS-01 | Repo exists with MIT license | manual | `gh repo view Benibur/android-audio-balance` | Cannot be automated pre-push |
| OSS-02 | README has required sections | manual | Visual inspection of rendered README | Markdown structure check |
| OSS-03 | No secrets in git history | semi-automated | Audit commands in Code Examples section | Pass = zero output from grep commands |

### Sampling Rate
- **Per task commit:** `./gradlew test` (unit tests only — fast)
- **Per wave merge:** `./gradlew test lint assembleDebug`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
None — existing test infrastructure (3 test files: `ApplyGainsTest`, `GainOffsetSliderTest`, `NotificationTextTest`) covers app logic. Phase 6 adds no new app code, so no new test files needed.

---

## Sources

### Primary (HIGH confidence)
- gradle/actions GitHub repo (v6.1.0 release, April 2026) — setup-gradle usage patterns
- actions/setup-java GitHub releases — confirmed v4 (v5.2.0 tag), Temurin distribution
- softprops/action-gh-release GitHub releases — confirmed v2.2.1 stable, v3.0.0 released 2026-04-12
- Android developer docs (lifecycle releases page) — confirmed lifecycle 2.10.0 stable, NullSafeMutableLiveData fix notes
- gh CLI manual — `gh repo create` flags, `gh release create` usage
- newren/git-filter-repo GitHub — removal command syntax
- Git log output (live audit, 2026-04-12) — confirmed `local.properties` committed in SHA `8244194`

### Secondary (MEDIUM confidence)
- Google Issue Tracker #371926651 — AGP 8.7 + navigation lint API version mismatch (confirms root cause pattern)
- Kotlin Slack archives (kotlinlang.org) — navigation-compose 2.9.0-beta02 IncompatibleClassChangeError report (May 2025)
- Android developer blog — AGP 8.7.0 release notes (October 2024)

### Tertiary (LOW confidence)
- Community workarounds for `NullSafeMutableLiveData` disable — multiple independent sources confirm `lint { disable += "NullSafeMutableLiveData" }` works

---

## Metadata

**Confidence breakdown:**
- Standard stack (actions, versions): HIGH — verified against official release pages
- Lint fix: HIGH — root cause confirmed via Google Issue Tracker + Android docs + community consensus
- local.properties finding: HIGH — confirmed via live git log on actual repo
- git-filter-repo procedure: HIGH — official docs
- Architecture patterns (CI workflow): HIGH — matches official gradle/actions + setup-java docs
- Branch name issue: MEDIUM — observed from git status, resolution depends on user preference

**Research date:** 2026-04-12
**Valid until:** 2026-05-12 (actions version pinning; gradle/actions moves fast)
