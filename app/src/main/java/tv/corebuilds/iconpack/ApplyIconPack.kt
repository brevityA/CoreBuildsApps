package tv.corebuilds.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Direct-apply support.
 *
 * Icon packs don't apply themselves — the launcher does. Each launcher
 * exposes its own intent, or none at all. We detect the HOME launcher,
 * list every known launcher that's installed, and either fire a real
 * apply contract or open the launcher with a named settings path.
 *
 * Brand Guide §05/§08: the button names the target before it is pressed.
 *
 * Robustness strategy (v1.5.2):
 * - Each launcher now exposes a *list* of candidate intents (not one).
 *   tryStandard candidates are tried in order until one both resolves
 *   and starts without throwing. A single bad contract no longer aborts.
 * - HOME detection tolerates a null resolveActivity (common on Fire TV /
 *   when no default is set) by falling back to queryIntentActivities(HOME).
 * - An unknown HOME package (not in ALL) is still offered as a target
 *   via a synthetic launcher that probes the five standard contracts.
 *   This makes “auto apply” work for the next TV launcher without an
 *   app update — it will at worst fall back to Manual + openLauncher.
 * - Manifest <queries> now declares generic apply actions as <intent>s,
 *   so an unknown launcher that declares e.g. com.novalauncher.THEME is
 *   visible on Android 11+ without a <package> entry.
 */
object ApplyIconPack {

    private const val TAG = "CoreBuildsApply"

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
        val manualPath: String,
        /** Full ordered list of intents to try for this launcher. */
        val candidates: (Context, String) -> List<Intent> = { ctx, self ->
            listOfNotNull(intent(ctx, self))
        }
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

    /** Ordered generic candidates probed when a launcher has no bespoke contract. */
    private fun standardCandidates(pkg: String, self: String): List<Intent> = listOf(
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
        ),
        // Some launchers (older Lawnchair forks) use the ICONPACK suffix.
        applyIntent(
            "ch.deletescape.lawnchair.ICONPACK", pkg,
            extra = "packageName" to self
        )
    )

    /**
     * Try the Blueprint / ADW / Nova / GO contracts against [pkg].
     * Used when a TV launcher claims icon-pack support but has no
     * documented apply extra of its own.
     */
    private fun tryStandardApply(ctx: Context, pkg: String, self: String): Intent? {
        return standardCandidates(pkg, self).firstOrNull { intent ->
            ctx.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        }
    }

    private fun candidatesForStandard(ctx: Context, pkg: String, self: String): List<Intent> =
        standardCandidates(pkg, self)

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
        manualPath = "Projectivy Settings → Appearance → Cards → Icon Pack → Core Builds",
        candidates = { _, self ->
            listOf(
                applyIntent(
                    "com.spocky.projengmenu.APPLY_ICONPACK",
                    "com.spocky.projengmenu",
                    extra = "com.spocky.projengmenu.extra.ICONPACK_PACKAGENAME" to self
                ),
                // Fallback: some Projectivy forks also accept the generic ADW/Nova contracts.
                // If Spocky's direct intent is missing (very old build), try generics so the
                // user still gets a handoff instead of a dead Manual.
                *standardCandidates("com.spocky.projengmenu", self).toTypedArray()
            )
        }
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
        manualPath = "Monet Settings → Icons → Icon pack → Core Builds Icon Pack",
        candidates = { ctx, self -> candidatesForStandard(ctx, "com.klevico.monet", self) }
    )

    val AT4K = Launcher(
        key = "at4k",
        displayName = "AT4K Launcher",
        packages = listOf("com.overdevs.at4k"),
        intent = { ctx, self -> tryStandardApply(ctx, "com.overdevs.at4k", self) },
        manualPath = "Open AT4K → Settings → Icon pack → Core Builds Icon Pack",
        candidates = { ctx, self -> candidatesForStandard(ctx, "com.overdevs.at4k", self) }
    )

    val LEANBACK = Launcher(
        key = "leanback",
        displayName = "Leanback on Fire",
        packages = listOf("com.amazon.tv.leanbacklauncher"),
        intent = { ctx, self ->
            tryStandardApply(ctx, "com.amazon.tv.leanbacklauncher", self)
        },
        manualPath = "Leanback on Fire has no icon-pack apply. Open it and assign icons per app.",
        candidates = { ctx, self -> candidatesForStandard(ctx, "com.amazon.tv.leanbacklauncher", self) }
    )

    val LTV = Launcher(
        key = "ltv",
        displayName = "L TV Launcher",
        packages = listOf("com.leanbitlab.ltvL"),
        intent = { ctx, self -> tryStandardApply(ctx, "com.leanbitlab.ltvL", self) },
        manualPath = "L TV Launcher Settings → Icon pack",
        candidates = { ctx, self -> candidatesForStandard(ctx, "com.leanbitlab.ltvL", self) }
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
        manualPath = "FLauncher Settings → Appearance → Icon pack",
        candidates = { ctx, self ->
            val pkg = listOf("me.efesser.flauncher", "com.kfaraj.launcher")
                .firstOrNull { ctx.isInstalled(it) } ?: return@Launcher emptyList()
            candidatesForStandard(ctx, pkg, self)
        }
    )

    val CHILLHUB = Launcher(
        key = "chillhub",
        displayName = "ChillHub",
        packages = listOf("app.lumoslabs.chillhub"),
        intent = { ctx, self -> tryStandardApply(ctx, "app.lumoslabs.chillhub", self) },
        manualPath = "ChillHub Settings → Icon pack",
        candidates = { ctx, self -> candidatesForStandard(ctx, "app.lumoslabs.chillhub", self) }
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
        manualPath = "Nova Settings → Look & feel → Icon style → Icon theme",
        candidates = { _, self ->
            listOf(
                applyIntent(
                    "com.teslacoilsw.launcher.APPLY_ICON_THEME",
                    "com.teslacoilsw.launcher",
                    extras = listOf(
                        "com.teslacoilsw.launcher.extra.ICON_THEME_TYPE" to "GO",
                        "com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE" to self
                    )
                ),
                *standardCandidates("com.teslacoilsw.launcher", self).toTypedArray()
            )
        }
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
        manualPath = "Lawnchair Settings → General → Icon style → Icon pack",
        candidates = { ctx, self ->
            val pkg = LAWNCHAIR_PACKAGES.firstOrNull { ctx.isInstalled(it) } ?: return@Launcher emptyList()
            listOf(
                applyIntent("ch.deletescape.lawnchair.APPLY_ICONS", pkg, extra = "packageName" to self),
                applyIntent("ch.deletescape.lawnchair.ICONPACK", pkg, extra = "packageName" to self),
                *standardCandidates(pkg, self).toTypedArray()
            )
        }
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
        manualPath = "Apex Settings → Theme settings",
        candidates = { _, self ->
            listOf(
                Intent("com.anddoes.launcher.SET_THEME").apply {
                    putExtra("com.anddoes.launcher.THEME_PACKAGE_NAME", self)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                Intent("com.anddoes.launcher.THEME").apply {
                    putExtra("com.anddoes.launcher.THEME_PACKAGE_NAME", self)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                *standardCandidates("com.anddoes.launcher", self).toTypedArray()
            )
        }
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
        manualPath = "ADW Settings → Themes",
        candidates = { ctx, self ->
            val isFreak = ctx.isInstalled("org.adwfreak.launcher")
            val prefixes = if (isFreak) listOf("org.adwfreak.launcher", "org.adw.launcher")
            else listOf("org.adw.launcher", "org.adwfreak.launcher")
            val themed = prefixes.map { p ->
                Intent("$p.SET_THEME").apply {
                    putExtra("$p.theme.NAME", self)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            themed + prefixes.flatMap { p -> standardCandidates(p, self) } +
                listOf(
                    applyIntent("org.adw.launcher.THEMES", "org.adw.launcher", extra = "org.adw.launcher.theme.NAME" to self),
                    applyIntent("org.adw.launcher.THEMES", "org.adwfreak.launcher", extra = "org.adwfreak.launcher.theme.NAME" to self)
                )
        }
    )

    /** Known launchers. HOME detection walks this list. Projectivy first. */
    val ALL = listOf(
        PROJECTIVY, MONET, AT4K, LEANBACK, LTV, FLAUNCHER, CHILLHUB,
        NOVA, LAWNCHAIR, APEX, ADW
    )

    fun Context.isInstalled(pkg: String): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(pkg, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }

    private fun Context.isInstalledAny(l: Launcher) = l.packages.any { isInstalled(it) }

    private fun Context.labelFor(pkg: String): String = try {
        val ai = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(pkg, 0)
        }
        packageManager.getApplicationLabel(ai).toString().ifBlank { pkg }
    } catch (_: Exception) {
        pkg
    }

    private fun syntheticLauncher(pkg: String, ctx: Context): Launcher {
        val label = try { ctx.labelFor(pkg) } catch (_: Exception) { pkg }
        return Launcher(
            key = "generic_$pkg",
            displayName = label,
            packages = listOf(pkg),
            intent = { c, self -> tryStandardApply(c, pkg, self) },
            manualPath = "Open $label → Settings → Icon pack → Core Builds Icon Pack",
            candidates = { _, self -> standardCandidates(pkg, self) }
        )
    }

    /** Package of the current HOME launcher, if any. */
    fun homePackage(context: Context): String? = try {
        val pm = context.packageManager
        // Prefer the resolved default.
        val resolved = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).let { home ->
            pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }
        if (resolved != null) return resolved

        // Fallback: some devices (Fire TV, no default set) return null for
        // resolveActivity but do list HOME handlers via queryIntentActivities.
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo?.packageName != null }
            ?.activityInfo?.packageName
            ?.also { Log.i(TAG, "homePackage via query fallback: $it") }
    } catch (e: Exception) {
        Log.w(TAG, "homePackage failed: ${e.message}")
        null
    }

    fun detectDefault(context: Context): Launcher? {
        val pkg = homePackage(context) ?: return null
        ALL.firstOrNull { l -> l.packages.any { it.equals(pkg, true) } }?.let { return it }
        // Unknown HOME — still offer it generically if the package is installed.
        return if (context.isInstalled(pkg)) {
            Log.i(TAG, "unknown HOME $pkg — offering generic candidates")
            syntheticLauncher(pkg, context)
        } else null
    }

    /** HOME launcher if we know it, otherwise the first installed we support. */
    fun detectInstalled(context: Context): Launcher? =
        detectDefault(context) ?: ALL.firstOrNull { context.isInstalledAny(it) }

    /** Every known launcher that is actually installed, HOME first. */
    fun installed(context: Context): List<Launcher> {
        val home = detectDefault(context)
        val rest = ALL.filter { context.isInstalledAny(it) && it != home }
        // If home is a synthetic unknown launcher, it won't be in ALL, so keep it.
        return if (home != null && home !in ALL) listOf(home) + rest
        else listOfNotNull(home) + rest
    }

    /** Try every candidate for [launcher] until one resolves and starts. */
    fun apply(context: Context, launcher: Launcher): Result {
        if (!context.isInstalledAny(launcher)) {
            // Synthetic generic launcher has a single package check.
            val anyInstalled = launcher.packages.any { context.isInstalled(it) }
            if (!anyInstalled) return Result.NotInstalled(launcher.displayName)
        }

        val self = context.packageName
        val attempts = try {
            launcher.candidates(context, self).ifEmpty {
                listOfNotNull(launcher.intent(context, self))
            }
        } catch (e: Exception) {
            Log.w(TAG, "candidates() threw for ${launcher.key}: ${e.message}")
            listOfNotNull(launcher.intent(context, self))
        }

        if (attempts.isEmpty()) {
            Log.w(TAG, "no candidates for ${launcher.displayName}")
            return Result.Manual(launcher.displayName, launcher.manualPath)
        }

        var lastReason = "no candidate resolved"
        for (intent in attempts) {
            val action = intent.action ?: "(no action)"
            val pkg = intent.`package` ?: launcher.packages.firstOrNull() ?: "?"
            val resolves = try {
                context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
            } catch (e: Exception) {
                Log.w(TAG, "query failed for $action/$pkg: ${e.message}")
                false
            }
            if (!resolves) {
                lastReason = "$action did not resolve for $pkg"
                Log.i(TAG, "skip $action → $pkg: $lastReason")
                continue
            }
            return try {
                context.startActivity(intent)
                Log.i(TAG, "applied via $action → $pkg")
                Result.Applied(launcher.displayName)
            } catch (e: SecurityException) {
                lastReason = e.message ?: "SecurityException"
                Log.w(TAG, "apply SecurityException $action → $pkg: $lastReason")
            } catch (e: Exception) {
                lastReason = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "apply failed $action → $pkg: $lastReason")
            }
        }

        Log.w(TAG, "all ${attempts.size} candidates failed for ${launcher.displayName}: $lastReason")
        return Result.Manual(launcher.displayName, launcher.manualPath)
    }

    /**
     * Best-effort: try [preferred] first, then every other installed launcher.
     * Returns Applied if any launcher accepted the handoff.
     */
    fun applyBestEffort(context: Context, preferred: Launcher? = null): Result {
        val pref = preferred ?: detectInstalled(context)
        if (pref != null) {
            val r = apply(context, pref)
            if (r is Result.Applied) return r
            // If preferred required Manual, still try siblings before giving up.
            if (r is Result.NotInstalled) { /* fall through */ }
        }
        for (other in installed(context)) {
            if (other == pref) continue
            val r = apply(context, other)
            if (r is Result.Applied) return r
        }
        return pref?.let { Result.Manual(it.displayName, it.manualPath) }
            ?: Result.Manual("launcher", "Open your launcher's Settings → Icon pack")
    }

    fun openLauncher(context: Context, launcher: Launcher): Boolean {
        val pkg = launcher.packages.firstOrNull { context.isInstalled(it) } ?: return false

        // 1. The normal launch intent.
        context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                Log.w(TAG, "getLaunchIntentForPackage $pkg failed: ${e.message}")
            }
        }

        // 2. Explicit HOME intent targeted at that package (works for some TV launchers).
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).apply {
                `package` = pkg
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) {
                context.startActivity(home)
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "HOME fallback for $pkg failed: ${e.message}")
        }

        // 3. Generic MAIN/LAUNCHER for that package.
        try {
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).apply {
                `package` = pkg
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.queryIntentActivities(main, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) {
                context.startActivity(main)
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "LAUNCHER fallback for $pkg failed: ${e.message}")
        }

        return false
    }

    @Suppress("unused")
    fun componentOf(pkg: String, cls: String) = ComponentName(pkg, cls)
}
