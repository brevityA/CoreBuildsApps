package dev.corebuilds.line

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Hosts the scoreboard bug in its own always-on-top window at the top of the
 * screen.
 *
 * It is built the way [VpnDotWindow] is, because the dot already solved the
 * hard part of drawing over someone else's picture on a TV:
 *
 * - **Its own window, its own lifecycle.** Not a mode of the crawl: the bug
 *   can run with the crawl and the dot off, and either of them without it.
 * - **Small, non-focusable, touch-through.** The window is the box, not a
 *   clear sheet over the screen, and it never takes focus, so the D-pad and
 *   OK behave exactly as if it were not there.
 * - **Absolute gravity.** "Top right" is the top right of the panel for every
 *   viewer; an RTL locale must not mirror it (LEFT/RIGHT, not START/END).
 * - **Inset by x/y from the anchored edge**, with the panel's calibrated
 *   overscan already folded into the margin by the web side, so the bug lands
 *   inside the safe area of a set that crops.
 *
 * What differs is the content: the dot is one native circle, the bug is a
 * WebView of `scorebug.html`, because the slate — ESPN, the NHL, the viewer's
 * feeds, favourites — is already built in JavaScript and the crawl renders it
 * the same way. Two copies of the score parser would disagree sooner or later.
 */
class ScoreBugWindow(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var view: WebView? = null
    private var config = ScoreBugConfig.DEFAULT

    private val isTv: Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)

    val showing: Boolean
        get() = view != null

    fun configure(next: ScoreBugConfig) {
        val clean = next.sanitized()
        val geometryChanged = clean.position != config.position || clean.marginDp != config.marginDp
        config = clean
        view?.let { wv ->
            wv.alpha = config.opacity / 100f
            if (geometryChanged) updateLayout(wv)
        }
    }

    fun show(): Boolean {
        if (view != null) return true
        val manager = windowManager ?: return false
        val wv = buildWebView()
        return try {
            manager.addView(wv, layoutParams())
            view = wv
            true
        } catch (err: Exception) {
            // Permission revoked between the check and the add, or the display
            // went away. The service treats false as "this surface is not up".
            wv.destroy()
            false
        }
    }

    fun hide() {
        val manager = windowManager
        val wv = view
        view = null
        if (wv == null) return
        try {
            manager?.removeView(wv)
        } catch (err: Exception) {
            // already detached (display removed, service killed)
        }
        wv.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView = WebView(appContext).apply {
        overScrollMode = View.OVER_SCROLL_NEVER
        isFocusable = false
        isFocusableInTouchMode = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(Color.TRANSPARENT)
        alpha = config.opacity / 100f
        webViewClient = LineWebClient(appContext)
        webChromeClient = WebChromeClient()
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        val tvParam = if (isTv) "&tv=1" else ""
        loadUrl("https://${LineWebClient.HOST}/scorebug.html?native=1$tvParam")
    }

    private fun updateLayout(wv: WebView) {
        val manager = windowManager ?: return
        try {
            manager.updateViewLayout(wv, layoutParams())
        } catch (err: Exception) {
            // window already gone; the service will rebuild it
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            dp(if (isTv) TV_WIDTH_DP else PHONE_WIDTH_DP),
            dp(if (isTv) TV_HEIGHT_DP else PHONE_HEIGHT_DP),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        val horizontal = when (config.position) {
            ScoreBugConfig.POSITION_TOP_LEFT -> Gravity.LEFT
            ScoreBugConfig.POSITION_TOP_RIGHT -> Gravity.RIGHT
            else -> Gravity.CENTER_HORIZONTAL
        }
        params.gravity = Gravity.TOP or horizontal
        val margin = dp(config.marginDp)
        // x is an offset from the anchored side; centred, there is no side to
        // move away from, so it stays 0 rather than pushing the box off-centre.
        params.x = if (horizontal == Gravity.CENTER_HORIZONTAL) 0 else margin
        params.y = margin
        return params
    }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private companion object {
        /**
         * The box, not the screen. At ten feet: a 22dp rail and a 60dp match
         * line with 40dp marks. 520dp is a bit over half of a 1080p panel's
         * 960dp, wide enough for two abbreviations, two scores and a channel
         * without wrapping.
         */
        const val TV_WIDTH_DP = 520
        const val TV_HEIGHT_DP = 92
        const val PHONE_WIDTH_DP = 340
        const val PHONE_HEIGHT_DP = 70
    }
}
