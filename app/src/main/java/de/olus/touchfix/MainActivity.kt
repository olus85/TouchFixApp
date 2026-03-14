package de.olus.touchfix

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusService: TextView
    private lateinit var statusPermission: TextView
    private lateinit var statusLog: TextView
    private lateinit var btnEnable: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusService = findViewById(R.id.status_service)
        statusPermission = findViewById(R.id.status_permission)
        statusLog = findViewById(R.id.status_log)
        btnEnable = findViewById(R.id.btn_enable_service)

        btnEnable.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        if (TouchFixService.isRunning) {
            statusService.text = "✅ TouchFix v6 ACTIVE (Input Kick)"
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
            statusPermission.text = "❌ WRITE_SECURE_SETTINGS fehlt\n\nVia ADB gewähren:\nadb shell pm grant de.olus.touchfix android.permission.WRITE_SECURE_SETTINGS"
            statusPermission.setTextColor(0xFFF44336.toInt())
        }

        if (TouchFixService.isRunning) {
            val sb = StringBuilder()
            sb.appendLine("Modus: Multi-Stage Reset (Input Kick)")
            sb.appendLine("Status: ${TouchFixService.lastStatus}")
            sb.appendLine("")
            sb.appendLine("Screen-On Events: ${TouchFixService.screenOnCount}")
            sb.appendLine("Input Kicks: ${TouchFixService.fixCount}")
            statusLog.text = sb.toString()
        } else {
            statusLog.text = "Service nicht aktiv – bitte zuerst aktivieren."
        }
    }
}
