package de.olus.touchfix

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Switch
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusService: TextView
    private lateinit var statusPermission: TextView
    private lateinit var statusLog: TextView
    private lateinit var btnEnable: Button
    private lateinit var switchProactivePing: Switch
    private lateinit var switchWakeLock: Switch
    private lateinit var switchUltraKick: Switch
    private lateinit var switchScreenToggle: Switch
    private lateinit var settings: TouchFixSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        settings = TouchFixSettings.getInstance(this)

        statusService = findViewById(R.id.status_service)
        statusPermission = findViewById(R.id.status_permission)
        statusLog = findViewById(R.id.status_log)
        btnEnable = findViewById(R.id.btn_enable_service)
        
        switchProactivePing = findViewById(R.id.switch_proactive_ping)
        switchWakeLock = findViewById(R.id.switch_wake_lock)
        switchUltraKick = findViewById(R.id.switch_ultra_kick)
        switchScreenToggle = findViewById(R.id.switch_screen_toggle)

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Set up switches
        switchProactivePing.setOnCheckedChangeListener { _, isChecked ->
            settings.proactivePingEnabled = isChecked
        }
        switchWakeLock.setOnCheckedChangeListener { _, isChecked ->
            settings.wakeLockEnabled = isChecked
        }
        switchUltraKick.setOnCheckedChangeListener { _, isChecked ->
            settings.ultraKickEnabled = isChecked
        }
        switchScreenToggle.setOnCheckedChangeListener { _, isChecked ->
            settings.screenToggleEnabled = isChecked
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        // Load settings
        switchProactivePing.isChecked = settings.proactivePingEnabled
        switchWakeLock.isChecked = settings.wakeLockEnabled
        switchUltraKick.isChecked = settings.ultraKickEnabled
        switchScreenToggle.isChecked = settings.screenToggleEnabled

        if (TouchFixService.isRunning) {
            statusService.text = "✅ TouchFix v11 ACTIVE"
            statusService.setTextColor(0xFF4CAF50.toInt())
            btnEnable.text = "Service-Einstellungen öffnen"
        } else {
            statusService.text = "❌ TouchFix Service NICHT aktiv"
            statusService.setTextColor(0xFFF44336.toInt())
            btnEnable.text = "Service jetzt aktivieren"
        }

        val hasPermission = try {
            Settings.Secure.putInt(contentResolver, "touchfix_permission_test", 0)
            true
        } catch (_: SecurityException) {
            false
        }

        if (hasPermission) {
            statusPermission.text = "✅ WRITE_SECURE_SETTINGS gewährt"
            statusPermission.setTextColor(0xFF4CAF50.toInt())
        } else {
            statusPermission.text = "❌ WRITE_SECURE_SETTINGS fehlt\n\nVia ADB:\nadb shell pm grant de.olus.touchfix android.permission.WRITE_SECURE_SETTINGS"
            statusPermission.setTextColor(0xFFF44336.toInt())
        }

        if (TouchFixService.isRunning) {
            val sb = StringBuilder()
            sb.appendLine("Status: ${if (settings.ultraKickEnabled) "Ultra Mode" else "Aktiv"}")
            sb.appendLine("")
            sb.appendLine("Kicks: ${TouchFixService.fixCount} | Fails: ${TouchFixService.failCount}")
            if (settings.proactivePingEnabled) {
                sb.appendLine("Pings: ${TouchFixService.pingCount}")
            }
            statusLog.text = sb.toString()
        } else {
            statusLog.text = "Service nicht aktiv - bitte zuerst aktivieren."
        }
    }
}
