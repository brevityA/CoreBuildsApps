package dev.corebuilds.shift

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The Core Builds wallpaper palette, and the shared "spectrum" motion built on
 * it.
 *
 * The wallpaper collection (Wallpapers/README.md, 70 pieces) is built from one
 * ordered ramp — cyan through build blue into dusk violet, with ember as the
 * warm counterweight. Core Shift's focus and header motion runs that same ramp
 * so the app reads as part of the suite rather than a generic list. Every
 * animated colour in the app comes from here; nothing hardcodes a hex.
 */
object CoreSpectrum {

    /** The suite ramp, in the order the wallpapers traverse it. */
    val RAMP = intArrayOf(
        0xFF00E5FF.toInt(), // Core Cyan
        0xFF00D4FF.toInt(), // Signal
        0xFF7EEEFF.toInt(), // Glow
        0xFF4FACFE.toInt(), // Build Blue
        0xFF8A4890.toInt(), // Dusk Violet
        0xFFC03A20.toInt(), // Ember
    )

    /** The cool half of the ramp — used where ember would be too loud. */
    val COOL_RAMP = intArrayOf(
        0xFF00E5FF.toInt(),
        0xFF7EEEFF.toInt(),
        0xFF4FACFE.toInt(),
        0xFF8A4890.toInt(),
    )

    private val ARGB = ArgbEvaluator()

    /**
     * Colour at [t] (0..1, wrapping) along [ramp], interpolated. Driving a
     * single 0..1 phase through this is what makes the border, the spine and
     * the header rule all move as one system.
     */
    fun sample(ramp: IntArray, t: Float): Int {
        val wrapped = ((t % 1f) + 1f) % 1f
        val scaled = wrapped * ramp.size
        val i = scaled.toInt() % ramp.size
        val next = (i + 1) % ramp.size
        val frac = scaled - scaled.toInt()
        return ARGB.evaluate(frac, ramp[i], ramp[next]) as Int
    }

    /**
     * A never-ending 0..1 phase animator at [periodMs] per full traversal.
     * Callers own the lifecycle — start on focus/attach, cancel on blur/recycle.
     */
    fun phaseAnimator(periodMs: Long, onPhase: (Float) -> Unit): ValueAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = periodMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { onPhase(it.animatedFraction) }
        }

    /**
     * Binds a horizontal spectrum sweep to [view]'s background. Returns the
     * animator so the caller can cancel it; TV apps leak animators easily and a
     * running ValueAnimator holds a hard reference to the view.
     */
    fun bindSweep(
        view: View,
        ramp: IntArray = RAMP,
        periodMs: Long = 9_000L,
        cornerRadiusPx: Float = 0f,
    ): ValueAnimator {
        val drawable = GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            cornerRadius = cornerRadiusPx
        }
        view.background = drawable

        // Three samples 1/3 apart give a continuously travelling gradient
        // rather than a crossfade between two flat colours.
        return phaseAnimator(periodMs) { phase ->
            drawable.colors = intArrayOf(
                sample(ramp, phase),
                sample(ramp, phase + 0.33f),
                sample(ramp, phase + 0.66f),
            )
        }.also { it.start() }
    }
}
