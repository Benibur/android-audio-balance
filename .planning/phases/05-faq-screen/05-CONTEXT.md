# Phase 5: FAQ Screen - Context

**Gathered:** 2026-04-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Static FAQ/About screen accessible from the device list. Explains what the app does, how sliders work, links to the GitHub repo, and warns about AudioEffect session 0 conflicts. No settings, no dynamic content, no user input.

</domain>

<decisions>
## Implementation Decisions

### Content & tone
- **Simple & direct** — short sentences, accessible language, no jargon unless necessary
- **6 FAQ entries** in accordion format:
  1. "What is this app?" — fixes left/right audio imbalance on BT headphones
  2. "How do the sliders work?" — explains balance (-100/+100) and gain offset (dB attenuation)
  3. "Is this app open source?" — yes, MIT license, clickable link to GitHub repo
  4. "Does this app drain battery?" — reassure: foreground service without polling, minimal impact
  5. "Which devices are supported?" — Android 8+, Bluetooth A2DP only, no wired/speaker
  6. "Why is another audio app overriding my settings?" — simple warning: close conflicting equalizer/bass booster apps and reconnect
- **Troubleshooting (FAQ-04)**: concise warning only, no technical explanation of session 0 / hasControl()

### Navigation & access
- **Info icon (ⓘ) in TopAppBar** of DeviceListScreen — navigates to FAQ screen
- New Compose route `"faq"` in `AppNavigation.kt`
- Back button in FAQ TopAppBar returns to device list (no back stack duplication)

### Screen format
- **Accordion** — questions as clickable headers that expand/collapse to show answers
- TopAppBar with back arrow + title "FAQ"
- Scrollable if content exceeds screen height

### GitHub link
- **Q/R + footer** — mentioned in the "Is this app open source?" answer AND as a footer section with GitHub icon + clickable link
- **Placeholder URL now**: `github.com/Benibur/android-audio-balance` — the repo doesn't exist yet (Phase 6 creates it), but the URL is hardcoded now

### Claude's Discretion
- Accordion animation style (expand/collapse)
- Exact wording of FAQ answers (following the tone: simple & direct)
- Icon choice for the info button (ⓘ vs ? vs HelpOutline)
- Spacing, padding, typography within the FAQ screen

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing UI layer
- `app/src/main/java/com/audiobalance/app/ui/navigation/AppNavigation.kt` — NavHost with existing routes (permissions, device_list). Add "faq" route here.
- `app/src/main/java/com/audiobalance/app/ui/screens/DeviceListScreen.kt` — TopAppBar where the info icon must be added
- `app/src/main/java/com/audiobalance/app/ui/screens/PermissionScreen.kt` — Example of a full-screen Compose layout in this project
- `app/src/main/java/com/audiobalance/app/ui/theme/` — Material3 theme (Theme.kt, Color.kt, Type.kt)

### Strings
- `app/src/main/res/values/strings.xml` — All user-facing text goes here (English)

### Phase context
- `.planning/REQUIREMENTS.md` — FAQ-01 through FAQ-04
- `.planning/ROADMAP.md` §"Phase 5: FAQ Screen" — Goal and 4 success criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Material3 theme** (Theme.kt, Color.kt, Type.kt) — ready to use
- **Scaffold + TopAppBar pattern** — already used in DeviceListScreen, replicate for FAQ
- **NavHost** in AppNavigation.kt — add a `composable("faq")` route

### Established Patterns
- **Single Activity** with Compose navigation (NavHost)
- **Strings in strings.xml** — all user-facing text externalized
- **No ViewModel needed** — FAQ is static content, no state management required

### Integration Points
- **DeviceListScreen TopAppBar** — add info icon action button
- **AppNavigation.kt** — add "faq" route, pass navController for back navigation
- **strings.xml** — add FAQ question/answer strings

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-faq-screen*
*Context gathered: 2026-04-12*
