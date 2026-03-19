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
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class TouchFixService : AccessibilityService() {

    companion object {
        private const val CHANNEL_ID = "touchfix_status"
        private const val NOTIFICATION_ID = 1001
        private const val SENSITIVITY_SETTING = "touch_sensitivity_enabled"
        private const val POINTER_LOCATION_SETTING = "pointer_location"

        var isRunning = false
            private set
        var fixCount = 0
            private set
        var failCount = 0
            private set
    }

    private lateinit var settings: TouchFixSettings
    private val handler = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null
    private var touchDetectedSinceScreenOn = false
    private var touchDetectedSinceUnlock = false
    private var screenOnTimestamp = 0L
    private var unlockTimestamp = 0L
    private var escalationLevel = 0
    private var wakeLock: PowerManager.WakeLock? = null

    // ──────────────────── Lifecycle ────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        settings = TouchFixSettings.getInstance(this)

        EventLog.i("════════════════════════════════")
        EventLog.ok(getString(R.string.log_service_started))
        EventLog.i(getString(R.string.log_device_info, android.os.Build.MODEL, android.os.Build.VERSION.SDK_INT))
        logActiveSettings()

        createNotificationChannel()
        updateNotification(getString(R.string.label_checking))
        registerScreenReceiver()
        EventLog.ok(getString(R.string.log_service_ready))
    }

    private fun logActiveSettings() {
        val active = mutableListOf<String>()
        if (settings.postFingerprintResetEnabled) active.add("PostFingerprint")
        if (settings.touchHalRestartEnabled) active.add("HAL-Restart")
        if (settings.phantomSwipeEnabled) active.add("PhantomSwipe")
        if (settings.sysfsResetEnabled) active.add("SysFS")
        if (settings.escalatingAutofixEnabled) active.add("EscalAutofix")
        EventLog.i("Aktiv: ${if (active.isEmpty()) "keine" else active.joinToString(", ")}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                if (!touchDetectedSinceScreenOn) {
                    touchDetectedSinceScreenOn = true
                    val delay = System.currentTimeMillis() - screenOnTimestamp
                    EventLog.ok(getString(R.string.log_touch_detected_screen_on, delay))
                    handler.removeCallbacksAndMessages("ESCALATE")
                    handler.removeCallbacksAndMessages("WATCHDOG")
                }
                if (!touchDetectedSinceUnlock && unlockTimestamp > 0) {
                    touchDetectedSinceUnlock = true
                    val delay = System.currentTimeMillis() - unlockTimestamp
                    EventLog.ok(getString(R.string.log_touch_detected_unlock, delay))
                    handler.removeCallbacksAndMessages("POST_FP")
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        EventLog.w("Service wird beendet!")
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        EventLog.e("Service beendet")
        super.onDestroy()
    }

    // ──────────────────── Screen Receiver ────────────────────

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
        EventLog.i("Receiver active")
    }

    // ──────────────────── Screen ON ────────────────────

    private fun onScreenOn() {
        screenOnTimestamp = System.currentTimeMillis()
        touchDetectedSinceScreenOn = false
        touchDetectedSinceUnlock = false
        escalationLevel = 0
        EventLog.i("━━━ SCREEN ON ━━━")

        // Acquire temporary wake lock for fix execution
        acquireWakeLock(15_000L)

        // Phantom Swipe: immediate swipe flood
        if (settings.phantomSwipeEnabled) {
            EventLog.i("[PhantomSwipe] Sofort-Swipes werden injiziert...")
            executePhantomSwipes()
        }

        // sysfs Reset: poke touch controller hardware
        if (settings.sysfsResetEnabled) {
            handler.postDelayed({
                EventLog.i("[SysFS] Touch-Controller Reset...")
                executeSysfsReset()
            }, 100)
        }

        // Escalating Auto-Fix: start watchdog chain
        if (settings.escalatingAutofixEnabled) {
            EventLog.i("[AutoFix] Watchdog gestartet – warte auf Touch...")
            scheduleEscalation(2000)
        }
    }

    // ──────────────────── Screen OFF ────────────────────

    private fun onScreenOff() {
        EventLog.i("━━━ SCREEN OFF ━━━")
        handler.removeCallbacksAndMessages(null)
        touchDetectedSinceScreenOn = false
        touchDetectedSinceUnlock = false
        unlockTimestamp = 0
        releaseWakeLock()
    }

    // ──────────────────── User Unlock (Fingerprint) ────────────────────

    private fun onUserPresent() {
        unlockTimestamp = System.currentTimeMillis()
        touchDetectedSinceUnlock = false
        val timeSinceScreenOn = unlockTimestamp - screenOnTimestamp
        EventLog.i(getString(R.string.log_unlock_detected, timeSinceScreenOn))

        // Post-Fingerprint Reset: the main targeted fix
        if (settings.postFingerprintResetEnabled) {
            EventLog.i(getString(R.string.log_post_fp_start))

            // Stage 1: Immediate (50ms) – fast sensitivity toggle
            handler.postDelayed({
                if (!touchDetectedSinceUnlock) {
                    EventLog.i("[PostFP] Stage 1/4: Sensitivity Toggle (50ms)")
                    try {
                        Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 1)
                        handler.postDelayed({
                            Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 0)
                        }, 30)
                    } catch (e: Exception) {
                        EventLog.e("[PostFP] Stage 1 Fehler: ${e.message}")
                    }
                }
            }, "POST_FP", 50)

            // Stage 2: 300ms – swipe injection
            handler.postDelayed({
                if (!touchDetectedSinceUnlock) {
                    EventLog.i("[PostFP] Stage 2/4: Swipe-Injection (300ms)")
                    injectSwipes(3)
                }
            }, "POST_FP", 300)

            // Stage 3: 600ms – pointer location rebuild
            handler.postDelayed({
                if (!touchDetectedSinceUnlock) {
                    EventLog.i("[PostFP] Stage 3/4: InputDispatcher Rebuild (600ms)")
                    try {
                        Settings.System.putInt(contentResolver, POINTER_LOCATION_SETTING, 1)
                        handler.postDelayed({
                            Settings.System.putInt(contentResolver, POINTER_LOCATION_SETTING, 0)
                        }, 50)
                    } catch (e: Exception) {
                        EventLog.e("[PostFP] Stage 3 Fehler: ${e.message}")
                    }
                }
            }, "POST_FP", 600)

            // Stage 4: 1000ms – HAL poke if enabled
            handler.postDelayed({
                if (!touchDetectedSinceUnlock) {
                    EventLog.w("[PostFP] Stage 4/4: Kein Touch nach 1s! HAL-Poke...")
                    pokeHal()
                    fixCount++
                    updateNotification("PostFP Kick #$fixCount")
                } else {
                    EventLog.ok("[PostFP] Touch funktioniert – kein weiterer Reset nötig ✓")
                }
            }, "POST_FP", 1000)
        }

        // Touch HAL Restart on unlock
        if (settings.touchHalRestartEnabled) {
            handler.postDelayed({
                if (!touchDetectedSinceUnlock) {
                    EventLog.w("[HAL-Restart] Kein Touch nach Unlock → HAL wird neu gestartet...")
                    executeTouchHalRestart()
                }
            }, 800)
        }
    }

    // ══════════════════════════════════════════════════════
    //  FIX 1: Post-Fingerprint Reset (stages in onUserPresent)
    // ══════════════════════════════════════════════════════

    private fun pokeHal() {
        try {
            // Poke the touch controller through multiple system paths
            val cmds = listOf(
                "input tap 1 1",
                "input tap 540 1200",
                "input swipe 100 500 400 500 50",
            )
            for (cmd in cmds) {
                Runtime.getRuntime().exec(cmd)
            }
            EventLog.i("[PostFP] HAL-Poke: 2 taps + 1 swipe gesendet")
        } catch (e: Exception) {
            EventLog.e("[PostFP] HAL-Poke Fehler: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════
    //  FIX 2: Touch HAL Restart
    // ══════════════════════════════════════════════════════

    private fun executeTouchHalRestart() {
        try {
            // Try various known Touch HAL service names on Pixel devices
            val halNames = listOf(
                "vendor.google.touch_offload@1.0-service",
                "vendor.google.touch_offload@2.0-service",
                "vendor.touch.touchscreen@1.0-service",
                "vendor.goodix.fingerprint@2.1-service",
            )
            EventLog.i("[HAL-Restart] Versuche Touch-HAL Services zu finden...")

            // First check which services exist
            val process = Runtime.getRuntime().exec("getprop")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val touchProps = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.contains("touch", ignoreCase = true) || l.contains("input", ignoreCase = true)) {
                    touchProps.add(l)
                }
            }
            reader.close()
            if (touchProps.isNotEmpty()) {
                EventLog.i("[HAL-Restart] Touch-Properties gefunden:")
                touchProps.take(5).forEach { EventLog.i("  $it") }
            }

            // Try to restart vendor init services
            for (name in halNames) {
                try {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "stop $name 2>/dev/null; start $name 2>/dev/null"))
                    EventLog.i("[HAL-Restart] Versuch: $name")
                } catch (_: Exception) {}
            }

            // Fallback: toggle input subsystem via settings
            Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 1)
            Thread.sleep(50)
            Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 0)
            Settings.System.putInt(contentResolver, POINTER_LOCATION_SETTING, 1)
            Thread.sleep(50)
            Settings.System.putInt(contentResolver, POINTER_LOCATION_SETTING, 0)

            fixCount++
            EventLog.ok("[HAL-Restart] Ausgeführt ✓ (Kick #$fixCount)")
            updateNotification("HAL Kick #$fixCount")
        } catch (e: Exception) {
            EventLog.e("[HAL-Restart] Fehler: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════
    //  FIX 3: Phantom Swipe Flood
    // ══════════════════════════════════════════════════════

    private fun executePhantomSwipes() {
        try {
            val dm = resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels

            // Inject diverse swipe patterns to exercise the full touch pipeline
            val swipes = listOf(
                // Horizontal swipes across screen
                "input swipe ${w/4} ${h/2} ${3*w/4} ${h/2} 30",
                // Vertical swipe
                "input swipe ${w/2} ${h/4} ${w/2} ${3*h/4} 30",
                // Diagonal
                "input swipe ${w/4} ${h/4} ${3*w/4} ${3*h/4} 30",
                // Short taps at edges
                "input tap 1 1",
                "input tap ${w-1} ${h-1}",
                "input tap ${w/2} 1",
            )

            var count = 0
            for (swipe in swipes) {
                Runtime.getRuntime().exec(swipe)
                count++
            }
            EventLog.ok("[PhantomSwipe] $count Swipes/Taps injiziert ✓")
        } catch (e: Exception) {
            EventLog.e("[PhantomSwipe] Fehler: ${e.message}")
        }
    }

    private fun injectSwipes(count: Int) {
        try {
            val dm = resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels
            for (i in 0 until count) {
                val y = h / 4 + (i * h / (2 * count))
                Runtime.getRuntime().exec("input swipe ${w/4} $y ${3*w/4} $y 20")
            }
            EventLog.i("  → $count Swipes injiziert")
        } catch (e: Exception) {
            EventLog.e("  → Swipe-Injection Fehler: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════
    //  FIX 4: sysfs Touch Reset
    // ══════════════════════════════════════════════════════

    private fun executeSysfsReset() {
        // Try common sysfs paths for touch controller reset on Pixel/Goodix devices
        val resetPaths = listOf(
            "/sys/class/input/input0/device/reset",
            "/sys/class/input/input1/device/reset",
            "/sys/class/input/input2/device/reset",
            "/sys/devices/virtual/input/input0/device/reset",
            "/proc/goodix_ts/reset",
            "/proc/tp_reset",
            "/sys/bus/i2c/devices/1-005d/reset",
            "/sys/bus/i2c/devices/2-005d/reset",
            "/sys/bus/spi/devices/spi0.0/reset",
            "/sys/bus/spi/devices/spi1.0/reset",
        )

        var found = false
        for (path in resetPaths) {
            val file = File(path)
            if (file.exists()) {
                try {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "echo 1 > $path"))
                    EventLog.ok("[SysFS] Reset via $path ✓")
                    found = true
                } catch (e: Exception) {
                    EventLog.e("[SysFS] Schreiben auf $path fehlgeschlagen: ${e.message}")
                }
            }
        }

        // Also try to find touch devices dynamically
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "find /sys/class/input -name 'name' -exec grep -l -i touch {} \\; 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                val deviceDir = File(l).parentFile?.absolutePath ?: continue
                EventLog.i("[SysFS] Touch-Device gefunden: $deviceDir")
                // Try to reset via its parent
                try {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "echo 1 > $deviceDir/device/reset 2>/dev/null"))
                    EventLog.i("[SysFS] Reset-Versuch: $deviceDir/device/reset")
                    found = true
                } catch (_: Exception) {}
            }
            reader.close()
        } catch (e: Exception) {
            EventLog.e("[SysFS] Scan fehlgeschlagen: ${e.message}")
        }

        if (!found) {
            EventLog.w("[SysFS] Keine beschreibbaren Reset-Pfade gefunden (root nötig)")
            // Fallback: toggle settings
            try {
                Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 1)
                handler.postDelayed({
                    Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 0)
                }, 40)
                EventLog.i("[SysFS] Fallback: Sensitivity Toggle stattdessen")
            } catch (e: Exception) {
                EventLog.e("[SysFS] Auch Fallback fehlgeschlagen: ${e.message}")
            }
        }
    }

    // ══════════════════════════════════════════════════════
    //  FIX 5: Escalating Auto-Fix
    // ══════════════════════════════════════════════════════

    private fun scheduleEscalation(delayMs: Long) {
        handler.postDelayed({
            if (touchDetectedSinceScreenOn) {
                EventLog.ok("[AutoFix] Touch erkannt – Eskalation gestoppt ✓")
                return@postDelayed
            }

            escalationLevel++
            EventLog.w("[AutoFix] ═══ Eskalation Stufe $escalationLevel ═══")

            when (escalationLevel) {
                1 -> {
                    EventLog.i("[AutoFix] Stufe 1: Settings-Toggle (sanft)")
                    try {
                        Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 1)
                        handler.postDelayed({
                            Settings.Secure.putInt(contentResolver, SENSITIVITY_SETTING, 0)
                        }, 40)
                        Settings.System.putInt(contentResolver, POINTER_LOCATION_SETTING, 1)
                        handler.postDelayed({
                            Settings.System.putInt(contentResolver, POINTER_LOCATION_SETTING, 0)
                        }, 80)
                    } catch (e: Exception) {
                        EventLog.e("[AutoFix] Stufe 1 Fehler: ${e.message}")
                    }
                    fixCount++
                    scheduleEscalation(2000)
                }

                2 -> {
                    EventLog.i("[AutoFix] Stufe 2: Swipe-Flood (mittel)")
                    executePhantomSwipes()
                    injectSwipes(5)
                    fixCount++
                    scheduleEscalation(2000)
                }

                3 -> {
                    EventLog.i("[AutoFix] Stufe 3: HAL-Poke (aggressiv)")
                    executeTouchHalRestart()
                    scheduleEscalation(3000)
                }

                4 -> {
                    EventLog.i("[AutoFix] Stufe 4: sysfs Reset (maximal)")
                    executeSysfsReset()
                    scheduleEscalation(3000)
                }

                else -> {
                    EventLog.e("[AutoFix] ═══ ALLE STUFEN GESCHEITERT ═══")
                    EventLog.e("[AutoFix] Display-Cycle als letzter Versuch...")
                    failCount++
                    triggerScreenCycle()
                    updateNotification("FAIL #$failCount")
                    showBugNotification()
                }
            }
        }, "ESCALATE", delayMs)
    }

    // ──────────────────── Screen Cycle (last resort) ────────────────────

    private fun triggerScreenCycle() {
        try {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (km.isKeyguardLocked) {
                EventLog.e("[ScreenCycle] Abgebrochen (Keyguard aktiv)")
                return
            }
            EventLog.w("[ScreenCycle] Display wird aus-/eingeschaltet...")
            Runtime.getRuntime().exec("input keyevent 26") // Power off
            handler.postDelayed({
                Runtime.getRuntime().exec("input keyevent 26") // Power on
                EventLog.i("[ScreenCycle] Display wieder an")
            }, 500)
        } catch (e: Exception) {
            EventLog.e("[ScreenCycle] Fehler: ${e.message}")
        }
    }

    // ──────────────────── Helpers ────────────────────

    private fun acquireWakeLock(timeoutMs: Long) {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TouchFix:Fix")
            wakeLock?.acquire(timeoutMs)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "TouchFix Status", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(status: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("TouchFix v14")
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun showBugNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("TouchFix Fehler!")
            .setContentText("Touch reagiert nicht (Fail #$failCount)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, notification)
    }
}
