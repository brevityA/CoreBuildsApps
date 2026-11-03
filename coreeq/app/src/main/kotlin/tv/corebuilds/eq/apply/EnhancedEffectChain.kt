package tv.corebuilds.eq.apply

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.util.Log

/**
 * The guard rail between Core EQ's DSP numbers and the framework's native
 * effect engine (the CVE-2019-9382 class of bug: libeffects has shipped an
 * out-of-bounds write from exactly this layer when a band index or gain value
 * arrived unvalidated, and application-level bounds checking is the fix).
 *
 * Every [Equalizer]/[AudioEffect] interaction in the apply path goes through
 * here. Three rules, none of them optional:
 *
 * 1. **Validate before the framework sees the number.** A band index outside
 *    `0 until numberOfBands` and a gain outside `bandLevelRange` are clamped
 *    or refused *before* `setBandLevel` is called — the native layer is never
 *    asked to write out of bounds.
 * 2. **Catch the two exceptions that actually occur.**
 *    [UnsupportedOperationException] when the native engine failed to
 *    initialise (missing library, invalid audioSessionId of 0), and
 *    [RuntimeException] when the effect is in an error state — the classic
 *    cause being a previous instance whose `release()` was never called.
 *    Both are logged and swallowed: a refused band disables that band, never
 *    the app.
 * 3. **Strict release lifecycle.** Every effect created through
 *    [createEqualizerSafely] is released exactly once through [safeRelease];
 *    a leaked effect is what corrupts the *next* initialisation attempt.
 *
 * The clamp/bounds core ([clampBandLevel]) is pure Kotlin so the whole rule is
 * unit-tested without Android ([EnhancedEffectChainTest]).
 */
object EnhancedEffectChain {

    private const val TAG = "CoreEQ"

    /**
     * The safe band level for [band] on a device with [numBands] bands and
     * millibel range [range], or null when the band index is invalid.
     *
     * Pure: no Android types, so the bounds rule is pinned by unit tests.
     * A null or short range means the device reported nothing usable and the
     * band is refused rather than guessed at.
     */
    fun clampBandLevel(band: Int, levelMb: Short, numBands: Int, range: ShortArray?): Short? {
        if (numBands <= 0) return null
        if (band !in 0 until numBands) return null
        if (range == null || range.size < 2) return null
        val lo = range[0]
        val hi = range[1]
        if (hi < lo) return null
        return levelMb.coerceIn(lo, hi)
    }

    /**
     * Set one band, fully guarded. Returns true when the framework accepted
     * the (clamped) level, false when the band was out of range, the level
     * could not be clamped, or the effect threw — in every false case the app
     * keeps running and the band is simply left at its previous level.
     */
    fun safeSetBandLevel(eq: Equalizer, band: Int, levelMb: Short): Boolean {
        val clamped = try {
            clampBandLevel(band, levelMb, eq.numberOfBands.toInt(), eq.bandLevelRange)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not read Equalizer capabilities", e)
            return false
        }
        if (clamped == null) {
            Log.e(TAG, "Invalid band index $band (device has ${eq.numberOfBands} bands)")
            return false
        }
        return try {
            eq.setBandLevel(band.toShort(), clamped)
            true
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "Band $band not supported on this device", e)
            false
        } catch (e: RuntimeException) {
            // Error state — usually a leaked previous instance. Do not retry:
            // the caller releases and recreates the effect instead.
            Log.e(TAG, "Runtime error setting band $band", e)
            false
        }
    }

    /**
     * Create a platform [Equalizer] that never crashes the app: returns null
     * (EQ disabled gracefully) when the native engine refuses to initialise.
     */
    fun createEqualizerSafely(sessionId: Int, priority: Int = 0): Equalizer? =
        try {
            Equalizer(priority, sessionId)
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "Effect engine unavailable on session $sessionId", e)
            null
        } catch (e: RuntimeException) {
            Log.e(TAG, "Effect state corrupted on session $sessionId", e)
            null
        }

    /** Enable/disable without throwing; false when the effect refused. */
    fun safeSetEnabled(effect: AudioEffect, on: Boolean): Boolean =
        try {
            effect.setEnabled(on) == AudioEffect.SUCCESS
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "${effect.javaClass.simpleName} refused enable=$on", e)
            false
        } catch (e: RuntimeException) {
            Log.e(TAG, "${effect.javaClass.simpleName} in error state for enable=$on", e)
            false
        }

    /**
     * Release an effect exactly once, swallowing the error-state throw. The
     * caller must treat the effect as dead afterwards — a released effect is
     * what makes the *next* construction throw RuntimeException, so this is
     * the other half of the lifecycle rule.
     */
    fun safeRelease(effect: AudioEffect?) {
        if (effect == null) return
        try {
            effect.release()
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "${effect.javaClass.simpleName} already released or uninitialised", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "${effect.javaClass.simpleName} release hit an error state", e)
        }
    }
}
