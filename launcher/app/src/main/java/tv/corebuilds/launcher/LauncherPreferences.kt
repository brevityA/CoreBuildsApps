package tv.corebuilds.launcher

import android.content.Context

/** Small, device-local state only. No account, network sync, or analytics. */
internal object LauncherPreferences {
    private const val FILE = "launcher_private_preferences"
    private const val KEY_PINS = "pinned_packages"
    private const val KEY_RECENTS = "recent_packages"
    private const val RECENTS_LIMIT = 5
    private const val RECENT_SEPARATOR = "\n"

    // On first run, surface the Core Builds apps that are actually installed.
    // After the user edits pins, their explicit selection is the source of truth.
    private val coreBuildsPackages = listOf(
        "tv.corebuilds.iconpack",
        "dev.corebuilds.line",
        "dev.corebuilds.shift",
        "tv.corebuilds.eq",
        "dev.corebuilds.doctor",
    )

    fun pinnedPackages(context: Context, installedPackages: Set<String>): Set<String> {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (preferences.contains(KEY_PINS)) {
            // Keep a user's preference if an app is temporarily uninstalled, but
            // never count or render a favourite that is not launchable right now.
            return preferences.getStringSet(KEY_PINS, emptySet()).orEmpty()
                .filterTo(linkedSetOf()) { it in installedPackages }
        }
        return coreBuildsPackages.filterTo(linkedSetOf()) { it in installedPackages }
    }

    /** The five most recently opened, still-launchable packages; history never leaves this device. */
    fun recentPackages(context: Context, installedPackages: Set<String>): List<String> {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val history = preferences.getString(KEY_RECENTS, null)
            .orEmpty()
            .split(RECENT_SEPARATOR)
        val storedHistory = history.filter { it.isNotBlank() }
        val recent = storedHistory
            .filter { it in installedPackages }
            .distinct()
            .take(RECENTS_LIMIT)
        if (preferences.contains(KEY_RECENTS) && storedHistory != recent) {
            // Drop stale uninstalls and old duplicates so the visible shelf can
            // still fill with the newest launchable apps.
            preferences.edit().putString(KEY_RECENTS, recent.joinToString(RECENT_SEPARATOR)).apply()
        }
        return recent
    }

    fun recordLaunch(context: Context, packageName: String) {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val history = preferences.getString(KEY_RECENTS, null)
            .orEmpty()
            .split(RECENT_SEPARATOR)
            .filter { it.isNotBlank() }
        val updated = (listOf(packageName) + history.filterNot { it == packageName }).take(RECENTS_LIMIT)
        preferences.edit().putString(KEY_RECENTS, updated.joinToString(RECENT_SEPARATOR)).apply()
    }

    fun setPinnedPackages(context: Context, packages: Set<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PINS, packages.toSet())
            .apply()
    }

    fun togglePinned(
        context: Context,
        packageName: String,
        installedPackages: Set<String>,
    ): Boolean {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val updated = if (preferences.contains(KEY_PINS)) {
            // Preserve temporarily unavailable packages when another favourite is edited.
            preferences.getStringSet(KEY_PINS, emptySet()).orEmpty().toMutableSet()
        } else {
            pinnedPackages(context, installedPackages).toMutableSet()
        }
        val isNowPinned = if (updated.contains(packageName)) {
            updated.remove(packageName)
            false
        } else {
            updated.add(packageName)
            true
        }
        setPinnedPackages(context, updated)
        return isNowPinned
    }
}
