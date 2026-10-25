package tv.corebuilds.eq

/**
 * The hard limits every correction Core EQ hands back must respect.
 *
 * These mirror `tools/core_eq_dsp.py` (F_MIN, F_MAX, MAX_BOOST_DB, MAX_CUT_DB,
 * CEILING_CHOICES_HZ), the reference the Kotlin measurement chain has to agree
 * with; the numbers are held to it by `tests/test_core_eq_parity.py`, so they
 * cannot drift apart.
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

    /**
     * Correction ceilings the user may choose, widest last. [MAX_HZ] is the
     * trusted default; every other value narrows the band.
     */
    val CEILING_CHOICES_HZ = doubleArrayOf(500.0, 1000.0, 2000.0, 4000.0, MAX_HZ)

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

    /**
     * A user-chosen correction ceiling, made safe: never above [MAX_HZ], never
     * below [MIN_HZ], and NaN/infinite requests fall back to [MAX_HZ].
     *
     * Narrowing is the only direction the safety contract allows. The ceiling
     * exists because a TV remote's measurement is least trustworthy in the
     * treble and a room's correction above a few kHz is mostly taste; but a
     * value beyond 8 kHz would claim accuracy the microphone does not have.
     */
    fun ceilingHz(requested: Double?): Double {
        if (requested == null || !requested.isFinite()) return MAX_HZ
        return requested.coerceIn(MIN_HZ, MAX_HZ)
    }

    /**
     * The ceiling actually applied: the narrower of the measured bandwidth
     * edge and the user's choice. [measuredMaxHz] comes from the sweep (where
     * the remote stopped hearing); it can only narrow the result.
     */
    fun effectiveCeilingHz(measuredMaxHz: Double, requested: Double?): Double {
        val measured = if (measuredMaxHz.isFinite() && measuredMaxHz > 0.0) measuredMaxHz else MAX_HZ
        return minOf(ceilingHz(measured), ceilingHz(requested))
    }

    /**
     * True when [ceiling] leaves the whole trusted band correctable. The
     * measure screen labels that choice "full band"; the label and the
     * analysis must agree, so the test lives here rather than in the Activity.
     */
    fun isFullBand(ceiling: Double): Boolean = ceilingHz(ceiling) >= MAX_HZ

    /**
     * One selected ceiling out of [CEILING_CHOICES_HZ].
     *
     * This exists so the measure screen does not own index arithmetic: the
     * button's state is a [CeilingChoice], every index it can hold is valid,
     * and wrapping around the list is [next]. Index handling in an Activity is
     * the kind of code that cannot be unit-tested here and that goes wrong
     * silently — an off-by-one selects 500 Hz when the user meant 1 kHz.
     */
    class CeilingChoice(index: Int) {
        /** Always a valid index into [CEILING_CHOICES_HZ]. */
        val index: Int = if (index in CEILING_CHOICES_HZ.indices) index else CEILING_CHOICES_HZ.lastIndex

        /** The top of the corrected band this choice asks for, already clamped. */
        val hz: Double = ceilingHz(CEILING_CHOICES_HZ[this.index])

        /** True when this choice leaves the whole trusted band in play. */
        val isFullBand: Boolean = CorrectionLimits.isFullBand(hz)

        /** The next choice, wrapping from the widest back to the narrowest. */
        fun next(): CeilingChoice = CeilingChoice((this.index + 1) % CEILING_CHOICES_HZ.size)

        override fun toString(): String = "ceiling ${hz.toInt()} Hz"

        companion object {
            /** The widest choice: what a fresh install and an unknown pref mean. */
            val DEFAULT = CeilingChoice(CEILING_CHOICES_HZ.lastIndex)
        }
    }

    /** True when [hz] is corrected under [ceiling]: inside the trusted band and at or below the ceiling. */
    fun isCorrectableAt(hz: Double, ceiling: Double): Boolean =
        isCorrectable(hz) && hz <= ceilingHz(ceiling)
}
