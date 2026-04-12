# Roadmap: Bluetooth Audio Balance Controller

## Milestones

- ✅ **v1.0 MVP** — Phases 0-3 (shipped 2026-04-07)
- 🚧 **v1.1 Gain Offset + FAQ + Open Source** — Phases 4-6 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 0-3) — SHIPPED 2026-04-07</summary>

- [x] Phase 0: Dev Environment (2/2 plans) — completed 2026-04-04
- [x] Phase 1: AudioEffect POC (3/3 plans) — completed 2026-04-05
- [x] Phase 2: Service + Persistence (3/3 plans) — completed 2026-04-06
- [x] Phase 3: UI (4/4 plans) — completed 2026-04-06

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

### 🚧 v1.1 Gain Offset + FAQ + Open Source (In Progress)

**Milestone Goal:** Add per-device gain offset control, publish a FAQ/About screen, and release the app as open source on GitHub.

- [x] **Phase 4: Gain Offset** — Per-device dB gain slider, persisted and auto-applied alongside balance (completed 2026-04-12)
- [ ] **Phase 5: FAQ Screen** — Static informational screen accessible from the device list
- [ ] **Phase 6: Open Source** — Public GitHub repo with README, MIT license, and audited history

## Phase Details

### Phase 4: Gain Offset
**Goal**: Users can adjust and persist a per-device gain offset that applies automatically alongside balance on every BT connect
**Depends on**: Phase 3 (v1.0 complete)
**Requirements**: GAIN-01, GAIN-02, GAIN-03, GAIN-04, GAIN-05
**Success Criteria** (what must be TRUE):
  1. User can drag a gain offset slider in the device card and see the numeric dB value update in real time
  2. Gain offset snaps to 0 dB when released near center, matching balance slider behavior
  3. When the device reconnects, the saved gain offset is applied together with balance in a single DynamicsProcessing call — no separate apply steps
  4. The foreground notification displays the current gain offset value when it is non-zero
  5. Gain offset survives app restart and phone reboot — the persisted value is restored from DataStore on reconnect
**Plans:** 2/2 plans complete

Plans:
- [ ] 04-01-PLAN.md — Data layer + service: DeviceEntry, repository, applyGains helper, seed_gain_offset, notification
- [ ] 04-02-PLAN.md — ViewModel + UI: gain offset override flow, DeviceCard slider, DeviceListScreen wiring

### Phase 5: FAQ Screen
**Goal**: Users can open a FAQ/About screen that explains the app and links to the GitHub repo
**Depends on**: Phase 4
**Requirements**: FAQ-01, FAQ-02, FAQ-03, FAQ-04
**Success Criteria** (what must be TRUE):
  1. An info icon in the device list top bar navigates to the FAQ screen; Back returns to the device list without duplicating the back stack
  2. The FAQ screen explains what the app does, why DynamicsProcessing is used, and what the balance and gain sliders control
  3. The FAQ screen includes a clickable link that opens the GitHub repo in the browser
  4. The FAQ screen includes a troubleshooting section warning about AudioEffect session 0 conflicts with other audio apps
**Plans**: TBD

### Phase 6: Open Source
**Goal**: The app's source code is publicly available on GitHub with a clear README and MIT license
**Depends on**: Phase 5
**Requirements**: OSS-01, OSS-02, OSS-03
**Success Criteria** (what must be TRUE):
  1. The repo `github.com/Benibur/android-audio-balance` is publicly accessible with MIT LICENSE file present
  2. The README describes what the app does, the Android 8+ requirement, how to build and sideload via ADB, and the session 0 AudioEffect limitation
  3. The git history contains no secrets — no `.jks`, `keystore.properties`, or sensitive local config files are reachable via any commit
**Plans**: TBD

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 0. Dev Environment | v1.0 | 2/2 | Complete | 2026-04-04 |
| 1. AudioEffect POC | v1.0 | 3/3 | Complete | 2026-04-05 |
| 2. Service + Persistence | v1.0 | 3/3 | Complete | 2026-04-06 |
| 3. UI | v1.0 | 4/4 | Complete | 2026-04-06 |
| 4. Gain Offset | 2/2 | Complete   | 2026-04-12 | - |
| 5. FAQ Screen | v1.1 | 0/? | Not started | - |
| 6. Open Source | v1.1 | 0/? | Not started | - |
