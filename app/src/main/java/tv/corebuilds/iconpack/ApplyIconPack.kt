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

        /**
         * A launcher that documents no incoming apply action, so nothing can be
         * pressed on the user's behalf and [instructions] would not fit on a
         * toast either: [steps] is the walk through the launcher's own settings,
         * in order, ending on the one pack to pick. [listed] is false when that
         * pack did not answer the launcher's own discovery actions - the one
         * case where it may be missing from the picker entirely.
         */
        data class Handoff(
            val launcherName: String,
            val steps: List<String>,
            val packLabel: String,
            val listed: Boolean
        ) : Result()

        /** Glyphs is selected but Core Builds Glyphs is missing or stale. */
        object NeedsCompanion : Result()
    }

    data class Launcher(
        val key: String,
        val displayName: String,
        val packages: List<String>,
        val intent: (Context, String) -> Intent?,
        val manualPath: String,
        /**
         * False when the launcher documents no incoming apply action, so
         * [intent]'s best-effort probe can only ever miss (Monet). The CTA then
         * leads to the setup screen instead of firing a probe with no chance of
         * resolving: a press that reports "sent" and changes nothing is worse
         * than one that shows the walk.
         */
        val inboundApply: Boolean = true,
        /**
         * The screens between the launcher's settings and its pack list,
         * outermost first, and *not* including the pack - [handoff] appends the
         * pick as the final step so exactly one pack is ever named. Empty for
         * every launcher that never gets a handoff.
         */
        val setupStops: List<String> = emptyList()
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
        // Monet 1.0.84 (decompiled 2026-09-12; re-confirms 1.0.76, still true
        // of 1.0.90's release notes) has no incoming apply extra. Its only
        // exported activities are HomeActivity and WallpaperShareActivity; the
        // settings activity is exported=false, so there is no deep link, and
        // tryStandardApply can only miss. Monet discovers packs through the
        // Nova/ADW/Apex/GO/Fede/Lawnchair/OnePlus/Tesla discovery actions (all
        // declared in our manifest) and reads `xml/appfilter`, then the user
        // picks the pack in Monet's own settings - so the honest route is the
        // handoff, not a probe dressed up as an apply. The lambda is kept, and
        // inboundApply flips to true the day a Monet build accepts one.
        // Icon packs are a Monet Premium feature.
        intent = { ctx, self ->
            tryStandardApply(ctx, "com.klevico.monet", self)
        },
        // 1.0.80 reorganised Settings: the "Icons" category became "Apps".
        manualPath = "Monet Settings → Apps → Icon pack → Core Builds Icon Pack",
        inboundApply = false,
        setupStops = listOf("Monet Settings", "Apps", "Icon pack")
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

    // FLauncher is intentionally not listed as an applicable launcher. Its
    // upstream project states that icon-pack support is not planned, so
    // advertising an apply/manual flow here would be misleading and would
    // make the primary action appear to succeed when nothing can change.

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
        PROJECTIVY, MONET, AT4K, LEANBACK, LTV, CHILLHUB,
        NOVA, LAWNCHAIR, APEX, ADW
    )

    fun Context.isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun Context.isInstalledAny(l: Launcher) = l.packages.any { isInstalled(it) }

    /** Package of the current HOME launcher. */
    fun homePackage(context: Context): String? = try {
        // CATEGORY_LEANBACK_LAUNCHER identifies TV apps, not the current HOME
        // activity. Resolving it here can return this icon-pack activity (it
        // also declares LEANBACK_LAUNCHER), causing a false launcher match.
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
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

    /**
     * The actions a launcher resolves to enumerate the icon packs installed on
     * the device. Monet builds its list from exactly this set (see
     * docs/MONET_LAUNCHER.md), so a pack that answers none of them will not
     * appear in its picker however carefully it is named.
     */
    private val DISCOVERY_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.fede.launcher.THEME_ICONPACK",
        "ch.deletescape.lawnchair.ICONPACK",
        "net.oneplus.launcher.icons.ACTION_PICK_ICON",
        "com.teslacoilsw.launcher.THEME"
    )

    /**
     * Is [pkg] one of the packs a launcher can find?
     *
     * Read-only and advisory by design: the answer is *reported* on the setup
     * screen and never acted on. Naming a pack that is not in the picker is the
     * dead end this route exists to remove, and a pack that is installed but not
     * answering is a far rarer case than a pack that was never installed.
     */
    fun listedAmongDiscoverers(context: Context, pkg: String): Boolean =
        DISCOVERY_ACTIONS.any { action ->
            context.packageManager.queryIntentActivities(Intent(action), 0)
                .any { it.activityInfo?.packageName == pkg }
        }

    /**
     * The setup route for a launcher with no inbound apply: the walk through its
     * own settings, ending once on the pack to pick.
     *
     * This replaces a sentence assembled by appending a companion clause to
     * [Launcher.manualPath] - a path that already ended in a pack name - so the
     * reader was told to pick two different packs, and to pick the companion
     * that 1.9.5 retired whenever Glyphs was selected. The steps here end in the
     * one pack [GlyphsCompanion.applyTarget] would have handed a launcher that
     * could take it.
     */
    fun handoff(context: Context, launcher: Launcher): Result.Handoff {
        val pack = GlyphsCompanion.applyTarget(context)
        val packLabel = if (pack == GlyphsCompanion.PACKAGE) {
            GlyphsCompanion.LABEL
        } else {
            context.getString(R.string.app_name)
        }
        val steps = launcher.setupStops.mapIndexed { index, stop ->
            context.getString(R.string.setup_step_open_fmt, index + 1, stop)
        } + context.getString(
            R.string.setup_step_pick_fmt, launcher.setupStops.size + 1, packLabel
        )
        return Result.Handoff(
            launcherName = launcher.displayName,
            steps = steps,
            packLabel = packLabel,
            listed = listedAmongDiscoverers(context, pack)
        )
    }

    /**
     * Point [launcher] at the pack the Banners/Glyphs setting selects.
     *
     * The apply contracts all take a package name, and that is the whole
     * mechanism behind the toggle: banners name this package, glyphs name
     * [GlyphsCompanion.PACKAGE]. A launcher with no apply contract gets the
     * same choice as a manual path that names the pack to pick.
     */
    fun apply(context: Context, launcher: Launcher): Result {
        if (!context.isInstalledAny(launcher)) {
            return Result.NotInstalled(launcher.displayName)
        }
        if (GlyphsCompanion.wanted(context) && !GlyphsCompanion.ready(context)) {
            return Result.NeedsCompanion
        }
        // No inbound apply: the walk is the answer, and it names the same pack
        // the contract below would have handed over. Checked after the
        // companion so that picking Glyphs installs the pack first - the setup
        // screen then shows the user a pack that is actually on the device.
        if (!launcher.inboundApply) {
            return handoff(context, launcher)
        }
        val pack = GlyphsCompanion.applyTarget(context)
        val manual = if (pack == context.packageName) {
            launcher.manualPath
        } else {
            context.getString(R.string.apply_manual_companion_fmt,
                              launcher.manualPath, GlyphsCompanion.LABEL)
        }

        val intent = launcher.intent(context, pack)
            ?: return Result.Manual(launcher.displayName, manual)

        val resolves = context.packageManager
            .queryIntentActivities(intent, 0).isNotEmpty()
        if (!resolves) {
            return Result.Manual(launcher.displayName, manual)
        }

        return try {
            context.startActivity(intent)
            Result.Applied(launcher.displayName)
        } catch (_: Exception) {
            Result.Manual(launcher.displayName, manual)
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
