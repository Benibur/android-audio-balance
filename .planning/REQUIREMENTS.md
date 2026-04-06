# Requirements: Bluetooth Audio Balance Controller

**Defined:** 2026-04-01
**Core Value:** Quand je connecte mes écouteurs Bluetooth, la balance stéréo que j'ai configurée s'applique automatiquement

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Feasibility

- [x] **FEAS-01**: AudioEffect session 0 applique un offset de balance stéréo gauche/droite mesurable sur le device physique de test
- [x] **FEAS-02**: L'approche de fallback est identifiée si session 0 échoue (AccessibilityService, DUMP permission, ou per-session)

### Audio

- [x] **AUDIO-01**: User peut ajuster la balance stéréo (-100% gauche à +100% droite) par device BT via un slider
- [x] **AUDIO-02**: Le coefficient de balance est appliqué system-wide au flux audio media quand le device BT est connecté

### Bluetooth

- [x] **BT-01**: L'app détecte automatiquement les connexions A2DP Bluetooth (adresse MAC)
- [x] **BT-02**: L'app détecte automatiquement les déconnexions A2DP Bluetooth
- [x] **BT-03**: Le coefficient de balance stocké est auto-appliqué à la connexion du device BT

### Service

- [x] **SVC-01**: Un foreground service (`connectedDevice`) maintient l'app active en arrière-plan
- [x] **SVC-02**: Une notification persistante indique l'état du service (device connecté, balance active)

### Persistence

- [x] **DATA-01**: Les profils de balance sont stockés de façon persistante (MAC → coefficient)
- [x] **DATA-02**: Les profils survivent aux redémarrages de l'app

### UI

- [x] **UI-01**: L'utilisateur voit la liste des devices BT connus avec leur coefficient de balance
- [x] **UI-02**: L'utilisateur peut ajuster la balance via un slider horizontal par device
- [x] **UI-03**: L'utilisateur peut activer/désactiver l'auto-apply par device via un toggle
- [x] **UI-04**: Le device actuellement connecté est visuellement distingué dans la liste
- [x] **UI-05**: Les permissions runtime nécessaires sont demandées avec explication claire

## v2 Requirements

### Lifecycle

- **LIFE-01**: L'app démarre automatiquement au boot (BOOT_COMPLETED)
- **LIFE-02**: Toggle global activation/désactivation (kill switch)

### Data

- **DATA-03**: Export des réglages en JSON
- **DATA-04**: Import des réglages depuis JSON

### UI Enhancements

- **UIX-01**: Bouton "Test balance" pour prévisualiser sans reconnecter
- **UIX-02**: Nicknames personnalisés par device
- **UIX-03**: Timestamp du dernier apply par device
- **UIX-04**: Action "Apply now" dans la notification

### Advanced

- **ADV-01**: Quick Settings Tile pour toggle rapide

## Out of Scope

| Feature | Reason |
|---------|--------|
| Contrôle du volume | Besoin séparé, problème différent — à traiter dans un autre projet |
| Égaliseur multi-bandes | Complexité disproportionnée ; Wavelet existe déjà pour ça |
| Support écouteurs filaires | Scope limité au BT (problème réel = écouteurs BT déséquilibrés) |
| Support speaker interne | Hors du cas d'usage |
| Publication Play Store | Déploiement USB direct uniquement |
| AutoEQ database | Wavelet le fait déjà ; le problème ici est le déséquilibre usine, pas la réponse fréquentielle |
| Sync cloud | Overkill pour usage personnel ; JSON export/import suffit |
| Profiles per-app | Nécessite DUMP permission + monitoring de sessions ; trop complexe pour v1 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| FEAS-01 | Phase 1 | Complete |
| FEAS-02 | Phase 1 | Complete |
| AUDIO-01 | Phase 3 | Complete |
| AUDIO-02 | Phase 2 | Complete |
| BT-01 | Phase 2 | Complete |
| BT-02 | Phase 2 | Complete |
| BT-03 | Phase 2 | Complete |
| SVC-01 | Phase 2 | Complete |
| SVC-02 | Phase 2 | Complete |
| DATA-01 | Phase 2 | Complete |
| DATA-02 | Phase 2 | Complete |
| UI-01 | Phase 3 | Complete |
| UI-02 | Phase 3 | Complete |
| UI-03 | Phase 3 | Complete |
| UI-04 | Phase 3 | Complete |
| UI-05 | Phase 3 | Complete |

**Coverage:**
- v1 requirements: 16 total
- Mapped to phases: 16
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-01*
*Last updated: 2026-04-01 after roadmap creation — traceability complete*
