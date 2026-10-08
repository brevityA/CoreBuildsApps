package tv.corebuilds.eq.apply

import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.export.Profile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The one place the correction curve becomes engine bands.
 *
 * [gainsDb] samples the measured correction plus manual filters at an engine's
 * own band centres, then shifts every band down by the largest boost. The
 * largest boost lands on 0 dB, so an engine with no preamp (the platform
 * [android.media.audiofx.Equalizer], and DynamicsProcessing without an input-gain
 * stage) retains the required digital headroom.
 * [millibels] is what the platform Equalizer wants; DynamicsProcessing takes
 * the dB values directly.
 *
 * `EqService`, the TV-settings export and the DynamicsProcessing path all
 * sample through here so the preview, the export and the applied bands cannot
 * drift apart. Pinned by `DpMappingTest`.
 *
 * Runtime [ToneLayers] (dialogue boost, low-volume bass) join the requested
 * response here, before headroom is taken, so their boost can never clip.
 * Exports pass none: a profile file carries the room and the user's tone bands.
 */
object BandMapping {

    /**
     * Requested response sampled at the actual engine centres: measured room
     * correction plus the user's independent manual EQ layer.
     */
    fun requestedGainsDb(
        profile: Profile,
        centresHz: List<Double>,
        layers: ToneLayers = ToneLayers.NONE
    ): DoubleArray =
        DoubleArray(centresHz.size) { hzIndex ->
            val hz = centresHz[hzIndex]
            profile.correctionAt(hz) + ManualEq.responseDb(profile.manualFilters, hz) +
                layers.responseDb(profile, hz)
        }

    /** The positive peak which must be offset to keep an engine without preamp headroom-safe. */
    fun headroomDb(
        profile: Profile,
        centresHz: List<Double>,
        layers: ToneLayers = ToneLayers.NONE
    ): Double = max(0.0, requestedGainsDb(profile, centresHz, layers).maxOrNull() ?: 0.0)

    /** Per-band gain in dB, headroom-shifted so nothing is ever boosted above 0. */
    fun gainsDb(
        profile: Profile,
        centresHz: List<Double>,
        layers: ToneLayers = ToneLayers.NONE
    ): DoubleArray {
        val raw = requestedGainsDb(profile, centresHz, layers)
        val headroom = max(0.0, raw.maxOrNull() ?: 0.0)
        return DoubleArray(raw.size) { raw[it] - headroom }
    }

    /** The dB gains as millibel band levels, clamped to the device's reported range. */
    fun millibels(gainsDb: DoubleArray, rangeMb: IntRange): ShortArray =
        ShortArray(gainsDb.size) {
            (gainsDb[it] * 100.0).roundToInt().coerceIn(rangeMb.first, rangeMb.last).toShort()
        }
}
