package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.export.Profile
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Room hints for the manual editor (1.12.0: rules rewritten, first shown on screen).
 *
 * The measured correction already does the room's work: where it cuts, the room is
 * loud there; where it lifts, the room is weak. Adding the same move by hand doubles
 * it, so no hint here ever asks for that. What a person tunes by ear is taste on top
 * of a correct curve, and the hints point that way:
 *
 * - **Stacked** — a manual band of 1 dB or more that pushes the same way as the
 *   correction at its centre, so the two together move it [STACKED_TOTAL_DB] or more.
 *   That is more than the editor's own ±6 dB range allows, so the two layers are doing
 *   what neither may do alone: a stacked lift spends headroom the rest of the EQ needs,
 *   and a stacked cut takes out more than the room asked for. Applying it sets the band
 *   back to 0 dB.
 * - **Ease** — at or above the room's transition, where the correction may only shape
 *   (±[DspConstants.MAX_SHAPING_DB] dB from twice the transition up) and a move of
 *   [EASE_THRESHOLD_DB] or more is the correction working near its limit, offer a
 *   trim of the opposite sign worth about a third of it (1–2 dB on the editor's 0.5 dB
 *   grid). A correction can be right and still sound thin or forward to one listener;
 *   this is the band their ear is most likely to want some of back. At most
 *   [MAX_EASE_HINTS], the largest first.
 * - **Modal** — below the transition, a correction of more than [MODAL_THRESHOLD_DB] is
 *   working on a room mode, which moves from seat to seat. The measurement knows where
 *   it is; a hand-set band does not. One caution row for all of them, never an action.
 *
 * A band that already carries a manual trim gets no Ease or Modal hint: the person
 * has already decided about it. Pure Kotlin, unit-tested without Android.
 */
object RoomAwareGuidance {

    /** One hint: what it says, and the band gain it sets when applied, if any. */
    data class Guidance(
        val kind: Kind,
        /** The manual band this hint is about; null for the grouped modal row. */
        val bandIndex: Int?,
        val title: String,
        val detail: String,
        /** The gain to give [bandIndex] when the hint is applied; null for advice only. */
        val applyGainDb: Double? = null
    ) {
        enum class Kind { STACKED, EASE_CUT, EASE_LIFT, MODAL }

        val actionable: Boolean get() = bandIndex != null && applyGainDb != null
    }

    /** Above the transition, a correction this large is near the ±3 dB shaping limit. */
    const val EASE_THRESHOLD_DB = 2.5

    /** Below the transition, a correction this large is working on a room mode. */
    const val MODAL_THRESHOLD_DB = 3.0

    /** A manual trim and the correction moving one band this far together are stacked. */
    const val STACKED_TOTAL_DB = 8.0

    /** Smaller manual trims are not worth undoing, whatever sits under them. */
    const val STACKED_MIN_MANUAL_DB = 1.0

    /**
     * A filter off the band centres (kept from an imported preset or profile) counts at
     * a band once it moves that band's centre this much: a band it shapes is a band the
     * person has already set, and an ease or modal hint there would add a second trim.
     */
    const val OFF_CENTRE_REACH_DB = 0.5

    const val MAX_EASE_HINTS = 3
    private const val EASE_FRACTION = 1.0 / 3.0
    private const val EASE_MIN_DB = 1.0
    private const val EASE_MAX_DB = 2.0

    /**
     * The hints for [profile]'s measured correction under [manualFilters] (the mode
     * being edited), most pressing first: stacked bands, then ease, then the modal
     * caution. Empty for a manual-only profile or one with no measured curve.
     */
    /**
     * The manual layer at band [index]: the band's own slider, plus what filters off
     * the band centres do at its centre when that reaches [OFF_CENTRE_REACH_DB].
     * Filters on other band centres are left out: their overlap is the graphic EQ
     * working as drawn, not a setting made at this band.
     */
    internal fun manualAt(manualFilters: List<PeakingFilter>, index: Int, fc: Double): Double {
        val own = ManualEq.gainAtBand(manualFilters, index)
        val offCentre = manualFilters.filter { filter -> ManualEq.BAND_CENTRES_HZ.none { abs(filter.fc - it) < 0.01 } }
        if (offCentre.isEmpty()) return own
        val reach = ManualEq.responseDb(offCentre, fc)
        return if (abs(reach) >= OFF_CENTRE_REACH_DB) own + reach else own
    }

    fun guide(profile: Profile, manualFilters: List<PeakingFilter>): List<Guidance> {
        if (profile.manualOnly || profile.curve.isEmpty()) return emptyList()
        val stacked = mutableListOf<Pair<Double, Guidance>>()
        val ease = mutableListOf<Pair<Double, Guidance>>()
        val modal = mutableListOf<Pair<Double, Double>>()

        ManualEq.BAND_CENTRES_HZ.forEachIndexed { index, fc ->
            val correction = profile.correctionAt(fc)
            val manual = manualAt(manualFilters, index, fc)
            when {
                manual != 0.0 -> {
                    val total = manual + correction
                    val sameWay = (manual > 0.0 && correction > 0.0) || (manual < 0.0 && correction < 0.0)
                    if (sameWay && abs(manual) >= STACKED_MIN_MANUAL_DB && abs(total) >= STACKED_TOTAL_DB) {
                        stacked += abs(total) to stackedHint(index, fc, manual, correction, total)
                    }
                }
                fc < profile.transitionHz -> {
                    if (abs(correction) > MODAL_THRESHOLD_DB) modal += fc to correction
                }
                abs(correction) >= EASE_THRESHOLD_DB -> {
                    ease += abs(correction) to easeHint(index, fc, correction)
                }
            }
        }

        return buildList {
            addAll(stacked.sortedByDescending { it.first }.map { it.second })
            addAll(ease.sortedByDescending { it.first }.take(MAX_EASE_HINTS).map { it.second })
            if (modal.isNotEmpty()) add(modalHint(modal, profile.transitionHz))
        }
    }

    /** The trim that eases [correctionDb]: opposite sign, about a third, 1–2 dB on a 0.5 dB grid. */
    fun easeDb(correctionDb: Double): Double {
        val size = (round(abs(correctionDb) * EASE_FRACTION / ManualEq.STEP_DB) * ManualEq.STEP_DB)
            .coerceIn(EASE_MIN_DB, EASE_MAX_DB)
        return if (correctionDb < 0.0) size else -size
    }

    private fun stackedHint(index: Int, fc: Double, manual: Double, correction: Double, total: Double): Guidance {
        val lift = correction > 0.0
        val detail = if (lift) {
            "The correction already lifts ${db(abs(correction))} here; with yours that is " +
                "${signed(total)}, which spends headroom the rest of the EQ needs."
        } else {
            "The correction already cuts ${db(abs(correction))} here; with yours that is " +
                "${signed(total)}, more than the room asked for."
        }
        return Guidance(
            Guidance.Kind.STACKED, index,
            "Undo your ${signed(manual)} at ${hz(fc)}",
            detail,
            applyGainDb = 0.0
        )
    }

    private fun easeHint(index: Int, fc: Double, correction: Double): Guidance {
        val trim = easeDb(correction)
        return if (correction < 0.0) {
            Guidance(
                Guidance.Kind.EASE_CUT, index,
                "Ease the cut at ${hz(fc)}: ${signed(trim)}",
                "The correction cuts ${db(abs(correction))} here, where the room is loud. If this " +
                    "range sounds thin or distant to you, this gives some of it back.",
                applyGainDb = trim
            )
        } else {
            Guidance(
                Guidance.Kind.EASE_LIFT, index,
                "Ease the lift at ${hz(fc)}: ${signed(trim)}",
                "The correction lifts ${db(correction)} here, where the room is weak. If this " +
                    "range sounds forward or harsh to you, this takes some of it off.",
                applyGainDb = trim
            )
        }
    }

    private fun modalHint(bands: List<Pair<Double, Double>>, transitionHz: Double): Guidance {
        val names = bands.map { hz(it.first) }
        val list = if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
        val moves = bands.joinToString(", ") { (fc, correction) -> "${hz(fc)} ${signed(correction)}" }
        return Guidance(
            Guidance.Kind.MODAL, null,
            "Leave $list to the correction",
            "Below this room's ${hz(transitionHz)} transition the correction is working on room " +
                "modes ($moves). A mode moves from seat to seat; the measurement knows where it " +
                "is, a hand-set band does not."
        )
    }

    /** Band centres as the editor labels them: whole Hz below 1 kHz, kHz above. */
    fun hz(value: Double): String = when {
        value < 1_000.0 -> String.format(Locale.US, "%.0f Hz", value)
        value % 1_000.0 == 0.0 -> String.format(Locale.US, "%.0f kHz", value / 1_000.0)
        else -> String.format(Locale.US, "%.1f kHz", value / 1_000.0)
    }

    private fun db(value: Double): String = String.format(Locale.US, "%.1f dB", value)

    private fun signed(value: Double): String = String.format(Locale.US, "%+.1f dB", value)
}
