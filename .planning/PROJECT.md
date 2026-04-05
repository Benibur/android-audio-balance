# Bluetooth Audio Balance Controller

## What This Is

Application Android légère qui applique automatiquement un coefficient de balance stéréo (gauche/droite) spécifique à chaque paire d'écouteurs Bluetooth. L'app détecte les connexions BT, applique le réglage persistant associé, et tourne en arrière-plan via un foreground service. Usage personnel pour compenser le déséquilibre audio d'écouteurs BT mal calibrés.

## Core Value

Quand je connecte mes écouteurs Bluetooth, la balance stéréo que j'ai configurée s'applique automatiquement — sans intervention manuelle.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Détection automatique des connexions/déconnexions BT audio (A2DP)
- [ ] Stockage persistant du coefficient de balance par adresse MAC
- [ ] Application de la balance stéréo system-wide sur le flux audio media
- [ ] Foreground service avec notification discrète pour maintien en arrière-plan
- [ ] Démarrage automatique au boot (BOOT_COMPLETED)
- [ ] UI Material Design minimaliste : liste des devices connus avec slider de balance
- [ ] Toggle global activation/désactivation par device
- [ ] Export/import des réglages en JSON
- [ ] Reset de tous les coefficients

### Out of Scope

- Contrôle du volume minimal — besoin séparé, à traiter plus tard
- Support des écouteurs filaires — uniquement Bluetooth
- Support du speaker interne — uniquement périphériques BT
- Publication sur Play Store — déploiement USB direct uniquement

## Context

- **Motivation** : Écouteurs BT personnels avec balance stéréo déséquilibrée d'usine
- **Difficulté technique principale** : ~~Pas d'API native Android pour la balance stéréo globale post-Android 10~~ **RÉSOLU en Phase 1** : `DynamicsProcessing(0, 0, config)` avec session ID = 0 littéral fonctionne sur Pixel 10 / Android 16. Voir `.planning/phases/01-audioeffect-poc/POC-RESULTS.md` pour le pattern exact.
- **Méthodologie** : Étude de faisabilité technique (recherche + POC si incertitude) avant le développement, pour valider l'approche de balance globale
- **Stack** : Kotlin, Jetpack Compose, Android 8+ (API 26+)
- **Environnement** : Android Studio, émulateur + device physique USB pour tests
- **Développeur** : Pas d'expérience Android préalable — Claude pilote l'implémentation

## Constraints

- **Tech stack** : Kotlin + Jetpack Compose — choix moderne, moins de boilerplate
- **Compatibilité** : Android 8+ (API 26+) — foreground service + BT API stables
- **Déploiement** : USB direct uniquement, pas de Play Store
- **Audio** : Balance system-wide requise, pas per-app — c'est le point critique de faisabilité
- **Batterie** : Impact minimal grâce au foreground service (pas de polling)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin + Compose | Standard moderne Android, moins de boilerplate, recommandé pour nouveaux projets | — Pending |
| Faisabilité avant dev | L'approche balance globale (AudioEffect session 0, AccessibilityService) n'est pas garantie — valider avant de s'engager | — Pending |
| Android 8+ minimum | API foreground service et BT stables à partir d'API 26 | — Pending |
| Uniquement Bluetooth | Scope limité au problème réel (écouteurs BT déséquilibrés) | — Pending |

---
*Last updated: 2026-04-05 after Phase 1 (AudioEffect POC) complete — FEAS-01/FEAS-02 validated, session 0 global approach confirmed*
