package tv.corebuilds.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Direct-apply support.
 *
 * Icon packs don't apply themselves — the launcher does. Each launcher exposes
 * its own intent contract for "adopt this pack", so applying is a matter of
 * firing the right intent at the right package with the pack's own package name
 * as the payload.
 *
 * The contracts below are the ones Blueprint has established across the icon
 * pack ecosystem; Projectivy's is:
 *
 *     action  com.spocky.projengmenu.APPLY_ICONPACK
 *     package com.spocky.projengmenu
 *     extra   com.spocky.projengmenu.extra.ICONPACK_PACKAGENAME = <our package>
 *
 * Projectivy handles this as an activity intent (startActivity), not a
 * broadcast. It applies the pack and returns.
 *
 * Brand Guide §05/§08: we say what will happen before it happens, and report
 * exactly what did — including naming the launcher when we can't do it.
 */
object ApplyIconPack {

    /** Result of an apply attempt. Never an unnamed error. */
    sealed class Result {
        /** The launcher accepted the intent. */
        data class Applied(val launcherName: String) : Result()

        /** Launcher isn't installed. */
        data class NotInstalled(val launcherName: String) : Result()

        /**
         * The launcher is installed but refused or doesn't support direct
         * apply. [instructions] names the manual path.
         */
        data class Manual(val launcherName: String, val instructions: String) : Result()
    }

    data class Launcher(
        val key: String,
        val displayName: String,
        val packages: List<String>,
        /** Builds the apply intent, given our own package name. */
        val intent: (Context, String) -> Intent?,
        /** Manual fallback, used when the intent bounces. */
        val manualPath: String
    )

    val PROJECTIVY = Launcher(
        key = "projectivy",
        displayName = "Projectivy Launcher",
        packages = listOf("com.spocky.projengmenu"),
        intent = { _, self ->
            Intent("com.spocky.projengmenu.APPLY_ICONPACK").apply {
                `package` = "com.spocky.projengmenu"
                putExtra("com.spocky.projengmenu.extra.ICONPACK_PACKAGENAME", self)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        },
        manualPath = "Projectivy Settings → Appearance → Cards → Icon Pack → Core Builds Icon Pack"
    )

    val NOVA = Launcher(
        key = "nova",
        displayName = "Nova Launcher",
        packages = listOf("com.teslacoilsw.launcher"),
        intent = { _, self ->
            Intent("com.teslacoilsw.launcher.APPLY_ICON_THEME").apply {
                `package` = "com.teslacoilsw.launcher"
                putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_TYPE", "GO")
                putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE", self)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        },
        manualPath = "Nova Settings → Look & feel → Icon style → Icon theme"
    )

    private val LAWNCHAIR_PACKAGES = listOf(
        "app.lawnchair",
        "ch.deletescape.lawnchair",
        "ch.deletescape.lawnchair.plah",
        "ch.deletescape.lawnchair.ci",
        "ch.deletescape.lawnchair.dev"
    )

    val LAWNCHAIR = Launcher(
        key = "lawnchair",
        displayName = "Lawnchair",
        packages = LAWNCHAIR_PACKAGES,
        intent = { ctx, self ->
            val pkg = LAWNCHAIR_PACKAGES.firstOrNull { ctx.isInstalled(it) }
            pkg?.let { p ->
                Intent("ch.deletescape.lawnchair.APPLY_ICONS").apply {
                    `package` = p
                    putExtra("packageName", self)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        },
        manualPath = "Lawnchair Settings → General → Icon style → Icon pack"
    )

    val APEX = Launcher(
        key = "apex",
        displayName = "Apex Launcher",
        packages = listOf("com.anddoes.launcher"),
        intent = { _, self ->
            Intent("com.anddoes.launcher.SET_THEME").apply {
                putExtra("com.anddoes.launcher.THEME_PACKAGE_NAME", self)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        },
        manualPath = "Apex Settings → Theme settings"
    )

    val ADW = Launcher(
        key = "adw",
        displayName = "ADW Launcher",
        packages = listOf("org.adw.launcher", "org.adwfreak.launcher"),
        intent = { ctx, self ->
            val ex = ctx.isInstalled("org.adwfreak.launcher")
            val prefix = if (ex) "org.adwfreak.launcher" else "org.adw.launcher"
            Intent("$prefix.SET_THEME").apply {
                putExtra("$prefix.theme.NAME", self)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        },
        manualPath = "ADW Settings → Themes"
    )

    /** Every launcher we can hand off to, Projectivy first — it's the target. */
    val ALL = listOf(PROJECTIVY, NOVA, LAWNCHAIR, APEX, ADW)

    fun Context.isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun Context.isInstalledAny(l: Launcher) = l.packages.any { isInstalled(it) }

    /** The installed launcher currently set as HOME, if we support it. */
    fun detectDefault(context: Context): Launcher? = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pkg = context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        ALL.firstOrNull { l -> pkg != null && l.packages.any { it.equals(pkg, true) } }
    } catch (_: Exception) {
        null
    }

    /** First supported launcher that's installed — Projectivy wins ties. */
    fun detectInstalled(context: Context): Launcher? =
        detectDefault(context) ?: ALL.firstOrNull { context.isInstalledAny(it) }

    /**
     * Fire the launcher's apply intent.
     *
     * Verifies the intent actually resolves before starting it, so a launcher
     * that dropped support gives us [Result.Manual] with the real menu path
     * instead of an ActivityNotFoundException the user can't act on.
     */
    fun apply(context: Context, launcher: Launcher): Result {
        if (!context.isInstalledAny(launcher)) {
            return Result.NotInstalled(launcher.displayName)
        }

        val intent = launcher.intent(context, context.packageName)
            ?: return Result.Manual(launcher.displayName, launcher.manualPath)

        val resolves = context.packageManager
            .queryIntentActivities(intent, 0).isNotEmpty()
        if (!resolves) {
            return Result.Manual(launcher.displayName, launcher.manualPath)
        }

        return try {
            context.startActivity(intent)
            Result.Applied(launcher.displayName)
        } catch (_: Exception) {
            Result.Manual(launcher.displayName, launcher.manualPath)
        }
    }

    /** Open a launcher's own settings, for the manual path. */
    fun openLauncher(context: Context, launcher: Launcher): Boolean {
        val pkg = launcher.packages.firstOrNull { context.isInstalled(it) } ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Unused today; kept because some launchers expect a broadcast, not an activity. */
    @Suppress("unused")
    fun componentOf(pkg: String, cls: String) = ComponentName(pkg, cls)
}
