# Bluetooth Audio Balance Controller

## What This Is

Application Android open source qui applique automatiquement un réglage de balance stéréo (gauche/droite) **et un offset de gain en dB** spécifiques à chaque paire d'écouteurs Bluetooth. L'app détecte les connexions BT A2DP, applique les réglages persistants via DynamicsProcessing session 0 global, et tourne en arrière-plan via un foreground service. Compose UI avec sliders temps réel, toggle auto-apply par device, écran FAQ, et flow de permissions. Distribuée en open source sous licence MIT sur github.com/Benibur/android-audio-balance, sideload USB uniquement.

## Core Value

Quand je connecte mes écouteurs Bluetooth, la balance stéréo que j'ai configurée s'applique automatiquement — sans intervention manuelle.

## Requirements

### Validated

- ✓ Détection automatique des connexions/déconnexions BT audio (A2DP) — v1.0
- ✓ Stockage persistant du coefficient de balance par adresse MAC — v1.0
- ✓ Application de la balance stéréo system-wide sur le flux audio media — v1.0
- ✓ Foreground service avec notification discrète pour maintien en arrière-plan — v1.0
- ✓ Démarrage automatique au boot (BOOT_COMPLETED) — v1.0
- ✓ UI Material Design minimaliste : liste des devices connus avec slider de balance — v1.0
- ✓ Toggle activation/désactivation par device — v1.0
- ✓ Gain offset par device — slider dB (-12..0) pour atténuer le volume global via DynamicsProcessing, persisté par MAC, appliqué en même temps que la balance — v1.1
- ✓ Écran FAQ — explication de l'app, mention open source, lien vers le repo GitHub, troubleshooting session 0 — v1.1
- ✓ Repo GitHub public — README EN, licence MIT, CHANGELOG, CONTRIBUTING, CI workflow, release v1.1 avec APK — v1.1
- ✓ Polish UI sliders — labels L/R/-12/0 plus grands et alignés, tick central sur slider de balance — v1.1
- ✓ Icône d'application dédiée (5 densités + variantes round + 512px store) — v1.1

### Active

(aucun — milestone v1.2 à définir via `/gsd:new-milestone`)

### Out of Scope

- Égaliseur multi-bandes — Wavelet existe déjà pour ça
- Support des écouteurs filaires — uniquement Bluetooth
- Support du speaker interne — hors du cas d'usage
- AutoEQ database — le problème ici est le déséquilibre usine, pas la réponse fréquentielle
- Sync cloud — overkill pour usage personnel ; JSON export/import suffit
- Profiles per-app — nécessite DUMP permission + monitoring de sessions ; trop complexe

### Backlog (candidats v1.2+)

- Publication sur Play Store — décision projet : sideload uniquement (à reconsidérer si demande utilisateurs)
- Export/import JSON, reset coefficients, nicknames, timestamps
- i18n FR/autres langues (README + UI)
- Bouton "Test balance" dans le device card
- Action notification (toggle rapide depuis la notif)
- Quick Settings Tile pour toggle global
- Toggle global activation/désactivation

## Current State

**Last shipped:** v1.1 le 2026-04-14 — repo public à https://github.com/Benibur/android-audio-balance, release v1.1 avec APK debug attaché.

**Next milestone:** v1.2 à définir (`/gsd:new-milestone`).

## Context

- **v1.0 shipped** : 4 phases (0-3), 12 plans, ~1718 LOC Kotlin
- **v1.1 shipped** : 3 phases (4-6), 8 plans, ~2377 LOC Kotlin/XML total maintenant
- **Device de test** : Pixel 10 (Android 16, API 36), Bose QC35 Ben
- **Stack** : Kotlin, Jetpack Compose, Material3, DataStore Preferences, DynamicsProcessing
- **Audio** : `DynamicsProcessing(0, 0, config)` session 0 global — fonctionne sans root sur Android 16. Config avec toutes stages désactivées obligatoire. Détail complet dans archive POC-RESULTS.
- **Architecture** : Foreground service `connectedDevice` + START_STICKY + BOOT_COMPLETED. BT receiver dynamique (RECEIVER_EXPORTED). DP auto-recovery si perte de contrôle. Balance + gain offset appliqués en un seul `setEnabled+apply` call.
- **Gotcha majeur** : une autre app AudioEffect sur session 0 (ex: Jazib Khan Equalizer) évince notre DP. Résolu par auto-recreation du DP à chaque apply.
- **CI** : GitHub Actions sur push/PR vers main — assembleDebug + testDebugUnitTest + lintDebug avec 4 détecteurs lint désactivés (Kotlin Analysis API mismatch, fix upstream attendu) + lint-baseline.xml pour grandfather les MissingPermission/NewApi pré-existants.
- **Historique git** : nettoyé via `git filter-repo` avant le go-public (retrait de `local.properties` qui leakait `sdk.dir`). Branche renommée master→main.

## Constraints

- **Tech stack** : Kotlin + Jetpack Compose
- **Compatibilité** : Android 8+ (API 26+) — compileSdk/targetSdk 35
- **Déploiement** : USB direct uniquement
- **Audio** : DynamicsProcessing session 0 global — une seule app AudioEffect peut contrôler session 0 à la fois
- **Batterie** : Impact minimal (foreground service sans polling)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin + Compose | Standard moderne Android, moins de boilerplate | ✓ Good |
| Faisabilité avant dev (Phase 1 POC) | L'approche balance globale n'est pas garantie — valider avant de s'engager | ✓ Good — session 0 fonctionne |
| DynamicsProcessing session 0 global | Seule API avec gain per-channel. Config toutes stages false obligatoire. | ✓ Good — validé sur Pixel 10/Android 16 |
| Service always-on (pas stop/restart) | Android 12+ interdit FGS start depuis BT broadcast. Service permanent seul viable. | ✓ Good |
| DataStore Preferences (pas SharedPrefs) | Async, coroutine-native, pas de risque ANR | ✓ Good |
| BOOT_COMPLETED inclus en v1 | Simple à ajouter, critique pour l'UX (balance prête dès le boot) | ✓ Good |
| DP auto-recovery | Recréer le DP si hasControl() retourne false (conflit avec autre app) | ✓ Good |
| Android 8+ minimum | API foreground service et BT stables à partir d'API 26 | ✓ Good |
| Uniquement Bluetooth | Scope limité au problème réel (écouteurs BT déséquilibrés) | ✓ Good |
| Gain offset dans le même DP que la balance | Un seul apply call → pas de glitch audio entre balance et gain | ✓ Good (v1.1) |
| FAQ statique en accordéon, 6 entrées hardcodées | Pas de CMS, pas de remote config — content shipped avec l'app | ✓ Good (v1.1) |
| Licence MIT + copyright "Benibur" (pseudo) | Permissive standard, préserve la vie privée | ✓ Good (v1.1) |
| `git filter-repo` avant go-public | Retrait de local.properties (leak sdk.dir) — historique réécrit avant le push initial donc sans force-push | ✓ Good (v1.1) |
| `lint-baseline.xml` + 4 détecteurs lint désactivés | Bug Kotlin Analysis API dans le lint jar de lifecycle — fix upstream attendu, baseline grandfather les pré-existants pour garder CI vert | ⚠️ Revisit quand AGP/lifecycle publient un fix |
| Sideload USB uniquement, pas de Play Store | Décision projet — perso / pas de store overhead. Backlog v1.2 si demande user. | — Pending |

---
*Last updated: 2026-04-14 — Milestone v1.1 shipped*
