package dev.corebuilds.line

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/**
 * The dot itself: a small circle drawn in an overlay window, in a colour that
 * means one thing and nothing else.
 *
 * Design constraints that come from the surface it sits on:
 *
 * - It is composited over *whatever* is playing — a dark scene, a white
 *   sports app, a bright menu — so it carries its own dark keyline. A status
 *   dot that disappears against a bright frame is worse than no dot, because
 *   the viewer reads absence as "off".
 * - It is not a widget with text. At three metres a 12dp dot is legible; a
 *   label is not, and a label over someone's show is intrusive.
 * - Only the fault states animate. A healthy dot holds still, which is both
 *   cheaper on a TV stick and the reason a moving dot reads as a warning.
 */
class VpnDotView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyline = Paint(Paint.ANTI_ALIAS_FLAG)

    private var state = VpnSnapshot.STATE_DOWN

    /** Alpha of the healthy dot, 0.15–1.0. The fault states ignore it. */
    var healthyOpacity: Float = 0.55f
        set(value) {
            field = value.coerceIn(0.15f, 1f)
            invalidate()
        }

    /** Show nothing while the VPN is up; the dot becomes a fault light only. */
    var hideWhenOk: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Slow pulse while the tunnel is down or not covering this app. */
    var blinkWhenFaulted: Boolean = true
        set(value) {
            field = value
            syncBlink()
        }

    private var blinkOn = true
    private val blinker = object : Runnable {
        override fun run() {
            blinkOn = !blinkOn
            invalidate()
            postDelayed(this, BLINK_MS)
        }
    }

    fun setVpnState(next: String) {
        if (next == state) return
        state = next
        syncBlink()
        invalidate()
    }

    private fun faulted(): Boolean = state != VpnSnapshot.STATE_OK

    private fun syncBlink() {
        removeCallbacks(blinker)
        blinkOn = true
        if (!blinkWhenFaulted || !faulted() || !isAttachedToWindow) return
        postDelayed(blinker, BLINK_MS)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncBlink()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blinker)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) syncBlink() else removeCallbacks(blinker)
    }

    override fun onDraw(canvas: Canvas) {
        if (hideWhenOk && !faulted()) return

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.24f
        val colour = when (state) {
            VpnSnapshot.STATE_OK -> context.getColor(R.color.vpn_ok)
            VpnSnapshot.STATE_PARTIAL -> context.getColor(R.color.vpn_warn)
            else -> context.getColor(R.color.vpn_down)
        }
        val alpha = when {
            state == VpnSnapshot.STATE_OK -> healthyOpacity
            blinkOn -> 1f
            else -> 0.35f
        }

        // Keyline first: 2px of near-black under the colour is what keeps the
        // dot readable on a white menu and on a black end-credit roll alike.
        keyline.style = Paint.Style.STROKE
        keyline.strokeWidth = radius * 0.42f
        keyline.color = Color.argb(190, 4, 7, 15)
        canvas.drawCircle(cx, cy, radius + keyline.strokeWidth / 2f, keyline)

        // A soft halo buys legibility on mid-grey content without stealing
        // attention from the game.
        halo.style = Paint.Style.FILL
        halo.color = withAlpha(colour, 0.22f * alpha)
        canvas.drawCircle(cx, cy, radius * 1.85f, halo)

        fill.style = Paint.Style.FILL
        fill.color = withAlpha(colour, alpha)
        canvas.drawCircle(cx, cy, radius, fill)
    }

    private fun withAlpha(colour: Int, alpha: Float): Int = Color.argb(
        (alpha.coerceIn(0f, 1f) * 255).toInt(),
        Color.red(colour),
        Color.green(colour),
        Color.blue(colour),
    )

    private companion object {
        const val BLINK_MS = 700L
    }
}
