package tv.corebuilds.motion

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Runs, on-device, exactly the query Projectivy runs to discover wallpaper
 * providers — then reports what it found.
 *
 * The point is to collapse the ambiguity in "Projectivy doesn't see the plugin".
 * If this reports every line green and the launcher still shows no source, the
 * problem is on Projectivy's side (Premium not active, or a stale plugin list
 * that a force-stop clears) and not in this APK.
 *
 * See docs/PROJECTIVY_DETECTION.md.
 */
object Diagnostics {

    const val PROJECTIVY_PACKAGE_ID = "com.spocky.projengmenu"
    private const val DISCOVERY_ACTION = "tv.projectivy.plugin.WALLPAPER_PROVIDER"

    data class Check(val label: String, val passed: Boolean, val detail: String)

    data class Report(val checks: List<Check>) {
        val allPassed: Boolean get() = checks.all { it.passed }

        /** Compact multi-line summary for the settings screen. */
        fun render(): String = buildString {
            for (c in checks) {
                append(if (c.passed) "✓ " else "✗ ")
                append(c.label)
                if (c.detail.isNotBlank()) {
                    append(" — ")
                    append(c.detail)
                }
                append('\n')
            }
            append('\n')
            append(
                if (allPassed) {
                    "This plugin is discoverable. If Projectivy still shows no " +
                        "\"Core Motion\" source, check that Premium is active, then " +
                        "force-stop Projectivy so it re-reads the plugin list."
                } else {
                    "Something above is blocking discovery. Reinstall the plugin " +
                        "APK for this user profile, then reopen this screen."
                },
            )
        }
    }

    fun run(context: Context): Report {
        val pm = context.packageManager
        val checks = mutableListOf<Check>()

        // 1 — is the launcher even here?
        val projectivy = runCatching { pm.getPackageInfo(PROJECTIVY_PACKAGE_ID, 0) }.getOrNull()
        checks += Check(
            "Projectivy installed",
            projectivy != null,
            projectivy?.let { "v${it.versionName}" } ?: "not found",
        )

        // 2 — the discovery query itself, GET_META_DATA, same as Projectivy.
        val resolved: List<ResolveInfo> = runCatching {
            pm.queryIntentServices(Intent(DISCOVERY_ACTION), PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        val mine = resolved.firstOrNull {
            it.serviceInfo?.packageName == context.packageName
        }
        checks += Check(
            "Own service resolves",
            mine != null,
            if (mine != null) {
                mine.serviceInfo.name.substringAfterLast('.')
            } else {
                "the WALLPAPER_PROVIDER intent does not resolve to this app"
            },
        )

        // 3 — the meta-data Projectivy reads off that service.
        val meta = mine?.serviceInfo?.metaData
        val apiVersion = meta?.get("apiVersion")?.toString()
        checks += Check("apiVersion", apiVersion == "1", apiVersion ?: "missing")

        val uuid = meta?.get("uuid")?.toString()
        checks += Check(
            "Plugin UUID",
            !uuid.isNullOrBlank() && uuid != "CHANGE_ME",
            uuid?.take(8)?.plus("…") ?: "missing",
        )

        val name = meta?.get("name")?.toString()
        checks += Check("Plugin name", !name.isNullOrBlank(), name ?: "missing")

        val settingsActivity = meta?.get("settingsActivity")?.toString()
        checks += Check(
            "Settings activity",
            !settingsActivity.isNullOrBlank(),
            settingsActivity ?: "missing",
        )

        // 4 — other providers found, useful as a control. If Overflight shows up
        //     here and Core Motion doesn't, the fault is definitively ours.
        val others = resolved
            .mapNotNull { it.serviceInfo?.packageName }
            .filter { it != context.packageName }
        checks += Check(
            "Other providers seen",
            true,
            if (others.isEmpty()) "none" else others.joinToString { it.substringAfterLast('.') },
        )

        return Report(checks)
    }
}
