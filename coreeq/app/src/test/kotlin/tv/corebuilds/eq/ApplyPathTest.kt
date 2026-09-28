package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.Resample
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** The pieces between a saved profile and the TV's own equaliser bands. */
class ApplyPathTest {

    private fun profile(vararg points: Pair<Double, Double>) = Profile(
        id = "t", name = "t", timestampMs = 0L, target = "flat", micType = "test",
        curve = points.map { CurvePoint(it.first, 0.0, it.second) }
    )

    @Test
    fun correctionIsInterpolatedOnALogAxisBetweenBands() {
        val p = profile(100.0 to -6.0, 400.0 to 0.0)
        // 200 Hz is one octave of two above 100 Hz: halfway on a log axis.
        assertEquals(-3.0, p.correctionAt(200.0), 1e-9)
        assertEquals(-6.0, p.correctionAt(100.0), 1e-9)
    }

    @Test
    fun outsideTheMeasuredCurveTheCorrectionIsZero() {
        val p = profile(40.0 to 3.0, 8000.0 to -2.0)
        assertEquals(0.0, p.correctionAt(14000.0), 0.0) // a TV's 14 kHz band gets nothing
        assertEquals(0.0, p.correctionAt(20.0), 0.0)
        assertEquals(0.0, profile().correctionAt(1000.0), 0.0)
    }

    @Test
    fun decimationKeepsTheBandAndRejectsWhatWouldAlias() {
        val fs = 48000
        fun tone(hz: Double) = DoubleArray(fs) { sin(2 * PI * hz * it / fs) }
        fun rms(x: DoubleArray) = sqrt(x.drop(200).dropLast(200).sumOf { it * it } / (x.size - 400))
        val kept = rms(Resample.decimate(tone(1000.0), 3))
        val rejected = rms(Resample.decimate(tone(12000.0), 3)) // would fold to 4 kHz at 16 kHz
        assertEquals(1.0 / sqrt(2.0), kept, 0.01)
        assertTrue("12 kHz leaked through at rms $rejected", rejected < 0.01)
    }
}
