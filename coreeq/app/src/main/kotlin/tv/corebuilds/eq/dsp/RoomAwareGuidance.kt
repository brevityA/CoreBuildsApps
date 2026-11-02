package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile

/**
 * Room-aware EQ guidance (1.11.0).
 *
 * A measured correction tells the story of the room: where it boosts, the
 * room is weak; where it cuts, the room is strong; where it is zero, the
 * room is either fine or uncorrectable (a null, a non-minimum-phase band,
 * past the measurement edge). This object reads a saved [Profile] and
 * suggests manual-EQ trims the viewer might want, or warnings about bands
 * to leave alone.
 *
 * Pure Kotlin, unit-tested without Android.
 */
object RoomAwareGuidance {

    /** One piece of guidance about a specific band. */
    data class Guidance(
        val fc: Double,
        val kind: Kind,
        val message: String,
        val icon: String
    ) {
        enum class Kind { SUGGEST_CUT, SUGGEST_BOOST, WARN_AVOID, INFO }
    }

    /** Bands the manual editor can touch, Hz. */
    private val MANUAL_BANDS = ManualEq.BAND_CENTRES_HZ

    /**
     * Read a profile and return guidance, most important first.
     *
     * @param profile the saved profile, which carries the measured curve,
     *   nulls, phase gates and transition frequency
     * @param manualFilters the manual filters already applied, so we do not
     *   suggest duplicating them
     */
    fun guide(
        profile: Profile,
        manualFilters: List<PeakingFilter>
    ): List<Guidance> {
        val curve = profile.curve
        if (curve.isEmpty()) return emptyList()

        val manualByCentre = manualFilters.associateBy { it.fc }
        val guidance = mutableListOf<Guidance>()

        // Interpolate the correction at each manual band centre.
        for (fc in MANUAL_BANDS) {
            val db = correctionAt(curve, fc)
            if (manualByCentre.containsKey(fc)) continue

            // Below the transition the room is modal: corrections there are
            // the least trusted, so warn rather than suggest.
            if (fc < profile.transitionHz) {
                if (db > 3.0) {
                    guidance += Guidance(
                        fc, Guidance.Kind.WARN_AVOID,
                        "Room dip at ${fc.toInt()} Hz is in the modal band — " +
                            "boosting here may not hold across seats",
                        "⚠️"
                    )
                } else if (db < -3.0) {
                    guidance += Guidance(
                        fc, Guidance.Kind.WARN_AVOID,
                        "Room peak at ${fc.toInt()} Hz is in the modal band — " +
                            "cutting here may move with listener position",
                        "⚠️"
                    )
                }
                continue
            }

            // Above the transition, large corrections are the room's tonal
            // signature. Suggest a manual trim if the viewer wants to adjust.
            if (db > 4.0) {
                guidance += Guidance(
                    fc, Guidance.Kind.SUGGEST_BOOST,
                    "Correction lifts ${db.toInt()} dB at ${fc.toInt()} Hz — " +
                        "the room is weak there; a manual boost can reinforce it",
                    "➕"
                )
            } else if (db < -4.0) {
                guidance += Guidance(
                    fc, Guidance.Kind.SUGGEST_CUT,
                    "Correction cuts ${(-db).toInt()} dB at ${fc.toInt()} Hz — " +
                        "the room is strong there; a manual trim can soften it",
                    "➖"
                )
            }
        }

        // Transition frequency context.
        if (profile.transitionHz > DspConstants.DEFAULT_TRANSITION_HZ) {
            guidance += Guidance(
                0.0, Guidance.Kind.INFO,
                "Full correction stops at ${profile.transitionHz.toInt()} Hz — " +
                    "bands below are gently shaped only",
                "ℹ️"
            )
        }

        return guidance.sortedByDescending { kindWeight(it.kind) }
    }

    private fun correctionAt(curve: List<CurvePoint>, hz: Double): Double {
        if (hz < curve.first().hz || hz > curve.last().hz) return 0.0
        for (i in 0 until curve.size - 1) {
            val a = curve[i]
            val b = curve[i + 1]
            if (hz in a.hz..b.hz) {
                val t = kotlin.math.ln(hz / a.hz) / kotlin.math.ln(b.hz / a.hz)
                return a.correctionDb + t * (b.correctionDb - a.correctionDb)
            }
        }
        return curve.last().correctionDb
    }

    private fun kindWeight(kind: Guidance.Kind): Int = when (kind) {
        Guidance.Kind.SUGGEST_CUT, Guidance.Kind.SUGGEST_BOOST -> 2
        Guidance.Kind.WARN_AVOID -> 1
        Guidance.Kind.INFO -> 0
    }
}
