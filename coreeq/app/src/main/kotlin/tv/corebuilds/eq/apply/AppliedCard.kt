package tv.corebuilds.eq.apply

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.R
import tv.corebuilds.eq.TvActivity

/**
 * The on-screen card (1.3.0): a few seconds over whatever is playing that
 * say correction is on, which profile, and how it is applied; or that it has
 * paused on an output with no profile of its own.
 *
 * Built the way Core Line's score box and VPN dot are (`ScoreBugWindow`,
 * `VpnDotWindow` in `ticker/android`), because they already solved drawing
 * over another app's picture on a TV:
 *
 * - **Its own window** (`TYPE_APPLICATION_OVERLAY`), added by the running
 *   [EqService], so it shows over the player rather than over Core EQ.
 * - **Small, non-focusable, touch-through.** The window is the card, not a
 *   sheet over the screen, and never takes focus: the D-pad and OK behave as
 *   if it were not there.
 * - **Absolute gravity**, top right (RIGHT, not END: an RTL locale must not
 *   move it), inset by the app's screen gutters so it stays inside the safe
 *   area of a set that overscans.
 *
 * It needs "Display over other apps". Without it [show] returns false and the
 * service falls back to a text toast; Fire TV refuses the permission outright.
 * The card's sizes come from the same 960dp box as the app's screens
 * ([TvActivity.normalizeDpBox]), so it looks the same on a 1080p and a 4K set.
 */
class AppliedCard(context: Context) {

    /** What the card says. [active] picks the colour: cyan on, amber paused. */
    data class Content(val active: Boolean, val title: String, val detail: String, val extras: String?)

    private val ui: Context = ContextThemeWrapper(
        TvActivity.normalizeDpBox(context.applicationContext), R.style.Theme_CoreEQ
    )
    private val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var card: View? = null
    private val fadeOut = Runnable { hide(animated = true) }

    /** Whether Android lets Core EQ draw over other apps right now. */
    fun allowed(): Boolean = Settings.canDrawOverlays(ui)

    /**
     * Show [content] for [SHOW_MS], replacing whatever the card said if it is
     * already up (the timer restarts). False when the overlay is not allowed
     * or Android refused the window, so the caller can fall back.
     */
    // InflateParams: an overlay window has no parent to inflate against; the
    // window's own LayoutParams size the card.
    @SuppressLint("InflateParams")
    fun show(content: Content): Boolean {
        if (!allowed()) return false
        val manager = windowManager ?: return false
        val view = card ?: LayoutInflater.from(ui).inflate(R.layout.view_applied_card, null)
        bind(view, content)
        if (card == null) {
            try {
                manager.addView(view, layoutParams())
            } catch (e: Exception) {
                // Permission revoked between the check and the add, or the
                // display went away: the same "not up" Core Line reports.
                return false
            }
            card = view
            view.alpha = 0f
            view.translationY = -dp(SLIDE_DP)
            view.animate().alpha(1f).translationY(0f).setDuration(FADE_IN_MS).start()
        } else {
            view.animate().cancel()
            view.alpha = 1f
            view.translationY = 0f
        }
        handler.removeCallbacks(fadeOut)
        handler.postDelayed(fadeOut, SHOW_MS)
        return true
    }

    /** Take the card down now (correction stopped, service ending). */
    fun dismiss() = hide(animated = false)

    private fun hide(animated: Boolean) {
        handler.removeCallbacks(fadeOut)
        val view = card ?: return
        if (!animated) {
            remove(view)
            return
        }
        view.animate().alpha(0f).translationY(-dp(SLIDE_DP)).setDuration(FADE_OUT_MS)
            .withEndAction { remove(view) }
            .start()
    }

    private fun remove(view: View) {
        view.animate().cancel()
        if (card === view) card = null
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            // already detached (display removed, service killed)
        }
    }

    private fun bind(view: View, content: Content) {
        val tint = ContextCompat.getColor(ui, if (content.active) R.color.cb_signal_cyan else R.color.cb_warning)
        ((view.background?.mutate() as? LayerDrawable)?.findDrawableByLayerId(R.id.card_edge) as? GradientDrawable)
            ?.setColor(tint)
        (view.findViewById<View>(R.id.card_dot).background?.mutate() as? GradientDrawable)?.setColor(tint)
        val rail = ui.getString(if (content.active) R.string.card_rail_on else R.string.card_rail_paused)
        view.findViewById<TextView>(R.id.card_rail).apply {
            text = rail
            setTextColor(tint)
        }
        view.findViewById<TextView>(R.id.card_title).text = content.title
        view.findViewById<TextView>(R.id.card_detail).text = content.detail
        view.findViewById<TextView>(R.id.card_extras).apply {
            text = content.extras.orEmpty()
            visibility = if (content.extras.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        view.contentDescription = ui.getString(R.string.card_description, rail, content.title, content.detail)
    }

    // RtlHardcoded: top right is the top right of the panel for every viewer,
    // as Core Line's windows do it; END would move it under an RTL locale.
    @SuppressLint("RtlHardcoded")
    private fun layoutParams(): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.RIGHT
        // x/y are offsets from the anchored edges (WindowManager.LayoutParams
        // has no margins), so positive values move the card inward.
        params.x = ui.resources.getDimensionPixelOffset(R.dimen.cb_gutter_side)
        params.y = ui.resources.getDimensionPixelOffset(R.dimen.cb_gutter_top)
        params.windowAnimations = 0 // the card animates itself; no system slide on top
        return params
    }

    private fun dp(value: Int): Float = value * ui.resources.displayMetrics.density

    private companion object {
        /** Long enough to read three short lines from the sofa, short enough not to sit on the film. */
        const val SHOW_MS = 4_000L
        const val FADE_IN_MS = 180L
        const val FADE_OUT_MS = 260L
        const val SLIDE_DP = 8
    }
}
