# TouchFixApp (Pixel 10 Pro)

A specialized Android accessibility service designed to resolve the intermittent touchscreen unresponsiveness (freeze) on the Pixel 10 Pro.

## The Problem

On some Pixel 10 Pro devices, the touchscreen becomes unresponsive for ~15 seconds after waking from standby or unlocking. This is caused by a bug in Google's `twoshay` touch pipeline and the `deeptouch` ML classifier, which incorrectly flags initial touch events as "abnormal" or "deep press" states, effectively blocking input.

## Feature Suite (v11)

To combat the persistent `twoshay` freeze on the Pixel 10 Pro, this app offers multiple defensive layers:

### 1. The "Ultra Kick" (Defensive Reset)
When the screen wakes, a high-speed sequence of system triggers forces a reconfiguration of the input pipeline:
- **Pointer Location Toggle**: Forces `InputDispatcher` to rebuild viewports.
- **Sensitivity/Resolution/Density Pulse**: Resets kernel-level touch parameters.
- **Haptic Feedback**: Confirms the kick has fired.

### 2. Proactive Defense
- **Proactive Ping**: Automatically "taps" the screen every 5 seconds (invisible coordinates) to keep the touch driver and `deeptouch` classifier awake.
- **Wake Lock**: Prevents the CPU from entering deep sleep, reducing the chance of a HAL deadlock during wake-up transitions.
- **Watchdog & Auto-Toggle**: Automatically detects if touch remains dead after a wake event and re-triggers the reset or even toggles the screen as a last resort.

## Installation & Setup

1.  **Install the APK**: Build the project or download the latest release.
2.  **Enable Accessibility Service**: Go to `Settings > Accessibility > TouchFix` and turn it on.
3.  **Grant Permissions via ADB**:
    ```bash
    # For Resolution/Density/Sensitivity resets:
    adb shell pm grant de.olus.touchfix android.permission.WRITE_SECURE_SETTINGS
    
    # For Pointer Location toggle (Ultra Kick):
    adb shell appops set de.olus.touchfix WRITE_SETTINGS allow
    ```

## How to Use

Open the app to toggle the new proactive features. The **Ultra Kick** is recommended for all users. Use **Wake Lock** and **Proactive Ping** if you still experience freezes after standard resets.

## License

This project is open-source. Feel free to use and modify it.
