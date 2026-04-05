# Roadmap: Bluetooth Audio Balance Controller

## Overview

Four phases from zero to working app: dev environment first (Phase 0), then a focused POC to validate whether AudioEffect session 0 works on the target hardware (Phase 1), then the full service+persistence backbone (Phase 2), and finally the Compose UI that surfaces everything to the user (Phase 3). The POC gate ensures we never build a service layer on an unvalidated audio foundation.

## Phases

**Phase Numbering:**
- Integer phases (0, 1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 0: Dev Environment** - Android Studio, SDK, emulator, ADB setup — everything needed to build and deploy (completed 2026-04-04)
- [ ] **Phase 1: AudioEffect POC** - Validate that balance can be applied system-wide on target hardware before any service work
- [ ] **Phase 2: Service + Persistence** - Foreground service, BT detection, DataStore persistence, and balance application wired together
- [ ] **Phase 3: UI** - Compose screens for device list, balance sliders, toggles, and permission flows

## Phase Details

### Phase 0: Dev Environment
**Goal**: The development environment is fully operational — code can be written, built, and deployed to a physical Android device
**Depends on**: Nothing (first phase)
**Requirements**: None (technical prerequisite, no v1 requirements map here)
**Success Criteria** (what must be TRUE):
  1. Android Studio opens and a new Kotlin/Compose project builds without errors
  2. An Android emulator boots and runs a Hello World app
  3. A physical Android device is recognized by ADB (`adb devices` lists it)
  4. A debug APK can be installed and launched on the physical device via USB
**Plans:** 2/2 plans complete
Plans:
- [ ] 00-01-PLAN.md — Install JDK/SDK and create Kotlin/Compose project skeleton with successful build
- [ ] 00-02-PLAN.md — Set up emulator and verify physical device deployment

### Phase 1: AudioEffect POC
**Goal**: The AudioEffect approach is validated on target hardware — we know exactly which code path produces an audible balance shift, or we have selected and validated a fallback
**Depends on**: Phase 0
**Requirements**: FEAS-01, FEAS-02
**Success Criteria** (what must be TRUE):
  1. A standalone Kotlin script or minimal Activity applies AudioEffect session 0 and produces a measurable left/right balance shift on the physical device
  2. If session 0 is silently blocked, the per-session-ID fallback path (ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) is identified and produces an audible balance shift instead
  3. The exact AudioEffect constructor call, band manipulation strategy, and error handling pattern are documented as the confirmed implementation approach for Phase 2
**Plans**: 3 plans
Plans:
- [ ] 01-01-PLAN.md — POC Activity: DynamicsProcessing session 0 with internal audio + ear-test (FEAS-01)
- [ ] 01-02-PLAN.md — External audio + fallback chain (per-session, broadcast, LoudnessEnhancer) + ear-test (FEAS-02)
- [ ] 01-03-PLAN.md — Write POC-RESULTS.md documenting confirmed approach for Phase 2

### Phase 2: Service + Persistence
**Goal**: The app silently monitors Bluetooth A2DP connections, persists per-device balance coefficients, and applies the correct balance automatically when a known device connects — all while running as a foreground service
**Depends on**: Phase 1
**Requirements**: AUDIO-02, BT-01, BT-02, BT-03, SVC-01, SVC-02, DATA-01, DATA-02
**Success Criteria** (what must be TRUE):
  1. When a Bluetooth A2DP device connects, the app detects the connection and logs the device MAC address without any user action
  2. A balance coefficient saved for a device MAC persists after the app is killed and the phone is restarted
  3. When a known BT device connects, the previously saved balance coefficient is applied automatically to the audio output
  4. A persistent foreground notification is visible while the service is running, showing the connected device name and active balance value
  5. When a BT device disconnects, the balance effect is removed and the notification updates accordingly
**Plans**: TBD

### Phase 3: UI
**Goal**: The user can see all known Bluetooth devices, adjust each device's balance with a slider, enable or disable auto-apply per device, and grant all required permissions through a guided flow
**Depends on**: Phase 2
**Requirements**: AUDIO-01, UI-01, UI-02, UI-03, UI-04, UI-05
**Success Criteria** (what must be TRUE):
  1. The main screen lists all previously connected BT devices with their stored balance value visible
  2. Dragging the slider for a device immediately updates the balance value displayed and the stored coefficient
  3. Toggling the enable/disable switch for a device prevents or restores auto-apply for that device
  4. The currently connected device is visually distinguished from devices that are not connected
  5. On first launch, the app requests all required runtime permissions (BLUETOOTH_CONNECT, notification) with a plain-language explanation before any Bluetooth call is made
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 0 → 1 → 2 → 3

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 0. Dev Environment | 2/2 | Complete   | 2026-04-04 |
| 1. AudioEffect POC | 1/3 | In Progress|  |
| 2. Service + Persistence | 0/? | Not started | - |
| 3. UI | 0/? | Not started | - |
