# TouchFixApp (Pixel 10 Pro)

A specialized Android accessibility service designed to resolve the intermittent touchscreen unresponsiveness (freeze) on the Pixel 10 Pro.

## The Problem

On some Pixel 10 Pro devices, the touchscreen becomes unresponsive after waking from standby or unlocking (especially via Fingerprint). This is caused by a bug in Google's `twoshay` touch pipeline and the `deeptouch` ML classifier.

## Feature Suite (v14)

### 1. Targeted Fingerprint Fixes
- **Post-Fingerprint Reset**: A multi-stage reset sequence specifically timed for the moments after a fingerprint unlock (50ms, 300ms, 600ms, 1000ms).
- **Touch HAL Restart**: Attempts to restart the `vendor.google.touch_offload` services.
- **Phantom Swipe Flood**: Injects high-speed swipe gestures to exercise the full input pipeline.
- **sysfs Touch Reset**: Direct hardware-level reset via kernel nodes.

### 2. Intelligent Watchdog
- **Escalating Auto-Fix**: Automatically detects if touch remains dead after wake and escalates through 4 severity levels (Settings → Swipes → HAL → sysfs → Screen-Cycle).

### 3. Internationalization (New in v14)
- Full support for **English** and **German**. The app automatically adapts to your system language.

## Installation & Setup

1.  **Install the APK**: Build the project or download the latest release.
2.  **Enable Accessibility Service**: Go to `Settings > Accessibility > TouchFix` and turn it on.
3.  **Grant Permissions via ADB**:
    ```bash
    adb shell pm grant de.olus.touchfix android.permission.WRITE_SECURE_SETTINGS
    ```

## How to Use

Open the app to toggle the fix options. **Post-Fingerprint Reset** and **Escalating Auto-Fix** are recommended for most users. The in-app console provides real-time feedback on every action taken by the service.

## License

This project is open-source. Feel free to use and modify it.
