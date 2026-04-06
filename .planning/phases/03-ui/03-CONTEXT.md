# Phase 3: UI - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Compose screens pour la liste des devices BT connus avec slider de balance, toggle auto-apply par device, distinction visuelle du device connecté, et flow de permissions runtime (BLUETOOTH_CONNECT, POST_NOTIFICATIONS) au premier lancement.

Hors scope :
- i18n multi-langues (deferred — Phase 4 / v2)
- Section "À propos" (deferred)
- FAQ / explications de la finalité (deferred)
- Export/import JSON (v2)
- Quick Settings Tile (v2)
- Toggle global on/off (v2)
- Nicknames personnalisés par device (v2)
- Timestamp du dernier apply (v2)
- Action "Apply now" dans la notification (v2)
- Bouton "Test balance" (v2)

</domain>

<decisions>
## Implementation Decisions

### Device list layout
- **Carte avec slider intégré** par device — chaque carte montre directement le slider + toggle, pas d'expand/collapse
- **Scroll vertical** — liste scrollable de cartes
- **Device connecté en tête de liste** avec badge "Connected". Les déconnectés en dessous, légèrement atténués visuellement.
- **Infos visibles** par device : nom BT + valeur balance (ex: "L+30%" ou "Center"). Pas d'adresse MAC affichée.

### Slider balance
- **Snap magnétique au center (0)** — le slider accroche légèrement au 0 pour éviter de rester à -2/+1 par accident
- **Feedback temps réel** — l'audio change pendant le drag du slider (via le service). La valeur est sauvée dans DataStore quand le doigt est relâché.
- **Labels** : "L" à gauche, "R" à droite du slider, valeur numérique au-dessus du thumb (ex: "L+30" ou "Center" ou "R+15")
- Range : -100 à +100, 0 = center (hérité de Phase 2)

### Toggle auto-apply
- **Switch par device** sur chaque carte — quand désactivé, le coefficient est conservé mais pas appliqué au connect BT
- L'état du toggle est persisté par device (DataStore)

### Permissions flow
- **Écran dédié au premier lancement** — écran plein avec explication en anglais claire : "To detect your Bluetooth headphones, this app needs..." + bouton "Grant permissions"
- Permissions demandées une par une dans l'ordre : BLUETOOTH_CONNECT puis POST_NOTIFICATIONS
- Si permission refusée : afficher un message d'explication avec lien vers les paramètres de l'app
- Remplace le `adb shell pm grant` temporaire de Phase 2

### Langue
- **Anglais** pour toute l'UI (strings en `res/values/strings.xml`)
- Pas d'i18n dans cette phase — préparé pour i18n futur via strings.xml standard Android

### Claude's Discretion
- Material3 color scheme (dark/light mode ou fixed)
- Spacing, padding, typography sizes
- Animation du snap magnétique
- Icon de l'app (actuellement ic_media_play placeholder)
- Structure des Composables (single Activity vs navigation)
- Comment communiquer entre l'UI et le service pour le feedback temps réel du slider (bound service, LiveData/Flow, broadcast)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Service layer (source de vérité Phase 2)
- `app/src/main/java/com/audiobalance/app/service/AudioBalanceService.kt` — Le service existant qui applique la balance. L'UI doit communiquer avec ce service pour : envoyer une nouvelle balance en temps réel, lire l'état du device connecté, et recevoir les mises à jour de connexion BT.
- `app/src/main/java/com/audiobalance/app/data/BalanceRepository.kt` — DataStore access layer. L'UI lit/écrit les coefficients via ce repository.
- `app/src/main/java/com/audiobalance/app/data/DevicePreferences.kt` — DataStore Preferences delegate.
- `app/src/main/java/com/audiobalance/app/util/BalanceMapper.kt` — Conversion -100/+100 → dB.

### UI theme (Phase 0)
- `app/src/main/java/com/audiobalance/app/ui/theme/Theme.kt` — Material3 theme existant
- `app/src/main/java/com/audiobalance/app/ui/theme/Color.kt` — Palette de couleurs
- `app/src/main/java/com/audiobalance/app/ui/theme/Type.kt` — Typography

### Phase context
- `.planning/PROJECT.md` — Contraintes projet
- `.planning/REQUIREMENTS.md` — Requirements AUDIO-01, UI-01, UI-02, UI-03, UI-04, UI-05
- `.planning/ROADMAP.md` §"Phase 3: UI" — Goal et 5 success criteria
- `.planning/phases/02-service-persistence/02-CONTEXT.md` — Décisions Phase 2 (balance range, notification format, etc.)

### External docs (à chercher en recherche)
- Jetpack Compose Slider + Material3 patterns 2025
- Android runtime permissions flow best practices
- Compose state management patterns (ViewModel + StateFlow vs remember)
- Foreground service binding from Compose Activity

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ui/theme/`** (Theme.kt, Color.kt, Type.kt) — Material3 theme prêt à l'emploi
- **`BalanceRepository`** — peut être appelé directement depuis un ViewModel pour lire/écrire les coefficients
- **`AudioBalanceService`** — déjà a un handler `seed_balance` qui applique la balance en temps réel (Intent avec extras). Pattern réutilisable pour le slider temps réel, ou possibilité de binder le service.
- **`BalanceMapper.toGainDb()`** — conversion déjà implémentée

### Established Patterns
- **Version catalog** : dépendances via `gradle/libs.versions.toml`
- **DataStore** : déjà configuré avec `DevicePreferences` delegate
- **Package structure** : `service/`, `data/`, `ui/theme/`, `util/`, `receiver/`, `poc/`

### Integration Points
- **MainActivity.kt** — actuellement démarre le service + affiche le POC UI. Phase 3 remplace le contenu Compose par les vrais screens (device list + permissions)
- **AndroidManifest.xml** — permissions BLUETOOTH_CONNECT et POST_NOTIFICATIONS déjà déclarées mais accordées via adb. Phase 3 ajoute le runtime permission flow.
- **Service ↔ UI** : l'UI doit pouvoir (1) envoyer une balance en temps réel pendant le drag, (2) recevoir la liste des devices connus, (3) être notifiée du device actuellement connecté. Options : bound service, SharedFlow dans un singleton, ou Intent-based comme le seed_balance actuel.

</code_context>

<specifics>
## Specific Ideas

- Le POC code dans `poc/` est jetable — Phase 3 n'y touche pas et ne le supprime pas (il reste comme référence historique)
- L'écran de permissions est un one-shot : une fois les permissions accordées, il ne réapparaît jamais
- Le slider temps réel va générer beaucoup d'appels au service pendant le drag — throttling nécessaire (pas un appel par pixel de mouvement)

</specifics>

<deferred>
## Deferred Ideas

- **i18n avec 10 langues** — l'utilisateur veut internationaliser l'app. Pas en Phase 3 mais préparer les strings dans strings.xml pour faciliter.
- **Section "À propos"** — page d'info sur l'app, auteur, version
- **FAQ** — expliquer la finalité de l'app (compensation de balance BT déséquilibrée)
- **Nicknames personnalisés par device** (v2 UIX-02)
- **Timestamp du dernier apply** (v2 UIX-03)
- **Bouton "Test balance"** (v2 UIX-01)
- **Quick Settings Tile** (v2 ADV-01)

</deferred>

---

*Phase: 03-ui*
*Context gathered: 2026-04-06*
