# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [v1.1] — 2026-04-13

### Added
- Per-device gain offset slider (dB) in the device card, persisted per MAC address (GAIN-01 … GAIN-05).
- Composed gain offset + balance into a single DynamicsProcessing call per channel.
- FAQ / About screen accessible via the info icon in the device list top bar (FAQ-01 … FAQ-04).
- Troubleshooting section in FAQ covering AudioEffect session 0 conflicts.

### Changed
- Foreground notification now displays the gain offset value when non-zero.

## [v1.0] — 2026-04-07

### Added
- Initial MVP: per-device stereo balance slider, auto-applied on Bluetooth A2DP connect.
- Foreground service handling BT connect/disconnect broadcasts.
- DataStore persistence of balance settings keyed by device MAC address.
- Device list UI with expandable device cards.
