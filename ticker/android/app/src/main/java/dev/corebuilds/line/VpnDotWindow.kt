package dev.corebuilds.line

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager

/**
 * Hosts the VPN dot in its own always-on-top window.
 *
 * It is deliberately a separate window from the ticker rather than a badge on
 * the chyron: the dot has to stay up when the ticker is off, and the ticker
 * has to stay usable when the dot is off. Two surfaces, two lifecycles, one
 * foreground service.
 *
 * The window is small, non-focusable and touch-through, so the D-pad and the
 * remote's OK button behave exactly as if it were not there. Corners are
 * addressed with absolute gravity (RIGHT/LEFT, not START/END): "bottom right"
 * means the bottom right of the panel to every viewer, and the app must not
 * mirror that under an RTL locale.
 */
class VpnDotWindow(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var view: VpnDotView? = null
    private var config = DotConfig.DEFAULT

    fun configure(next: DotConfig) {
        val clean = next.sanitized()
        val geometryChanged = clean.corner != config.corner || clean.marginDp != config.marginDp
        config = clean
        view?.let { dot ->
            applyDrawingPrefs(dot)
            if (geometryChanged) updateLayout(dot)
            else dot.invalidate()
        }
    }

    fun show(): Boolean {
        if (view != null) return true
        val manager = windowManager ?: return false
        val dot = VpnDotView(appContext)
        applyDrawingPrefs(dot)
        return try {
            manager.addView(dot, layoutParams())
            view = dot
            true
        } catch (err: Exception) {
            // The overlay permission was revoked between the check and the
            // add, or the display went away under us. The service treats a
            // false here as "this surface is not up".
            false
        }
    }

    fun hide() {
        val manager = windowManager
        val dot = view
        view = null
        if (manager == null || dot == null) return
        try {
            manager.removeView(dot)
        } catch (err: Exception) {
            // already detached (display removed, service killed)
        }
    }

    fun apply(state: String) {
        view?.setVpnState(state)
    }

    private fun applyDrawingPrefs(dot: VpnDotView) {
        dot.healthyOpacity = config.opacity / 100f
        dot.hideWhenOk = config.hideWhenOk
        dot.blinkWhenFaulted = config.blink
    }

    private fun updateLayout(dot: VpnDotView) {
        val manager = windowManager ?: return
        try {
            manager.updateViewLayout(dot, layoutParams())
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
            dp(WINDOW_DP),
            dp(WINDOW_DP),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        val margin = dp(config.marginDp)
        val right = config.corner == DotConfig.CORNER_BOTTOM_RIGHT || config.corner == DotConfig.CORNER_TOP_RIGHT
        val bottom = config.corner == DotConfig.CORNER_BOTTOM_RIGHT || config.corner == DotConfig.CORNER_BOTTOM_LEFT
        params.gravity = (if (bottom) Gravity.BOTTOM else Gravity.TOP) or
            (if (right) Gravity.RIGHT else Gravity.LEFT)
        params.x = 0
        params.y = 0
        params.leftMargin = if (right) 0 else margin
        params.rightMargin = if (right) margin else 0
        params.topMargin = if (bottom) 0 else margin
        params.bottomMargin = if (bottom) margin else 0
        return params
    }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private companion object {
        /**
         * A 56dp window around a ~13dp dot: the halo needs the room, and a
         * window that clips its own glow looks like a rendering bug.
         */
        const val WINDOW_DP = 56
    }
}
