# Device Info — Phase 1 AudioEffect POC

## Status

> **PHYSICAL DEVICE NOT CONNECTED at time of file creation.**
> Device serial `56191FDCR002NG` was not listed by `adb devices` during Task 1 execution.
> The physical device properties below are **PENDING** — they must be filled in when the device is reconnected before the ear test.
> The emulator data is recorded for reference (confirms API level compatibility on test infrastructure).

---

## Physical Device — PENDING (serial: 56191FDCR002NG)

| Property | Value |
|---|---|
| `ro.product.manufacturer` | *(run: `adb -s 56191FDCR002NG shell getprop ro.product.manufacturer`)* |
| `ro.product.model` | *(run: `adb -s 56191FDCR002NG shell getprop ro.product.model`)* |
| `ro.product.brand` | *(run: `adb -s 56191FDCR002NG shell getprop ro.product.brand`)* |
| `ro.build.version.release` | *(run: `adb -s 56191FDCR002NG shell getprop ro.build.version.release`)* |
| `ro.build.version.sdk` | *(run: `adb -s 56191FDCR002NG shell getprop ro.build.version.sdk`)* |
| `ro.product.device` | *(run: `adb -s 56191FDCR002NG shell getprop ro.product.device`)* |

### Physical Device Interpretation

*(To be filled after device reconnect)*

- If SDK >= 28: DynamicsProcessing API 28+ is available — Approach A is applicable
- If SDK 26–27: DynamicsProcessing NOT available — document as hardware limitation, POC must use Equalizer probe only
- If manufacturer is Samsung or Xiaomi: **HIGH SILENT-FAILURE RISK on session 0** (per research Pitfall 1)

---

## Emulator Reference (emulator-5554 — confirmed connected during Task 1)

| Property | Value |
|---|---|
| `ro.product.manufacturer` | Google |
| `ro.product.model` | sdk_gphone64_x86_64 |
| `ro.product.brand` | google |
| `ro.build.version.release` | 15 |
| `ro.build.version.sdk` | 35 |
| `ro.product.device` | emu64xa |

### Emulator Interpretation

- **SDK 35 >= 28: DynamicsProcessing API 28+ is available — Approach A is applicable**
- Manufacturer is Google (AOSP emulator): no Samsung/Xiaomi silent-failure risk on this device
- Emulator cannot be used for ear-test (no Bluetooth audio output) — physical device 56191FDCR002NG is required

---

## Next Step

Connect physical device `56191FDCR002NG` via USB, authorize ADB on-device, then run:

```bash
adb -s 56191FDCR002NG shell getprop ro.product.manufacturer
adb -s 56191FDCR002NG shell getprop ro.product.model
adb -s 56191FDCR002NG shell getprop ro.product.brand
adb -s 56191FDCR002NG shell getprop ro.build.version.release
adb -s 56191FDCR002NG shell getprop ro.build.version.sdk
adb -s 56191FDCR002NG shell getprop ro.product.device
```

Update the "Physical Device — PENDING" table above with real values before the ear test.
