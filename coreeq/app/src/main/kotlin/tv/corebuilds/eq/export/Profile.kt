package tv.corebuilds.eq.export

import tv.corebuilds.eq.dsp.PeakingFilter

data class PlatformBand(
    val centerHz: Double,
    val millibels: Int
)

data class CurvePoint(
    val hz: Double,
    val measuredDb: Double,
    val correctionDb: Double
)

/**
 * A saved correction. Room facts that were not measured are null, never a
 * plausible-looking default: a profile only claims what its measurement found.
 */
data class Profile(
    val id: String,
    val name: String,
    val timestampMs: Long,
    val target: String,
    val micType: String,
    val deviceName: String = "Android TV",
    val stimulus: String = "sweep_10s",
    val captureSeconds: Double = 10.0,
    val volumeM3: Double? = null,
    val rt60Seconds: Double? = null,
    val schroederHz: Double? = null,
    val transitionHz: Double = 300.0,
    val rolloffHz: Double = 40.0,
    val snrDb: Double? = null,
    val nullsUntouchedHz: List<Double> = emptyList(),
    /** Room-correction-only reserve; Formats recomputes the combined value with manual filters. */
    val preampDb: Double = 0.0,
    val filters: List<PeakingFilter> = emptyList(),
    val platformBands: List<PlatformBand> = emptyList(),
    val curve: List<CurvePoint> = emptyList(),
    val capabilityVerdict: Map<String, String> = emptyMap(),
    /** Measurement limits that must travel with the curve (e.g. phase not analysed on a REW magnitude import). */
    val measurementNotes: List<String> = emptyList(),
    /**
     * The output chain this profile is assigned to (see OutputRoute: "speaker",
     * "hdmi_arc", "bluetooth", …). A TV sweep uses its reported AudioTrack
     * route; an external REW import can only use the current route estimate,
     * which is recorded in [measurementNotes]. Null is reserved for older
     * profiles saved before outputs were recorded; those retain legacy
     * all-output behavior.
     */
    val outputKind: String? = null,
    /** What that output called itself ("Sonos Beam"), for display. */
    val outputName: String? = null,
    /** User-authored graphic EQ trims layered on top of [curve]. */
    val manualFilters: List<PeakingFilter> = emptyList(),
    /** True only for a manual profile created without a room measurement. */
    val manualOnly: Boolean = false
) {
    /** Correction in dB at [hz], interpolated on the measured curve; 0 outside it. */
    fun correctionAt(hz: Double): Double {
        if (curve.isEmpty() || hz < curve.first().hz || hz > curve.last().hz) return 0.0
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
}
