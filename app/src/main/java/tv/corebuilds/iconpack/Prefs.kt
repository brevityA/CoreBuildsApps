package tv.corebuilds.iconpack

import android.content.Context
import android.content.SharedPreferences

/**
 * The app's first persisted state.
 *
 * Until 1.8.21 nothing was stored at all: every launch started from the same
 * place, which was fine for a pack whose only job was handing icons to a
 * launcher. These three flags are the ones that change what the app *does*,
 * and each is read somewhere with an observable effect — a toggle that writes
 * a boolean nothing reads is worse than no toggle, so this file stays small
 * deliberately. Anything that cannot point at its reader does not belong here.
 *
 * Defaults reproduce 1.8.20 exactly: update checks on, motion on, night rather
 * than void chrome. Nobody's pack changes behaviour merely because this file
 * started existing.
 *
 * Deliberately not DataStore. Three booleans read on the main thread during
 * onCreate do not justify a coroutine dependency in a module that currently
 * has none.
 */
object Prefs {

    private const val FILE = "core_builds_settings"

    const val KEY_UPDATE_CHECKS = "update_checks"
    const val KEY_REDUCE_MOTION = "reduce_motion"
    const val KEY_AMOLED = "amoled_chrome"
    const val KEY_PICK_BANNERS = "pick_banners"
    const val KEY_WHATS_NEW_SEEN = "whats_new_seen_version"
    const val KEY_BANNERS_PENDING_APPLY = "banners_pending_apply"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Whether to look for a new release on resume.
     *
     * Read by [MainActivity.onResume]. Off means the update bar never appears
     * and [UpdateChecker] is never called, so the app makes no network request
     * of its own at all.
     */
    fun updateChecks(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UPDATE_CHECKS, true)

    /**
     * Whether to skip the focus-scale animation on the filter chips.
     *
     * Read by [ChipAdapter]. That animation is the only one the app runs; on
     * older boxes it is also the only thing between a chip strip that tracks
     * the remote and one that lags behind it.
     */
    fun reduceMotion(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REDUCE_MOTION, false)

    /**
     * Whether to paint the window `cb_void` instead of `cb_night`.
     *
     * Read by [applyChrome]. The icons are drawn for dark cards either way;
     * this exists because the pack ships an AMOLED wallpaper series and the
     * app sitting two shades lighter than its own wallpapers looked wrong.
     */
    fun amoled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AMOLED, false)

    /**
     * The Glyphs/Banners art style. Square is the shipped default (false).
     * Read by the catalogue, by the icon picker (which opens on the matching
     * shape chip and writes this back, so a shape choice simply *stays*), and
     * by [BannersCompanion.applyTarget]: on builds with a companion, banners
     * means launchers are told to apply Core Builds Banners, whose appfilter
     * maps every app to its 16:9 art, instead of this package's glyphs.
     */
    fun pickerPrefersBanners(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PICK_BANNERS, false)

    /**
     * The launcher (its [ApplyIconPack.Launcher.key]) owed an apply once the
     * Banners companion installs, or null. Written by [BannersCompanion.ensure]
     * just before the system installer opens; read and cleared by
     * [BannersCompanion.takePendingApply] when Settings or the home screen
     * resumes with the companion in place. Persisted rather than held in a
     * field because the screen may be recreated while the installer is up.
     */
    fun bannersPendingApply(context: Context): String? =
        prefs(context).getString(KEY_BANNERS_PENDING_APPLY, null)

    fun setBannersPendingApply(context: Context, launcherKey: String?) {
        prefs(context).edit().apply {
            if (launcherKey == null) remove(KEY_BANNERS_PENDING_APPLY)
            else putString(KEY_BANNERS_PENDING_APPLY, launcherKey)
        }.apply()
    }

    /**
     * The highest versionCode the What's New sheet has narrated for. Read by
     * MainActivity's upgrade gate; written by the sheet itself when it closes
     * and by first-launch init (a brand-new install has nothing to narrate,
     * so the gateload seeds it to the current code rather than firing). The
     * sheet is the in-app what's-changed surface: what the update bar says
     * in one line before an update, it says in full after one.
     */
    fun whatsNewSeen(context: Context): Int =
        prefs(context).getInt(KEY_WHATS_NEW_SEEN, 0)

    fun setWhatsNewSeen(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_WHATS_NEW_SEEN, versionCode).apply()
    }

    fun set(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    /**
     * Paint this activity's window for the current chrome setting.
     *
     * Called from onCreate before setContentView. Layouts keep their
     * `@color/cb_night` background for the designed case; this only overrides
     * the window beneath them when the void option is on, so a screen that has
     * not opted in still looks exactly as drawn.
     */
    fun applyChrome(activity: android.app.Activity) {
        if (!amoled(activity)) return
        val void = androidx.core.content.ContextCompat.getColor(activity, R.color.cb_void)
        activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(void))
        activity.window.statusBarColor = void
        activity.window.navigationBarColor = void
    }
}
