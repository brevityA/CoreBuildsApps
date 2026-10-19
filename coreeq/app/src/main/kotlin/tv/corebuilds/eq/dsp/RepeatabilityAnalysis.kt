package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.export.RepeatabilityMetrics
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.sqrt

/** A repeat comparison is deliberately descriptive; it has no unqualified pass/fail threshold. */
data class RepeatabilityComparison(
    val metrics: RepeatabilityMetrics?,
    /** Repeat trace shifted to the reference level on the reference grid; NaN marks unusable bands. */
    val alignedRepeatDb: DoubleArray,
    val reason: String? = null
)

/**
 * Same-position repeat analysis over the measured, SNR-gated band magnitudes.
 * A robust scalar level offset is removed using 500 Hz–2 kHz; no captures are
 * averaged and the result is not a claim about another seat or room.
 */
object RepeatabilityAnalysis {
    const val ALIGN_LOW_HZ = 500.0
    const val ALIGN_HIGH_HZ = 2_000.0
    const val MIN_ALIGNMENT_BANDS = 3
    const val MIN_COMPARISON_BANDS = 8
    private const val MIN_BAND_SNR_DB = SweepAnalysis.BAND_MIN_SNR_DB

    private data class Point(
        val index: Int,
        val hz: Double,
        val referenceDb: Double,
        val repeatDb: Double
    )

    private data class Sample(val db: Double, val snrDb: Double)

    fun compare(reference: SweepResult, repeat: SweepResult): RepeatabilityComparison {
        val aligned = DoubleArray(reference.centresHz.size) { Double.NaN }
        if (!validGrid(reference) || !validGrid(repeat)) {
            return RepeatabilityComparison(null, aligned, "The two captures do not contain valid, ordered measurement bands.")
        }

        val commonLow = maxOf(reference.floorHz, repeat.floorHz)
        val commonHigh = minOf(reference.centresHz.last(), repeat.centresHz.last())
        if (!commonLow.isFinite() || !commonHigh.isFinite() || commonLow >= commonHigh) {
            return RepeatabilityComparison(null, aligned, "The captures have no overlapping trusted frequency range.")
        }

        val points = mutableListOf<Point>()
        for (index in reference.centresHz.indices) {
            val hz = reference.centresHz[index]
            val referenceSnrDb = bandSnr(reference, index)
            if (hz < commonLow || hz > commonHigh || !referenceSnrDb.isFinite() || referenceSnrDb < MIN_BAND_SNR_DB) continue
            val repeatAtFrequency = sampleAt(repeat, hz) ?: continue
            if (repeatAtFrequency.snrDb < MIN_BAND_SNR_DB) continue
            val db = reference.measuredDb[index]
            if (!db.isFinite()) continue
            points += Point(index, hz, db, repeatAtFrequency.db)
        }

        val anchors = points.filter { it.hz in ALIGN_LOW_HZ..ALIGN_HIGH_HZ }
        if (anchors.size < MIN_ALIGNMENT_BANDS) {
            return RepeatabilityComparison(
                null,
                aligned,
                "Fewer than $MIN_ALIGNMENT_BANDS shared, clean bands between 500 Hz and 2 kHz are available for level alignment."
            )
        }

        // Add this robust offset to the repeat so the comparison is about
        // spectral shape, not a small change in capture gain or playback level.
        val levelOffsetDb = median(anchors.map { it.referenceDb - it.repeatDb })
        val differences = DoubleArray(points.size) { index ->
            val point = points[index]
            point.repeatDb + levelOffsetDb - point.referenceDb
        }
        if (differences.size < MIN_COMPARISON_BANDS) {
            return RepeatabilityComparison(
                null,
                aligned,
                "Only ${differences.size} shared bands passed the per-band SNR and range checks; at least $MIN_COMPARISON_BANDS are needed for a summary."
            )
        }

        points.forEach { point ->
            aligned[point.index] = point.repeatDb + levelOffsetDb
        }
        val absolute = differences.map(::abs).sorted()
        val metrics = RepeatabilityMetrics(
            comparedBands = differences.size,
            lowFrequencyHz = points.first().hz,
            highFrequencyHz = points.last().hz,
            levelOffsetDb = levelOffsetDb,
            medianAbsDifferenceDb = median(absolute),
            rmsDifferenceDb = sqrt(differences.sumOf { it * it } / differences.size),
            p95AbsDifferenceDb = absolute[(ceil(0.95 * absolute.size).toInt() - 1).coerceIn(0, absolute.lastIndex)]
        )
        return RepeatabilityComparison(metrics, aligned)
    }

    /** Compare output evidence only when Android reported enough information on both sides. */
    fun reportedOutputMismatch(
        firstKind: String?,
        firstName: String?,
        secondKind: String?,
        secondName: String?
    ): String? {
        val firstReportedKind = firstKind?.trim()?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        val secondReportedKind = secondKind?.trim()?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        if (firstReportedKind != null && secondReportedKind != null &&
            !firstReportedKind.equals(secondReportedKind, ignoreCase = true)
        ) return "Android reported a different TV audio output for the repeat."

        val firstReportedName = firstName?.trim()?.takeIf(String::isNotBlank)
        val secondReportedName = secondName?.trim()?.takeIf(String::isNotBlank)
        if (firstReportedName != null && secondReportedName != null &&
            !firstReportedName.equals(secondReportedName, ignoreCase = true)
        ) return "Android reported a different named TV audio output for the repeat."
        return null
    }

    /** A definite mismatch only when Android reported both sides of a field. */
    fun reportedInputMismatch(first: tv.corebuilds.eq.export.CaptureInfo?, second: tv.corebuilds.eq.export.CaptureInfo?): String? {
        if (first == null || second == null) return null
        fun differs(a: String?, b: String?): Boolean =
            !a.isNullOrBlank() && !b.isNullOrBlank() && !a.trim().equals(b.trim(), ignoreCase = true)
        return when {
            differs(first.captureDeviceModel, second.captureDeviceModel) -> "The recording device changed between sweeps."
            differs(first.inputDeviceType, second.inputDeviceType) -> "Android reported a different microphone input route for the repeat."
            differs(first.inputDeviceName, second.inputDeviceName) -> "Android reported a different microphone input device for the repeat."
            differs(first.audioSource, second.audioSource) -> "Android used a different audio-recording source for the repeat."
            first.inputSampleRateHz != null && second.inputSampleRateHz != null &&
                first.inputSampleRateHz != second.inputSampleRateHz -> "The microphone input sample rate changed between sweeps."
            first.analysisSampleRateHz != null && second.analysisSampleRateHz != null &&
                first.analysisSampleRateHz != second.analysisSampleRateHz -> "The analysis sample rate changed between sweeps."
            else -> null
        }
    }

    private fun validGrid(result: SweepResult): Boolean =
        result.centresHz.isNotEmpty() &&
            result.centresHz.size == result.measuredDb.size &&
            result.centresHz.all { it.isFinite() && it > 0.0 } &&
            result.centresHz.asList().zipWithNext().all { (a, b) -> b > a } &&
            result.floorHz.isFinite() &&
            (result.bandSnrDb.isEmpty() || result.bandSnrDb.size == result.centresHz.size)

    private fun bandSnr(result: SweepResult, index: Int): Double =
        result.bandSnrDb.getOrElse(index) { result.snrDb }

    private fun sampleAt(result: SweepResult, hz: Double): Sample? {
        val centres = result.centresHz
        if (hz < centres.first() || hz > centres.last()) return null
        val exact = centres.binarySearch(hz)
        if (exact >= 0) {
            val db = result.measuredDb[exact]
            val snr = bandSnr(result, exact)
            return if (db.isFinite() && snr.isFinite()) Sample(db, snr) else null
        }
        val upper = -exact - 1
        if (upper <= 0 || upper >= centres.size) return null
        val lower = upper - 1
        val lowSnr = bandSnr(result, lower)
        val highSnr = bandSnr(result, upper)
        val lowDb = result.measuredDb[lower]
        val highDb = result.measuredDb[upper]
        if (!lowDb.isFinite() || !highDb.isFinite() || !lowSnr.isFinite() || !highSnr.isFinite()) return null
        val t = ln(hz / centres[lower]) / ln(centres[upper] / centres[lower])
        val db = lowDb + t * (highDb - lowDb)
        return Sample(db, minOf(lowSnr, highSnr))
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }
}
