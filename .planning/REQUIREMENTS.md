# Requirements: Bluetooth Audio Balance Controller

**Defined:** 2026-04-01
**Core Value:** Quand je connecte mes écouteurs Bluetooth, la balance stéréo que j'ai configurée s'applique automatiquement

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Feasibility

- [ ] **FEAS-01**: AudioEffect session 0 applique un offset de balance stéréo gauche/droite mesurable sur le device physique de test
- [ ] **FEAS-02**: L'approche de fallback est identifiée si session 0 échoue (AccessibilityService, DUMP permission, ou per-session)

### Audio

- [ ] **AUDIO-01**: User peut ajuster la balance stéréo (-100% gauche à +100% droite) par device BT via un slider
- [ ] **AUDIO-02**: Le coefficient de balance est appliqué system-wide au flux audio media quand le device BT est connecté

### Bluetooth

- [ ] **BT-01**: L'app détecte automatiquement les connexions A2DP Bluetooth (adresse MAC)
- [ ] **BT-02**: L'app détecte automatiquement les déconnexions A2DP Bluetooth
- [ ] **BT-03**: Le coefficient de balance stocké est auto-appliqué à la connexion du device BT

### Service

- [ ] **SVC-01**: Un foreground service (`connectedDevice`) maintient l'app active en arrière-plan
- [ ] **SVC-02**: Une notification persistante indique l'état du service (device connecté, balance active)

### Persistence

- [ ] **DATA-01**: Les profils de balance sont stockés de façon persistante (MAC → coefficient)
- [ ] **DATA-02**: Les profils survivent aux redémarrages de l'app

### UI

- [ ] **UI-01**: L'utilisateur voit la liste des devices BT connus avec leur coefficient de balance
- [ ] **UI-02**: L'utilisateur peut ajuster la balance via un slider horizontal par device
- [ ] **UI-03**: L'utilisateur peut activer/désactiver l'auto-apply par device via un toggle
- [ ] **UI-04**: Le device actuellement connecté est visuellement distingué dans la liste
- [ ] **UI-05**: Les permissions runtime nécessaires sont demandées avec explication claire

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
| FEAS-01 | — | Pending |
| FEAS-02 | — | Pending |
| AUDIO-01 | — | Pending |
| AUDIO-02 | — | Pending |
| BT-01 | — | Pending |
| BT-02 | — | Pending |
| BT-03 | — | Pending |
| SVC-01 | — | Pending |
| SVC-02 | — | Pending |
| DATA-01 | — | Pending |
| DATA-02 | — | Pending |
| UI-01 | — | Pending |
| UI-02 | — | Pending |
| UI-03 | — | Pending |
| UI-04 | — | Pending |
| UI-05 | — | Pending |

**Coverage:**
- v1 requirements: 16 total
- Mapped to phases: 0
- Unmapped: 16 ⚠️

---
*Requirements defined: 2026-04-01*
*Last updated: 2026-04-01 after initial definition*
