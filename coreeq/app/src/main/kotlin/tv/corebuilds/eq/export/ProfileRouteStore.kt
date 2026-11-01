package tv.corebuilds.eq.export

import android.content.Context
import android.content.SharedPreferences
import tv.corebuilds.eq.apply.OutputRoute

/**
 * Per-output preferred-profile learning (1.11.0). When the user manually
 * selects a profile on an output, Core EQ remembers it. On returning to that
 * output, the remembered profile is offered instead of re-picking by output
 * kind alone.
 *
 * Each output keeps its own preference: the profile that sounds right on
 * the TV's speakers need not be the one that sounds right on a soundbar.
 */
class ProfileRouteStore(private val prefs: SharedPreferences) {

    /** The profile the user last chose for [outputKey], or none. */
    fun preferredProfile(outputKey: String): String? =
        prefs.getString(KEY_PREFERRED + outputKey, null)

    /** Remember that the user chose [profileId] on [outputKey]. */
    fun rememberPreference(outputKey: String, profileId: String) {
        prefs.edit().putString(KEY_PREFERRED + outputKey, profileId).apply()
    }

    /** Forget the preference for [outputKey]. */
    fun forgetPreference(outputKey: String) {
        prefs.edit().remove(KEY_PREFERRED + outputKey).apply()
    }

    /** The current output as a stable key, or null. */
    fun currentOutputKey(context: Context): String? =
        OutputRoute.current(context)?.let { "${it.kind}|${it.name}" }.takeIf { it.isNotBlank() }

    companion object {
        private const val PREFS_NAME = "profile_route_store"
        /** Followed by "kind|name": one preferred profile per output. */
        private const val KEY_PREFERRED = "preferred_profile:"

        fun of(context: Context): ProfileRouteStore =
            ProfileRouteStore(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}
