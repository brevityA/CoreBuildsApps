package tv.corebuilds.eq.ui

import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.RoomAwareGuidance
import java.util.Locale

/**
 * The one line under the manual bands that names the selected band (1.12.0).
 *
 * It used to print the screen-reader sentence ("Manual equaliser. Band 5 of 10, 500
 * hertz, +0.0 decibels. Use left and right…") because the view's content description
 * was the only text the screen was handed. That sentence stays for TalkBack; this is
 * the short, visible version, and it says what the room correction is already doing
 * at the band, which is what a person moving it needs to know.
 */
object ManualEqReadout {

    /**
     * @param bandIndex the selected band
     * @param bandGainDb the band's own slider value
     * @param correctionDb the measured correction at the band centre, or null for a
     *   profile with no room measurement
     * @param togetherDb correction plus the whole manual layer's response at the centre
     *   (neighbouring bands overlap, so this is not simply the sum of the two above)
     */
    fun line(bandIndex: Int, bandGainDb: Double, correctionDb: Double?, togetherDb: Double?): String {
        val fc = ManualEq.BAND_CENTRES_HZ.getOrNull(bandIndex) ?: return ""
        val band = "${RoomAwareGuidance.hz(fc)} · band ${signed(bandGainDb)}"
        if (correctionDb == null) return "$band · no room measurement"
        val together = togetherDb?.let { " · together ${signed(it)}" }.orEmpty()
        return "$band · room correction ${signed(correctionDb)}$together"
    }

    /**
     * What plays at [hz]: the measured [correctionDb] plus the whole manual layer's
     * response there, not just the selected band's slider, because neighbouring bands
     * overlap. Null without a room measurement.
     */
    fun together(correctionDb: Double?, manualFilters: List<tv.corebuilds.eq.dsp.PeakingFilter>, hz: Double): Double? =
        correctionDb?.let { it + ManualEq.responseDb(manualFilters, hz) }

    private fun signed(value: Double): String {
        // -0.04 dB would print as "-0.0"; a band that is flat reads +0.0.
        val shown = if (kotlin.math.abs(value) < 0.05) 0.0 else value
        return String.format(Locale.US, "%+.1f dB", shown)
    }
}
