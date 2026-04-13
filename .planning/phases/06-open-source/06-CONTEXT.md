# Phase 6: Open Source - Context

**Gathered:** 2026-04-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Publier le code source sur `github.com/Benibur/android-audio-balance` avec un README complet, une licence MIT, un CHANGELOG, un template d'issue bug report, un CONTRIBUTING.md, un workflow CI GitHub Actions, et une release taguée v1.1 incluant le debug APK pour sideload. L'historique git doit être audité pour garantir l'absence de secrets avant le premier push public.

Hors scope : Play Store, signing de release APK (debug seulement), i18n multi-langues, marketing/promotion.

</domain>

<decisions>
## Implementation Decisions

### README
- **Langue** : anglais uniquement (convention open-source)
- **Sections obligatoires** :
  1. What it does — problème résolu (left/right imbalance on BT headphones) + galerie de screenshots
  2. Requirements — Android 8+, Bluetooth A2DP only, USB debugging pour install
  3. Build & sideload — commandes `./gradlew assembleDebug` + `adb install`
  4. Known limitations — AudioEffect session 0 conflicts, pas de wired/speaker, pas de Play Store
- **Screenshots** : galerie complète (4+ écrans) fournis par le user depuis son Pixel — device list, device card dépliée, FAQ, permissions screen. À placer dans `docs/screenshots/`
- **Badges** : 3 badges en haut — MIT license, Android 8+ platform, GitHub Actions CI build status

### Repo metadata (GitHub)
- **Description/tagline** : "Fix left/right audio imbalance on your Bluetooth headphones"
- **Topics** : `android`, `bluetooth`, `audio`, `accessibility`, `hearing`, `audioeffect`, `dynamicsprocessing`
- **Issues** : activées, PRs acceptées
- **Wiki/Discussions** : pas explicitement demandé — défaut GitHub

### Licence
- **Type** : MIT
- **Copyright holder** : "Benibur" (pseudo GitHub, préserve la vie privée)
- **Année** : 2026

### CONTRIBUTING.md
- Contenu : build steps (gradle + adb) + commit convention (Conventional Commits déjà utilisé) + "please open an issue before a large PR"
- Fichier séparé, référencé depuis README

### Fichiers meta additionnels
- `.github/ISSUE_TEMPLATE/bug_report.md` — template avec champs : device model, Android version, app version, steps to reproduce, expected vs actual
- `CHANGELOG.md` — démarrer avec `v1.1 — Initial public release` listant les grandes features (per-device balance, gain offset, FAQ screen)

### CI (GitHub Actions)
- **Scope** : build debug APK + unit tests + lint
- **Trigger** : push sur `main` + PRs
- **Prérequis bloquant** : **fixer le bug lint pré-existant `NonNullableMutableLiveDataDetector` / `IncompatibleClassChangeError`** avant d'activer le lint en CI (sinon CI rouge en permanence)
- Workflow file : `.github/workflows/build.yml`

### Audit de l'historique git
- **Méthode** : scan automatisé + revue manuelle
- **Commandes à exécuter avant le premier push** :
  - `git log --all -p | grep -iE "keystore|\.jks|password|secret|api[_-]?key|token"` — détecte les valeurs sensibles dans le contenu des commits
  - `git log --all --diff-filter=A --name-only | sort -u | grep -iE "local\.properties|\.jks|keystore\.properties|\.env"` — détecte tout fichier sensible jamais ajouté, même supprimé plus tard
  - `git ls-tree -r --name-only HEAD` — liste tous les fichiers actuellement trackés pour validation visuelle
- **Résultat attendu** : aucun match → push sûr. Un match → présenter au user avant décision (rewrite vs push quand même).
- **État actuel connu** : `.gitignore` couvre déjà `local.properties`, `.idea`, `build/` — pas de `.jks` ni `keystore.properties` n'ont jamais été commités (vérifié au moment du discuss-phase).

### Stratégie du premier push
- **Historique** : push historique complet (`git push --all`) — préserve le storytelling GSD/Claude Code, transparent sur le process de dev
- **Dossier `.planning/`** : reste public, pas de secrets dedans, démontre l'usage de GSD
- **Tag** : créer `v1.1` sur le HEAD après le push
- **Release GitHub** : attacher le debug APK (`app-debug.apk`) à la release v1.1 pour faciliter le sideload (l'user n'a pas besoin de builder)

### Claude's Discretion
- Le wording exact du README (ton, phrases)
- Le contenu détaillé du CHANGELOG (exhaustivité des features listées)
- Le layout de la galerie de screenshots (tableau markdown vs HTML)
- Le nom exact du job/step dans le workflow CI
- L'ordre des badges dans le README

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project-level
- `.planning/PROJECT.md` — Vision produit, milestone v1.1, "sideload USB direct uniquement" (pas de Play Store)
- `.planning/REQUIREMENTS.md` — Requirements OSS-01, OSS-02, OSS-03
- `.planning/ROADMAP.md` §Phase 6 — Goal + success criteria

### Related phase decisions
- `.planning/phases/05-faq-screen/05-CONTEXT.md` — URL du repo déjà hardcodée (`github.com/Benibur/android-audio-balance`), mention MIT license dans la FAQ Q3 — doit rester cohérent avec ce qui est publié

### External standards (pas de fichiers locaux — refs conceptuelles)
- MIT License text : https://opensource.org/licenses/MIT (à copier verbatim dans LICENSE)
- Conventional Commits : https://www.conventionalcommits.org (pattern déjà suivi dans l'historique git)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `.gitignore` existant : couvre `local.properties`, `.idea/`, `build/`, `app/build/`, `.gradle/`, `*.iml` — base saine
- `local.properties` : existe localement, correctement ignoré
- Historique git : pas de pollution secrets détectée au pre-check (aucun commit touchant `*.jks`, `keystore.properties`)

### Established Patterns
- Conventional Commits : tout l'historique suit `feat(XX-YY): ...` / `docs(phase-N): ...` / `fix(...): ...` — à documenter dans CONTRIBUTING.md comme convention attendue
- GSD workflow : phase directories `.planning/phases/NN-slug/` avec CONTEXT.md, RESEARCH.md, PLAN.md, SUMMARY.md, VERIFICATION.md — reste public dans le repo

### Integration Points
- `.github/workflows/build.yml` — nouveau fichier (aucun workflow existant)
- `README.md`, `LICENSE`, `CHANGELOG.md`, `CONTRIBUTING.md` — à créer à la racine
- `.github/ISSUE_TEMPLATE/bug_report.md` — nouveau fichier
- `docs/screenshots/` — nouveau dossier, fichiers fournis par le user

### Blocking prerequisite
- **Bug lint pré-existant** : `NonNullableMutableLiveDataDetector` crash (`IncompatibleClassChangeError`) dans le lint tooling — documenté dans les SUMMARYs des phases 04 et 05. Le CI lint sera rouge tant que ce n'est pas résolu. Options (à décider en planning) : bump AGP/lint version, désactiver ce détecteur spécifique via `lintOptions`, ou attendre un fix upstream.

</code_context>

<specifics>
## Specific Ideas

- Le user veut que le historique git complet soit push — il valorise la transparence du process de dev avec GSD/Claude Code
- Le debug APK attaché à la release v1.1 doit être le APK courant (celui qui tourne déjà sur le Pixel 10 du user) pour que les early users aient exactement la même version vérifiée
- Les topics GitHub incluent `accessibility` et `hearing` — le user vise (aussi) des personnes avec des problèmes d'équilibre auditif, pas seulement des devs

</specifics>

<deferred>
## Deferred Ideas

- CODE_OF_CONDUCT.md — pas demandé, peut être ajouté plus tard si des contributeurs externes arrivent
- Signing de release APK (keystore de production) — hors scope, debug APK suffisant pour sideload
- Emulator screenshots plus propres — deferred, le user prend ses captures au premier push
- i18n du README (FR / autres langues) — anglais-only décidé
- Playstore listing — explicitement out-of-scope au niveau projet
- Wiki GitHub / Discussions — défaut GitHub, pas configuré activement
- Release automation (semantic-release, changelog auto) — overkill pour v1.1

</deferred>

---

*Phase: 06-open-source*
*Context gathered: 2026-04-13*
