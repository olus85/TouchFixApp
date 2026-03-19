package de.olus.touchfix

import android.content.Context
import android.content.SharedPreferences

class TouchFixSettings(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "touchfix_prefs"
        // v13: 5 new targeted fixes
        private const val KEY_POST_FINGERPRINT_RESET = "post_fingerprint_reset"
        private const val KEY_TOUCH_HAL_RESTART = "touch_hal_restart"
        private const val KEY_PHANTOM_SWIPE = "phantom_swipe"
        private const val KEY_SYSFS_RESET = "sysfs_reset"
        private const val KEY_ESCALATING_AUTOFIX = "escalating_autofix"
        
        @Volatile
        private var instance: TouchFixSettings? = null
        
        fun getInstance(context: Context): TouchFixSettings {
            return instance ?: synchronized(this) {
                instance ?: TouchFixSettings(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var postFingerprintResetEnabled: Boolean
        get() = prefs.getBoolean(KEY_POST_FINGERPRINT_RESET, true)
        set(value) = prefs.edit().putBoolean(KEY_POST_FINGERPRINT_RESET, value).apply()

    var touchHalRestartEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_HAL_RESTART, false)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH_HAL_RESTART, value).apply()

    var phantomSwipeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PHANTOM_SWIPE, false)
        set(value) = prefs.edit().putBoolean(KEY_PHANTOM_SWIPE, value).apply()

    var sysfsResetEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYSFS_RESET, false)
        set(value) = prefs.edit().putBoolean(KEY_SYSFS_RESET, value).apply()

    var escalatingAutofixEnabled: Boolean
        get() = prefs.getBoolean(KEY_ESCALATING_AUTOFIX, true)
        set(value) = prefs.edit().putBoolean(KEY_ESCALATING_AUTOFIX, value).apply()
}
