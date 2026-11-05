package tv.corebuilds.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/** One launchable activity per installed package, preferring its TV entry point. */
internal data class LaunchableApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val tvOptimized: Boolean,
    val lastUpdateTime: Long,
    val art: CardArt,
) {
    /** Changes whenever what the card shows could change: a new label, entry point, update or art. */
    val renderKey: String get() = "$packageName/$activityName:$label:$tvOptimized:$lastUpdateTime:${art.fromPack}"
}

internal object LauncherAppCatalog {
    /**
     * Card art keyed by app version and [ArtSpec], so a refresh on every return
     * to Home re-renders only apps that changed. Pruned to the apps still installed.
     */
    private val artCache = HashMap<String, CardArt>()

    fun load(context: Context, spec: ArtSpec, pack: IconPack?): List<LaunchableApp> {
        val pm = context.packageManager
        val chosen = linkedMapOf<String, Pair<ResolveInfo, Boolean>>()
        val queries = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) to true,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER) to false,
        )
        for ((intent, tvOptimized) in queries) {
            for (resolved in pm.queryActivities(intent)) {
                val activity = resolved.activityInfo ?: continue
                val packageName = activity.packageName
                if (packageName == context.packageName) continue
                // An entry guarded by a permission this launcher does not hold
                // throws SecurityException on launch. Skip it, so the package's
                // other launchable entry (often its LAUNCHER one) is kept instead
                // of a card that can only show the "not allowed" toast.
                if (!canStart(pm, context.packageName, activity.permission)) continue
                val previous = chosen[packageName]
                // A package can expose both mobile and TV activities. Keep the
                // TV-specific one; otherwise keep the first launchable entry.
                if (previous == null || (tvOptimized && !previous.second)) chosen[packageName] = resolved to tvOptimized
            }
        }

        val liveKeys = HashSet<String>()
        val apps = chosen.map { (packageName, entry) ->
            val (resolved, tvOptimized) = entry
            val activity = resolved.activityInfo
            val label = resolved.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: activity.name.substringAfterLast('.')
            val updated = packageLastUpdateTime(pm, packageName)
            val cacheKey = "$packageName/${activity.name}:$updated:$tvOptimized:${spec}:${pack?.packageName}"
            liveKeys += cacheKey
            val art = synchronized(artCache) { artCache[cacheKey] } ?: CardArtFactory.create(
                spec = spec,
                packArt = pack?.drawableFor(packageName, activity.name),
                // TV apps publish a 16:9 banner; it fills the card. Others show their icon.
                appBanner = { if (tvOptimized) loadSafely { activity.loadBanner(pm) } else null },
                appIcon = { loadSafely { resolved.loadIcon(pm) } ?: pm.defaultActivityIcon },
            ).also { synchronized(artCache) { artCache[cacheKey] = it } }
            LaunchableApp(packageName, activity.name, label, tvOptimized, updated, art)
        }
        synchronized(artCache) { artCache.keys.retainAll(liveKeys) }
        return apps.sortedWith(
            compareBy<LaunchableApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.packageName },
        )
    }

    private inline fun <T> loadSafely(block: () -> T?): T? = try {
        block()
    } catch (_: RuntimeException) {
        null
    }

    /** True when [ownPackage] may start an activity guarded by [permission] (null means unguarded). */
    internal fun canStart(pm: PackageManager, ownPackage: String, permission: String?): Boolean =
        permission.isNullOrEmpty() || pm.checkPermission(permission, ownPackage) == PackageManager.PERMISSION_GRANTED

    private fun packageLastUpdateTime(pm: PackageManager, packageName: String): Long =
        pm.packageInfoOrNull(packageName)?.lastUpdateTime ?: 0L
}
