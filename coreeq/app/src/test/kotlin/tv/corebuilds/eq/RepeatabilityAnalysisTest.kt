package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.RepeatabilityAnalysis
import tv.corebuilds.eq.dsp.SweepResult
import tv.corebuilds.eq.export.CaptureInfo
import kotlin.math.abs

class RepeatabilityAnalysisTest {
    private val frequencies = doubleArrayOf(
        250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0,
        1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0
    )

    private fun result(
        measured: DoubleArray,
        snr: DoubleArray = DoubleArray(frequencies.size) { 30.0 },
        floorHz: Double = 250.0
    ) = SweepResult(
        centresHz = frequencies.copyOf(),
        measuredDb = measured,
        targetDb = DoubleArray(frequencies.size),
        correctionDb = DoubleArray(frequencies.size),
        nullMask = BooleanArray(frequencies.size),
        minPhaseOk = BooleanArray(frequencies.size) { true },
        rt60Seconds = null,
        schroederHz = null,
        transitionHz = 300.0,
        rolloffHz = floorHz,
        floorHz = floorHz,
        snrDb = 40.0,
        latencyMs = 50.0,
        filters = emptyList(),
        preampDb = 0.0,
        bandSnrDb = snr
    )

    @Test
    fun levelOffsetIsRemovedAndRepeatTraceIsAlignedWithoutAveraging() {
        val referenceDb = DoubleArray(frequencies.size) { index -> index * 0.25 }
        val repeatDb = DoubleArray(frequencies.size) { index -> referenceDb[index] - 2.5 }
        val referenceSnr = DoubleArray(frequencies.size) { 30.0 }.apply { this[2] = 5.0 }
        val repeatSnr = DoubleArray(frequencies.size) { 30.0 }.apply { this[12] = 8.0 }

        val comparison = RepeatabilityAnalysis.compare(
            result(referenceDb, referenceSnr),
            result(repeatDb, repeatSnr)
        )

        val metrics = comparison.metrics
        assertNotNull(metrics)
        assertEquals(2.5, metrics!!.levelOffsetDb, 1e-9)
        assertEquals(frequencies.size - 2, metrics.comparedBands)
        assertEquals(0.0, metrics.medianAbsDifferenceDb, 1e-9)
        assertEquals(0.0, metrics.rmsDifferenceDb, 1e-9)
        assertEquals(0.0, metrics.p95AbsDifferenceDb, 1e-9)
        assertEquals(referenceDb[6], comparison.alignedRepeatDb[6], 1e-9)
        assertTrue(comparison.alignedRepeatDb[2].isNaN())
        assertTrue(comparison.alignedRepeatDb[12].isNaN())
        assertNull(comparison.reason)
    }

    @Test
    fun nonFiniteBandSnrIsExcludedRatherThanCountedAsClean() {
        val referenceSnr = DoubleArray(frequencies.size) { 30.0 }.apply { this[3] = Double.NaN }
        val comparison = RepeatabilityAnalysis.compare(
            result(DoubleArray(frequencies.size), referenceSnr),
            result(DoubleArray(frequencies.size))
        )

        assertEquals(frequencies.size - 1, comparison.metrics!!.comparedBands)
        assertTrue(comparison.alignedRepeatDb[3].isNaN())
    }

    @Test
    fun robustAnchorDoesNotLetOneBandSetTheGainOffset() {
        val referenceDb = DoubleArray(frequencies.size) { index -> index * 0.1 }
        val repeatDb = DoubleArray(frequencies.size) { index -> referenceDb[index] - 1.25 }
            .apply { this[7] += 8.0 }
        val comparison = RepeatabilityAnalysis.compare(result(referenceDb), result(repeatDb))
        val metrics = comparison.metrics

        assertNotNull(metrics)
        assertEquals(1.25, metrics!!.levelOffsetDb, 1e-9)
        assertTrue(metrics.medianAbsDifferenceDb < 1e-9)
        assertTrue(metrics.p95AbsDifferenceDb >= 0.0)
        assertTrue(metrics.p95AbsDifferenceDb <= 8.0)
    }

    @Test
    fun frequencyInterpolationComparesSlightlyDifferentBandGrids() {
        val referenceDb = DoubleArray(frequencies.size) { index -> index.toDouble() }
        val repeatDb = DoubleArray(frequencies.size) { index -> index - 4.0 }
        val repeatGrid = DoubleArray(frequencies.size) { index -> frequencies[index] * 1.01 }
        val repeat = result(repeatDb).copy(centresHz = repeatGrid)

        val comparison = RepeatabilityAnalysis.compare(result(referenceDb), repeat)
        assertNotNull(comparison.metrics)
        assertTrue(abs(comparison.metrics!!.levelOffsetDb - 4.0) < 0.1)
        assertFalse(comparison.alignedRepeatDb[6].isNaN())
    }

    @Test
    fun exactlyThreeAlignmentAndEightCommonBandsAreEnoughForADescriptiveSummary() {
        val snr = DoubleArray(frequencies.size) { 0.0 }.apply {
            listOf(3, 6, 9, 10, 11, 12, 13, 14).forEach { this[it] = 30.0 }
        }
        val reference = result(DoubleArray(frequencies.size), snr)
        val repeat = result(DoubleArray(frequencies.size) { -1.0 }, snr)
        val comparison = RepeatabilityAnalysis.compare(reference, repeat)

        assertNotNull(comparison.metrics)
        assertEquals(8, comparison.metrics!!.comparedBands)
        assertEquals(1.0, comparison.metrics!!.levelOffsetDb, 1e-9)
    }

    @Test
    fun threeAlignmentBandsDoNotPermitASummaryWithFewerThanEightCommonBands() {
        val snr = DoubleArray(frequencies.size) { 0.0 }.apply {
            listOf(3, 6, 9, 10, 11, 12, 13).forEach { this[it] = 30.0 }
        }
        val comparison = RepeatabilityAnalysis.compare(
            result(DoubleArray(frequencies.size), snr),
            result(DoubleArray(frequencies.size), snr)
        )

        assertNull(comparison.metrics)
        assertTrue(comparison.alignedRepeatDb.none { it.isFinite() })
        assertTrue(comparison.reason!!.contains("at least 8"))
    }

    @Test
    fun comparisonWithoutEnoughCleanMidBandsDoesNotInventAMetric() {
        val lowOnlyFrequencies = doubleArrayOf(250.0, 315.0, 400.0)
        val lowOnly = result(doubleArrayOf(0.0, 1.0, 2.0)).copy(
            centresHz = lowOnlyFrequencies,
            measuredDb = doubleArrayOf(0.0, 1.0, 2.0),
            targetDb = DoubleArray(3),
            correctionDb = DoubleArray(3),
            nullMask = BooleanArray(3),
            minPhaseOk = BooleanArray(3) { true },
            bandSnrDb = DoubleArray(3) { 30.0 }
        )
        val comparison = RepeatabilityAnalysis.compare(lowOnly, lowOnly)

        assertNull(comparison.metrics)
        assertTrue(comparison.reason!!.contains("500 Hz"))
    }

    @Test
    fun missingOutputMetadataIsProvisionalRatherThanAProvenRouteMismatch() {
        assertNull(RepeatabilityAnalysis.reportedOutputMismatch("unknown", null, "hdmi_arc", "Soundbar"))
        assertNull(RepeatabilityAnalysis.reportedOutputMismatch("speaker", "TV speakers", "speaker", null))
        assertTrue(
            RepeatabilityAnalysis.reportedOutputMismatch("speaker", "TV speakers", "hdmi_arc", "Soundbar")!!
                .contains("different TV audio output")
        )
        assertTrue(
            RepeatabilityAnalysis.reportedOutputMismatch("speaker", "TV speakers", "speaker", "Other TV")!!
                .contains("different named")
        )
    }

    @Test
    fun changedReportedPhoneOrTvInputPathIsFlaggedButMissingMetadataIsNotGuessed() {
        val first = CaptureInfo(inputDeviceType = "remote", inputSampleRateHz = 16_000)
        val second = CaptureInfo(inputDeviceType = "phone", inputSampleRateHz = 16_000)
        assertTrue(RepeatabilityAnalysis.reportedInputMismatch(first, second)!!.contains("input route"))
        assertTrue(
            RepeatabilityAnalysis.reportedInputMismatch(first, second.copy(inputDeviceType = "remote", inputSampleRateHz = 44_100))!!
                .contains("sample rate")
        )
        assertNull(RepeatabilityAnalysis.reportedInputMismatch(null, second))
        assertNull(RepeatabilityAnalysis.reportedInputMismatch(first, first.copy(inputDeviceName = null)))
    }
}
