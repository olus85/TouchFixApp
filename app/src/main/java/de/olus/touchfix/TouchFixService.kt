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
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class TouchFixService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchFix"
        private const val CHANNEL_ID = "touchfix_status"
        private const val NOTIFICATION_ID = 1001
        private const val DEEP_PRESS_SETTING = "deep_press_enabled"
        private const val DENSITY_SETTING = "display_density_forced"
        private const val SENSITIVITY_SETTING = "touch_sensitivity_enabled"
        private const val SIZE_SETTING = "display_size_forced"
        private const val POINTER_LOCATION_SETTING = "pointer_location"
        private const val TOUCH_STATS_SETTING = "touch_statistics"
        private const val STYLUS_FINGER_SETTING = "stylus_finger_as_finger"
        private const val RETRY_COUNT = 3
        private const val WATCHDOG_DELAY_MS = 1500L

        var isRunning = false
            private set
        var fixCount = 0
            private set
        var failCount = 0
            private set
        var pingCount = 0
            private set
    }

    private lateinit var settings: TouchFixSettings
    private val handler = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null
    private var originalDensity: Int = 0
    private var touchDetectedThisCycle = false
    private var lastFixTime = 0L
    private var retryAttempt = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var isScreenOn = false
    private var pingRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        settings = TouchFixSettings.getInstance(this)
        originalDensity = resources.displayMetrics.densityDpi
        Log.i(TAG, "TouchFixService v11 connected")
        createNotificationChannel()
        updateMainNotification()
        ensureDeepPressDisabled()
        registerScreenReceiver()
        if (settings.proactivePingEnabled || settings.wakeLockEnabled) {
            startProactiveMode()
        }
        updateStatus("Bereit")
    }

    private fun startProactiveMode() {
        if (settings.wakeLockEnabled) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TouchFix:Proactive")
            try {
                wakeLock?.acquire(10 * 60 * 1000L)
                Log.i(TAG, "WakeLock ON")
            } catch (e: Exception) {
                Log.e(TAG, "WakeLock failed", e)
            }
        }
        if (settings.proactivePingEnabled) {
            val intervalMs = settings.pingIntervalSeconds * 1000L
            pingRunnable = object : Runnable {
                override fun run() {
                    if (isScreenOn) {
                        sendPing()
                    }
                    handler.postDelayed(this, intervalMs)
                }
            }
            handler.post(pingRunnable!!)
            Log.i(TAG, "Ping every ${settings.pingIntervalSeconds}s")
        }
    }

    private fun sendPing() {
        pingCount++
        try {
            Runtime.getRuntime().exec("input tap 1 1")
        } catch (e: Exception) {
            Log.e(TAG, "Ping failed", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                if (!touchDetectedThisCycle && !isKeyguardLocked()) {
                    touchDetectedThisCycle = true
                    handler.removeCallbacksAndMessages("INPUT_KICK")
                    handler.removeCallbacksAndMessages("WATCHDOG")
                    cleanupAllFlips()
                }
            }
        }
    }

    private fun isKeyguardLocked(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        return km.isKeyguardLocked
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        pingRunnable?.let { handler.removeCallbacks(it) }
        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }
        cleanupAllFlips()
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        onScreenOn()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        onScreenOff()
                    }
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
        Log.i(TAG, "SCREEN ON")
        touchDetectedThisCycle = false
        retryAttempt = 0

        // Extra pings on screen on
        if (settings.proactivePingEnabled) {
            sendPing()
            sendPing()
            sendPing()
        }

        // Ultra kick if enabled
        if (settings.ultraKickEnabled) {
            startUltraReset()

            handler.postDelayed({
                if (!touchDetectedThisCycle && retryAttempt < RETRY_COUNT) {
                    retryAttempt++
                    startUltraReset()
                } else if (!touchDetectedThisCycle && retryAttempt >= RETRY_COUNT && settings.screenToggleEnabled) {
                    triggerScreenToggle()
                } else if (!touchDetectedThisCycle && retryAttempt >= RETRY_COUNT) {
                    showBugNotification()
                }
            }, "WATCHDOG", WATCHDOG_DELAY_MS)
        }
    }

    private fun onScreenOff() {
        handler.removeCallbacksAndMessages(null)
        touchDetectedThisCycle = false
        cleanupAllFlips()
    }

    private fun onUserPresent() {
        Log.i(TAG, "USER UNLOCKED")
        if (settings.ultraKickEnabled) {
            startUltraReset()
        }
    }

    private fun triggerScreenToggle() {
        if (isKeyguardLocked()) {
            showBugNotification()
            return
        }
        try {
            Runtime.getRuntime().exec("input keyevent 26")
            handler.postDelayed({
                Runtime.getRuntime().exec("input keyevent 26")
            }, 400)
        } catch (e: Exception) {
            Log.e(TAG, "Toggle failed", e)
            showBugNotification()
        }
    }

    private fun showBugNotification() {
        failCount++
        Log.e(TAG, "BUG NOT FIXED! Fail: $failCount")
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("TouchFix Fehler!")
            .setContentText("Touch reagiert nicht (Fail #$failCount)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, notification)
        updateStatus("Fail #$failCount")
    }

    private fun startUltraReset() {
        val now = System.currentTimeMillis()
        if (now - lastFixTime < 200) return
        lastFixTime = now
        fixCount++

        val baseDelay = 30L

        // Stage 1: Haptic
        handler.postAtTime({
            try {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, 100))
            } catch (e: Exception) {
                Log.e(TAG, "Haptic failed", e)
            }
        }, "INPUT_KICK", System.currentTimeMillis() + baseDelay)

        // Stage 2: Sensitivity
        handler.postAtTime({
            toggleSetting(Settings.Secure.getUriFor(SENSITIVITY_SETTING), 1, 0)
        }, "INPUT_KICK", System.currentTimeMillis() + baseDelay + 60)

        // Stage 3: Resolution
        handler.postAtTime({
            val dm = resources.displayMetrics
            Settings.Global.putString(contentResolver, SIZE_SETTING, "${dm.widthPixels}x${dm.heightPixels + 1}")
            handler.postDelayed({
                Settings.Global.putString(contentResolver, SIZE_SETTING, null)
            }, 40)
        }, "INPUT_KICK", System.currentTimeMillis() + baseDelay + 120)

        // Stage 4: Density
        handler.postAtTime({
            Settings.Secure.putString(contentResolver, DENSITY_SETTING, (originalDensity + 1).toString())
            handler.postDelayed({
                resetDensity()
            }, 40)
        }, "INPUT_KICK", System.currentTimeMillis() + baseDelay + 200)

        // Stage 5: Touch Stats
        handler.postAtTime({
            try {
                Settings.System.putInt(contentResolver, TOUCH_STATS_SETTING, 1)
                handler.postDelayed({
                    Settings.System.putInt(contentResolver, TOUCH_STATS_SETTING, 0)
                }, 40)
            } catch (e: Exception) {
                Log.e(TAG, "Step 5 failed", e)
            }
        }, "INPUT_KICK", System.currentTimeMillis() + baseDelay + 280)

        // Stage 6: Stylus Setting
        handler.postAtTime({
            try {
                Settings.Secure.putInt(contentResolver, STYLUS_FINGER_SETTING, 1)
                handler.postDelayed({
                    Settings.Secure.putInt(contentResolver, STYLUS_FINGER_SETTING, 0)
                }, 40)
            } catch (e: Exception) {
                Log.e(TAG, "Step 6 failed", e)
            }
        }, "INPUT_KICK", System.currentTimeMillis() + baseDelay + 360)

        // Stage 7: Pointer Location
        handler.postAtTime({
            try {
                Settings.System.putInt(contentResolver, POINTER_LOCATION_SETTING, 1)
                handler.postDelayed({
                    Settings.System.putInt(contentResolver, POINTER_LOCATION_SETTING, 0)
                    updateStatus("Kick #$fixCount OK")
                }, 40)
            } catch (e: Exception) {
                Log.e(TAG, "Step 7 failed", e)
            }
        }, "INPUT_KICK", System.currentTimeMillis() + baseDelay + 440)
    }

    private fun toggleSetting(uri: android.net.Uri, onValue: Int, offValue: Int) {
        try {
            Settings.Secure.putInt(contentResolver, uri.pathSegments.last(), onValue)
            handler.postDelayed({
                Settings.Secure.putInt(contentResolver, uri.pathSegments.last(), offValue)
            }, 50)
        } catch (e: Exception) {
            Log.e(TAG, "Toggle failed", e)
        }
    }

    private fun cleanupAllFlips() {
        try {
            Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 0)
            Settings.Global.putString(contentResolver, SIZE_SETTING, null)
            Settings.System.putInt(contentResolver, POINTER_LOCATION_SETTING, 0)
            Settings.System.putInt(contentResolver, TOUCH_STATS_SETTING, 0)
            resetDensity()
        } catch (_: Exception) {
        }
    }

    private fun resetDensity() {
        try {
            Settings.Secure.putString(contentResolver, DENSITY_SETTING, null)
        } catch (_: Exception) {
        }
    }

    private fun ensureDeepPressDisabled() {
        try {
            Settings.Secure.putInt(contentResolver, DEEP_PRESS_SETTING, 0)
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "TouchFix Status", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateMainNotification() {
        val sb = StringBuilder()
        sb.append("v11 - ")
        if (settings.ultraKickEnabled) sb.append("Kick ")
        if (settings.proactivePingEnabled) sb.append("Ping ")
        if (settings.wakeLockEnabled) sb.append("WakeLock ")
        if (settings.screenToggleEnabled) sb.append("Toggle")
        showNotification("TouchFix", sb.toString())
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
        val extras = if (pingCount > 0) " | Pings: $pingCount" else ""
        showNotification("TouchFix", "$status$extras")
    }
}
