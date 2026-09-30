package dev.corebuilds.line

import android.content.Context

/**
 * The overlay surfaces' native memory.
 *
 * The web UI owns the settings; this file exists for exactly one reason:
 * restoring the VPN dot after a reboot, which happens before any WebView (and
 * therefore any localStorage) is alive. It stores *what was running and with
 * which shape*, never a second copy of the settings model — the drawer writes
 * it every time a surface starts, and nothing reads it except [BootReceiver].
 *
 * The ticker is deliberately not restored: it is a WebView, and spinning one
 * up from a boot broadcast on a TV stick to draw a crawl nobody asked to see
 * yet is the kind of thing that gets an app uninstalled. The dot is a native
 * view and comes back.
 */
class OverlayPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var ticker: Boolean
        get() = prefs.getBoolean(KEY_TICKER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TICKER, value).apply()
        }

    var tickerPosition: String
        get() = if (prefs.getString(KEY_TICKER_POSITION, "bottom") == "top") "top" else "bottom"
        set(value) {
            prefs.edit().putString(KEY_TICKER_POSITION, if (value == "top") "top" else "bottom").apply()
        }

    val dotEnabled: Boolean
        get() = prefs.getBoolean(KEY_DOT, false)

    /** Null when the dot has never been enabled on this install. */
    fun readDotConfig(): DotConfig? {
        if (!dotEnabled) return null
        return DotConfig(
            corner = DotConfig.normalizeCorner(prefs.getString(KEY_DOT_CORNER, DotConfig.CORNER_BOTTOM_RIGHT)),
            opacity = prefs.getInt(KEY_DOT_OPACITY, DotConfig.DEFAULT.opacity),
            hideWhenOk = prefs.getBoolean(KEY_DOT_HIDE_OK, false),
            blink = prefs.getBoolean(KEY_DOT_BLINK, true),
            marginDp = prefs.getInt(KEY_DOT_MARGIN, DotConfig.DEFAULT.marginDp),
        ).sanitized()
    }

    fun writeDotConfig(config: DotConfig) {
        val clean = config.sanitized()
        prefs.edit()
            .putBoolean(KEY_DOT, true)
            .putString(KEY_DOT_CORNER, clean.corner)
            .putInt(KEY_DOT_OPACITY, clean.opacity)
            .putBoolean(KEY_DOT_HIDE_OK, clean.hideWhenOk)
            .putBoolean(KEY_DOT_BLINK, clean.blink)
            .putInt(KEY_DOT_MARGIN, clean.marginDp)
            .apply()
    }

    fun clearDot() {
        prefs.edit()
            .putBoolean(KEY_DOT, false)
            .remove(KEY_DOT_CORNER)
            .remove(KEY_DOT_OPACITY)
            .remove(KEY_DOT_HIDE_OK)
            .remove(KEY_DOT_BLINK)
            .remove(KEY_DOT_MARGIN)
            .apply()
    }

    private companion object {
        const val FILE = "coreline.overlay"
        const val KEY_TICKER = "ticker"
        const val KEY_TICKER_POSITION = "ticker.position"
        const val KEY_DOT = "dot"
        const val KEY_DOT_CORNER = "dot.corner"
        const val KEY_DOT_OPACITY = "dot.opacity"
        const val KEY_DOT_HIDE_OK = "dot.hideWhenOk"
        const val KEY_DOT_BLINK = "dot.blink"
        const val KEY_DOT_MARGIN = "dot.marginDp"
    }
}
