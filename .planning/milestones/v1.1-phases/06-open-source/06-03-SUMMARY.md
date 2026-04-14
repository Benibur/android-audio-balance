---
phase: 06-open-source
plan: "03"
subsystem: git-history / repository
tags: [security, git-filter-repo, history-rewrite, branch-rename, screenshots]
dependency_graph:
  requires: ["06-01", "06-02"]
  provides: ["clean-git-history", "main-branch", "screenshots"]
  affects: ["README.md", "docs/screenshots"]
tech_stack:
  added: ["git-filter-repo v2.38.0"]
  patterns: ["git history rewrite with --invert-paths"]
key_files:
  created:
    - docs/screenshots/device-list.png
    - docs/screenshots/faq.png
  modified:
    - README.md (gallery reduced from 4 to 2 screenshots)
    - .gitignore (verified; /local.properties already present, no change needed)
decisions:
  - "Screenshots reduced from 4 to 2 — device-card-expanded dropped (no expand/collapse in current UI), permissions dropped (redundant with Android conventions); user decision in Task 1"
  - "filter-repo --force used because this is the dev working repo, not a fresh clone; no remote exists so rewrite is safe"
  - "Branch renamed master → main before screenshots commit so all new commits land on main directly"
metrics:
  duration: "~30 minutes (tasks 1+2 in prior session, task 3 now)"
  completed: "2026-04-14"
  tasks_completed: 3
  files_modified: 3
  commits_rewritten: 134
  post_rewrite_commits: 133
  new_head_sha: 2e3473391333e39c3b632cec25348b4821831b39
---

# Phase 06 Plan 03: History Rewrite + Branch Rename Summary

**One-liner:** Rewrote all 134 commits with git-filter-repo to excise local.properties, renamed master → main, committed 2 user-captured screenshots referenced by README.md gallery.

## What Was Done

### Task 1: Screenshots (prior session, commit 8c8a35b → rewritten to part of 2e34733 timeline)
User captured 2 PNG screenshots on Pixel 10 (scope reduced from 4):
- `docs/screenshots/device-list.png` (104 KB, 452×1018)
- `docs/screenshots/faq.png` (84 KB, 452×1018)

`device-card-expanded.png` and `permissions.png` were dropped — the device card has no expand/collapse UI state and the permissions screenshot was deemed redundant with Android conventions.

### Task 2: Dry-run and user approval (prior session)
- Confirmed no remote (`git remote -v` returned empty)
- Dry-run showed `local.properties` would be removed
- User explicitly approved: "approved — rewrite history"

### Task 3: Execution (this session)

**Pre-rewrite state:** 134 commits on branch `master`, `local.properties` present in HEAD and history since commit `8244194`.

**Step A — .gitignore:** `/local.properties` was already present on line 3. No change needed.

**Step B — filter-repo run:**
```
git filter-repo --path local.properties --invert-paths --force
```
Output: `Parsed 134 commits ... New history written in 0.04 seconds`

**Step C — local.properties absent from all history:**
```
git log --all --full-history -- local.properties
(empty output — PASS)
```

**Step D — local.properties absent from HEAD tree:**
```
git ls-tree -r --name-only HEAD | grep -x local.properties
(no match — PASS)
```

**Step E — Full audit results:**

Audit 1 — Content scan for actual secrets:
```
git log --all -p | grep -iE "keystore|\.jks|password=|secret=|api[_-]?key|token=" | head -50
```
Result: Matches found are all documentation text in planning files (README.md audit instructions,
RESEARCH.md notes describing what keystore risks look like). Zero actual secret values — no
`password=`, `api_key=`, `token=` assignments. **PASS**

Audit 2 — Sensitive files ever committed:
```
git log --all --diff-filter=A --name-only --pretty=format:"" | sort -u | grep -iE "local\.properties|\.jks|keystore\.properties|\.env"
(empty — PASS)
```

Audit 3 — HEAD tree (first 30 entries, confirming no sensitive files present):
```
.github/ISSUE_TEMPLATE/bug_report.md
.github/workflows/build.yml
.gitignore
.planning/HANDOFF.json
...
```
No `local.properties`, no `.jks`, no `keystore.properties` in tree.

**Step F — Branch rename:**
```
git branch -m master main
git branch --show-current → main
git branch --list master   → (empty)
```

**Post-rewrite state:** 133 commits on branch `main`, new HEAD SHA: `2e3473391333e39c3b632cec25348b4821831b39`

Note: commit count went from 134 to 133 because filter-repo collapsed one empty commit created when local.properties was the only change in that commit.

## Acceptance Criteria Verification

| Criterion | Result |
|-----------|--------|
| `git log --all --full-history -- local.properties` returns 0 lines | PASS (0 lines) |
| `git ls-tree -r --name-only HEAD \| grep -x local.properties` exits 1 | PASS (no match) |
| Sensitive files scan returns empty | PASS |
| Content scan: no actual secret values | PASS (only documentation text) |
| `git branch --show-current` = `main` | PASS |
| `git branch --list master` returns empty | PASS |
| `git remote -v` returns empty | PASS |
| Screenshots committed (device-list.png, faq.png) | PASS |

## Deviations from Plan

### Scope change (Task 1 — prior session, user decision)

**[User Decision] Screenshots reduced from 4 to 2**
- Found during: Task 1
- Issue: `device-card-expanded.png` — no expand/collapse state exists in current UI; `permissions.png` — user determined redundant with Android conventions
- Fix: README.md gallery updated to 2-image layout; plan's 4-PNG acceptance criteria relaxed by user
- Files modified: README.md, docs/screenshots/ (2 PNGs instead of 4)
- Commit: 2e34733 (post-rewrite SHA of what was 8c8a35b)

### Auto-fix: None required

Plan executed exactly as written for Task 3.

## New HEAD SHA (for Plan 04 first public push / v1.1 tag target)

```
2e3473391333e39c3b632cec25348b4821831b39
```

This is the commit that will become the v1.1 release tag target when Plan 04 creates the GitHub remote and pushes.
