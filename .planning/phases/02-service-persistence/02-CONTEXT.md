# Phase 2: Service + Persistence - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Foreground service qui monitore les connexions/déconnexions BT A2DP, stocke les coefficients de balance par adresse MAC device, et applique automatiquement le bon coefficient quand un device connu se connecte — le tout via un seul `DynamicsProcessing(0, 0, config)` session 0 global tenu par le service.

Hors scope :
- UI finale (Phase 3) — cette phase ne construit PAS de screens Compose, juste le service + persistance. Seuls les tests manuels via adb ou logs valident le comportement.
- Export/import JSON (v2)
- Toggle global activation/désactivation (v2)
- Quick Settings Tile (v2)

</domain>

<decisions>
## Implementation Decisions

### Notification foreground service
- **Format minimal** quand device connecté : `"{device_name} • Balance: L+{value}%"` (ou `R+{value}%` ou `Center`)
- **Aucune action rapide** dans la notification — pour modifier la balance, l'utilisateur ouvre l'app
- **Service toujours actif** — le service tourne en permanence (START_STICKY + BOOT_COMPLETED). Quand aucun device BT connecté, notification minimale type "Audio Balance • En attente". Raison : Android 12+ interdit le démarrage de foreground service depuis un broadcast BT background. Un service permanent est la seule approche fiable pour ne jamais rater une connexion BT.

### Déconnexion BT
- **Délai de 2 secondes** après réception du broadcast de déconnexion A2DP, puis reset balance à center (0dB/0dB) SI le device n'est pas revenu entre-temps
- **Si reconnexion dans les 2s** (micro-déconnexion BT fréquente) : ne rien changer, garder la balance active
- **Release du DP** : NE PAS libérer le DynamicsProcessing à la déconnexion — juste reset à center. Le DP est tenu par le service pour la durée de vie du service, pas du device BT.

### Reconnexion BT
- **Délai de 1 seconde** après réception du broadcast de connexion A2DP avant d'appliquer la balance
- Raison : laisser le routing audio BT se stabiliser. Le profil A2DP est parfois annoncé avant que le sink audio soit prêt.
- Ensuite : lire le coefficient stocké pour cette adresse MAC et l'appliquer via `setInputGainbyChannel`

### Balance par device
- **Range utilisateur** : -100 (tout à gauche) à +100 (tout à droite), 0 = center
- **Mapping interne** vers dB par Claude (détail d'implémentation)
- **Nouveau device inconnu** : balance neutre (0 = center) par défaut. Le device est ajouté à la liste des devices connus avec balance 0. L'utilisateur peut configurer en Phase 3 (UI).
- **Persistance** : adresse MAC → coefficient, stocké via DataStore ou SharedPreferences (Claude décide)
- Les coefficients survivent au kill du service et au redémarrage du téléphone

### Cycle de vie du service
- **START_STICKY** : si Android tue le service (mémoire basse), il est relancé automatiquement
- **Démarrage au boot** : inclure BOOT_COMPLETED receiver — le service démarre au démarrage du téléphone (note : c'est LIFE-01 des requirements v2, inclus ici par décision utilisateur car simple à ajouter)
- **DynamicsProcessing lifecycle** : une seule instance DP créée au démarrage du service, tenue pour toute la durée de vie du service, releasée seulement quand le service est détruit
- Le DP est créé dès le service start (pas au premier BT connect) — raison : le timing "effet existe AVANT la session media" est critique selon les findings Phase 1. Si le service tourne déjà, le DP existe quand l'app de musique démarre.

### Architecture audio (locked from Phase 1)
- **Constructor** : `DynamicsProcessing(0, 0, config)` avec session ID = 0 littéral (global output mix)
- **Config** : `DynamicsProcessing.Config.Builder(0, 2, false, 0, false, 0, false, 0, false)` — toutes stages désactivées
- **Balance** : `setInputGainbyChannel(0, leftDb)` / `setInputGainbyChannel(1, rightDb)` — voir POC-RESULTS.md pour le détail du mapping
- **Permission** : `MODIFY_AUDIO_SETTINGS` (déjà dans AndroidManifest.xml)

### Claude's Discretion
- Choix entre DataStore Preferences et SharedPreferences pour la persistance
- Format exact du mapping -100/+100 vers dB
- Type de foreground service (MEDIA_PLAYBACK vs SPECIAL_USE — contraintes Android 14+)
- Strategy pour gérer les restrictions de foreground service sur Android 12+ au boot
- Structure du code (packages, classes, interfaces)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Audio architecture (CRITICAL — source de vérité Phase 2)
- `.planning/phases/01-audioeffect-poc/POC-RESULTS.md` — Pattern d'implémentation confirmé : constructor exact, Config, gain calls, gotchas, architecture recommendation. **LIRE INTÉGRALEMENT avant de planifier.**
- `app/src/main/java/com/audiobalance/app/poc/AudioEffectPoc.kt` — Code de référence fonctionnel (POC jetable mais API pattern correct)

### Phase context
- `.planning/PROJECT.md` — Contraintes projet, core value
- `.planning/REQUIREMENTS.md` — Requirements AUDIO-02, BT-01, BT-02, BT-03, SVC-01, SVC-02, DATA-01, DATA-02
- `.planning/ROADMAP.md` §"Phase 2: Service + Persistence" — Goal et success criteria (5 critères)
- `.planning/phases/01-audioeffect-poc/DEVICE-INFO.md` — Pixel 10, Android 16, API 36

### External docs (à chercher en recherche)
- Android foreground service types et restrictions Android 12+/14+ (FGS type changes)
- BroadcastReceiver pour BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
- DataStore Preferences vs SharedPreferences patterns 2025
- BOOT_COMPLETED receiver + foreground service restrictions
- NotificationChannel + notification builder patterns

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`poc/AudioEffectPoc.kt`** — Contient le pattern DynamicsProcessing exact qui fonctionne. Le code Phase 2 repartira de zéro (pas de refactoring du POC) mais DOIT utiliser le même constructor, la même Config, et le même pattern de gain. L'objet `GlobalDpHolder` singleton est le prototype du pattern lifecycle à réutiliser.
- **`poc/SessionBroadcastReceiver.kt`** — Pattern de BroadcastReceiver avec intent-filter. Réutilisable comme modèle pour le receiver BT A2DP.
- **`AndroidManifest.xml`** — A déjà `MODIFY_AUDIO_SETTINGS` permission et le receiver déclaré.

### Established Patterns
- **Version catalog** : dépendances via `gradle/libs.versions.toml` (pattern Phase 0)
- **Package** : `com.audiobalance.app` — le service sera dans `com.audiobalance.app.service` ou similaire
- **Compose + Material3** : configuré mais pas utilisé pour cette phase (Phase 2 = service headless)

### Integration Points
- **MainActivity.kt** — devra démarrer le service au premier lancement. Mais l'UI de contrôle est Phase 3.
- **AndroidManifest.xml** — ajouter : le foreground service, le BT receiver, le BOOT_COMPLETED receiver, les permissions BT
- **build.gradle.kts** — ajouter : DataStore ou SharedPreferences dépendances si nécessaire

</code_context>

<specifics>
## Specific Ideas

- Le POC (poc/) est jetable — Phase 2 code le service proprement, pas un refactoring du POC. Mais les findings et patterns du POC sont la source de vérité.
- L'utilisateur a ses écouteurs BT personnels comme test device — la balance doit s'appliquer automatiquement quand ils se connectent, sans ouvrir l'app.
- Le service est "headless" dans cette phase — pas d'UI de configuration. L'utilisateur configure via Phase 3. Ici on valide que la mécanique automatique fonctionne (détecter BT → appliquer balance → persister).
- Pour tester : l'utilisateur stockera un coefficient via adb ou un simple bouton temporaire dans MainActivity, puis testera la reconnexion BT.

</specifics>

<deferred>
## Deferred Ideas

- BOOT_COMPLETED — normalement v2 (LIFE-01) mais inclus en Phase 2 par décision utilisateur
- Toggle global on/off (kill switch) — v2 (LIFE-02)
- Action rapide dans la notification — exclu de cette phase, pourra être ajoutée en v2 (UIX-04)
- Quick Settings Tile — v2 (ADV-01)
- Export/import JSON — v2 (DATA-03, DATA-04)

</deferred>

---

*Phase: 02-service-persistence*
*Context gathered: 2026-04-06*
