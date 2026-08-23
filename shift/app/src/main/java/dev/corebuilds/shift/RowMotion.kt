package dev.corebuilds.shift

import android.animation.ValueAnimator
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.view.ViewCompat

/**
 * Focus motion for a live-wallpaper row.
 *
 * On a TV there is exactly one focused thing and the user is six feet away, so
 * focus has to be unmistakable without being noisy. This does four things at
 * once, all off a single state change:
 *
 *  - the card lifts (scale + elevation) on a decelerate curve;
 *  - its border runs the wallpaper suite's cyan→violet ramp, travelling;
 *  - the leading accent spine wipes in from the top;
 *  - the poster thumb saturates from 45% to full and slowly drifts (ken burns),
 *    which is what makes a still thumbnail read as "this one is alive".
 *
 * Unfocused rows are deliberately desaturated: with ten cyan-heavy thumbs in a
 * column, saturating only the focused one is what creates the depth.
 *
 * Every animator is owned and cancelled — see [release]. A running ValueAnimator
 * holds a hard reference to its target, and RecyclerView will recycle these
 * rows out from under us.
 */
class RowMotion(
    private val card: View,
    private val spine: View,
    private val thumb: ImageView,
) {

    private val density = card.resources.displayMetrics.density
    private val cornerRadius = 12f * density
    private val strokeWidth = (2f * density).toInt()
    private val liftZ = 10f * density

    private val surface = card.context.getColor(R.color.cb_surface)
    private val surfaceFocused = card.context.getColor(R.color.cb_surface_focused)

    /** The card background is built in code so the stroke colour can animate;
     *  a selector drawable can only cut between static states. */
    private val background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = this@RowMotion.cornerRadius
        setColor(surface)
        setStroke(strokeWidth, 0x00000000)
    }

    private val spineDrawable = GradientDrawable().apply {
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        cornerRadius = 2f * density
    }

    private var sweep: ValueAnimator? = null
    private var kenBurns: ValueAnimator? = null
    private var lift: ValueAnimator? = null

    private var focused = false
    private var currentSaturation = RESTING_SATURATION

    init {
        card.background = background
        card.clipToOutline = false
        spine.background = spineDrawable
        spine.alpha = 0f
        spine.scaleY = 0.2f
        spine.pivotY = 0f
        applyThumbSaturation(RESTING_SATURATION)
    }

    /**
     * @param animate false on (re)bind, so a recycled row snaps to the right
     *        state instead of playing a phantom focus animation.
     */
    fun setFocused(value: Boolean, animate: Boolean = true) {
        if (focused == value && animate) return
        focused = value
        if (value) enter(animate) else exit(animate)
    }

    private fun enter(animate: Boolean) {
        card.pivotX = 0f
        card.pivotY = card.height / 2f

        // Travelling border + spine, both off the same phase so they read as
        // one light source moving across the card.
        sweep?.cancel()
        sweep = CoreSpectrum.phaseAnimator(SWEEP_PERIOD_MS) { phase ->
            background.setStroke(strokeWidth, CoreSpectrum.sample(CoreSpectrum.COOL_RAMP, phase))
            spineDrawable.colors = intArrayOf(
                CoreSpectrum.sample(CoreSpectrum.COOL_RAMP, phase),
                CoreSpectrum.sample(CoreSpectrum.COOL_RAMP, phase + 0.4f),
            )
        }.also { it.start() }

        background.setColor(surfaceFocused)

        animateLift(
            toScale = FOCUSED_SCALE,
            toZ = liftZ,
            toSaturation = 1f,
            duration = if (animate) ENTER_MS else 0L,
        )

        spine.animate()
            .alpha(1f).scaleY(1f)
            .setDuration(if (animate) ENTER_MS else 0L)
            .setInterpolator(DECELERATE)
            .start()

        // Slow continuous drift on the poster. Long period + small amplitude:
        // it should be felt, not watched.
        kenBurns?.cancel()
        kenBurns = ValueAnimator.ofFloat(1f, THUMB_DRIFT_SCALE).apply {
            duration = KEN_BURNS_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                val s = it.animatedValue as Float
                thumb.scaleX = s
                thumb.scaleY = s
            }
            start()
        }
    }

    private fun exit(animate: Boolean) {
        sweep?.cancel()
        sweep = null
        kenBurns?.cancel()
        kenBurns = null

        background.setColor(surface)
        background.setStroke(strokeWidth, 0x00000000)

        animateLift(
            toScale = 1f,
            toZ = 0f,
            toSaturation = RESTING_SATURATION,
            duration = if (animate) EXIT_MS else 0L,
        )

        spine.animate()
            .alpha(0f).scaleY(0.2f)
            .setDuration(if (animate) EXIT_MS else 0L)
            .setInterpolator(DECELERATE)
            .start()

        thumb.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(if (animate) EXIT_MS else 0L)
            .start()
    }

    /** Scale, elevation and thumb saturation share one animator — three
     *  separate ones drift apart on a slow TV SoC and the card looks rubbery. */
    private fun animateLift(
        toScale: Float,
        toZ: Float,
        toSaturation: Float,
        duration: Long,
    ) {
        lift?.cancel()

        val fromScale = card.scaleX
        val fromZ = ViewCompat.getTranslationZ(card)
        val fromSaturation = currentSaturation

        if (duration == 0L) {
            applyLift(toScale, toZ, toSaturation)
            return
        }

        lift = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DECELERATE
            addUpdateListener {
                val f = it.animatedFraction
                applyLift(
                    fromScale + (toScale - fromScale) * f,
                    fromZ + (toZ - fromZ) * f,
                    fromSaturation + (toSaturation - fromSaturation) * f,
                )
            }
            start()
        }
    }

    private fun applyLift(scale: Float, z: Float, saturation: Float) {
        card.scaleX = scale
        card.scaleY = scale
        ViewCompat.setTranslationZ(card, z)
        applyThumbSaturation(saturation)
    }

    private fun applyThumbSaturation(value: Float) {
        currentSaturation = value
        thumb.colorFilter = if (value >= 0.999f) {
            null
        } else {
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(value) })
        }
        // A touch of dimming on resting rows, so focus also reads as brightness.
        thumb.alpha = 0.80f + 0.20f * value
    }

    /** Must be called from onViewRecycled / onViewDetachedFromWindow. */
    fun release() {
        sweep?.cancel(); sweep = null
        kenBurns?.cancel(); kenBurns = null
        lift?.cancel(); lift = null
        card.animate().cancel()
        spine.animate().cancel()
        thumb.animate().cancel()
        focused = false
        applyLift(1f, 0f, RESTING_SATURATION)
        thumb.scaleX = 1f
        thumb.scaleY = 1f
        spine.alpha = 0f
        spine.scaleY = 0.2f
        background.setColor(surface)
        background.setStroke(strokeWidth, 0x00000000)
    }

    private companion object {
        const val FOCUSED_SCALE = 1.03f
        const val THUMB_DRIFT_SCALE = 1.07f
        const val RESTING_SATURATION = 0.45f
        const val ENTER_MS = 220L
        const val EXIT_MS = 180L
        const val SWEEP_PERIOD_MS = 7_000L
        const val KEN_BURNS_MS = 6_000L
        val DECELERATE = DecelerateInterpolator(1.6f)
    }
}
