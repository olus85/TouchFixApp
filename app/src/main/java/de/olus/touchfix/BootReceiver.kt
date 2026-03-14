package de.olus.touchfix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("TouchFix", "Boot completed – TouchFixService will auto-start when accessibility is enabled")
            // The AccessibilityService starts automatically if it's enabled in settings.
            // No manual action needed here.
        }
    }
}
