package dev.corebuilds.shift

import android.content.Context

class RotationPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("core_shift", Context.MODE_PRIVATE)

    var rotationEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var intervalMinutes: Long
        get() = prefs.getLong(KEY_INTERVAL, 60L)
        set(value) = prefs.edit().putLong(KEY_INTERVAL, value).apply()

    var lastIndex: Int
        get() = prefs.getInt(KEY_LAST_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_INDEX, value).apply()

    companion object {
        private const val KEY_ENABLED = "rotation_enabled"
        private const val KEY_INTERVAL = "rotation_interval_minutes"
        private const val KEY_LAST_INDEX = "rotation_last_index"
    }
}
