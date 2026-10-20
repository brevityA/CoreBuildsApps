package tv.corebuilds.iconpack

import android.content.Context

/**
 * What the one-off "Core Builds Premium" purchase unlocks, and where it gates.
 *
 * The Play listing is free with a single non-consumable purchase. Play will not
 * let a free app become paid, and it will not accept a purchase that buys
 * nothing, so the purchase has to own a real digital good in the app. It owns
 * two: the **live loops** (every animated wallpaper, the whole Live set) and
 * the **eight later still series** (AMOLED, deep space, cinema, icon echo,
 * earth, wild, flux and the Retrowave set). The three series that shipped with
 * the pack — Motion, Horizons and Circuit Core, 36 walls — stay free, as do
 * every icon, every tool, the auditor, the exporter and the Glyphs Pack.
 *
 * Three series free is a deliberate number, not an accident of ordering: a
 * launcher needs something worth applying before anyone trusts the pack with a
 * purchase, and the free set is what the app's own screenshots come from.
 *
 * **Only the Play build gates anything.** [GATED] is `Distribution.PLAY`, so
 * the GitHub / Downloader APK — the one that updates itself and is what most
 * of this pack's users actually install — still ships every wallpaper and
 * every loop for nothing. That is not a policy hole: Play's rules bind the app
 * distributed on Play, and gating the sideload build too would be a pricing
 * decision the owner has not made.
 *
 * The entitlement is cached in [Prefs] so the app works offline and after a
 * restore, and re-verified against Play whenever [Billing] connects.
 */
object Premium {

    /**
     * The Play Console product id. Non-consumable: one purchase, owned
     * forever, restored by querying Play, never re-buyable.
     *
     * Never rename it. Play keys entitlements by product id; a rename makes
     * every existing purchase invisible and asks buyers to pay twice.
     */
    const val PRODUCT = "premium_unlock"

    /** True only in the Play build: the one build where anything is gated. */
    val GATED: Boolean = Distribution.PLAY

    /**
     * The series that stay free. Everything else in `Wallpapers/manifest.json`
     * and every [LiveLoop] is behind the purchase — in the Play build only.
     *
     * tests/test_premium_purchase.py holds these ids to the manifest, so a new
     * series has to be placed here or gated deliberately rather than by
     * forgetting to look.
     */
    val FREE_SERIES: Set<String> = setOf(
        "series-2-motion",
        "series-3-horizons",
        "series-6-circuit-core",
    )

    /** Whether this wall needs the purchase, in this build, right now. */
    fun needsPurchase(context: Context, wallpaper: Wallpaper): Boolean =
        GATED && !unlocked(context) && !isFree(wallpaper)

    /** A wall is free when its still series is one of [FREE_SERIES]. */
    fun isFree(wallpaper: Wallpaper): Boolean = when {
        // Every loop is premium: the animated set is the headline good, and
        // they are the expensive thing to render and host.
        wallpaper.isLive -> false
        wallpaper.series in FREE_SERIES -> true
        // Anything the catalog cannot place (a future series nobody listed)
        // is free rather than locked: a wall behind a purchase by accident is
        // a bug report, a wall given away by accident is a pricing fix.
        else -> false
    }

    fun unlocked(context: Context): Boolean = !GATED || Prefs.premiumOwned(context)
}
