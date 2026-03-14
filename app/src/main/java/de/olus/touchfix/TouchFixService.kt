package de.olus.touchfix

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

/**
 * TouchFixService v6 – Multi-Stage Reset ("Input Kick")
 *
 * Strategy: A progressive sequence of system triggers to "wake up" the touch stack.
 * 1. Sensitivity Pulse: Toggle "Display Protector Mode" to reset kernel touch parameters.
 * 2. Resolution Shift: Change display size by 1 pixel to force InputReader viewport rebuild.
 * 3. Density Flip: Toggle DPI to force InputDispatcher reconfiguration.
 *
 * Sequence is aborted immediately if touch interaction is detected.
 */
class TouchFixService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchFix"
        private const val CHANNEL_ID = "touchfix_status"
        private const val NOTIFICATION_ID = 1001
        
        // Settings Keys
        private const val DEEP_PRESS_SETTING = "deep_press_enabled"
        private const val DENSITY_SETTING = "display_density_forced"
        private const val SENSITIVITY_SETTING = "touch_sensitivity_enabled" // High sensitivity / Screen Protector Mode
        private const val SIZE_SETTING = "display_size_forced"

        // Timing constants
        private const val INITIAL_DELAY_MS = 400L
        private const val PULSE_DURATION_MS = 200L
        private const val STAGE_SPACING_MS = 300L

        var isRunning = false
            private set
        var lastStatus = "Idle"
            private set
        var fixCount = 0
            private set
        var screenOnCount = 0
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null
    private var originalDensity: Int = 0
    private var originalSize: String? = null
    private var touchDetectedThisCycle = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        
        // Capture baseline
        originalDensity = resources.displayMetrics.densityDpi
        originalSize = Settings.Global.getString(contentResolver, SIZE_SETTING)
        
        Log.i(TAG, "══════════════════════════════════════")
        Log.i(TAG, "TouchFixService v6 connected (Input Kick)")
        Log.i(TAG, "Original Density: $originalDensity")
        Log.i(TAG, "Original Size: ${originalSize ?: "Default"}")
        Log.i(TAG, "══════════════════════════════════════")

        createNotificationChannel()
        showNotification("TouchFix v6", "Multi-Stage Reset aktiv")

        ensureDeepPressDisabled()
        registerScreenReceiver()
        updateStatus("Bereit – Input-Kick Modus")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                if (!touchDetectedThisCycle) {
                    touchDetectedThisCycle = true
                    Log.i(TAG, "Touch interaction detected! Aborting/Skipping sequence.")
                    handler.removeCallbacksAndMessages("INPUT_KICK")
                    // If we were in the middle of a flip, reset immediately
                    cleanupAllFlips()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "TouchFixService interrupted")
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        cleanupAllFlips()
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        super.onDestroy()
        Log.i(TAG, "TouchFixService destroyed")
    }

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> onScreenOn()
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_USER_PRESENT -> onUserPresent()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun onScreenOn() {
        screenOnCount++
        touchDetectedThisCycle = false
        Log.i(TAG, "───── SCREEN ON #$screenOnCount ─────")
        
        ensureDeepPressDisabled()
        
        // Start the kick sequence
        startMultiStageReset()
    }

    private fun onScreenOff() {
        handler.removeCallbacksAndMessages(null)
        touchDetectedThisCycle = false
        cleanupAllFlips()
        Log.i(TAG, "───── SCREEN OFF ─────")
    }

    private fun onUserPresent() {
        Log.i(TAG, "───── USER PRESENT ─────")
    }

    /**
     * The v6 "Input Kick" Sequence.
     * Each stage is scheduled with a unique token "INPUT_KICK" for easy cancellation.
     */
    private fun startMultiStageReset() {
        fixCount++
        updateStatus("Reset #$fixCount startet…")

        // Stage 1: Sensitivity Pulse (T + 300ms)
        handler.postAtTime({
            if (touchDetectedThisCycle) return@postAtTime
            Log.d(TAG, "Kick Stage 1: Sensitivity Pulse")
            try {
                Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 1)
                handler.postDelayed({
                    Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 0)
                }, PULSE_DURATION_MS)
            } catch (e: Exception) { Log.e(TAG, "Stage 1 failed", e) }
        }, "INPUT_KICK", System.currentTimeMillis() + INITIAL_DELAY_MS)

        // Stage 2: Resolution Shift (T + 600ms)
        handler.postAtTime({
            if (touchDetectedThisCycle) return@postAtTime
            Log.d(TAG, "Kick Stage 2: Resolution Shift")
            try {
                // Shift resolution by 1 pixel height
                val dm = resources.displayMetrics
                val targetSize = "${dm.widthPixels}x${dm.heightPixels + 1}"
                Settings.Global.putString(contentResolver, SIZE_SETTING, targetSize)
                
                handler.postDelayed({ 
                    Settings.Global.putString(contentResolver, SIZE_SETTING, null) 
                }, PULSE_DURATION_MS)
            } catch (e: Exception) { Log.e(TAG, "Stage 2 failed", e) }
        }, "INPUT_KICK", System.currentTimeMillis() + INITIAL_DELAY_MS + STAGE_SPACING_MS)

        // Stage 3: Density Flip (T + 900ms)
        handler.postAtTime({
            if (touchDetectedThisCycle) return@postAtTime
            Log.d(TAG, "Kick Stage 3: Density Flip")
            try {
                val targetDensity = originalDensity + 1
                Settings.Secure.putString(contentResolver, DENSITY_SETTING, targetDensity.toString())
                
                handler.postDelayed({ 
                    resetDensity()
                    updateStatus("Reset #$fixCount abgeschlossen ✓")
                }, PULSE_DURATION_MS)
            } catch (e: Exception) { Log.e(TAG, "Stage 3 failed", e) }
        }, "INPUT_KICK", System.currentTimeMillis() + INITIAL_DELAY_MS + STAGE_SPACING_MS * 2)
    }

    private fun cleanupAllFlips() {
        try {
            Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 0)
            Settings.Global.putString(contentResolver, SIZE_SETTING, null)
            resetDensity()
        } catch (_: Exception) {}
    }

    private fun resetDensity() {
        try {
            Settings.Secure.putString(contentResolver, DENSITY_SETTING, null)
        } catch (_: Exception) {}
    }

    private fun ensureDeepPressDisabled() {
        try {
            val current = Settings.Secure.getInt(contentResolver, DEEP_PRESS_SETTING, 1)
            if (current != 0) {
                Settings.Secure.putInt(contentResolver, DEEP_PRESS_SETTING, 0)
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TouchFix Status",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun showNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun updateStatus(status: String) {
        lastStatus = status
        Log.i(TAG, "Status: $status")
        showNotification("TouchFix", status)
    }
}
