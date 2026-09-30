package tv.corebuilds.eq.apply

import tv.corebuilds.eq.export.Profile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The one place the correction curve becomes engine bands.
 *
 * [gainsDb] samples the profile's curve at an engine's own band centres and
 * shifts every band down by the largest boost, so the largest boost lands on
 * 0 dB: an engine with no preamp (the platform [android.media.audiofx.Equalizer],
 * and DynamicsProcessing without an input-gain stage) cannot clip the signal.
 * [millibels] is what the platform Equalizer wants; DynamicsProcessing takes
 * the dB values directly.
 *
 * `EqService`, the TV-settings export and the DynamicsProcessing path all
 * sample through here so the preview, the export and the applied bands cannot
 * drift apart. Pinned by `DpMappingTest`.
 */
object BandMapping {

    /** Per-band gain in dB, headroom-shifted so nothing is ever boosted above 0. */
    fun gainsDb(profile: Profile, centresHz: List<Double>): DoubleArray {
        val raw = DoubleArray(centresHz.size) { profile.correctionAt(centresHz[it]) }
        val headroom = max(0.0, raw.maxOrNull() ?: 0.0)
        return DoubleArray(raw.size) { raw[it] - headroom }
    }

    /** The dB gains as millibel band levels, clamped to the device's reported range. */
    fun millibels(gainsDb: DoubleArray, rangeMb: IntRange): ShortArray =
        ShortArray(gainsDb.size) {
            (gainsDb[it] * 100.0).roundToInt().coerceIn(rangeMb.first, rangeMb.last).toShort()
        }
}
