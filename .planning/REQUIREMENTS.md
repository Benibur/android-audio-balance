# Requirements: Bluetooth Audio Balance Controller

**Defined:** 2026-04-07
**Core Value:** Quand je connecte mes écouteurs Bluetooth, la balance stéréo que j'ai configurée s'applique automatiquement — sans intervention manuelle.

## v1.1 Requirements

Requirements for v1.1 milestone. Each maps to roadmap phases.

### Gain Offset

- [x] **GAIN-01**: User can adjust a per-device gain offset slider (dB) in the device card
- [x] **GAIN-02**: Gain offset is persisted per MAC address and restored on reconnect
- [x] **GAIN-03**: Gain offset and balance are composed into a single DynamicsProcessing call per channel
- [x] **GAIN-04**: Gain offset auto-applies on BT connect (same behavior as balance)
- [x] **GAIN-05**: Notification displays gain offset when non-zero

### FAQ

- [ ] **FAQ-01**: User can access a FAQ screen from the device list (info icon)
- [ ] **FAQ-02**: FAQ explains what the app does and why
- [ ] **FAQ-03**: FAQ mentions open source and links to the GitHub repo
- [ ] **FAQ-04**: FAQ includes troubleshooting for AudioEffect session 0 conflicts

### Open Source

- [ ] **OSS-01**: GitHub repo `Benibur/android-audio-balance` created with MIT license
- [ ] **OSS-02**: README with app description, Android 8+ requirement, build instructions, session 0 warning
- [ ] **OSS-03**: Git history audited for secrets before first public push

## Future Requirements

Deferred from v1.0 active list. Tracked but not in current roadmap.

### Quality of Life

- **QOL-01**: Export/import des réglages en JSON
- **QOL-02**: Reset de tous les coefficients
- **QOL-03**: Nicknames personnalisés par device
- **QOL-04**: Timestamp du dernier apply par device

### UX Enhancements

- **UX-01**: Bouton "Test balance" pour prévisualiser
- **UX-02**: Action "Apply now" dans la notification
- **UX-03**: Quick Settings Tile pour toggle rapide
- **UX-04**: Toggle global on/off (kill switch)

### Localization

- **I18N-01**: i18n avec 10 langues

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Égaliseur multi-bandes | Wavelet existe déjà pour ça |
| Support écouteurs filaires | Uniquement Bluetooth |
| Support speaker interne | Hors du cas d'usage |
| Publication Play Store | Déploiement USB direct uniquement |
| AutoEQ database | Le problème est le déséquilibre usine, pas la réponse fréquentielle |
| Sync cloud | Overkill pour usage personnel ; JSON export/import suffit |
| Profiles per-app | Nécessite DUMP permission + monitoring de sessions ; trop complexe |
| Compression dynamique (min/max indépendants) | Approche A (gain offset unique) suffit ; compression = artefacts audio |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| GAIN-01 | Phase 4 | Complete |
| GAIN-02 | Phase 4 | Complete |
| GAIN-03 | Phase 4 | Complete |
| GAIN-04 | Phase 4 | Complete |
| GAIN-05 | Phase 4 | Complete |
| FAQ-01 | Phase 5 | Pending |
| FAQ-02 | Phase 5 | Pending |
| FAQ-03 | Phase 5 | Pending |
| FAQ-04 | Phase 5 | Pending |
| OSS-01 | Phase 6 | Pending |
| OSS-02 | Phase 6 | Pending |
| OSS-03 | Phase 6 | Pending |

**Coverage:**
- v1.1 requirements: 12 total
- Mapped to phases: 12
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-07*
*Last updated: 2026-04-07 after roadmap creation (phases 4-6 assigned)*
