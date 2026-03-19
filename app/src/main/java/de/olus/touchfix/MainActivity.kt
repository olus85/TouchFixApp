package de.olus.touchfix

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusService: TextView
    private lateinit var statusPermission: TextView
    private lateinit var statusLog: TextView
    private lateinit var btnEnable: Button
    private lateinit var btnClearLog: Button
    private lateinit var consoleText: TextView
    private lateinit var consoleScroll: ScrollView

    private lateinit var switchPostFingerprint: Switch
    private lateinit var switchHalRestart: Switch
    private lateinit var switchPhantomSwipe: Switch
    private lateinit var switchSysfsReset: Switch
    private lateinit var switchEscalating: Switch

    private lateinit var settings: TouchFixSettings
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var logListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = TouchFixSettings.getInstance(this)

        // Bind views
        statusService = findViewById(R.id.status_service)
        statusPermission = findViewById(R.id.status_permission)
        statusLog = findViewById(R.id.status_log)
        btnEnable = findViewById(R.id.btn_enable_service)
        btnClearLog = findViewById(R.id.btn_clear_log)
        consoleText = findViewById(R.id.console_text)
        consoleScroll = findViewById(R.id.console_scroll)

        switchPostFingerprint = findViewById(R.id.switch_post_fingerprint)
        switchHalRestart = findViewById(R.id.switch_hal_restart)
        switchPhantomSwipe = findViewById(R.id.switch_phantom_swipe)
        switchSysfsReset = findViewById(R.id.switch_sysfs_reset)
        switchEscalating = findViewById(R.id.switch_escalating)

        // Button handlers
        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnClearLog.setOnClickListener {
            EventLog.clear()
            EventLog.i(getString(R.string.btn_clear))
        }

        // Switch listeners
        switchPostFingerprint.setOnCheckedChangeListener { _, isChecked ->
            settings.postFingerprintResetEnabled = isChecked
            EventLog.i("Setting: Post-Fingerprint Reset → ${if (isChecked) "AN" else "AUS"}")
        }
        switchHalRestart.setOnCheckedChangeListener { _, isChecked ->
            settings.touchHalRestartEnabled = isChecked
            EventLog.i("Setting: Touch HAL Restart → ${if (isChecked) "AN" else "AUS"}")
        }
        switchPhantomSwipe.setOnCheckedChangeListener { _, isChecked ->
            settings.phantomSwipeEnabled = isChecked
            EventLog.i("Setting: Phantom Swipe → ${if (isChecked) "AN" else "AUS"}")
        }
        switchSysfsReset.setOnCheckedChangeListener { _, isChecked ->
            settings.sysfsResetEnabled = isChecked
            EventLog.i("Setting: sysfs Reset → ${if (isChecked) "AN" else "AUS"}")
        }
        switchEscalating.setOnCheckedChangeListener { _, isChecked ->
            settings.escalatingAutofixEnabled = isChecked
            EventLog.i("Setting: Escalating Auto-Fix → ${if (isChecked) "AN" else "AUS"}")
        }

        // Log listener for live console
        logListener = {
            handler.post { refreshConsole() }
        }
        EventLog.addListener(logListener!!)

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        refreshRunnable = object : Runnable {
            override fun run() {
                updateStats()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(refreshRunnable!!)
    }

    override fun onPause() {
        super.onPause()
        refreshRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        logListener?.let { EventLog.removeListener(it) }
    }

    private fun updateUI() {
        // Load switch states
        switchPostFingerprint.isChecked = settings.postFingerprintResetEnabled
        switchHalRestart.isChecked = settings.touchHalRestartEnabled
        switchPhantomSwipe.isChecked = settings.phantomSwipeEnabled
        switchSysfsReset.isChecked = settings.sysfsResetEnabled
        switchEscalating.isChecked = settings.escalatingAutofixEnabled

        // Service status
        if (TouchFixService.isRunning) {
            statusService.text = getString(R.string.status_service_active)
            statusService.setTextColor(0xFF4CAF50.toInt())
            btnEnable.text = getString(R.string.btn_service_settings)
        } else {
            statusService.text = getString(R.string.status_service_inactive)
            statusService.setTextColor(0xFFF44336.toInt())
            btnEnable.text = getString(R.string.btn_service_enable_now)
        }

        // Permission status
        val hasPermission = try {
            Settings.Secure.putInt(contentResolver, "touchfix_permission_test", 0)
            true
        } catch (_: SecurityException) {
            false
        }

        if (hasPermission) {
            statusPermission.text = getString(R.string.status_permission_granted)
            statusPermission.setTextColor(0xFF4CAF50.toInt())
        } else {
            statusPermission.text = getString(R.string.status_permission_missing)
            statusPermission.setTextColor(0xFFF44336.toInt())
        }

        updateStats()
        refreshConsole()
    }

    private fun updateStats() {
        if (TouchFixService.isRunning) {
            val active = listOf(
                settings.postFingerprintResetEnabled,
                settings.touchHalRestartEnabled,
                settings.phantomSwipeEnabled,
                settings.sysfsResetEnabled,
                settings.escalatingAutofixEnabled,
            ).count { it }
            statusLog.text = "Kicks: ${TouchFixService.fixCount} | Fails: ${TouchFixService.failCount} | Active: $active/5"
        } else {
            statusLog.text = getString(R.string.status_service_inactive)
        }
    }

    private fun refreshConsole() {
        val entries = EventLog.getSnapshot()
        if (entries.isEmpty()) {
            consoleText.text = getString(R.string.label_waiting_events)
            return
        }
        consoleText.text = entries.joinToString("\n") { it.formatted }
        consoleScroll.post {
            consoleScroll.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
