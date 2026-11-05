package tv.corebuilds.launcher

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/** Where the card art comes from: the app's own art, or an installed icon pack. */
internal sealed class ArtworkChoice {
    /** Not chosen yet: Core Builds banners when that pack is installed, else app art. */
    object Automatic : ArtworkChoice()
    object AppArtwork : ArtworkChoice()
    data class IconPack(val packageName: String) : ArtworkChoice()

    fun stored(): String = when (this) {
        Automatic -> ""
        AppArtwork -> APP
        is IconPack -> packageName
    }

    companion object {
        private const val APP = "app"
        fun parse(value: String?): ArtworkChoice = when {
            value.isNullOrEmpty() -> Automatic
            value == APP -> AppArtwork
            else -> IconPack(value)
        }
    }
}

internal enum class Backdrop { FOCUSED_APP, CORE_BUILDS, VOID }

/** How much the scrim darkens the background, as an alpha over black. */
internal enum class Dim(val scrimAlpha: Int) { LIGHT(90), MEDIUM(140), STRONG(196) }

internal enum class CardSize(val perRow: Int) { COMPACT(6), STANDARD(5), LARGE(4) }

/** User-selected transition amount. Android's system animator scale is applied on top. */
internal enum class MotionMode(val factor: Float) { FULL(1f), CALM(0.55f), OFF(0f) }

/** The order of the All apps grid. Inputs are local state, package metadata and optional owner-granted usage. */
internal enum class AppSort { A_Z, MOST_USED, TV_FIRST, NEWEST }

/** Everything the Customise panel can change. */
internal data class HomeSettings(
    val artwork: ArtworkChoice = ArtworkChoice.Automatic,
    val backdrop: Backdrop = Backdrop.FOCUSED_APP,
    val dim: Dim = Dim.MEDIUM,
    val cardSize: CardSize = CardSize.STANDARD,
    val showRecents: Boolean = true,
    val showClock: Boolean = true,
    val motion: MotionMode = MotionMode.FULL,
    val smartSuggestions: Boolean = true,
    val appSort: AppSort = AppSort.A_Z,
    /** A wallpaper URL the owner typed; empty means no network fetch. */
    val wallpaperUrl: String = "",
    /** Optional BOOT_COMPLETED fallback when this app is already HOME. */
    val launchOnBoot: Boolean = false,
)

/** One consistent read of the saved layout, taken off the main thread with each catalog scan. */
internal data class HomeState(
    /** Favourites in the user's order that are launchable now. */
    val favourites: List<String>,
    val hidden: Set<String>,
    /** Recently opened, or locally ranked suggestions, newest/best first. */
    val recents: List<String>,
    val settings: HomeSettings,
    /** Artwork the user chose per app, by package. Kept while an app is uninstalled. */
    val customArt: Map<String, CustomArt> = emptyMap(),
    /** Local launch counts plus optional system foreground minutes; used by Most used sorting. */
    val usage: Map<String, Long> = emptyMap(),
)

/** A launch record for local suggestions. The slot is a coarse hour/day part, never a location. */
private data class LaunchEvent(
    val packageName: String,
    val at: Long,
    val slot: Int,
    val dayType: Int,
)

/** Small, owner-controlled state. No account, cloud sync, or analytics. */
internal object LauncherPreferences {
    private const val FILE = "launcher_private_preferences"

    // 0.1 kept favourites as an unordered string set. 0.2 keeps an ordered list
    // and migrates the set once, in the order 0.1 showed it (A-Z by app name).
    private const val KEY_LEGACY_PINS = "pinned_packages"
    private const val KEY_FAVOURITES = "favourite_order"
    private const val KEY_HIDDEN = "hidden_packages"
    private const val KEY_RECENTS = "recent_packages"
    private const val KEY_LAUNCH_EVENTS = "launch_events"
    private const val KEY_ARTWORK = "artwork"
    private const val KEY_BACKDROP = "backdrop"
    private const val KEY_DIM = "backdrop_dim"
    private const val KEY_CARD_SIZE = "card_size"
    private const val KEY_SHOW_RECENTS = "show_recents"
    private const val KEY_SHOW_CLOCK = "show_clock"
    private const val KEY_MOTION = "motion"
    private const val KEY_SMART_SUGGESTIONS = "smart_suggestions"
    private const val KEY_APP_SORT = "app_sort"
    private const val KEY_WALLPAPER_URL = "wallpaper_url"
    private const val KEY_LAUNCH_ON_BOOT = "launch_on_boot"
    private const val KEY_CUSTOM_ART = "custom_art"

    private const val RECENTS_LIMIT = 5
    private const val EVENT_LIMIT = 96
    private const val EVENT_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
    private const val SEPARATOR = "\n"

    /** Backup/restore writes only these launcher-owned keys. */
    private val WRITABLE_KEYS = setOf(
        KEY_LEGACY_PINS, KEY_FAVOURITES, KEY_HIDDEN, KEY_RECENTS, KEY_LAUNCH_EVENTS,
        KEY_ARTWORK, KEY_BACKDROP, KEY_DIM, KEY_CARD_SIZE, KEY_SHOW_RECENTS,
        KEY_SHOW_CLOCK, KEY_MOTION, KEY_SMART_SUGGESTIONS, KEY_APP_SORT,
        KEY_WALLPAPER_URL, KEY_LAUNCH_ON_BOOT, KEY_CUSTOM_ART,
    )

    // On first run, surface the Core Builds apps that are actually installed.
    // After the user edits favourites, their explicit list is the source of truth.
    // Each entry is one app. Where it ships as a sideload and a Google Play build,
    // one installed build is seeded (the sideload one first), so a TV with both gets one card.
    private val coreBuildsPackages = listOf(
        IconPacks.CORE_BUILDS_BANNERS,
        listOf("dev.corebuilds.line"),
        listOf("dev.corebuilds.shift"),
        listOf("tv.corebuilds.eq"),
        listOf("dev.corebuilds.doctor"),
    )

    internal fun rawPreferences(context: Context): SharedPreferences = prefs(context)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun SharedPreferences.list(key: String): List<String> =
        getString(key, null).orEmpty().split(SEPARATOR).filter { it.isNotBlank() }.distinct()

    private fun SharedPreferences.Editor.putList(key: String, value: List<String>): SharedPreferences.Editor =
        putString(key, value.distinct().joinToString(SEPARATOR))

    /**
     * Reads the whole layout at once. [installedInOrder] is every launchable
     * package, A-Z by label: the order a 0.1 favourite set is migrated in.
     */
    fun snapshot(context: Context, installedInOrder: List<String>): HomeState {
        val preferences = prefs(context)
        val installed = installedInOrder.toSet()
        // Keep a user's choice while an app is uninstalled; never show it.
        val favourites = savedFavourites(preferences, installedInOrder).filter { it in installed }
        val hidden = preferences.list(KEY_HIDDEN).toSet()
        val settings = settings(preferences)
        val exclude = favourites.toSet() + hidden
        val events = activeEvents(preferences)
        val localUsage = events.groupingBy { it.packageName }.eachCount().mapValues { it.value.toLong() }
        val usage = LauncherUsage.usageCounts(context, installed, localUsage)
        return HomeState(
            favourites = favourites,
            hidden = hidden,
            recents = suggestions(preferences, events, usage, installed, exclude, settings.smartSuggestions),
            settings = settings,
            customArt = customArt(preferences),
            usage = usage,
        )
    }

    /** One "package<TAB>art" line per app with chosen artwork. */
    private fun customArt(preferences: SharedPreferences): Map<String, CustomArt> =
        preferences.list(KEY_CUSTOM_ART).mapNotNull { line ->
            val app = line.substringBefore('\t', "")
            val art = CustomArt.parse(line.substringAfter('\t', ""))
            if (app.isBlank() || art == null) null else app to art
        }.toMap()

    fun readCustomArt(context: Context): Map<String, CustomArt> = customArt(prefs(context))

    /** Sets or (with null) clears [packageName]'s artwork and returns what it replaced. */
    fun setCustomArt(context: Context, packageName: String, art: CustomArt?): CustomArt? {
        val preferences = prefs(context)
        val current = customArt(preferences).toMutableMap()
        val previous = current.remove(packageName)
        if (art != null) current[packageName] = art
        preferences.edit().putList(KEY_CUSTOM_ART, current.map { (app, value) -> "$app\t${value.stored()}" }).apply()
        return previous
    }

    /** The saved order, migrating 0.1's set or seeding the first-run Core Builds favourites. */
    private fun savedFavourites(preferences: SharedPreferences, installedInOrder: List<String>): List<String> {
        if (preferences.contains(KEY_FAVOURITES)) return preferences.list(KEY_FAVOURITES)
        if (preferences.contains(KEY_LEGACY_PINS)) {
            // Uninstalled favourites go after the visible ones instead of being dropped.
            val legacy = preferences.getStringSet(KEY_LEGACY_PINS, emptySet()).orEmpty()
            val migrated = installedInOrder.filter { it in legacy } + legacy.filterNot { it in installedInOrder }.sorted()
            preferences.edit().putList(KEY_FAVOURITES, migrated).remove(KEY_LEGACY_PINS).apply()
            return migrated
        }
        return coreBuildsPackages.mapNotNull { builds -> builds.firstOrNull { it in installedInOrder } }
    }

    /** The five most recently opened launchable packages that are not favourites or hidden. */
    private fun recentPackages(preferences: SharedPreferences, installed: Set<String>, exclude: Set<String>): List<String> {
        val stored = preferences.list(KEY_RECENTS)
        val launchable = stored.filter { it in installed }
        if (preferences.contains(KEY_RECENTS) && launchable != stored) {
            // Drop uninstalled apps so the row can fill with launchable ones.
            preferences.edit().putList(KEY_RECENTS, launchable.take(RECENTS_LIMIT)).apply()
        }
        return launchable.filterNot { it in exclude }.take(RECENTS_LIMIT)
    }

    /**
     * Smart suggestions combine recent use, frequency and the current coarse
     * time context. The score is deterministic and uses only events recorded on
     * this TV in the last 30 days. With no events it is exactly the legacy row.
     */
    private fun suggestions(
        preferences: SharedPreferences,
        events: List<LaunchEvent>,
        usage: Map<String, Long>,
        installed: Set<String>,
        exclude: Set<String>,
        smart: Boolean,
    ): List<String> {
        val fallback = recentPackages(preferences, installed, exclude)
        if (!smart) return fallback
        val eligible = events.filter { it.packageName in installed && it.packageName !in exclude }
        if (eligible.isEmpty()) return fallback
        val now = System.currentTimeMillis()
        val slot = timeSlot(now)
        val dayType = dayType(now)
        val candidates = (fallback + eligible.map { it.packageName }.distinct()).distinct()
        return candidates.map { packageName ->
            val own = eligible.filter { it.packageName == packageName }
            val last = own.maxOfOrNull { it.at } ?: 0L
            val age = (now - last).coerceIn(0L, EVENT_WINDOW_MS)
            val recency = 50f * (1f - age.toFloat() / EVENT_WINDOW_MS.toFloat())
            val frequency = own.size * 5f
            val context = own.count { it.slot == slot && it.dayType == dayType } * 12f
            val legacyIndex = fallback.indexOf(packageName)
            val legacy = if (legacyIndex >= 0) (RECENTS_LIMIT - legacyIndex) * 0.2f else 0f
            val foreground = (usage[packageName] ?: 0L).coerceAtMost(100L)
            packageName to (recency + frequency + context + legacy + foreground)
        }
            .sortedWith(compareByDescending<Pair<String, Float>> { it.second }.thenBy { it.first })
            .map { it.first }
            .take(RECENTS_LIMIT)
    }

    /** Reads and prunes the bounded event window; a malformed line is ignored. */
    private fun activeEvents(preferences: SharedPreferences): List<LaunchEvent> {
        val now = System.currentTimeMillis()
        val stored = preferences.list(KEY_LAUNCH_EVENTS)
        val events = stored.mapNotNull { line ->
            val parts = line.split('|')
            val packageName = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val at = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            val slot = parts.getOrNull(2)?.toIntOrNull() ?: timeSlot(at)
            val day = parts.getOrNull(3)?.toIntOrNull() ?: dayType(at)
            LaunchEvent(packageName, at, slot, day)
        }.filter { it.at in 0..now && now - it.at <= EVENT_WINDOW_MS }.takeLast(EVENT_LIMIT)
        if (events.size != stored.size) {
            preferences.edit().putList(KEY_LAUNCH_EVENTS, events.map { it.stored() }).apply()
        }
        return events
    }

    private fun LaunchEvent.stored(): String = "$packageName|$at|$slot|$dayType"

    /** Morning, day, evening and late night; no exact timestamp is kept in a suggestion model. */
    private fun timeSlot(time: Long): Int {
        val hour = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> 0
            in 11..16 -> 1
            in 17..22 -> 2
            else -> 3
        }
    }

    private fun dayType(time: Long): Int {
        val day = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) 1 else 0
    }

    /** The Customise settings alone; the scan reads them first to size the card art. */
    fun readSettings(context: Context): HomeSettings = settings(prefs(context))

    private fun settings(preferences: SharedPreferences): HomeSettings {
        val defaults = HomeSettings()
        return HomeSettings(
            artwork = ArtworkChoice.parse(preferences.getString(KEY_ARTWORK, null)),
            backdrop = enumValue(preferences.getString(KEY_BACKDROP, null), defaults.backdrop),
            dim = enumValue(preferences.getString(KEY_DIM, null), defaults.dim),
            cardSize = enumValue(preferences.getString(KEY_CARD_SIZE, null), defaults.cardSize),
            showRecents = preferences.getBoolean(KEY_SHOW_RECENTS, defaults.showRecents),
            showClock = preferences.getBoolean(KEY_SHOW_CLOCK, defaults.showClock),
            motion = enumValue(preferences.getString(KEY_MOTION, null), defaults.motion),
            smartSuggestions = preferences.getBoolean(KEY_SMART_SUGGESTIONS, defaults.smartSuggestions),
            appSort = enumValue(preferences.getString(KEY_APP_SORT, null), defaults.appSort),
            wallpaperUrl = preferences.getString(KEY_WALLPAPER_URL, defaults.wallpaperUrl).orEmpty(),
            launchOnBoot = preferences.getBoolean(KEY_LAUNCH_ON_BOOT, defaults.launchOnBoot),
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    fun saveSettings(context: Context, settings: HomeSettings) {
        prefs(context).edit()
            .putString(KEY_ARTWORK, settings.artwork.stored())
            .putString(KEY_BACKDROP, settings.backdrop.name)
            .putString(KEY_DIM, settings.dim.name)
            .putString(KEY_CARD_SIZE, settings.cardSize.name)
            .putBoolean(KEY_SHOW_RECENTS, settings.showRecents)
            .putBoolean(KEY_SHOW_CLOCK, settings.showClock)
            .putString(KEY_MOTION, settings.motion.name)
            .putBoolean(KEY_SMART_SUGGESTIONS, settings.smartSuggestions)
            .putString(KEY_APP_SORT, settings.appSort.name)
            .putString(KEY_WALLPAPER_URL, settings.wallpaperUrl)
            .putBoolean(KEY_LAUNCH_ON_BOOT, settings.launchOnBoot)
            .apply()
    }

    /**
     * Adds [packageName] at the end of the row or removes it. Writes the state
     * asked for rather than toggling, so a panel opened before the last render
     * still does what its label said. Saved apps that are not installed keep their place.
     */
    fun setFavourite(context: Context, packageName: String, favourite: Boolean, installedInOrder: List<String>) {
        val preferences = prefs(context)
        val saved = savedFavourites(preferences, installedInOrder).toMutableList()
        saved.remove(packageName)
        if (favourite) saved.add(packageName)
        preferences.edit().putList(KEY_FAVOURITES, saved).apply()
    }

    /**
     * Saves a new order for the favourites on screen. Favourites that are not
     * installed right now are not on screen; they keep their saved slots and the
     * on-screen ones fill the rest in the new order.
     */
    fun reorderFavourites(context: Context, visibleOrder: List<String>, installedInOrder: List<String>) {
        val preferences = prefs(context)
        val saved = savedFavourites(preferences, installedInOrder)
        val visible = visibleOrder.toSet()
        val queue = ArrayDeque(visibleOrder.filter { it in saved })
        val merged = saved.map { if (it in visible) queue.removeFirst() else it } + queue
        preferences.edit().putList(KEY_FAVOURITES, merged).apply()
    }

    fun setHidden(context: Context, packageName: String, hidden: Boolean) {
        val preferences = prefs(context)
        val current = preferences.list(KEY_HIDDEN).toMutableList()
        current.remove(packageName)
        if (hidden) current.add(packageName)
        preferences.edit().putList(KEY_HIDDEN, current).apply()
    }

    /** Records a launch for both the legacy five-item row and local smart suggestions. */
    fun recordLaunch(context: Context, packageName: String) {
        val preferences = prefs(context)
        val now = System.currentTimeMillis()
        val event = LaunchEvent(packageName, now, timeSlot(now), dayType(now))
        val events = (activeEvents(preferences) + event).takeLast(EVENT_LIMIT)
        val updated = (listOf(packageName) + preferences.list(KEY_RECENTS).filterNot { it == packageName })
            .take(RECENTS_LIMIT)
        preferences.edit()
            .putList(KEY_LAUNCH_EVENTS, events.map { it.stored() })
            .putList(KEY_RECENTS, updated)
            .apply()
    }

    /** Replaces only known launcher keys from a validated, user-chosen backup. */
    fun replaceAll(context: Context, values: Map<String, Any?>) {
        val editor = prefs(context).edit().clear()
        for (key in WRITABLE_KEYS) {
            when (val value = values[key]) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is List<*> -> {
                    val strings = value.filterIsInstance<String>()
                    if (key == KEY_LEGACY_PINS) editor.putStringSet(key, strings.toSet())
                    else editor.putString(key, strings.distinct().joinToString(SEPARATOR))
                }
                else -> Unit
            }
        }
        editor.apply()
    }

    /** Favourites, hidden apps, history and settings back to first run. */
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
