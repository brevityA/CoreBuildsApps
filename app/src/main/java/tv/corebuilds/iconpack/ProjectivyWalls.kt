package tv.corebuilds.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONObject
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperProviderContract
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper as ProjectivyWallpaper

/**
 * The Core Builds walls as a Projectivy wallpaper source
 * ([WallsProviderService]). Projectivy 4.51 and later list it under its
 * wallpaper settings; wallpaper plugins are a Projectivy Premium feature.
 *
 * Everyone gets the main collection in Projectivy's own rotation, in every
 * build. Core Builds Plus adds the choice of what rotates (favourites, one
 * series, the Plus walls with the rest) and a second choice for night hours,
 * the same hours as the night loop ([DayNight]). A stored choice that Plus no
 * longer covers, or that matches no wall, falls back to the next one down and
 * finally to everything, so Projectivy is never handed an empty list.
 *
 * Projectivy downloads each wall from its URL (raw GitHub, as the app's own
 * browser does) and caches the list for an hour
 * (`items_cache_duration_millis`), so night hours take effect within the
 * hour. A change made here is sent to Projectivy at once ([notifyProjectivy]).
 */
object ProjectivyWalls {

    const val ALL = "all"
    const val FAVOURITES = "favourites"

    const val PROJECTIVY = "com.spocky.projengmenu"
    private const val AUTHOR = "Core Builds"
    private const val TAG = "CoreBuilds/Walls"

    /**
     * Every source the Settings row offers, in its order: all walls,
     * favourites, then each series in catalogue order.
     */
    fun sources(walls: List<Wallpaper>): List<String> =
        listOf(ALL, FAVOURITES) + walls.map { it.series }.distinct()

    /** The day row's next source after [current]; an unknown one starts over at [ALL]. */
    fun nextSource(current: String, options: List<String>): String =
        options[(options.indexOf(current) + 1) % options.size]

    /**
     * The walls for one request. Without Plus, the main collection whatever is
     * stored. With Plus, the Plus walls join the pool and the night source,
     * during night hours, then the day source choose from it.
     */
    fun pick(
        main: List<Wallpaper>,
        plus: List<Wallpaper>,
        owned: Boolean,
        source: String,
        night: String?,
        isNight: Boolean,
        favourites: Set<String>,
    ): List<Wallpaper> {
        if (!owned) return main
        val pool = main + plus
        for (chosen in listOfNotNull(night?.takeIf { isNight }, source)) {
            val walls = when (chosen) {
                ALL -> pool
                FAVOURITES -> pool.filter { it.cacheName in favourites }
                else -> pool.filter { it.series == chosen }
            }
            if (walls.isNotEmpty()) return walls
        }
        return pool
    }

    /** True when a star changes what Projectivy rotates. */
    fun usesFavourites(context: Context): Boolean =
        Plus.owned(context) &&
            (Prefs.wallsSource(context) == FAVOURITES || Prefs.wallsNight(context) == FAVOURITES)

    /** The walls Projectivy gets now, from this TV's settings. */
    fun walls(context: Context, now: Long = System.currentTimeMillis()): List<Wallpaper> = pick(
        main = WallpaperCatalog.load(context),
        plus = WallpaperCatalog.loadPlus(context),
        owned = Plus.owned(context),
        source = Prefs.wallsSource(context),
        night = Prefs.wallsNight(context),
        isNight = DayNight.isNightAt(context, now),
        favourites = Prefs.wallpaperFavourites(context),
    )

    fun toProjectivy(wall: Wallpaper): ProjectivyWallpaper = ProjectivyWallpaper(
        uri = wall.url,
        type = WallpaperType.IMAGE,
        displayMode = WallpaperDisplayMode.CROP,
        title = wall.title,
        source = WallpaperCatalog.seriesLabel(wall.series),
        author = AUTHOR,
    )

    /** A source's name in Settings; favourites carry their count, so an empty set shows. */
    fun label(context: Context, source: String, walls: List<Wallpaper>): String = when (source) {
        ALL -> context.getString(R.string.settings_walls_all)
        FAVOURITES -> {
            val stars = Prefs.wallpaperFavourites(context)
            context.getString(R.string.settings_walls_favourites, walls.count { it.cacheName in stars })
        }
        else -> WallpaperCatalog.seriesLabel(source)
    }

    /** The settings Projectivy backs up with its own (getPreferences). */
    fun exportPrefs(context: Context): String = JSONObject()
        .put("source", Prefs.wallsSource(context))
        .put("night", Prefs.wallsNight(context) ?: JSONObject.NULL)
        .toString()

    /** Restores [exportPrefs]' output; anything else is ignored. */
    fun importPrefs(context: Context, params: String?) {
        val o = try {
            JSONObject(params ?: return)
        } catch (_: Exception) {
            return
        }
        o.optString("source").takeIf { it.isNotBlank() }?.let { Prefs.setWallsSource(context, it) }
        if (o.has("night")) {
            Prefs.setWallsNight(context, if (o.isNull("night")) null else o.optString("night"))
        }
    }

    /**
     * Ask Projectivy to fetch the list again now rather than when its cache
     * runs out. Sent to Projectivy only; nothing happens if it is not there.
     */
    fun notifyProjectivy(context: Context, reason: Int) {
        val id = providerId(context) ?: return
        context.sendBroadcast(
            Intent(WallpaperProviderContract.ACTION_WALLPAPER_PROVIDER_UPDATED)
                .setPackage(PROJECTIVY)
                .putExtra(WallpaperProviderContract.EXTRA_PROVIDER_ID, id)
                .putExtra(WallpaperProviderContract.EXTRA_UPDATE_REASON, reason)
        )
    }

    /**
     * This build's provider id: the service's `uuid` meta-data, which
     * app/build.gradle.kts derives from the application id, so the GitHub,
     * Play, Glyphs and test builds on one TV are four sources, not one.
     */
    @Suppress("DEPRECATION") // the ComponentInfoFlags overload is API 33+; minSdk is 21
    private fun providerId(context: Context): String? = try {
        context.packageManager
            .getServiceInfo(ComponentName(context, WallsProviderService::class.java), PackageManager.GET_META_DATA)
            .metaData?.getString("uuid")
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "provider service not found", e)
        null
    }
}
