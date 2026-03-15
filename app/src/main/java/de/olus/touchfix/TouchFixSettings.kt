package de.olus.touchfix

import android.content.Context
import android.content.SharedPreferences

class TouchFixSettings(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "touchfix_prefs"
        private const val KEY_PROACTIVE_PING = "proactive_ping"
        private const val KEY_WAKE_LOCK = "wake_lock"
        private const val KEY_ULTRA_KICK = "ultra_kick"
        private const val KEY_SCREEN_TOGGLE = "screen_toggle"
        private const val KEY_PING_INTERVAL = "ping_interval"
        
        @Volatile
        private var instance: TouchFixSettings? = null
        
        fun getInstance(context: Context): TouchFixSettings {
            return instance ?: synchronized(this) {
                instance ?: TouchFixSettings(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    var proactivePingEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROACTIVE_PING, false)
        set(value) = prefs.edit().putBoolean(KEY_PROACTIVE_PING, value).apply()
    
    var wakeLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_LOCK, value).apply()
    
    var ultraKickEnabled: Boolean
        get() = prefs.getBoolean(KEY_ULTRA_KICK, true)
        set(value) = prefs.edit().putBoolean(KEY_ULTRA_KICK, value).apply()
    
    var screenToggleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_TOGGLE, false)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_TOGGLE, value).apply()
    
    var pingIntervalSeconds: Int
        get() = prefs.getInt(KEY_PING_INTERVAL, 5)
        set(value) = prefs.edit().putInt(KEY_PING_INTERVAL, value).apply()
}
