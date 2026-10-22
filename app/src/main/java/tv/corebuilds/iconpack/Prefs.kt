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
    const val KEY_LIVE_LOOP = "live_loop"
    const val KEY_LIVE_LOOP_CHANGED_AT = "live_loop_changed_at"
    const val KEY_LIVE_ROTATE_HOURS = "live_rotate_hours"
    const val KEY_WHATS_NEW_SEEN = "whats_new_seen_version"
    const val KEY_WP_NEW_SEEN = "wallpapers_new_seen_since"
    const val KEY_WP_FAVOURITES = "wallpaper_favourites"
    const val KEY_COMPANION_PENDING_APPLY = "glyphs_pending_apply"

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
     * The Banners/Glyphs art style. Banners is the shipped default (true),
     * as it was before 1.9.2: TV launcher cards are 16:9. Read by the
     * catalogue, by the icon picker, and by [GlyphsCompanion.applyTarget]:
     * on builds with a companion, glyphs (false) means launchers are told to
     * apply Core Builds Glyphs, whose appfilter maps every app to its square
     * glyph, instead of this package's banners. The key keeps its 1.9.x name
     * so a style someone already chose survives the update.
     */
    fun pickerPrefersBanners(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PICK_BANNERS, true)

    /**
     * The launcher (its [ApplyIconPack.Launcher.key]) owed an apply once the
     * Glyphs companion installs, or null. Written by [GlyphsCompanion.ensure]
     * just before the system installer opens; read and cleared by
     * [GlyphsCompanion.takePendingApply] when Settings or the home screen
     * resumes with the companion in place. Persisted rather than held in a
     * field because the screen may be recreated while the installer is up.
     */
    fun companionPendingApply(context: Context): String? =
        prefs(context).getString(KEY_COMPANION_PENDING_APPLY, null)

    fun setCompanionPendingApply(context: Context, launcherKey: String?) {
        prefs(context).edit().apply {
            if (launcherKey == null) remove(KEY_COMPANION_PENDING_APPLY)
            else putString(KEY_COMPANION_PENDING_APPLY, launcherKey)
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

    /**
     * The newest wallpaper `since` the user has opened the browser for. The
     * home rail's Wallpapers row advertises "N new" until this matches the
     * manifest's newest value; [WallpapersActivity] records it on open.
     */
    fun wallpapersNewSeen(context: Context): String =
        prefs(context).getString(KEY_WP_NEW_SEEN, "") ?: ""

    fun setWallpapersNewSeen(context: Context, since: String) {
        prefs(context).edit().putString(KEY_WP_NEW_SEEN, since).apply()
    }

    /**
     * Wallpapers starred with the preview's Favourite button, by
     * [Wallpaper.cacheName]. That is the file name, which a re-render keeps
     * (only `since`, and with it [Wallpaper.storeName], moves), so a favourite
     * survives its wall being redrawn. Read by [WallpapersActivity] for the
     * Favourites chip and the tiles' star. A name that leaves the catalogue
     * stays stored but is not counted, so a wall that returns comes back
     * starred.
     */
    fun wallpaperFavourites(context: Context): Set<String> =
        // A copy: the set getStringSet returns must not be modified.
        prefs(context).getStringSet(KEY_WP_FAVOURITES, null)?.toSet() ?: emptySet()

    fun setWallpaperFavourite(context: Context, cacheName: String, favourite: Boolean) {
        val next = wallpaperFavourites(context).toMutableSet()
        if (favourite) next += cacheName else next -= cacheName
        prefs(context).edit().putStringSet(KEY_WP_FAVOURITES, next).apply()
    }

    /**
     * The motion loop [LiveWallpaperService] plays ([LiveLoop.Loop.id]).
     * Written by the preview's "Set live" button; read by the engine on
     * every visibility change, so re-picking a loop re-points wallpaper
     * that is already active. Unknown values fall back to the first loop
     * in [LiveLoop.byId] rather than crashing the engine.
     */
    fun liveLoopId(context: Context): String =
        prefs(context).getString(KEY_LIVE_LOOP, LiveLoop.LOOPS[0].id)
            ?: LiveLoop.LOOPS[0].id

    /**
     * Every change of loop, by hand or by [LoopRotation], restarts the
     * rotation interval, so a loop someone just picked stays for the whole of
     * it.
     */
    fun setLiveLoopId(context: Context, loopId: String, now: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(KEY_LIVE_LOOP, loopId)
            .putLong(KEY_LIVE_LOOP_CHANGED_AT, now)
            .apply()
    }

    fun liveLoopChangedAt(context: Context): Long =
        prefs(context).getLong(KEY_LIVE_LOOP_CHANGED_AT, 0L)

    fun setLiveLoopChangedAt(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_LIVE_LOOP_CHANGED_AT, at).apply()
    }

    /** Hours between live loop rotations (Core Builds Plus); 0 is off. */
    fun liveRotateHours(context: Context): Int =
        prefs(context).getInt(KEY_LIVE_ROTATE_HOURS, 0)

    /** Switching rotation on starts the interval now rather than at the last pick. */
    fun setLiveRotateHours(context: Context, hours: Int, now: Long = System.currentTimeMillis()) {
        val wasOff = liveRotateHours(context) == 0
        val edit = prefs(context).edit().putInt(KEY_LIVE_ROTATE_HOURS, hours)
        if (wasOff && hours > 0) edit.putLong(KEY_LIVE_LOOP_CHANGED_AT, now)
        edit.apply()
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
