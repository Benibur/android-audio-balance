# Bluetooth Audio Balance Controller

## What This Is

Application Android qui applique automatiquement un coefficient de balance stéréo (gauche/droite) spécifique à chaque paire d'écouteurs Bluetooth. L'app détecte les connexions BT A2DP, applique le réglage persistant via DynamicsProcessing session 0 global, et tourne en arrière-plan via un foreground service. Compose UI avec slider temps réel, toggle auto-apply par device, et flow de permissions.

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

### Active

- [ ] Gain offset par device — slider dB pour atténuer/booster le volume global via DynamicsProcessing, persisté par MAC
- [ ] Écran FAQ — explication de l'app, mention open source, lien vers le repo GitHub
- [ ] Repo GitHub public — README, licence MIT, code source sur github.com/Benibur/android-audio-balance

### Out of Scope

- Égaliseur multi-bandes — Wavelet existe déjà pour ça
- Support des écouteurs filaires — uniquement Bluetooth
- Support du speaker interne — hors du cas d'usage
- Publication sur Play Store — déploiement USB direct uniquement
- AutoEQ database — le problème ici est le déséquilibre usine, pas la réponse fréquentielle
- Sync cloud — overkill pour usage personnel ; JSON export/import suffit
- Profiles per-app — nécessite DUMP permission + monitoring de sessions ; trop complexe
- Export/import JSON, reset coefficients, nicknames, timestamps — déféré post-v1.1
- i18n 10 langues — déféré post-v1.1
- Bouton "Test balance", action notification, Quick Settings Tile, toggle global — déféré post-v1.1

## Current Milestone: v1.1 Gain Offset + FAQ + Open Source

**Goal:** Ajouter un contrôle de gain global par device et publier l'app en open source avec FAQ.

**Target features:**
- Gain offset par device (slider dB, DynamicsProcessing)
- Écran FAQ (explication app + open source)
- Repo GitHub public (README + licence MIT)

## Context

- **v1.0 shipped** : 4 phases, 12 plans, 1718 LOC Kotlin, 21 fichiers
- **Device de test** : Pixel 10 (Android 16, API 36), Bose QC35 Ben
- **Stack** : Kotlin, Jetpack Compose, Material3, DataStore Preferences, DynamicsProcessing
- **Audio** : `DynamicsProcessing(0, 0, config)` session 0 global — fonctionne sans root sur Android 16. Config avec toutes stages désactivées obligatoire. Détail complet dans `POC-RESULTS.md`.
- **Architecture** : Foreground service `connectedDevice` + START_STICKY + BOOT_COMPLETED. BT receiver dynamique (RECEIVER_EXPORTED). DP auto-recovery si perte de contrôle.
- **Gotcha majeur** : une autre app AudioEffect sur session 0 (ex: Jazib Khan Equalizer) évince notre DP. Résolu par auto-recreation du DP à chaque apply.

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

---
*Last updated: 2026-04-07 after v1.1 milestone start*
