package tv.corebuilds.eq

/**
 * The hard limits every correction Core EQ hands back must respect.
 *
 * These mirror `tools/core_eq_dsp.py` (F_MIN, F_MAX, MAX_BOOST_DB, MAX_CUT_DB),
 * the reference the Kotlin measurement chain has to agree with; the numbers are
 * held to it by `tests/test_core_eq_parity.py`, so they cannot drift apart.
 *
 * - A TV remote microphone cannot be trusted below 40 Hz or above 8 kHz
 *   (16 kHz ADPCM caps it at an 8 kHz Nyquist), so nothing outside that band is
 *   ever corrected: its gain is exactly zero, not merely small.
 * - Boosts stop at +6 dB and cuts at -12 dB: a null is interference and cannot
 *   be filled, and a large boost only buys distortion.
 */
object CorrectionLimits {
    const val MIN_HZ = 40.0
    const val MAX_HZ = 8000.0
    const val MAX_BOOST_DB = 6.0
    const val MAX_CUT_DB = 12.0

    /** True when [hz] lies in the band Core EQ is allowed to correct. */
    fun isCorrectable(hz: Double): Boolean = hz in MIN_HZ..MAX_HZ

    /**
     * The gain Core EQ may apply at [hz] for a requested [gainDb]: clamped to
     * +[MAX_BOOST_DB] / -[MAX_CUT_DB], and exactly 0 outside the trusted band.
     */
    fun limit(hz: Double, gainDb: Double): Double {
        if (!isCorrectable(hz) || gainDb.isNaN()) return 0.0
        return gainDb.coerceIn(-MAX_CUT_DB, MAX_BOOST_DB)
    }
}
