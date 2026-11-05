package tv.corebuilds.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.LruCache

/** One launchable activity per installed package, preferring its TV entry point. */
internal data class LaunchableApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val tvOptimized: Boolean,
    val lastUpdateTime: Long,
    val art: CardArt,
    /** The user's artwork choice for this app, as stored; empty for none. */
    val artKey: String = "",
    /** First install time, for the Newest install sort. */
    val installedAt: Long = 0L,
) {
    /** Changes whenever what the card shows could change: a new label, entry point, update or art. */
    val renderKey: String get() = "$packageName/$activityName:$label:$tvOptimized:$lastUpdateTime:${art.source}:$artKey"
}

internal object LauncherAppCatalog {
    /**
     * Card art keyed by app version and [ArtSpec], so a refresh on every return
     * to Home re-renders only apps that changed. The byte cap mirrors the
     * bounded-image-cache lesson from current launcher releases: a TV with a
     * large app catalogue cannot make bitmap memory grow without limit.
     */
    private const val ART_CACHE_BYTES = 24 * 1024 * 1024
    private val artCache = object : LruCache<String, CardArt>(ART_CACHE_BYTES) {
        override fun sizeOf(key: String, value: CardArt): Int =
            listOf(value.banner, value.icon, value.backdrop).sumOf { bitmap -> bitmap?.byteCount ?: 0 }
                .coerceAtLeast(1)
        // Do not recycle evicted bitmaps: a card in the current view may still draw them.
    }

    @Suppress("DEPRECATION")
    fun load(context: Context, spec: ArtSpec, pack: IconPack?, custom: Map<String, CustomArt> = emptyMap()): List<LaunchableApp> {
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

        // QUERY_ALL_PACKAGES: include packages whose launch activity was hidden
        // by Android 11's visibility filter but which still publish a HOME-
        // launchable intent. Permission-guarded entries are skipped exactly as
        // above. The narrow queries remain the first, cheapest discovery path.
        val installedApplications = try {
            pm.getInstalledApplications(0)
        } catch (_: RuntimeException) {
            emptyList()
        }
        for (application in installedApplications) {
            val packageName = application.packageName ?: continue
            if (packageName == context.packageName || chosen.containsKey(packageName)) continue
            val launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName)
                ?: pm.getLaunchIntentForPackage(packageName)
                ?: continue
            val resolved = try {
                pm.resolveActivity(launchIntent, 0)
            } catch (_: RuntimeException) {
                null
            } ?: continue
            val activity = resolved.activityInfo ?: continue
            if (!canStart(pm, context.packageName, activity.permission)) continue
            chosen[packageName] = resolved to launchIntent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val liveKeys = HashSet<String>()
        val apps = chosen.map { (packageName, entry) ->
            val (resolved, tvOptimized) = entry
            val activity = resolved.activityInfo
            val label = resolved.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: activity.name.substringAfterLast('.')
            // One PackageInfo lookup gives both the art invalidation timestamp
            // and the install time used by the Newest install sort.
            val info = pm.packageInfoOrNull(packageName)
            val updated = info?.lastUpdateTime ?: 0L
            val installedAt = info?.firstInstallTime ?: 0L
            val pick = custom[packageName]
            val cacheKey = "$packageName/${activity.name}:$updated:$tvOptimized:${spec}:${pack?.packageName}:${pick?.stored()}"
            liveKeys += cacheKey
            val art = synchronized(artCache) { artCache.get(cacheKey) } ?: run {
                // The user's pick for this app wins; if its pack or file is gone, the app falls back.
                val customDrawable = pick?.let { CustomArtStore.load(context, it) }
                CardArtFactory.create(
                    spec = spec,
                    packArt = customDrawable ?: pack?.drawableFor(packageName, activity.name),
                    packSource = if (customDrawable != null) ArtSource.CUSTOM else ArtSource.PACK,
                    // TV apps publish a 16:9 banner; it fills the card. Others show their icon.
                    appBanner = { if (tvOptimized) loadSafely { activity.loadBanner(pm) } else null },
                    appIcon = { loadSafely { resolved.loadIcon(pm) } ?: pm.defaultActivityIcon },
                )
            }.also { synchronized(artCache) { artCache.put(cacheKey, it) } }
            LaunchableApp(packageName, activity.name, label, tvOptimized, updated, art, pick?.stored().orEmpty(), installedAt)
        }
        synchronized(artCache) {
            for (key in artCache.snapshot().keys) if (key !in liveKeys) artCache.remove(key)
            artCache.trimToSize(ART_CACHE_BYTES)
        }
        return apps.sortedWith(
            compareBy<LaunchableApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.packageName },
        )
    }

    /** Retires cached bitmaps when the art source or card size changes. */
    fun clearArtCache() {
        synchronized(artCache) { artCache.evictAll() }
    }

    private inline fun <T> loadSafely(block: () -> T?): T? = try {
        block()
    } catch (_: RuntimeException) {
        null
    }

    /** True when [ownPackage] may start an activity guarded by [permission] (null means unguarded). */
    internal fun canStart(pm: PackageManager, ownPackage: String, permission: String?): Boolean =
        permission.isNullOrEmpty() || pm.checkPermission(permission, ownPackage) == PackageManager.PERMISSION_GRANTED
}

/** Small compatibility façade for cache lifecycle calls from the activity. */
internal object LauncherApps {
    fun clearArtCache() = LauncherAppCatalog.clearArtCache()
}
