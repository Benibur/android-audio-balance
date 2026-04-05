# Device Info — Phase 1 AudioEffect POC

## Status

> **Physical device `56191FDCR002NG` values recorded after ear-test session (2026-04-04).**
> FEAS-01 validated: DynamicsProcessing on `mediaPlayer.audioSessionId` produces audible L/R balance shift on Pixel 10 / Android 16 / API 36.

---

## Physical Device (serial: 56191FDCR002NG)

| Property | Value |
|---|---|
| `ro.product.manufacturer` | Google |
| `ro.product.model` | Pixel 10 |
| `ro.product.brand` | google |
| `ro.build.version.release` | 16 |
| `ro.build.version.sdk` | 36 |
| `ro.product.device` | *(not captured — not needed after validation)* |

### Physical Device Interpretation

- **SDK 36 >= 28: DynamicsProcessing API 28+ is available — Approach A is applicable and VALIDATED**
- Manufacturer is Google (Pixel): no Samsung/Xiaomi silent-failure risk on this device
- FEAS-01 verdict: **AUDIBLE SHIFT confirmed** — Full Left and Full Right both produced clear audible balance shifts through user's Bluetooth headphones

### Session ID Note

The on-screen log reported `Session ID: 15521` (not 0). The effect was attached to `mediaPlayer.audioSessionId` (15521), **not** to hardcoded global session 0. This is a critical nuance for Phase 2 architecture:

- **Tested in Plan 01-01:** Attaching DynamicsProcessing to a known `audioSessionId` from an internal MediaPlayer → WORKS
- **NOT yet tested:** Attaching to session 0 directly (for external apps like Spotify/YouTube) → Plan 01-02

Plan 01-02 will test the session 0 / external-session path. If session 0 is silently blocked on this device, Phase 2 must use `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast to capture per-session IDs.

---

## Emulator Reference (emulator-5554 — used during Task 1 before physical device connected)

| Property | Value |
|---|---|
| `ro.product.manufacturer` | Google |
| `ro.product.model` | sdk_gphone64_x86_64 |
| `ro.product.brand` | google |
| `ro.build.version.release` | 15 |
| `ro.build.version.sdk` | 35 |
| `ro.product.device` | emu64xa |

### Emulator Interpretation

- SDK 35 >= 28: DynamicsProcessing API 28+ is available — Approach A is applicable
- Emulator cannot be used for ear-test (no Bluetooth audio output) — physical device 56191FDCR002NG is required for all audible validation
