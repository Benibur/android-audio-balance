# Phase 1: AudioEffect POC - Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Valider sur le device physique (`56191FDCR002NG`) qu'une approche AudioEffect produit un décalage de balance stéréo gauche/droite audible sur la sortie Bluetooth. Si AudioEffect session 0 échoue silencieusement, tester des fallbacks. Le livrable est une Activity de POC qui permet à l'utilisateur de valider à l'oreille que l'effet fonctionne, et un document qui fige l'approche retenue pour Phase 2.

Hors scope : toute logique de persistance, détection BT automatique, service en background, ou UI finale. C'est un test de faisabilité, pas un début d'implémentation finale.

</domain>

<decisions>
## Implementation Decisions

### Méthode de test
- **Validation à l'oreille uniquement** — pas d'enregistrement loopback, pas de micro externe. L'utilisateur écoute avec ses écouteurs BT et juge.
- **Critère de succès POC** : différence audible nette quand la balance est poussée à fond d'un côté. Pas besoin de coupure totale du côté opposé ni d'ajustement progressif multi-niveaux pour valider le POC.
- Device de test : `56191FDCR002NG` (physique, déjà validé Phase 0). Les écouteurs BT personnels de l'utilisateur sont le "ground truth".

### Source audio
- **Double mode obligatoire** :
  1. Le POC embarque une source audio interne (musique courte ou tone) jouée via MediaPlayer ou ExoPlayer — pour un test reproductible, toujours disponible, sans dépendre d'une autre app.
  2. Le POC doit AUSSI fonctionner sur une source audio externe (Spotify/YouTube lancé en parallèle) — car c'est le use case réel de l'app finale. Si l'effet ne s'applique qu'à la source interne, le POC est un échec partiel.
- Les deux modes doivent être testés dans la session POC.

### Stratégie fallback
- **Si AudioEffect session 0 échoue** (silencieusement ou avec exception), tester automatiquement dans l'ordre :
  1. Per-session via `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast pour capturer les sessions actives
  2. `LoudnessEnhancer` sur session 0 (variante d'API souvent moins bloquée)
  3. Autres AudioEffect constructors/subclasses disponibles
- **Fallbacks non-tentés dans cette phase** (à documenter comme options si tout échoue) : AccessibilityService volume override, DUMP permission via ADB, MediaSession callback interception.
- Chaque tentative doit logger : quelle API a été tentée, a-t-elle été acceptée (pas d'exception), et l'utilisateur confirme-t-il un effet audible.

### POC UI (Claude's Discretion)
- Une Activity unique avec des contrôles minimum pour tester chaque scénario : balance gauche à fond, balance droite à fond, reset, toggle source interne/externe, boutons pour basculer entre approches (session 0, per-session, LoudnessEnhancer).
- Pas besoin de Material Design poli, juste lisible et fonctionnel.
- Logs visibles à l'écran OU dans logcat — au choix de Claude.

### Documentation livrable
- À la fin du POC, produire un document (markdown dans le phase dir) qui fige :
  - Quelle approche fonctionne (constructor exact, flags, session ID)
  - Comment l'appliquer et la retirer proprement
  - Gotchas observés sur ce device/Android version
  - Pattern error-handling recommandé pour Phase 2
- Ce document est l'input principal de Phase 2.

### Claude's Discretion
- Langage de l'UI (Compose vs XML Activity) — Compose est déjà configuré, probablement plus simple
- Source audio exacte embarquée (tone généré vs fichier MP3 court dans res/raw)
- Mécanisme de détection "session 0 échoue" (try/catch sur constructor, test de silence, etc.)
- Structure du code POC (une Activity monolithique vs split en helpers)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project context
- `.planning/PROJECT.md` — Contraintes projet (Kotlin+Compose, API 26+, USB-only)
- `.planning/REQUIREMENTS.md` §Feasibility — FEAS-01, FEAS-02 définitions
- `.planning/ROADMAP.md` §"Phase 1: AudioEffect POC" — Goal et success criteria
- `.planning/phases/00-dev-environment/00-CONTEXT.md` — Décisions Phase 0 (package name, SDK versions)
- `.planning/phases/00-dev-environment/00-01-SUMMARY.md` — Détails de la structure projet existante

### External docs (à chercher en recherche)
- Android AudioEffect API docs (session 0 semantics post-Android 10)
- `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast contract
- LoudnessEnhancer class reference
- Existing OSS apps that do global audio effects on modern Android (references for working patterns)

Aucun ADR ou spec externe au projet — les canonical refs sont les fichiers .planning/ internes.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`app/src/main/java/com/audiobalance/app/MainActivity.kt`** — Existing Hello World Activity with AudioBalanceTheme + Compose Surface. Le POC peut remplacer ou étendre ce fichier.
- **`app/src/main/java/com/audiobalance/app/ui/theme/`** — Theme Compose déjà configuré (Theme.kt, Color.kt, Type.kt). Réutilisable tel quel.
- **`app/build.gradle.kts`** — minSdk=26, compileSdk=35, Compose BOM 2024.12.01, Material3 activé.

### Established Patterns
- **Version catalog** — Nouvelles dépendances doivent passer par `gradle/libs.versions.toml` (pattern Phase 0).
- **Package base** : `com.audiobalance.app` — nouveaux fichiers vont sous ce package.

### Integration Points
- Le POC builde via `./gradlew assembleDebug` et se déploie via `adb -s 56191FDCR002NG install app/build/outputs/apk/debug/app-debug.apk`.
- Permissions : à ajouter dans AndroidManifest.xml si AudioEffect en requiert (probablement `MODIFY_AUDIO_SETTINGS`, éventuellement `RECORD_AUDIO` pour session 0 sur certaines versions).

</code_context>

<specifics>
## Specific Ideas

- L'utilisateur écoute avec SES écouteurs BT personnels (les écouteurs mal calibrés qui motivent tout le projet). Le test doit se faire avec ces écouteurs spécifiquement — pas un autre modèle.
- Le POC est jetable : son rôle est de produire du savoir, pas du code qui va dans le produit final. Le code de Phase 2 peut repartir de zéro une fois l'approche validée.

</specifics>

<deferred>
## Deferred Ideas

- AccessibilityService fallback — documenté comme option mais pas tenté en Phase 1 (trop complexe pour un POC)
- DUMP permission via ADB — même raison
- MediaSession callback interception — même raison
- Enregistrement loopback / mesure quantitative — pas nécessaire pour le POC, l'oreille suffit
- Ajustement progressif multi-niveaux — à faire en Phase 3 (UI finale avec slider), pas en POC

</deferred>

---

*Phase: 01-audioeffect-poc*
*Context gathered: 2026-04-05*
