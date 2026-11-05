package tv.corebuilds.launcher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * System usage statistics are optional and owner-granted. With access, the
 * Suggested row and Most used order can use foreground time from the last 30
 * days; without it, the prototype falls back to its own bounded launch events.
 * No statistics leave the device and no account is involved.
 */
internal object LauncherUsage {
    private const val WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
    private const val MAX_DAILY_MINUTES = 600L

    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        return try {
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /** Foreground minutes from system usage stats, plus a small local-launch weight. */
    fun usageCounts(context: Context, installed: Set<String>, local: Map<String, Long>): Map<String, Long> {
        if (!hasAccess(context)) return local
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return local
        val now = System.currentTimeMillis()
        val stats = try {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - WINDOW_MS, now).orEmpty()
        } catch (_: Exception) {
            return local
        }
        val counts = LinkedHashMap<String, Long>()
        stats.forEach { stat ->
            val packageName = stat.packageName ?: return@forEach
            if (packageName !in installed) return@forEach
            val minutes = (stat.totalTimeInForeground / 60_000L).coerceIn(0L, MAX_DAILY_MINUTES)
            counts[packageName] = (counts[packageName] ?: 0L) + minutes
        }
        local.forEach { (packageName, launches) ->
            counts[packageName] = (counts[packageName] ?: 0L) + launches * 2L
        }
        return counts.ifEmpty { local }
    }
}
