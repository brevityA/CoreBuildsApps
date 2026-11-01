package tv.corebuilds.eq.apply

import android.content.Context
import android.content.SharedPreferences
import tv.corebuilds.eq.apply.OutputRoute

/**
 * Persistent record of which outputs have had correction auto-applied at least
 * once (1.11.0). [AutoApplyPolicy.decide] consults this: a device whose key is
 * already recorded only gets a suggestion, never an automatic re-apply.
 *
 * Each output keeps its own entry because a TV's speakers and a soundbar are
 * different devices with different volumes, profiles, and correction needs.
 */
class DeviceKeyStore(private val prefs: SharedPreferences) {

    /** The keys of every output that has had correction auto-applied. */
    fun appliedKeys(): Set<String> =
        prefs.getStringSet(KEY_APPLIED, emptySet())?.toSet() ?: emptySet()

    /** Record that [deviceKey] has had correction applied. */
    fun recordApplied(deviceKey: String) {
        if (deviceKey.isBlank()) return
        prefs.edit().putStringSet(KEY_APPLIED, appliedKeys() + deviceKey).apply()
    }

    /** Forget every recorded device (testing / reset). */
    fun clear() = prefs.edit().remove(KEY_APPLIED).apply()

    /** The output currently in use, as a stable device key. */
    fun currentDeviceKey(context: Context): String? =
        OutputRoute.current(context)?.let { "${it.kind}|${it.name}" }?.takeIf { it.isNotBlank() }

    companion object {
        private const val PREFS_NAME = "device_key_store"
        private const val KEY_APPLIED = "applied_device_keys"

        fun of(context: Context): DeviceKeyStore =
            DeviceKeyStore(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}
