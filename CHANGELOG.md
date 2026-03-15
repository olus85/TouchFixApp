# Changelog - TouchFixApp

All notable changes to the TouchFix project.

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
