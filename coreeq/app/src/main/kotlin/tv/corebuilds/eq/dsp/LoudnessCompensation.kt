package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.dsp.ManualEq

/**
 * Full ISO 226:2003 equal-loudness compensation mapped to the ten manual EQ
 * bands (1.11.0).
 *
 * Human hearing is non-linear: at lower SPL, bass and treble perception drops
 * disproportionately compared to midrange. [EqualLoudness.compensationDb]
 * already computes the contour difference at the ISO frequencies; this object
 * samples it at the ten manual EQ band centres so the result can be applied
 * through the existing ToneLayers / BandMapping pipeline.
 *
 * The compensation is normalised to 0 dB at 1 kHz: midrange is never touched,
 * only the bass and treble the ear loses at quiet listening are restored.
 *
 * Pure Kotlin, unit-tested without Android.
 */
object LoudnessCompensation {

    /**
     * Per-band gain in dB at the ten manual EQ band centres, for a volume
     * [dbBelowReference] below the reference listening level.
     *
     * @param dbBelowReference how many dB below the reference level the playback
     *   is. Zero or positive means no compensation (all zeros).
     * @param strength 0.0 (off) to 1.0 (full ISO 226 compensation).
     *
     * Returns a [DoubleArray] aligned with [ManualEq.BAND_CENTRES_HZ]. Values
     * are positive (boost) at low volume, zero at reference, and never exceed
     * [MAX_COMPENSATION_DB] in magnitude.
     */
    fun bandGains(
        dbBelowReference: Double,
        strength: Double = 1.0
    ): DoubleArray {
        val gains = DoubleArray(ManualEq.BAND_CENTRES_HZ.size)
        if (!dbBelowReference.isFinite() || dbBelowReference <= 0.0) return gains
        val s = strength.coerceIn(0.0, 1.0)
        if (s <= 0.0) return gains

        // Sample the ISO 226 compensation curve at the manual band centres by
        // log interpolation between the ISO frequencies.
        val iso = EqualLoudness.compensationDb(REFERENCE_PHON, REFERENCE_PHON - dbBelowReference, s)
        for (i in ManualEq.BAND_CENTRES_HZ.indices) {
            gains[i] = interpolateDb(ManualEq.BAND_CENTRES_HZ[i], iso)
                .coerceIn(-MAX_COMPENSATION_DB, MAX_COMPENSATION_DB)
        }
        return gains
    }

    /** Maximum compensation magnitude in either direction, dB. */
    private const val MAX_COMPENSATION_DB = 6.0

    /** Reference listening level in phon (matches ToneLayers.REFERENCE_PHON). */
    private const val REFERENCE_PHON = 80.0

    /**
     * Log-interpolate the compensation curve (aligned with ISO frequencies) at
     * one arbitrary frequency in Hz.
     */
    private fun interpolateDb(hz: Double, iso: DoubleArray): Double {
        val freqs = EqualLoudness.FREQUENCIES_HZ
        var i = 0
        while (i < freqs.size - 1 && freqs[i + 1] < hz) i++
        val fLo = freqs[i]
        val fHi = freqs[minOf(i + 1, freqs.size - 1)]
        val t = if (fHi > fLo) kotlin.math.log(hz / fLo) / kotlin.math.log(fHi / fLo) else 0.0
        return iso[i] + t * (iso[minOf(i + 1, freqs.size - 1)] - iso[i])
    }
}
