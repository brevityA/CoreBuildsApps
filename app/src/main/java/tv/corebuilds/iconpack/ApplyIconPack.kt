package tv.corebuilds.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Direct-apply support.
 *
 * Icon packs don't apply themselves — the launcher does. Each launcher
 * exposes its own intent, or none at all. We detect the HOME launcher,
 * list every known launcher that's installed, and either fire a real
 * apply contract or open the launcher with a named settings path.
 *
 * Brand Guide §05/§08: the button names the target before it is pressed.
 */
object ApplyIconPack {

    sealed class Result {
        data class Applied(val launcherName: String) : Result()
        data class NotInstalled(val launcherName: String) : Result()
        data class Manual(val launcherName: String, val instructions: String) : Result()
    }

    data class Launcher(
        val key: String,
        val displayName: String,
        val packages: List<String>,
        val intent: (Context, String) -> Intent?,
        val manualPath: String
    )

    private fun applyIntent(
        action: String,
        pkg: String,
        extra: Pair<String, String>? = null,
        extras: List<Pair<String, String>> = emptyList()
    ): Intent = Intent(action).apply {
        `package` = pkg
        extra?.let { putExtra(it.first, it.second) }
        extras.forEach { putExtra(it.first, it.second) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Try the Blueprint / ADW / Nova / GO contracts against [pkg].
     * Used when a TV launcher claims icon-pack support but has no
     * documented apply extra of its own.
     */
    private fun tryStandardApply(ctx: Context, pkg: String, self: String): Intent? {
        val candidates = listOf(
            applyIntent(
                "com.novalauncher.THEME", pkg,
                extras = listOf("com.novalauncher.extra.ICON_THEME_PACKAGE" to self)
            ),
            applyIntent(
                "org.adw.launcher.THEMES", pkg,
                extra = "org.adw.launcher.theme.NAME" to self
            ),
            applyIntent(
                "com.gau.go.launcherex.theme", pkg
            ),
            applyIntent(
                "com.anddoes.launcher.THEME", pkg,
                extra = "com.anddoes.launcher.THEME_PACKAGE_NAME" to self
            ),
            applyIntent(
                "$pkg.APPLY_ICONPACK", pkg,
                extra = "$pkg.extra.ICONPACK_PACKAGENAME" to self
            )
        )
        return candidates.firstOrNull { intent ->
            ctx.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        }
    }

    val PROJECTIVY = Launcher(
        key = "projectivy",
        displayName = "Projectivy Launcher",
        packages = listOf("com.spocky.projengmenu"),
        intent = { _, self ->
            applyIntent(
                "com.spocky.projengmenu.APPLY_ICONPACK",
                "com.spocky.projengmenu",
                extra = "com.spocky.projengmenu.extra.ICONPACK_PACKAGENAME" to self
            )
        },
        manualPath = "Projectivy Settings → Appearance → Cards → Icon Pack → Core Builds"
    )

    val MONET = Launcher(
        key = "monet",
        displayName = "Monet Launcher",
        packages = listOf("com.klevico.monet"),
        // Monet 1.0.76 (decompiled 2026-08-19) has no incoming apply extra.
        // It lists packs via Nova/ADW/GO/Lawnchair/Fede discovery actions,
        // then setIconPackPackage() from its own settings. tryStandardApply
        // is a best-effort; expect Manual on current builds.
        intent = { ctx, self ->
            tryStandardApply(ctx, "com.klevico.monet", self)
        },
        manualPath = "Monet Settings → Icons → Icon pack → Core Builds Icon Pack"
    )

    val AT4K = Launcher(
        key = "at4k",
        displayName = "AT4K Launcher",
        packages = listOf("com.overdevs.at4k"),
        intent = { ctx, self -> tryStandardApply(ctx, "com.overdevs.at4k", self) },
        manualPath = "Open AT4K → Settings → Icon pack → Core Builds Icon Pack"
    )

    val LEANBACK = Launcher(
        key = "leanback",
        displayName = "Leanback on Fire",
        packages = listOf("com.amazon.tv.leanbacklauncher"),
        intent = { ctx, self ->
            tryStandardApply(ctx, "com.amazon.tv.leanbacklauncher", self)
        },
        manualPath = "Leanback on Fire has no icon-pack apply. Open it and assign icons per app."
    )

    val LTV = Launcher(
        key = "ltv",
        displayName = "L TV Launcher",
        packages = listOf("com.leanbitlab.ltvL"),
        intent = { ctx, self -> tryStandardApply(ctx, "com.leanbitlab.ltvL", self) },
        manualPath = "L TV Launcher Settings → Icon pack"
    )

    val FLAUNCHER = Launcher(
        key = "flauncher",
        displayName = "FLauncher",
        packages = listOf("me.efesser.flauncher", "com.kfaraj.launcher"),
        intent = { ctx, self ->
            val pkg = listOf("me.efesser.flauncher", "com.kfaraj.launcher")
                .firstOrNull { ctx.isInstalled(it) } ?: return@Launcher null
            tryStandardApply(ctx, pkg, self)
        },
        manualPath = "FLauncher Settings → Appearance → Icon pack"
    )

    val CHILLHUB = Launcher(
        key = "chillhub",
        displayName = "ChillHub",
        packages = listOf("app.lumoslabs.chillhub"),
        intent = { ctx, self -> tryStandardApply(ctx, "app.lumoslabs.chillhub", self) },
        manualPath = "ChillHub Settings → Icon pack"
    )

    val NOVA = Launcher(
        key = "nova",
        displayName = "Nova Launcher",
        packages = listOf("com.teslacoilsw.launcher"),
        intent = { _, self ->
            applyIntent(
                "com.teslacoilsw.launcher.APPLY_ICON_THEME",
                "com.teslacoilsw.launcher",
                extras = listOf(
                    "com.teslacoilsw.launcher.extra.ICON_THEME_TYPE" to "GO",
                    "com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE" to self
                )
            )
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
                applyIntent("ch.deletescape.lawnchair.APPLY_ICONS", p, extra = "packageName" to self)
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
            val prefix = if (ctx.isInstalled("org.adwfreak.launcher"))
                "org.adwfreak.launcher" else "org.adw.launcher"
            Intent("$prefix.SET_THEME").apply {
                putExtra("$prefix.theme.NAME", self)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        },
        manualPath = "ADW Settings → Themes"
    )

    /** Known launchers. HOME detection walks this list. Projectivy first. */
    val ALL = listOf(
        PROJECTIVY, MONET, AT4K, LEANBACK, LTV, FLAUNCHER, CHILLHUB,
        NOVA, LAWNCHAIR, APEX, ADW
    )

    fun Context.isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun Context.isInstalledAny(l: Launcher) = l.packages.any { isInstalled(it) }

    /** Package of the current HOME launcher, if any. */
    /** Package of the current HOME launcher, checking both standard and Leanback categories. */
    fun homePackage(context: Context): String? = try {
        val pm = context.packageManager
        // Leanback launchers often respond to CATEGORY_LEANBACK_LAUNCHER rather than CATEGORY_HOME.
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val leanbackPkg = pm.resolveActivity(leanback, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

        if (leanbackPkg != null && leanbackPkg != "android") {
            return leanbackPkg
        }

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    } catch (_: Exception) {
        null
    }

    fun detectDefault(context: Context): Launcher? {
        val pkg = homePackage(context) ?: return null
        return ALL.firstOrNull { l -> l.packages.any { it.equals(pkg, true) } }
    }

    /** HOME launcher if we know it, otherwise the first installed we support. */
    fun detectInstalled(context: Context): Launcher? =
        detectDefault(context) ?: ALL.firstOrNull { context.isInstalledAny(it) }

    /** Every known launcher that is actually installed, HOME first. */
    fun installed(context: Context): List<Launcher> {
        val home = detectDefault(context)
        val rest = ALL.filter { context.isInstalledAny(it) && it != home }
        return listOfNotNull(home) + rest
    }

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

    @Suppress("unused")
    fun componentOf(pkg: String, cls: String) = ComponentName(pkg, cls)
}
