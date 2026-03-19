# Changelog - TouchFixApp

All notable changes to the TouchFix project.

## [v13] - 2026-03-19 "Fingerprint-Fix Edition"
### Changed
- **ALLE 10 alten Switches entfernt** – keiner hat den Bug zuverlässig behoben.
### Added
- **Post-Fingerprint Reset**: Gezielter 4-Stufen-Reset nach Fingerprint-Unlock (50/300/600/1000ms Timing).
- **Touch HAL Restart**: Versucht den Vendor Touch-HAL-Service neu zu starten.
- **Phantom Swipe Flood**: Injiziert 6 diverse Swipe-Gesten statt punktueller Taps bei Screen-On.
- **sysfs Touch Reset**: Schreibt direkt auf Hardware-Reset-Nodes des Touch-Controllers.
- **Escalating Auto-Fix**: Intelligenter Watchdog mit 4 Eskalationsstufen (Settings → Swipes → HAL → sysfs → Screen-Cycle).
- **Live-Konsole**: Scrollbare Echtzeit-Konsole mit Zeitstempeln und Farbcodierung.

## [v12] - 2026-03-19
### Added
- **Touch Alive Keep**: Hält Touch-Treiber bei Screen-Off aktiv (WakeLock + periodische Touch-Events alle 2s).
- **Input Device Reset**: Setzt Input-Devices via `dumpsys input` zurück (disable/re-enable).
- **Aggressive Pre-Wake**: Sofortiger Kick bei Screen-On (0ms Delay) mit 7 Multi-Position Taps.
- **Double-Unlock Kick**: Doppelter Reset-Zyklus bei Unlock mit 200ms Pause.
- **Touch Input Inject**: 15 synthetische Taps auf verschiedene Positionen in 3s nach Screen-On.
- **Live-Konsole**: Scrollbare Echtzeit-Konsole in der App mit Zeitstempeln, Farbcodierung und 200-Zeilen Ringpuffer.
### Changed
- Alle Service-Aktionen loggen jetzt detailliert in die In-App-Konsole.
- UI: Experimentelle Fixes in eigenem Card mit Beschreibungen.
- Statistik-Anzeige mit aktiver Fix-Zählung.

## [v11] - 2026-03-15
### Added
- **Proactive Ping**: Periodic invisible touch events to keep the driver active.
- **Wake Lock Support**: Optional CPU wake lock to prevent driver power-down deadlocks.
- **Emergency Screen Toggle**: Option to automatically cycle screen power if resets fail.
- **Ultra Mode UI**: New settings toggles in MainActivity for fine-grained control.
### Changed
- UI cleanup: Removed redundant status lines, replaced with toggle switches.
- Improved watchdog timing and reliability.

## [v8] - "The Nuclear Option"
### Added
- **Pointer Location Force**: Toggling `pointer_location` to force-rebuild `InputDispatcher` viewports.
- **Watchdog Mechanism**: Automatic re-kick after 2 seconds if no touch is detected.
### Changed
- Refined multi-stage timing for faster recovery.

## [v7] - Fingerprint Optimization
### Added
- **Unlock Trigger**: Immediate reset on `ACTION_USER_PRESENT`.
### Fixed
- Improved logic to prevent resets while interacting with the fingerprint sensor.

## [v6] - Multi-Stage Kick
### Added
- Initial implementation of the 3-stage reset (Sensitivity, Resolution, Density).
