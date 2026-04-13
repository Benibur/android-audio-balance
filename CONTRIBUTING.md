# Contributing to Android Audio Balance

Thank you for your interest in contributing. This document explains how to build and test the project locally, the commit convention used, and how to open a pull request.

## Prerequisites

- **JDK 17** — [Eclipse Temurin](https://adoptium.net/) recommended
- **Android SDK** with API 35 (compileSdk) and API 26 (minSdk) installed
- A physical or virtual device running Android 8 (API 26) or higher
- **ADB** available in your PATH for sideload testing

## Build & test locally

```bash
./gradlew :app:testDebugUnitTest       # unit tests
./gradlew :app:lintDebug                # lint
./gradlew :app:assembleDebug            # debug APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

All three commands should pass before submitting a PR. If you see a lint crash related to `NonNullableMutableLiveDataDetector` / `IncompatibleClassChangeError`, that is a known AGP 8.7.x issue and is already suppressed in `app/build.gradle.kts`.

## Commit convention

This project uses [Conventional Commits](https://www.conventionalcommits.org). Every commit message must start with one of these prefixes followed by an optional scope in parentheses:

| Prefix | When to use |
|--------|-------------|
| `feat` | New feature or user-visible behaviour |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `chore` | Tooling, dependencies, CI config |
| `refactor` | Code cleanup with no behaviour change |
| `test` | Test additions or changes |

**Examples from this repo's history:**

```
feat(04-01): add DeviceEntry data class replacing Triple return type
fix(04-02): compose gain offset + balance into single applyGains() call
docs(06-02): add README, LICENSE, CHANGELOG, CONTRIBUTING
```

## Pull requests

Please **open an issue before starting a large PR** so we can discuss scope and avoid duplicate effort. Small fixes (typos, clear bugs) can go straight to a PR without prior discussion.

Each PR should:
- Target the `main` branch
- Pass all unit tests and lint (`./gradlew :app:testDebugUnitTest :app:lintDebug`)
- Include a clear description of what changed and why

## Reporting bugs

Use the [bug report issue template](.github/ISSUE_TEMPLATE/bug_report.md). Include your device model, Android version, app version, and logcat output if available.
