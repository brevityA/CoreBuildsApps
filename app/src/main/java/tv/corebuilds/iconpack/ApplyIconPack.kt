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

    /**
     * What a launcher applies from this pack. BANNERS: the 16:9 banners the
     * appfilter maps - the pack's native art on a TV card. GLYPHS: the square
     * mark, which a phone launcher takes from the Core Builds Glyphs
     * companion, because a phone launcher has no banner surface. NONE: the
     * launcher applies no pack art at all.
     */
    enum class Art { BANNERS, GLYPHS, NONE }

    /**
     * How the pack reaches a launcher. AUTO: a press from this app fires the
     * launcher's own apply contract. WALK: the launcher has no inbound apply,
     * so the user picks the pack in the launcher's own settings - the handoff
     * screen names the stops. PER_APP: there is no pack apply at all; icons
     * are assigned one app at a time. NONE: the launcher does not apply icon
     * packs, and nothing here should claim otherwise.
     */
    enum class ApplyMode { AUTO, WALK, PER_APP, NONE }

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
        val setupStops: List<String> = emptyList(),
        /**
         * What this launcher shows when it applies the pack. TV launchers take
         * the 16:9 banners; phone launchers take the square glyphs from the
         * companion. A launcher with no pack support applies nothing.
         */
        val art: Art = Art.BANNERS,
        /**
         * How the press routes: [ApplyMode.AUTO] fires [intent],
         * [ApplyMode.WALK] leads to the setup walk ([setupStops]),
         * [ApplyMode.PER_APP] says so plainly, [ApplyMode.NONE] means no apply
         * at all. WALK pairs with [inboundApply] false: the walk exists because
         * the probe would only miss.
         */
        val applyMode: ApplyMode = ApplyMode.AUTO,
        /**
         * The launcher-specific facts that decide whether an applied icon
         * appears - premium gates, renamed screens, launcher-internal cards.
         * Shown on the compatibility screen and kept short enough to read on
         * a 540p panel; the deep versions live in docs/research.
         */
        val notes: List<String> = emptyList()
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
        manualPath = "Projectivy Settings → Appearance → Cards → Icon Pack → Core Builds",
        setupStops = listOf("Projectivy Settings", "Appearance", "Cards", "Icon Pack"),
        notes = listOf(
            // docs/research/community-input-icons-2026-09.md §4.5: the dev has
            // said the input-card namespace may change again, and the standing
            // practice is a rescan after each major Projectivy version.
            "Projectivy's HDMI and AV input cards are launcher-internal; if they revert to stock after a Projectivy update, re-apply the pack."
        )
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
        setupStops = listOf("Monet Settings", "Apps", "Icon pack"),
        applyMode = ApplyMode.WALK,
        notes = listOf(
            // Play listing, re-confirmed in docs/research/launcher-style-audit-2026-09.md:
            // icon-pack support is a Monet Premium feature, and the 7 icon
            // shapes include circle.
            "Icon packs are a Monet Premium feature.",
            "Monet offers seven icon shapes, including circle."
        )
    )

    val AT4K = Launcher(
        key = "at4k",
        displayName = "AT4K Launcher",
        packages = listOf("com.overdevs.at4k"),
        // The 2026-09 audit found no third-party pack consumption; by 2026-10
        // AT4K lists installed packs under Settings → Themes → Icon packs but
        // documents no incoming apply action — the developer routes manual
        // cases there (r/AT4K, 2026-10-01), and the standard probes miss, so
        // the honest contract is the walk, as with Monet. The 1.9.x toast
        // said "Settings → Icon pack", a screen AT4K does not have; users
        // followed it into a dead end (r/AT4K "no icon pack under
        // settings!?"). The lambda is kept, and inboundApply flips to true
        // the day an AT4K build accepts an apply intent.
        intent = { ctx, self -> tryStandardApply(ctx, "com.overdevs.at4k", self) },
        manualPath = "AT4K Settings → Themes → Icon packs → Core Builds Icon Pack",
        inboundApply = false,
        setupStops = listOf("AT4K Settings", "Themes", "Icon packs"),
        applyMode = ApplyMode.WALK,
        notes = listOf(
            // v1.2 bundles its own free transparent-icon theme
            // (docs/research/launcher-style-audit-2026-09.md): a TV on the
            // bundled set looks applied, so the pack has to be picked.
            "AT4K ships a built-in transparent icon theme; the pack applies only when picked under Settings → Themes → Icon packs."
        )
    )

    val LEANBACK = Launcher(
        key = "leanback",
        displayName = "Leanback on Fire",
        packages = listOf("com.amazon.tv.leanbacklauncher"),
        intent = { ctx, self ->
            tryStandardApply(ctx, "com.amazon.tv.leanbacklauncher", self)
        },
        manualPath = "Leanback on Fire has no icon-pack apply. Open it and assign icons per app.",
        applyMode = ApplyMode.PER_APP,
        notes = listOf(
            // The not-applying form's own preamble: Fire TV ships different
            // launcher activities by region and carrier, so a stale mapping
            // there is a component question, not a pack question.
            "No icon-pack apply: icons are assigned per app. Fire TV builds vary by region and carrier, so the report form asks for the exact launcher activity."
        )
    )

    val LTV = Launcher(
        key = "ltv",
        displayName = "L TV Launcher",
        packages = listOf("com.leanbitlab.ltvL"),
        intent = { ctx, self -> tryStandardApply(ctx, "com.leanbitlab.ltvL", self) },
        manualPath = "L TV Launcher Settings → Icon pack",
        setupStops = listOf("L TV Launcher Settings", "Icon pack")
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
        manualPath = "ChillHub Settings → Icon pack",
        setupStops = listOf("ChillHub Settings", "Icon pack")
    )

    /**
     * What a phone launcher applies: the square glyph art, never the 16:9
     * banners - a phone tile has no banner surface. This is the whole reason
     * the Banners/Glyphs toggle exists, and the answer when a TV user who
     * installs a phone launcher sees stock icons: apply Core Builds Glyphs
     * there, not this pack.
     */
    private val PHONE_LAUNCHER_NOTE =
        "Phone launcher: applies the square glyph art, not the 16:9 banners. Use Core Builds Glyphs for this launcher."

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
        setupStops = listOf("Nova Settings", "Look & feel", "Icon style", "Icon theme"),
        art = Art.GLYPHS,
        notes = listOf(PHONE_LAUNCHER_NOTE)
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
        setupStops = listOf("Lawnchair Settings", "General", "Icon style", "Icon pack"),
        art = Art.GLYPHS,
        notes = listOf(PHONE_LAUNCHER_NOTE)
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
        setupStops = listOf("Apex Settings", "Theme settings"),
        art = Art.GLYPHS,
        notes = listOf(PHONE_LAUNCHER_NOTE)
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
        setupStops = listOf("ADW Settings", "Themes"),
        art = Art.GLYPHS,
        notes = listOf(PHONE_LAUNCHER_NOTE)
    )

    /** Known launchers. HOME detection walks this list. Projectivy first. */
    val ALL = listOf(
        PROJECTIVY, MONET, AT4K, LEANBACK, LTV, CHILLHUB,
        NOVA, LAWNCHAIR, APEX, ADW
    )

    /**
     * Known, and intentionally inapplicable: a [Launcher] here documents that
     * the pack does NOT apply to it, so the compatibility screen can say so
     * instead of a user on it being pointed at an apply that cannot land.
     * FLauncher's upstream project states icon-pack support is not planned
     * (the 2026-09-24 changelog entry that removed its apply support keeps
     * this exclusion deliberate).
     */
    val FLAUNCHER = Launcher(
        key = "flauncher",
        displayName = "FLauncher",
        packages = listOf("me.efesser.flauncher"),
        intent = { _, _ -> null },
        manualPath = "FLauncher does not apply icon packs.",
        art = Art.NONE,
        applyMode = ApplyMode.NONE,
        notes = listOf(
            "FLauncher's upstream project states that icon-pack support is not planned."
        )
    )

    val NOT_SUPPORTED = listOf(FLAUNCHER)

    /**
     * The compatibility screen's list: every launcher the pack can reach,
     * then the ones it deliberately cannot. The screen marks which of them
     * is this TV's HOME and which are installed.
     */
    val COMPATIBILITY = ALL + NOT_SUPPORTED

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
     * Is [launcher] on this device, by any of its packages? [installed] is the
     * ordered variant over [ALL]; the compatibility screen needs the raw
     * answer for [NOT_SUPPORTED] entries too, which [installed] never yields.
     */
    fun isInstalled(context: Context, launcher: Launcher): Boolean =
        context.isInstalledAny(launcher)

    /**
     * The [art] sentence for the user: what a launcher applies from the pack.
     * Shared by the compatibility screen and the auditor's launcher line so
     * the two screens can never phrase the same fact two ways.
     */
    fun artText(context: Context, art: Art): String = when (art) {
        Art.BANNERS -> context.getString(R.string.launcher_compat_art_banners)
        Art.GLYPHS -> context.getString(R.string.launcher_compat_art_glyphs)
        Art.NONE -> context.getString(R.string.launcher_compat_art_none)
    }

    /**
     * [installed], reordered for the Apply button: the first launcher is the
     * one a press should go to.
     *
     * HOME still leads when it can take an apply. When it cannot (Monet), the
     * first installed launcher whose apply intent actually resolves
     * (Projectivy, in [ALL]'s order) goes first instead, and HOME's setup
     * screen stays one chip away. 1.9.5 sent the button to HOME
     * unconditionally, so a TV with Monet as its default home and Projectivy
     * beside it lost the one-press apply it had in 1.9.4.
     *
     * Resolving, not just [Launcher.inboundApply], decides the promotion:
     * Leanback on Fire claims no inbound flag of its own but has no receiver
     * either, and promoting it would trade Monet's walk for a manual toast.
     */
    fun applyOrder(context: Context): List<Launcher> {
        val all = installed(context)
        val home = all.firstOrNull() ?: return all
        if (home.inboundApply) return all
        val direct = all.firstOrNull { it.inboundApply && resolvesApply(context, it) }
            ?: return all
        return listOf(direct) + all.filter { it != direct }
    }

    /** True when [launcher]'s apply intent reaches an activity on this device. */
    private fun resolvesApply(context: Context, launcher: Launcher): Boolean {
        val intent = launcher.intent(context, context.packageName) ?: return false
        return context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    /** The launcher an Apply or re-apply press goes to; see [applyOrder]. */
    fun detectApplyTarget(context: Context): Launcher? = applyOrder(context).firstOrNull()

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
            ?: return probeMissed(context, launcher, manual)

        val resolves = context.packageManager
            .queryIntentActivities(intent, 0).isNotEmpty()
        if (!resolves) {
            return probeMissed(context, launcher, manual)
        }

        return try {
            context.startActivity(intent)
            Result.Applied(launcher.displayName)
        } catch (_: Exception) {
            probeMissed(context, launcher, manual)
        }
    }

    /**
     * The apply probe missed on this box. A launcher whose settings tree we
     * know gets the walk screen instead of the one-line toast: the toast
     * expires on the way to following it, and when the path it names is stale
     * it is a dead end - the AT4K "Settings → Icon pack" toast sent users to
     * a screen that does not exist (r/AT4K, 2026-10-01). Only launchers with
     * no known tree (Leanback on Fire) keep the sentence.
     */
    private fun probeMissed(context: Context, launcher: Launcher, manual: String): Result =
        if (launcher.setupStops.isNotEmpty()) handoff(context, launcher)
        else Result.Manual(launcher.displayName, manual)

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
