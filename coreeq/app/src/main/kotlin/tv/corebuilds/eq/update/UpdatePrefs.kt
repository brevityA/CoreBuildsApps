package tv.corebuilds.eq.update

import android.content.Context
import android.content.SharedPreferences

/**
 * The two things the updater remembers: whether the user wants Core EQ to
 * check on its own, and which offered version they already pushed away with
 * "Later".
 *
 * Both default to the quiet choice that still tells the truth: checks on, so a
 * TV that is never updated can at least learn that a newer build exists, and
 * "Later" remembered per version, so the bar does not reappear on every Home
 * visit for the same release. A failed check is never remembered: the next
 * Home visit may have a network.
 */
class UpdatePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Off means the app makes no network request until the user asks. */
    var checksEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHECKS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CHECKS_ENABLED, value).apply()

    fun dismissedVersionCode(): Int = prefs.getInt(KEY_DISMISSED_VERSION_CODE, 0)

    fun dismiss(versionCode: Int) {
        prefs.edit().putInt(KEY_DISMISSED_VERSION_CODE, versionCode).apply()
    }

    private companion object {
        const val PREFS_NAME = "core_eq_updates"
        const val KEY_CHECKS_ENABLED = "checks_enabled"
        const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
    }
}
