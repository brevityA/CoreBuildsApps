package tv.corebuilds.eq.dsp

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
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
 * Reference: ISO 226:2003, table 1. Frequencies from 20 Hz to 12 500 Hz.
 * L_N values at the standard phon steps (20 to 90) are linearly
 * interpolated to the requested phon.
 */
object EqualLoudness {

    /** ISO 226:2003 standard frequencies (Hz). */
    val FREQUENCIES_HZ = doubleArrayOf(
        20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0,
        200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0,
        2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0
    )
    private val ONE_KHZ_INDEX = FREQUENCIES_HZ.indices.first { FREQUENCIES_HZ[it] == 1000.0 }

    /**
     * ISO 226:2003 table 1 — equal-loudness-level contours.
     * Each row is one phon level (20, 30, ..., 90), each column one frequency.
     * Values are the SPL (dB) needed at that frequency to sound as loud as
     * the phon value at 1 kHz.
     */
    private val CONTOURS_DB = arrayOf(
        // 20 phon
        doubleArrayOf(78.5, 68.7, 59.5, 51.1, 44.0, 37.5, 31.5, 26.5, 22.1, 17.9,
            14.4, 11.4, 8.6, 6.2, 4.4, 3.0, 2.2, 2.4, 3.5, 1.7,
            -1.3, -4.2, -6.0, -5.4, -1.5, 6.0, 12.6, 13.9, 12.3),
        // 30 phon
        doubleArrayOf(85.1, 76.1, 67.3, 59.3, 51.8, 45.3, 39.1, 33.8, 29.1, 24.7,
            21.0, 17.7, 14.8, 12.3, 10.3, 8.7, 7.6, 7.7, 8.4, 6.2,
            2.8, -0.3, -2.4, -2.0, 1.6, 8.9, 15.0, 16.1, 14.3),
        // 40 phon
        doubleArrayOf(91.1, 82.8, 74.5, 66.8, 59.5, 52.9, 46.8, 41.2, 36.3, 31.7,
            27.8, 24.3, 21.2, 18.5, 16.3, 14.6, 13.2, 13.3, 13.6, 11.0,
            7.3, 4.0, 1.4, 1.7, 5.0, 11.9, 17.7, 18.7, 16.7),
        // 50 phon
        doubleArrayOf(96.7, 89.0, 81.2, 74.0, 67.0, 60.6, 54.6, 48.9, 43.7, 39.0,
            34.9, 31.3, 28.0, 25.2, 22.8, 21.0, 19.4, 19.4, 19.2, 16.3,
            12.2, 8.8, 5.7, 5.7, 8.7, 15.1, 20.6, 21.5, 19.3),
        // 60 phon
        doubleArrayOf(102.0, 94.8, 87.5, 80.8, 74.2, 68.1, 62.3, 56.7, 51.3, 46.4,
            42.2, 38.4, 35.1, 32.2, 29.7, 27.8, 26.0, 25.8, 25.2, 22.1,
            17.7, 14.0, 10.5, 10.0, 12.6, 18.6, 23.8, 24.5, 22.1),
        // 70 phon
        doubleArrayOf(107.1, 100.3, 93.5, 87.2, 81.0, 75.2, 69.7, 64.3, 58.9, 53.9,
            49.6, 45.8, 42.4, 39.4, 36.9, 34.9, 33.1, 32.5, 31.5, 28.2,
            23.6, 19.6, 15.8, 14.8, 16.9, 22.3, 27.1, 27.7, 25.1),
        // 80 phon
        doubleArrayOf(112.2, 105.8, 99.3, 93.4, 87.7, 82.3, 77.0, 71.8, 66.5, 61.5,
            57.2, 53.2, 49.8, 46.8, 44.2, 42.1, 40.2, 39.3, 38.0, 34.6,
            29.7, 25.4, 21.3, 19.8, 21.3, 26.2, 30.6, 31.0, 28.3),
        // 90 phon
        doubleArrayOf(117.3, 111.3, 105.2, 99.6, 94.3, 89.3, 84.3, 79.4, 74.2, 69.2,
            64.8, 60.8, 57.3, 54.3, 51.7, 49.5, 47.5, 46.3, 44.7, 41.2,
            36.1, 31.5, 27.0, 25.1, 26.1, 30.3, 34.3, 34.4, 31.6)
    )

    /** Phon levels corresponding to CONTOURS_DB rows. */
    private val PHON_LEVELS = intArrayOf(20, 30, 40, 50, 60, 70, 80, 90)

    /**
     * Compute the SPL at each ISO 226 frequency for a given phon level
     * (loudness level in phons). Linear interpolation between table rows.
     */
    fun contourAtPhon(phon: Double): DoubleArray {
        val clamped = phon.coerceIn(20.0, 90.0)
        val out = DoubleArray(FREQUENCIES_HZ.size)

        // Find the two bracketing rows
        var lo = 0
        for (i in PHON_LEVELS.indices) {
            if (PHON_LEVELS[i] <= clamped) lo = i
        }
        val hi = min(lo + 1, PHON_LEVELS.lastIndex)

        if (lo == hi) {
            for (j in out.indices) out[j] = CONTOURS_DB[lo][j]
            return out
        }

        val frac = (clamped - PHON_LEVELS[lo]) / (PHON_LEVELS[hi] - PHON_LEVELS[lo])
        for (j in out.indices) {
            out[j] = CONTOURS_DB[lo][j] + frac * (CONTOURS_DB[hi][j] - CONTOURS_DB[lo][j])
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
