package tv.corebuilds.eq.apply

import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.export.Profile
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Headroom-shifted correction sampled at one concrete engine's reported/configured bands. */
data class BackendBandFit(
    val centresHz: List<Double>,
    /** Requested room + manual + runtime response before the shared headroom offset. */
    val requestedDb: DoubleArray,
    val headroomDb: Double,
    /** Relative target after taking headroom, before a backend's range/step limits. */
    val targetDb: DoubleArray,
    /** Values made representable by the supplied backend range (if one is reported). */
    val configuredDb: DoubleArray,
    val rangeLimitedBands: Int
) {
    /** Compare the requested centre gains with what the backend says it retained. */
    fun report(readBackDb: DoubleArray = configuredDb): BackendFitReport {
        require(readBackDb.size == targetDb.size) { "Read-back band count differs from the fit" }
        require(readBackDb.all { it.isFinite() }) { "Read-back gains must be finite" }
        val errors = DoubleArray(readBackDb.size) { abs(readBackDb[it] - targetDb[it]) }
        val maxError = errors.maxOrNull() ?: 0.0
        val rmsError = if (errors.isEmpty()) 0.0 else sqrt(errors.sumOf { it * it } / errors.size)
        return BackendFitReport(
            bandCount = centresHz.size,
            headroomDb = headroomDb,
            rangeLimitedBands = rangeLimitedBands,
            rmsCentreGainResidualDb = rmsError,
            maxCentreGainResidualDb = maxError
        )
    }
}

/** A configuration/read-back diagnostic; it is not a measured acoustic response. */
data class BackendFitReport(
    val bandCount: Int,
    val headroomDb: Double,
    val rangeLimitedBands: Int,
    val rmsCentreGainResidualDb: Double,
    val maxCentreGainResidualDb: Double
) {
    fun statusSummary(): String = buildString {
        append("backend centre-gain fit · ").append(bandCount).append(" bands")
        append(" · ").append(String.format(Locale.US, "%.1f", headroomDb)).append(" dB headroom")
        append(" · read-back residual RMS/max ")
            .append(String.format(Locale.US, "%.2f/%.2f", rmsCentreGainResidualDb, maxCentreGainResidualDb)).append(" dB")
        if (rangeLimitedBands > 0) append(" · ").append(rangeLimitedBands).append(" range-limited")
    }
}

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

    /**
     * Fit the requested response to the actual centres and optional gain range
     * reported by one backend. Android's Equalizer supplies a range; the
     * DynamicsProcessing API does not, so its configured gains are read back
     * and compared after application instead.
     *
     * The residual is only a centre-gain/configuration residual. It says
     * nothing about a vendor filter's response between bands or the acoustic
     * response at the speakers.
     */
    fun fitToBackend(
        profile: Profile,
        centresHz: List<Double>,
        layers: ToneLayers = ToneLayers.NONE,
        minGainDb: Double? = null,
        maxGainDb: Double? = null
    ): BackendBandFit {
        require(centresHz.isNotEmpty() && centresHz.all { it.isFinite() && it > 0.0 }) {
            "Backend band centres must be non-empty, finite and positive"
        }
        require(centresHz.zipWithNext().all { (a, b) -> b > a }) {
            "Backend band centres must be strictly ascending"
        }
        require((minGainDb == null) == (maxGainDb == null)) {
            "Both backend gain limits must be reported together"
        }
        if (minGainDb != null && maxGainDb != null) {
            require(minGainDb.isFinite() && maxGainDb.isFinite() && minGainDb <= maxGainDb) {
                "Backend gain range is invalid"
            }
        }
        val requested = requestedGainsDb(profile, centresHz, layers)
        require(requested.all { it.isFinite() }) { "Requested centre gains must be finite" }
        val headroom = max(0.0, requested.maxOrNull() ?: 0.0)
        val target = DoubleArray(requested.size) { requested[it] - headroom }
        var limited = 0
        val configured = DoubleArray(target.size) { index ->
            val value = target[index]
            if (minGainDb == null || maxGainDb == null) value else {
                if (value < minGainDb || value > maxGainDb) limited++
                value.coerceIn(minGainDb, maxGainDb)
            }
        }
        return BackendBandFit(
            centresHz = centresHz.toList(),
            requestedDb = requested,
            headroomDb = headroom,
            targetDb = target,
            configuredDb = configured,
            rangeLimitedBands = limited
        )
    }

    /** Per-band gain in dB, headroom-shifted so nothing is ever boosted above 0. */
    fun gainsDb(
        profile: Profile,
        centresHz: List<Double>,
        layers: ToneLayers = ToneLayers.NONE
    ): DoubleArray = fitToBackend(profile, centresHz, layers).targetDb

    /** The dB gains as millibel band levels, clamped to the device's reported range. */
    fun millibels(gainsDb: DoubleArray, rangeMb: IntRange): ShortArray =
        ShortArray(gainsDb.size) {
            (gainsDb[it] * 100.0).roundToInt().coerceIn(rangeMb.first, rangeMb.last).toShort()
        }
}
