# Milestones

## v1.0 MVP (Shipped: 2026-04-07)

**Phases:** 4 | **Plans:** 12 | **Commits:** 85 | **LOC:** 1,718 Kotlin (21 files)
**Timeline:** 2026-04-01 → 2026-04-06 (6 days)
**Device:** Pixel 10 (Android 16, API 36)

**Key accomplishments:**

1. **AudioEffect breakthrough** — `DynamicsProcessing(0, 0, config)` session 0 global fonctionne sans root sur Android 16, contrairement à la doc Google "deprecated post-Android 10"
2. **Foreground service** — Monitoring BT A2DP, persistence DataStore par MAC, balance auto-apply avec délai 1s connect / 2s disconnect
3. **Compose UI** — Device list avec slider temps réel (snap center, throttle 50ms), toggle auto-apply par device, permissions flow dédié
4. **Boot receiver** — Service démarre au boot, balance prête dès la première connexion BT
5. **DP auto-recovery** — Recréation automatique du DynamicsProcessing si perte de contrôle (conflit avec autre app AudioEffect)
6. **10 bugs trouvés et fixés** en cours de route : Config DP silencing, RECEIVER_NOT_EXPORTED, currentDeviceMac, service crash sans permission, BootReceiver sans guard, slider/toggle cross-device, DP dead after reconnect

**Archive:** `.planning/milestones/v1.0-ROADMAP.md`, `.planning/milestones/v1.0-REQUIREMENTS.md`

---
