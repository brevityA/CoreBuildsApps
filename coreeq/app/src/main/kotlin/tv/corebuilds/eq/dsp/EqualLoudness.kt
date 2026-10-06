package tv.corebuilds.eq.dsp

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * ISO 226:2003 equal-loudness contours and the compensation curve Core EQ
 * layers on top of room correction when the viewer turns the volume down.
 *
 * Human hearing is non-linear: at lower SPL, bass and treble perception
 * drops disproportionately compared to midrange (the Fletcher-Munson
 * effect). Without compensation, a flat-corrected room sounds thin at
 * low volume — the bass the measurement fixed is no longer audible.
 *
 * The compensation is the difference between the contour at reference
 * level (where the room was measured) and the contour at playback level,
 * normalised to 0 dB at 1 kHz so midrange is never touched.
 *
 * Reference: ISO 226:2003, §4.1 equation (1) and table 1. Frequencies run
 * from 20 Hz to 12 500 Hz; the standard's upper loudness limits (90 phon
 * through 4 kHz, 80 phon above 4 kHz) are applied at each frequency.
 */
object EqualLoudness {

    /** ISO 226:2003 standard frequencies (Hz). */
    val FREQUENCIES_HZ = doubleArrayOf(
        20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0,
        200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0,
        2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0
    )
    private val ONE_KHZ_INDEX = FREQUENCIES_HZ.indices.first { FREQUENCIES_HZ[it] == 1000.0 }

    /** ISO 226:2003 Table 1 coefficients: alpha_f, L_U (dB), and T_f (dB). */
    private val ALPHA_F = doubleArrayOf(
        0.532, 0.506, 0.480, 0.455, 0.432, 0.409, 0.387, 0.367, 0.349, 0.330,
        0.315, 0.301, 0.288, 0.276, 0.267, 0.259, 0.253, 0.250, 0.246, 0.244,
        0.243, 0.243, 0.243, 0.242, 0.242, 0.245, 0.254, 0.271, 0.301
    )
    private val L_U_DB = doubleArrayOf(
        -31.6, -27.2, -23.0, -19.1, -15.9, -13.0, -10.3, -8.1, -6.2, -4.5,
        -3.1, -2.0, -1.1, -0.4, 0.0, 0.3, 0.5, 0.0, -2.7, -4.1,
        -1.0, 1.7, 2.5, 1.2, -2.1, -7.1, -11.2, -10.7, -3.1
    )
    private val T_F_DB = doubleArrayOf(
        78.5, 68.7, 59.5, 51.1, 44.0, 37.5, 31.5, 26.5, 22.1, 17.9,
        14.4, 11.4, 8.6, 6.2, 4.4, 3.0, 2.2, 2.4, 3.5, 1.7,
        -1.3, -4.2, -6.0, -5.4, -1.5, 6.0, 12.6, 13.9, 12.3
    )

    /**
     * Compute the SPL at each ISO 226 frequency for a given phon level using
     * the standard's Equation (1), not interpolation through its threshold
     * coefficient column.
     */
    fun contourAtPhon(phon: Double): DoubleArray {
        val out = DoubleArray(FREQUENCIES_HZ.size)
        for (i in out.indices) {
            val maximumPhon = if (FREQUENCIES_HZ[i] <= 4_000.0) 90.0 else 80.0
            val level = phon.coerceIn(20.0, maximumPhon)
            val alpha = ALPHA_F[i]
            val transferDb = L_U_DB[i]
            val thresholdDb = T_F_DB[i]
            val hearingThresholdTerm = 0.4 * 10.0.pow((thresholdDb + transferDb) / 10.0 - 9.0)
            val loudnessTerm = 4.47e-3 * (10.0.pow(0.025 * level) - 1.15)
            val a = loudnessTerm + hearingThresholdTerm.pow(alpha)
            out[i] = (10.0 / alpha) * log10(a) - transferDb + 94.0
        }
        return out
    }

    /**
     * The compensation curve in dB at each ISO 226 frequency.
     *
     * @param referencePhon the phon level at which the room was calibrated
     *   (typically 75–85 phon for a comfortable TV listening level).
     * @param playbackPhon the current phon level, derived from the playback
     *   volume. Lower values produce more bass/treble boost.
     * @param strength 0.0 (off) to 1.0 (full ISO compensation). Intermediate
     *   values allow a gentler curve that some listeners prefer.
     *
     * The curve is normalised to 0 dB at 1 kHz so the midrange is never
     * touched — loudness compensation only restores what the ears lose.
     */
    fun compensationDb(
        referencePhon: Double,
        playbackPhon: Double,
        strength: Double = 1.0
    ): DoubleArray {
        val ref = contourAtPhon(referencePhon)
        val play = contourAtPhon(playbackPhon)
        val out = DoubleArray(FREQUENCIES_HZ.size)

        // The 1 kHz index: the contour is the SPL needed at each frequency
        // to match 1 kHz at the given phon. The difference between the
        // reference and playback contours is the compensation needed.
        val kHz1Index = ONE_KHZ_INDEX
        val refNorm = ref[kHz1Index]
        val playNorm = play[kHz1Index]

        for (i in out.indices) {
            // Normalise each contour to 0 dB at 1 kHz
            val refDb = ref[i] - refNorm
            val playDb = play[i] - playNorm
            // Compensation = what the playback ear loses relative to reference
            val raw = playDb - refDb
            out[i] = raw * strength.coerceIn(0.0, 1.0)
        }
        return out
    }

    /**
     * Interpolate the compensation curve onto arbitrary frequency points
     * (e.g. the DP band centres or the ManualEq band centres).
     */
    fun compensationAtFreqs(
        referencePhon: Double,
        playbackPhon: Double,
        strength: Double,
        freqs: List<Double>
    ): DoubleArray {
        val comp = compensationDb(referencePhon, playbackPhon, strength)
        val out = DoubleArray(freqs.size)
        for (i in freqs.indices) {
            out[i] = interpolate(freqs[i], FREQUENCIES_HZ, comp)
        }
        return out
    }

    /**
     * Estimate the phon level from an Android stream volume (0–15 on most
     * TVs) and a calibration reference. The mapping is logarithmic: each
     * volume step is approximately 3–4 dB.
     *
     * @param streamVolume the current STREAM_MUSIC volume index.
     * @param maxStreamVolume the maximum volume index for this device.
     * @param referencePhon the phon level at maximum volume. A typical
     *   living-room TV at max volume is approximately 85 phon at the
     *   listening position.
     */
    fun estimatePhon(
        streamVolume: Int,
        maxStreamVolume: Int,
        referencePhon: Double = 85.0
    ): Double {
        if (streamVolume <= 0 || maxStreamVolume <= 0) return 20.0
        val fraction = streamVolume.toDouble() / maxStreamVolume
        // Each halving of amplitude is about -10 phon (a factor of 2 in
        // perceived loudness). Use a logarithmic mapping.
        val dbBelowRef = -20.0 * log10(max(fraction, 1e-9))
        return max(20.0, referencePhon - dbBelowRef)
    }

    private fun interpolate(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (xs.isEmpty()) return 0.0
        if (x <= xs[0]) return ys[0]
        if (x >= xs[xs.size - 1]) return ys[ys.size - 1]
        for (i in 0 until xs.size - 1) {
            if (x in xs[i]..xs[i + 1]) {
                val frac = (x - xs[i]) / (xs[i + 1] - xs[i])
                return ys[i] + frac * (ys[i + 1] - ys[i])
            }
        }
        return ys[0]
    }
}
