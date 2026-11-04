package tv.corebuilds.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build

/** One launchable activity per installed package, preferring its TV entry point. */
internal data class LaunchableApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable,
    val banner: Drawable?,
    val tvOptimized: Boolean,
    val lastUpdateTime: Long,
)

internal object LauncherAppCatalog {
    fun load(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val byPackage = linkedMapOf<String, LaunchableApp>()
        val queries = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) to true,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER) to false,
        )

        queries.forEach { (intent, tvOptimized) ->
            queryActivities(pm, intent).forEach activityLoop@{ resolved ->
                val activity = resolved.activityInfo ?: return@activityLoop
                val packageName = activity.packageName
                if (packageName == context.packageName) return@activityLoop
                // An entry guarded by a permission this launcher does not hold
                // throws SecurityException on launch. Skip it, so the package's
                // other launchable entry (often its LAUNCHER one) is kept instead
                // of a tile that can only show the "denied" toast.
                if (!canStart(pm, context.packageName, activity.permission)) return@activityLoop

                val previous = byPackage[packageName]
                // A package can expose both mobile and TV activities. Keep the
                // TV-specific one; otherwise keep the first launchable entry.
                if (previous == null || (tvOptimized && !previous.tvOptimized)) {
                    val label = resolved.loadLabel(pm)?.toString()?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: activity.name.substringAfterLast('.')
                    val icon = try {
                        resolved.loadIcon(pm)
                    } catch (_: RuntimeException) {
                        pm.defaultActivityIcon
                    }
                    // TV apps often publish a 16:9 banner for the focused
                    // spotlight. Keep it local and optional; the UI falls back
                    // to the package icon when no banner is supplied.
                    val banner = if (tvOptimized) {
                        try {
                            activity.loadBanner(pm)
                        } catch (_: RuntimeException) {
                            null
                        }
                    } else {
                        null
                    }
                    byPackage[packageName] = LaunchableApp(
                        packageName = packageName,
                        activityName = activity.name,
                        label = label,
                        icon = icon,
                        banner = banner,
                        tvOptimized = tvOptimized,
                        lastUpdateTime = packageLastUpdateTime(pm, packageName),
                    )
                }
            }
        }

        return byPackage.values.sortedWith(
            compareBy<LaunchableApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
                .thenBy { it.packageName },
        )
    }

    /** True when [ownPackage] may start an activity guarded by [permission] (null means unguarded). */
    internal fun canStart(pm: PackageManager, ownPackage: String, permission: String?): Boolean =
        permission.isNullOrEmpty() || pm.checkPermission(permission, ownPackage) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun packageLastUpdateTime(pm: PackageManager, packageName: String): Long = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L)).lastUpdateTime
        } else {
            pm.getPackageInfo(packageName, 0).lastUpdateTime
        }
    } catch (_: PackageManager.NameNotFoundException) {
        0L
    }

    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
    }
}
