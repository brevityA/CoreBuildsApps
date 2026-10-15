package tv.corebuilds.eq.export

import tv.corebuilds.eq.dsp.CustomTarget
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.dsp.TargetCurveImport
import tv.corebuilds.eq.dsp.Targets

data class PlatformBand(
    val centerHz: Double,
    val millibels: Int
)

data class CurvePoint(
    val hz: Double,
    val measuredDb: Double,
    val correctionDb: Double
)

/** Shape-only difference after removing the repeat sweep's broadband level offset. */
data class RepeatabilityMetrics(
    val comparedBands: Int,
    val lowFrequencyHz: Double,
    val highFrequencyHz: Double,
    /** dB added to the repeat trace to align it to the reference over 500 Hz–2 kHz. */
    val levelOffsetDb: Double,
    val medianAbsDifferenceDb: Double,
    val rmsDifferenceDb: Double,
    val p95AbsDifferenceDb: Double
)

/** The second trace and its provenance; metrics may be absent when paths/bands were not comparable. */
data class RepeatabilityCheck(
    val metrics: RepeatabilityMetrics?,
    val comparisonCurve: List<CurvePoint>,
    val captureInfo: CaptureInfo? = null,
    val outputKind: String? = null,
    val outputName: String? = null,
    val snrDb: Double? = null,
    val driftPpm: Double? = null,
    val note: String? = null
)

/** A microphone response point reported by Android; it is metadata, not a correction applied by Core EQ. */
data class MicrophoneResponsePoint(
    val microphone: String,
    val frequencyHz: Double,
    val responseDb: Double
)

/**
 * What Android exposed about the actual input path for a sweep. Device support
 * varies; missing fields mean "not reported", not "none". Microphone response
 * points are preserved for audit/research and are explicitly not applied to
 * the room curve. The input rate is the rate Android reports for the
 * AudioRecord stream; it is not a measurement of the microphone ADC clock.
 */
data class CaptureInfo(
    val requestedSampleRateHz: Int? = null,
    val inputSampleRateHz: Int? = null,
    val analysisSampleRateHz: Int? = null,
    val audioSource: String? = null,
    val inputDeviceName: String? = null,
    val inputDeviceType: String? = null,
    val activeMicrophoneCount: Int? = null,
    /** Manufacturer/model of the device running AudioRecord; distinct from the TV profile owner. */
    val captureDeviceModel: String? = null,
    val captureOsVersion: String? = null,
    val microphoneDescriptions: List<String> = emptyList(),
    val processing: List<String> = emptyList(),
    val unprocessedAudioSourceSupported: Boolean? = null,
    val microphoneResponse: List<MicrophoneResponsePoint> = emptyList()
) {
    /** A concise line for the Measure result and support report. */
    fun summary(): String {
        val input = inputDeviceName?.takeIf { it.isNotBlank() }
            ?: inputDeviceType?.takeIf { it.isNotBlank() }
            ?: "input not named by Android"
        val rates = when {
            inputSampleRateHz == null && analysisSampleRateHz == null -> "sample rate not reported"
            inputSampleRateHz == analysisSampleRateHz -> "${inputSampleRateHz} Hz"
            else -> "${inputSampleRateHz ?: "?"} Hz input → ${analysisSampleRateHz ?: "?"} Hz analysis"
        }
        val source = audioSource?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val recorder = captureDeviceModel?.takeIf { it.isNotBlank() }?.let { " · recorder $it" }.orEmpty()
        val mic = activeMicrophoneCount?.let { " · $it active mic${if (it == 1) "" else "s"}" }.orEmpty()
        val unprocessed = when (unprocessedAudioSourceSupported) {
            true -> " · Android reports UNPROCESSED available"
            false -> " · Android reports UNPROCESSED unavailable"
            null -> " · UNPROCESSED availability not reported"
        }
        val effectState = processing.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?.take(110)
            ?.let { " · $it" }.orEmpty()
        val calibration = if (microphoneResponse.isNotEmpty()) {
            " · Android reported ${microphoneResponse.size} mic-response points (stored, not applied)"
        } else ""
        return "Capture: $input · $rates$source$recorder$mic$unprocessed$effectState$calibration"
    }

    /** Short, explicit provenance comments carried with text exports. */
    fun exportNotes(): List<String> = buildList {
        val rates = when {
            inputSampleRateHz == null && analysisSampleRateHz == null -> "sample rate not reported"
            else -> "${inputSampleRateHz ?: "unknown"} Hz input; ${analysisSampleRateHz ?: "unknown"} Hz analysis"
        }
        val route = listOfNotNull(inputDeviceName?.takeIf { it.isNotBlank() }, inputDeviceType?.takeIf { it.isNotBlank() })
            .distinct().joinToString(" / ").ifBlank { "input route not reported" }
        val source = audioSource?.takeIf { it.isNotBlank() } ?: "audio source not reported"
        add("Capture path: $source; $route; $rates.")
        captureDeviceModel?.takeIf { it.isNotBlank() }?.let { model ->
            val os = captureOsVersion?.takeIf { it.isNotBlank() }?.let { " · Android $it" }.orEmpty()
            add("Recording device: $model$os.")
        }
        if (microphoneResponse.isNotEmpty()) {
            add("Android reported ${microphoneResponse.size} microphone frequency-response points; metadata only, not applied to the correction.")
        }
        processing.takeIf { it.isNotEmpty() }?.let { add("Capture processing: ${it.joinToString("; ")}.") }
    }
}

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
    /** Capture provenance from Android, absent on old profiles, REW imports and manual-only profiles. */
    val captureInfo: CaptureInfo? = null,
    /** Optional second same-position capture; curves are retained separately and never averaged. */
    val repeatabilityCheck: RepeatabilityCheck? = null,
    val deviceName: String = "Android TV",
    val stimulus: String = "sweep_10s",
    val captureSeconds: Double = 10.0,
    val volumeM3: Double? = null,
    val rt60Seconds: Double? = null,
    val schroederHz: Double? = null,
    val transitionHz: Double = 300.0,
    val rolloffHz: Double = 40.0,
    val snrDb: Double? = null,
    /**
     * MeasurementQuality score at save time; null for imports and older profiles.
     * 1.3.0-1.3.1 saved the room-weighted score here, 1.3.2+ the recording score.
     * The UI recomputes the recording score from [snrDb] and [rt60Seconds]
     * (QualityText.scoreOf), so this field is kept for backups, not read for display.
     */
    val qualityScore: Int? = null,
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
    val manualOnly: Boolean = false,
    /**
     * The imported curve (Hz, dB) a [CustomTarget.KEY] profile was measured
     * against, copied at save time. Built-in targets are rebuilt from their
     * key; an imported one cannot be, because the next import replaces it.
     */
    val targetPoints: List<Pair<Double, Double>> = emptyList()
) {
    /**
     * The target this profile was corrected towards, on [freqs]. Null for an
     * imported target saved without its curve: drawing whatever is imported now
     * would show a target the correction was never built for.
     */
    fun targetDb(freqs: DoubleArray): DoubleArray? = when {
        target != CustomTarget.KEY -> Targets.targetCurve(target, freqs)
        targetPoints.size >= 2 -> TargetCurveImport.targetDb(targetPoints, freqs)
        else -> null
    }

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
