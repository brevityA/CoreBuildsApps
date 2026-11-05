package tv.corebuilds.launcher

import android.content.Context
import android.content.SharedPreferences

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

/** Everything the Customise panel can change. */
internal data class HomeSettings(
    val artwork: ArtworkChoice = ArtworkChoice.Automatic,
    val backdrop: Backdrop = Backdrop.FOCUSED_APP,
    val dim: Dim = Dim.MEDIUM,
    val cardSize: CardSize = CardSize.STANDARD,
    val showRecents: Boolean = true,
    val showClock: Boolean = true,
)

/** One consistent read of the saved layout, taken off the main thread with each catalog scan. */
internal data class HomeState(
    /** Favourites in the user's order that are launchable now. */
    val favourites: List<String>,
    val hidden: Set<String>,
    /** Recently opened, newest first, never a favourite or a hidden app. */
    val recents: List<String>,
    val settings: HomeSettings,
)

/** Small, device-local state only. No account, network sync, or analytics. */
internal object LauncherPreferences {
    private const val FILE = "launcher_private_preferences"

    // 0.1 kept favourites as an unordered string set. 0.2 keeps an ordered list
    // and migrates the set once, in the order 0.1 showed it (A-Z by app name).
    private const val KEY_LEGACY_PINS = "pinned_packages"
    private const val KEY_FAVOURITES = "favourite_order"
    private const val KEY_HIDDEN = "hidden_packages"
    private const val KEY_RECENTS = "recent_packages"
    private const val KEY_ARTWORK = "artwork"
    private const val KEY_BACKDROP = "backdrop"
    private const val KEY_DIM = "backdrop_dim"
    private const val KEY_CARD_SIZE = "card_size"
    private const val KEY_SHOW_RECENTS = "show_recents"
    private const val KEY_SHOW_CLOCK = "show_clock"

    private const val RECENTS_LIMIT = 5
    private const val SEPARATOR = "\n"

    // On first run, surface the Core Builds apps that are actually installed.
    // After the user edits favourites, their explicit list is the source of truth.
    private val coreBuildsPackages = listOf(
        "tv.corebuilds.iconpack",
        "dev.corebuilds.line",
        "dev.corebuilds.shift",
        "tv.corebuilds.eq",
        "dev.corebuilds.doctor",
    )

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
        return HomeState(
            favourites = favourites,
            hidden = hidden,
            recents = recentPackages(preferences, installed, exclude = favourites.toSet() + hidden),
            settings = settings(preferences),
        )
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
        return coreBuildsPackages.filter { it in installedInOrder }
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

    fun recordLaunch(context: Context, packageName: String) {
        val preferences = prefs(context)
        val updated = (listOf(packageName) + preferences.list(KEY_RECENTS).filterNot { it == packageName })
            .take(RECENTS_LIMIT)
        preferences.edit().putList(KEY_RECENTS, updated).apply()
    }

    /** Favourites, hidden apps, history and settings back to first run. */
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
