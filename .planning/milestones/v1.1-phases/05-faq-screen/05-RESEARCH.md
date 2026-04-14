# Phase 5: FAQ Screen - Research

**Researched:** 2026-04-12
**Domain:** Jetpack Compose static screen, Compose Navigation, AnimatedVisibility accordion
**Confidence:** HIGH

## Summary

Phase 5 adds a single static screen to a working Compose + Material3 app. All required primitives are already present in the project dependencies (`androidx.compose.material3`, `androidx.navigation.compose`, `androidx.material.icons.extended`). No new libraries are needed.

The implementation is purely additive: one new screen file (`FaqScreen.kt`), one new composable route in `AppNavigation.kt`, one new action icon in `DeviceListScreen.kt`, and a batch of new string resources in `strings.xml`. There is no ViewModel, no repository, no network call, and no data layer change.

The accordion pattern uses `AnimatedVisibility` with local `mutableStateOf` per item — the standard Compose idiom for independent expand/collapse state. The clickable GitHub URL pattern already exists in `PermissionScreen.kt` (it uses `Intent(ACTION_VIEW, Uri.parse(...))` via `LocalContext.current`).

**Primary recommendation:** Single plan covering all four files in one wave. No architecture risk — replicate existing patterns exactly.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **Accordion format** — 6 FAQ entries as clickable question headers that expand/collapse
- **Navigation**: info icon (ⓘ) in DeviceListScreen TopAppBar → `navController.navigate("faq")`. Back arrow → `navController.popBackStack()`.
- **Route name**: `"faq"` added as `composable("faq")` in AppNavigation.kt
- **No ViewModel** — static content only, local `remember` state per accordion item
- **All 6 FAQ questions and answers** are locked (see CONTEXT.md `## Implementation Decisions`)
- **GitHub URL placeholder**: `https://github.com/Benibur/android-audio-balance` (real repo created in Phase 6)
- **GitHub link appears twice**: inside FAQ Q3 answer AND as a footer section
- **Troubleshooting (FAQ-04)**: concise warning only — no technical explanation of session 0 / hasControl()
- **Strings in strings.xml** — all user-facing text externalized, English only

### Claude's Discretion

- Accordion animation style (expand/collapse — `AnimatedVisibility` with default fade + expand vertical)
- Exact wording of FAQ answers (following the tone: simple & direct)
- Icon choice for the info button (ⓘ vs ? vs HelpOutline — see UI-SPEC: `Icons.Outlined.Info`)
- Spacing, padding, typography within the FAQ screen (see UI-SPEC for full specification)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| FAQ-01 | User can access a FAQ screen from the device list (info icon) | Nav pattern: add `composable("faq")` in AppNavigation.kt; add `IconButton` action to DeviceListScreen TopAppBar |
| FAQ-02 | FAQ explains what the app does and why | Static string resources; 6 accordion items with Q/A pairs |
| FAQ-03 | FAQ mentions open source and links to the GitHub repo | `Intent(ACTION_VIEW, Uri.parse(...))` via LocalContext — same pattern as PermissionScreen.kt settings link |
| FAQ-04 | FAQ includes troubleshooting for AudioEffect session 0 conflicts | FAQ Q6 (concise warning, no technical detail) |
</phase_requirements>

---

## Standard Stack

### Core (already in project — no new deps needed)

| Library | Version in project | Purpose | Why Standard |
|---------|--------------------|---------|--------------|
| `androidx.compose.material3` | BOM-managed | Scaffold, TopAppBar, LazyColumn, AnimatedVisibility, Text, Icon | Project standard; all screens use it |
| `androidx.navigation.compose` | BOM-managed | NavHost / composable routes / navController | Already wires permissions → device_list |
| `androidx.compose.material.icons.extended` | BOM-managed | Icons.Outlined.* (Info, ArrowBack, ExpandMore, ExpandLess, OpenInBrowser) | Already used in DeviceListScreen and PermissionScreen |
| `androidx.compose.runtime` | BOM-managed | `remember { mutableStateOf(false) }` for accordion state | Project standard |

### Supporting (already in project)

| Library | Purpose | When to Use |
|---------|---------|-------------|
| `android.content.Intent` + `android.net.Uri` | Open URL in browser | Used in PermissionScreen.kt for Settings intent — same pattern |
| `androidx.compose.ui.platform.LocalContext` | Access context for `startActivity` | Same pattern as PermissionScreen |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `AnimatedVisibility` (default) | Custom `animateContentSize()` | AnimatedVisibility is simpler, already shipped with Material3; animateContentSize is for single-container expansion. AnimatedVisibility is the right tool here. |
| Multiple `mutableStateOf` per item | Single list of booleans in a shared state | Shared state adds complexity with zero benefit (UI-SPEC explicitly allows multiple open simultaneously). Local state is correct. |

**Installation:** No new packages required.

---

## Architecture Patterns

### Recommended Project Structure

```
app/src/main/java/com/audiobalance/app/ui/screens/
├── DeviceListScreen.kt   # ADD: info icon to TopAppBar actions
├── FaqScreen.kt          # NEW: full FAQ screen + FaqItem composable + FooterSection
└── PermissionScreen.kt   # unchanged (reference only)

app/src/main/java/com/audiobalance/app/ui/navigation/
└── AppNavigation.kt      # ADD: composable("faq") route; pass navController to DeviceListScreen

app/src/main/res/values/
└── strings.xml           # ADD: ~20 faq_* string resources
```

### Pattern 1: NavController Threading to Screens

The current `DeviceListScreen` does not receive a `navController` parameter — it is called from `AppNavigation.kt` with no arguments. To add the info icon that navigates to FAQ, the navController must be passed in.

**What to do:** Add `navController: NavController` parameter to `DeviceListScreen`. Update the `composable("device_list")` call in `AppNavigation.kt` to pass `navController`.

```kotlin
// AppNavigation.kt — updated composable block
composable("device_list") {
    DeviceListScreen(navController = navController)
}

// AppNavigation.kt — new faq route
composable("faq") {
    FaqScreen(navController = navController)
}

// DeviceListScreen.kt — updated signature
@Composable
fun DeviceListScreen(
    viewModel: DeviceListViewModel = viewModel(),
    navController: NavController
)
```

**Back navigation in FaqScreen:**
```kotlin
navController.popBackStack()
```
This is the correct idiom — no `popUpTo`, no `inclusive = true`. Those are only needed when clearing the back stack (as in permissions → device_list flow). Simple pop is correct here.

### Pattern 2: TopAppBar actions parameter

```kotlin
// Source: Material3 TopAppBar signature
TopAppBar(
    title = { Text(...) },
    actions = {
        IconButton(onClick = { navController.navigate("faq") }) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.faq_info_icon_description),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
)
```

### Pattern 3: Back arrow as navigationIcon in TopAppBar

```kotlin
TopAppBar(
    title = { Text(stringResource(R.string.faq_screen_title)) },
    navigationIcon = {
        IconButton(onClick = { navController.popBackStack() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.faq_back_description)
            )
        }
    }
)
```

Note: `Icons.AutoMirrored.Outlined.ArrowBack` is the correct import — the non-AutoMirrored variant works but the AutoMirrored version is the Material3-recommended form for directional icons.

### Pattern 4: Accordion item with AnimatedVisibility

```kotlin
@Composable
fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    val bgColor = if (expanded) MaterialTheme.colorScheme.surfaceVariant
                  else Color.Transparent

    Surface(
        color = bgColor,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 48.dp),  // accessibility touch target
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess
                                  else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
```

### Pattern 5: Clickable URL link

Reuses the exact approach from `PermissionScreen.kt`'s `openAppSettings()`:

```kotlin
val context = LocalContext.current
val url = stringResource(R.string.faq_github_url)
Text(
    text = stringResource(R.string.faq_footer_link_label),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
    textDecoration = TextDecoration.Underline,
    modifier = Modifier.clickable {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
)
```

### Anti-Patterns to Avoid

- **Passing lambda for navigation instead of navController:** The project passes `navController` directly to screens (not a `() -> Unit` lambda). Match the established pattern or both choices are fine — but be consistent. If adding `navController` to DeviceListScreen, use the same style for FaqScreen.
- **Using `navigate("faq") { popUpTo(...) }` for FAQ navigation:** Do NOT clear the back stack when navigating to FAQ. Only the permissions → device_list flow uses `popUpTo`. FAQ is a normal push navigation.
- **Hardcoding text in Composables:** All user-facing strings must go in `strings.xml`. The project enforces this consistently across existing screens.
- **Adding a ViewModel for FAQ:** The CONTEXT.md explicitly prohibits this. Static content, local `remember` state only.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Expand/collapse animation | Custom height animation | `AnimatedVisibility` | Ships with Compose; correct default transitions (fade + expand) |
| Open URL in browser | WebView fragment or custom intent assembly | `Intent(ACTION_VIEW, Uri.parse(url))` via `startActivity` | Standard Android idiom; already in PermissionScreen.kt |
| Back navigation | Custom back handler | `navController.popBackStack()` | NavHost manages the back stack; manual handling causes duplication |
| Icon assets | Custom drawable XML | `Icons.Outlined.*` from `material.icons.extended` | Already imported; `Info`, `ExpandMore`, `ExpandLess`, `ArrowBack`, `OpenInBrowser` all available |

---

## Common Pitfalls

### Pitfall 1: Back stack duplication on FAQ navigation

**What goes wrong:** Using `navigate("faq") { launchSingleTop = false }` or forgetting `launchSingleTop = true` — user taps info icon twice and gets two FAQ screens stacked.

**Why it happens:** NavHost default behavior pushes a new instance every time `navigate()` is called without `launchSingleTop`.

**How to avoid:** Use `navController.navigate("faq") { launchSingleTop = true }` on the info icon tap. This ensures only one FAQ instance exists in the back stack at a time.

**Warning signs:** Pressing back from FAQ lands on another FAQ screen instead of device list.

### Pitfall 2: navController not available in DeviceListScreen

**What goes wrong:** `DeviceListScreen` currently takes no navController. Forgetting to thread navController through the `composable("device_list")` call and the screen signature will cause a compile error or require `LocalContext`-based workarounds.

**How to avoid:** Two-step change: (1) update the `composable("device_list") { DeviceListScreen(navController = navController) }` call in AppNavigation.kt, (2) update the `DeviceListScreen` function signature to accept `navController: NavController`.

### Pitfall 3: `Icons.AutoMirrored` import confusion

**What goes wrong:** `Icons.AutoMirrored.Outlined.ArrowBack` has a different import path than standard `Icons.Outlined.*`. Forgetting the import leads to an unresolved reference.

**Import needed:**
```kotlin
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
// then use: Icons.AutoMirrored.Outlined.ArrowBack
```
Alternatively, `Icons.Outlined.ArrowBack` (non-auto-mirrored) compiles fine and is acceptable for an LTR-only app.

### Pitfall 4: Animated background color not updating

**What goes wrong:** Using `Modifier.background(color)` directly instead of a `Surface` with `color` parameter means the background color change during expand/collapse is not smoothly animated.

**How to avoid:** Wrap each accordion item in a `Surface(color = bgColor, shape = shape)`. Surface handles shape clipping and color. Optionally wrap color in `animateColorAsState` for a smooth transition.

### Pitfall 5: Missing `@OptIn(ExperimentalMaterial3Api::class)` annotation

**What goes wrong:** `TopAppBar` is still `@ExperimentalMaterial3Api` in the Material3 version used by this project. Omitting the annotation causes a compilation warning or error.

**How to avoid:** Add `@OptIn(ExperimentalMaterial3Api::class)` to `FaqScreen` — same annotation already present on `DeviceListScreen`.

---

## Code Examples

### FaqScreen skeleton (verified against project patterns)

```kotlin
// File: app/src/main/java/com/audiobalance/app/ui/screens/FaqScreen.kt
package com.audiobalance.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.audiobalance.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.faq_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.faq_back_description)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 6 FaqItem entries — one per question
            // item { FooterSection() }
        }
    }
}
```

### DeviceListScreen TopAppBar update

```kotlin
// Source: DeviceListScreen.kt, AppNavigation.kt project patterns
TopAppBar(
    title = { Text(text = stringResource(R.string.device_list_title), style = MaterialTheme.typography.titleLarge) },
    actions = {
        IconButton(onClick = { navController.navigate("faq") { launchSingleTop = true } }) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.faq_info_icon_description),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual `Column` height animation | `AnimatedVisibility` | Compose 1.1 (2022) | No custom animation code needed |
| `Icons.Outlined.ArrowBack` | `Icons.AutoMirrored.Outlined.ArrowBack` | Material Icons 1.6 (2024) | Auto-mirrors in RTL layouts; both compile, AutoMirrored is recommended |

**Deprecated/outdated:**
- `androidx.navigation.compose.LocalNavController`: Not how navController is passed in this project. Thread it explicitly as a parameter (project convention).

---

## Open Questions

1. **`launchSingleTop` for FAQ navigation**
   - What we know: Without it, double-tapping info icon pushes two FAQ screens
   - What's unclear: Whether the user's tap speed makes this a real risk
   - Recommendation: Use `launchSingleTop = true` — zero cost, prevents the edge case

2. **FAQ Q3 answer: inline clickable link vs plain text + footer**
   - What we know: UI-SPEC shows FAQ Q3 answer as plain text (`faq_q3_answer = "Yes. Audio Balance is MIT licensed."`) with the link in the footer only; the inline link in Q3 answer uses `faq_q3_link_label`
   - What's unclear: Whether to render an inline clickable link inside the answer text or just rely on the footer
   - Recommendation: Match UI-SPEC exactly — short plain answer text in Q3, full GitHub link only in footer. Avoids AnnotatedString clickable span complexity.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 (local unit tests) |
| Config file | none — standard Android test runner via Gradle |
| Quick run command | `./gradlew :app:testDebugUnitTest` |
| Full suite command | `./gradlew :app:testDebugUnitTest` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| FAQ-01 | Info icon navigates to FAQ; back returns correctly | manual-only | — (Compose UI test or manual) | ❌ not applicable for unit tests |
| FAQ-02 | FAQ content is present and correct | manual-only | — (visual verification) | ❌ not applicable |
| FAQ-03 | GitHub URL is correct and clickable | unit | `./gradlew :app:testDebugUnitTest --tests "*.FaqContentTest"` | ❌ Wave 0 gap (optional) |
| FAQ-04 | Troubleshooting warning text present | manual-only | — (visual verification) | ❌ not applicable |

**Note on unit test value for this phase:** Phase 5 is a purely static UI screen with no business logic, no math helpers, no data transformations. The existing test files (`GainOffsetSliderTest.kt`, `ApplyGainsTest.kt`, `NotificationTextTest.kt`) all test pure functions — that pattern does not apply here. The only testable unit would be the GitHub URL constant, which has marginal value. Manual verification is the appropriate gate for this phase.

### Sampling Rate

- **Per task commit:** `./gradlew :app:testDebugUnitTest` (fast, ~3 seconds — confirms no regressions in existing tests)
- **Per wave merge:** `./gradlew :app:testDebugUnitTest`
- **Phase gate:** Existing tests green + manual visual check of FAQ screen on device/emulator

### Wave 0 Gaps

None mandatory — no new business logic to unit test. Existing test infrastructure covers all prior requirements. FAQ-specific tests are optional and low-value.

*(Optional: `FaqContentTest.kt` to assert GitHub URL string = expected value — covers FAQ-03 URL correctness at zero maintenance cost)*

---

## Sources

### Primary (HIGH confidence)

- Project source code (`AppNavigation.kt`, `DeviceListScreen.kt`, `PermissionScreen.kt`, `strings.xml`, `build.gradle.kts`) — verified all patterns and existing dependencies directly
- `05-CONTEXT.md` — locked decisions for this phase
- `05-UI-SPEC.md` — complete visual and interaction contract

### Secondary (MEDIUM confidence)

- Material3 TopAppBar `actions` parameter — standard Compose API; consistent with existing `@OptIn(ExperimentalMaterial3Api::class)` usage in project
- `Icons.AutoMirrored.Outlined.ArrowBack` import path — verified against material-icons-extended artifact conventions

### Tertiary (LOW confidence)

- None

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all dependencies confirmed present in `build.gradle.kts`
- Architecture: HIGH — all patterns copied from existing project files
- Pitfalls: HIGH — derived from reading actual code, not general knowledge

**Research date:** 2026-04-12
**Valid until:** 2026-05-12 (stable Compose/Material3 ecosystem)
