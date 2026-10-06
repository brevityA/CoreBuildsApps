package tv.corebuilds.eq.ui

import android.content.Context
import android.content.SharedPreferences
import tv.corebuilds.eq.mode.ContentType

/**
 * Preferences for enhanced audio features including effect chain configuration,
 * night mode, and device-specific settings.
 */
class EnhancedAudioPrefs(context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("enhanced_audio_prefs", Context.MODE_PRIVATE)
    
    // Effect chain settings
    var bassBoostEnabled: Boolean
        get() = prefs.getBoolean(KEY_BASS_BOOST_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BASS_BOOST_ENABLED, value).apply()
    
    var loudnessEnhancerEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOUDNESS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LOUDNESS_ENABLED, value).apply()
    
    var dynamicsProcessingEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMICS_ENABLED, value).apply()
    
    // Night mode settings
    var nightModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT_MODE_ENABLED, value).apply()
    
    var nightModeAutoEnabled: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_MODE_AUTO, false)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT_MODE_AUTO, value).apply()
    
    var nightModeStartHour: Int
        get() = prefs.getInt(KEY_NIGHT_MODE_START_HOUR, 22) // 10 PM
        set(value) = prefs.edit().putInt(KEY_NIGHT_MODE_START_HOUR, value).apply()
    
    var nightModeEndHour: Int
        get() = prefs.getInt(KEY_NIGHT_MODE_END_HOUR, 7) // 7 AM
        set(value) = prefs.edit().putInt(KEY_NIGHT_MODE_END_HOUR, value).apply()
    
    // Content type preferences
    var lastContentType: ContentType
        get() {
            val name = prefs.getString(KEY_LAST_CONTENT_TYPE, ContentType.MOVIE.name)
            return try {
                ContentType.valueOf(name ?: ContentType.MOVIE.name)
            } catch (e: Exception) {
                ContentType.MOVIE
            }
        }
        set(value) = prefs.edit().putString(KEY_LAST_CONTENT_TYPE, value.name).apply()
    
    var autoContentTypeEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONTENT_TYPE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CONTENT_TYPE, value).apply()
    
    // Device-specific settings
    var hdmiPassthroughWarningShown: Boolean
        get() = prefs.getBoolean(KEY_HDMI_WARNING_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_HDMI_WARNING_SHOWN, value).apply()
    
    var showHdmiWarnings: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HDMI_WARNINGS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HDMI_WARNINGS, value).apply()
    
    // Advanced settings
    var effectChainPriority: Int
        get() = prefs.getInt(KEY_EFFECT_CHAIN_PRIORITY, 0) // 0 = balanced, 1 = quality, 2 = performance
        set(value) = prefs.edit().putInt(KEY_EFFECT_CHAIN_PRIORITY, value).apply()
    
    var showAdvancedStats: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ADVANCED_STATS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ADVANCED_STATS, value).apply()
    
    /**
     * Check if night mode should be active based on current time and settings
     */
    fun shouldEnableNightMode(): Boolean {
        if (!nightModeEnabled) return false
        if (!nightModeAutoEnabled) return true
        
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (nightModeStartHour > nightModeEndHour) {
            // Crosses midnight (e.g., 22:00 to 07:00)
            currentHour >= nightModeStartHour || currentHour < nightModeEndHour
        } else {
            // Same day (e.g., 14:00 to 18:00)
            currentHour in nightModeStartHour until nightModeEndHour
        }
    }
    
    /**
     * Get device-specific profile ID
     */
    fun getDeviceProfileId(deviceType: String, deviceAddress: String?): String {
        return if (deviceAddress != null) {
            "${deviceType}_${deviceAddress.hashCode()}"
        } else {
            deviceType
        }
    }
    
    /**
     * Save device-specific profile mapping
     */
    fun saveDeviceProfileMapping(deviceId: String, profileId: String) {
        val mappings = getDeviceProfileMappings().toMutableMap()
        mappings[deviceId] = profileId
        val serialized = mappings.entries.joinToString("|") { "${it.key}=${it.value}" }
        prefs.edit().putString(KEY_DEVICE_PROFILES, serialized).apply()
    }
    
    /**
     * Get device-specific profile mapping
     */
    fun getDeviceProfileId(deviceId: String): String? {
        return getDeviceProfileMappings()[deviceId]
    }
    
    private fun getDeviceProfileMappings(): Map<String, String> {
        val serialized = prefs.getString(KEY_DEVICE_PROFILES, "") ?: return emptyMap()
        if (serialized.isBlank()) return emptyMap()
        return serialized.split("|")
            .filter { it.contains("=") }
            .associate { 
                val parts = it.split("=", limit = 2)
                parts[0] to parts[1]
            }
    }
    
    /**
     * Reset all preferences to defaults
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
    
    companion object {
        private const val KEY_BASS_BOOST_ENABLED = "bass_boost_enabled"
        private const val KEY_LOUDNESS_ENABLED = "loudness_enhancer_enabled"
        private const val KEY_DYNAMICS_ENABLED = "dynamics_processing_enabled"
        private const val KEY_NIGHT_MODE_ENABLED = "night_mode_enabled"
        private const val KEY_NIGHT_MODE_AUTO = "night_mode_auto"
        private const val KEY_NIGHT_MODE_START_HOUR = "night_mode_start_hour"
        private const val KEY_NIGHT_MODE_END_HOUR = "night_mode_end_hour"
        private const val KEY_LAST_CONTENT_TYPE = "last_content_type"
        private const val KEY_AUTO_CONTENT_TYPE = "auto_content_type"
        private const val KEY_HDMI_WARNING_SHOWN = "hdmi_warning_shown"
        private const val KEY_SHOW_HDMI_WARNINGS = "show_hdmi_warnings"
        private const val KEY_EFFECT_CHAIN_PRIORITY = "effect_chain_priority"
        private const val KEY_SHOW_ADVANCED_STATS = "show_advanced_stats"
        private const val KEY_DEVICE_PROFILES = "device_profiles"
    }
}
