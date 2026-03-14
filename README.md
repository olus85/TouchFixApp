# TouchFixApp (Pixel 10 Pro)

A specialized Android accessibility service designed to resolve the intermittent touchscreen unresponsiveness (freeze) on the Pixel 10 Pro.

## The Problem

On some Pixel 10 Pro devices, the touchscreen becomes unresponsive for ~15 seconds after waking from standby or unlocking. This is caused by a bug in Google's `twoshay` touch pipeline and the `deeptouch` ML classifier, which incorrectly flags initial touch events as "abnormal" or "deep press" states, effectively blocking input.

## The Solution: "Input Kick" (v6)

Instead of a disruptive power toggle, this app uses a multi-stage "kick" to force the Android `InputReader` and `InputDispatcher` to reconfigure their viewports and reset the kernel-level touch handler:

1.  **Sensitivity Pulse**: Toggles "Screen Protector Mode" to reset kernel touch parameters.
2.  **Resolution Shift**: Momentarily changes display size by 1 pixel to force a complete viewport rebuild.
3.  **Density Flip**: Toggles display density (+1 DPI) for a final reconfiguration signal.

The sequence is nearly invisible and **automatically aborts** if any touch is detected during the process.

## Installation & Setup

1.  **Install the APK**: Build the project using Android Studio or download the latest release.
2.  **Enable Accessibility Service**: Go to `Settings > Accessibility > TouchFix` and turn it on.
3.  **Grant Secure Permissions**: This app requires the `WRITE_SECURE_SETTINGS` permission to modify display density and resolution. Run the following command via ADB:

    ```bash
    adb shell pm grant de.olus.touchfix android.permission.WRITE_SECURE_SETTINGS
    ```

## How to use

Once the service is active and permission is granted, the app works automatically in the background. Every time the screen turns on, it will perform the "Input Kick" unless you touch the screen immediately.

## License

This project is open-source. Feel free to use and modify it.
