package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetCurveImportTest {

    private fun ok(text: String): TargetCurveImport.Result.Ok {
        val result = TargetCurveImport.parse(text)
        assertTrue("expected Ok, got $result", result is TargetCurveImport.Result.Ok)
        return result as TargetCurveImport.Result.Ok
    }

    @Test
    fun `normalises the target to 0 dB at 1 kHz`() {
        val target = ok("GraphicEQ: 20 -6; 1000 -2; 20000 0")
        assertEquals(listOf(20.0, 1000.0, 20000.0), target.points.map { it.first })
        assertEquals(-4.0, target.points[0].second, 1e-9)
        assertEquals(0.0, target.points[1].second, 1e-9)
        assertEquals(2.0, target.points[2].second, 1e-9)
        assertTrue(target.notes.isEmpty())
    }

    @Test
    fun `sorts points and drops repeated frequencies`() {
        val target = ok("GraphicEQ: 1000 0; 100 3; 100 9; 10000 -1")
        assertEquals(listOf(100.0, 1000.0, 10000.0), target.points.map { it.first })
        assertEquals(3.0, target.points[0].second, 1e-9)
    }

    @Test
    fun `limiting keeps 1 kHz at 0 dB when the file has no 1 kHz point`() {
        val target = ok("GraphicEQ: 100 -20; 10000 20")
        val db = TargetCurveImport.targetDb(target.points, doubleArrayOf(100.0, 1000.0, 10000.0))
        assertEquals(-12.0, db[0], 1e-9)
        assertEquals(0.0, db[1], 1e-9)
        assertEquals(6.0, db[2], 1e-9)
        assertTrue(target.notes.any { it.contains("limited") })
    }

    @Test
    fun `limits gains to the correction budget and says so`() {
        val target = ok("GraphicEQ: 20 -20; 1000 0")
        assertEquals(-12.0, target.points[0].second, 1e-9)
        assertTrue(target.notes.any { it.contains("limited") })
    }

    @Test
    fun `rejects files that are not a GraphicEQ target`() {
        assertTrue(TargetCurveImport.parse("hello") is TargetCurveImport.Result.Error)
        assertTrue(TargetCurveImport.parse("GraphicEQ: 1000 0") is TargetCurveImport.Result.Error)
        assertTrue(TargetCurveImport.parse("GraphicEQ: 1000 x; 2000 0") is TargetCurveImport.Result.Error)
        assertTrue(TargetCurveImport.parse("GraphicEQ: 5 0; 1000 0") is TargetCurveImport.Result.Error)
        assertTrue(TargetCurveImport.parse("GraphicEQ: 1000 0 0; 2000 0") is TargetCurveImport.Result.Error)
        assertTrue(TargetCurveImport.parse("GraphicEQ: 1000 0; 1000 2") is TargetCurveImport.Result.Error)
    }

    @Test
    fun `resamples a dense file instead of refusing it`() {
        // 700 points, as an AutoEq target CSV carries: a log sweep from 20 Hz to 20 kHz
        // on a gentle tilt, 0 dB at 1 kHz.
        val lines = (0 until 700).joinToString("\n") { k ->
            val f = 20.0 * Math.pow(1000.0, k / 699.0)
            "$f,${-2.0 * Math.log10(f / 1000.0)}"
        }
        val target = ok("frequency,raw\n$lines")
        assertTrue(target.points.size <= TargetCurveImport.MAX_POINTS + 1)
        assertTrue(target.notes.any { it.contains("resampled") })
        val db = TargetCurveImport.targetDb(target.points, doubleArrayOf(100.0, 1000.0, 10000.0))
        assertEquals(2.0, db[0], 0.05)
        assertEquals(0.0, db[1], 1e-9)
        assertEquals(-2.0, db[2], 0.05)
    }

    @Test
    fun `refuses a file over 64 KB`() {
        val big = "20 0\n".repeat(TargetCurveImport.MAX_BYTES / 4)
        assertTrue(TargetCurveImport.parse(big) is TargetCurveImport.Result.Error)
    }

    @Test
    fun `reads a REW house curve with comments`() {
        val target = ok("* Room house curve\n* exported by hand\n20 6\n100 3\n1000 0\n10000 -3\n20000 -5\n")
        assertEquals(listOf(20.0, 100.0, 1000.0, 10000.0, 20000.0), target.points.map { it.first })
        assertEquals(6.0, target.points[0].second, 1e-9)
        assertEquals(-5.0, target.points[4].second, 1e-9)
    }

    @Test
    fun `reads an AutoEq CSV by its first two columns`() {
        val target = ok("frequency,raw,error\n20.0,4.0,0.1\n1000.0,0.0,0.0\n20000.0,-6.0,0.0\n")
        assertEquals(listOf(20.0, 1000.0, 20000.0), target.points.map { it.first })
        assertEquals(4.0, target.points[0].second, 1e-9)
        assertEquals(-6.0, target.points[2].second, 1e-9)
    }

    @Test
    fun `reads decimal commas when columns are split by semicolons or tabs`() {
        val semi = ok("Frequenz;Pegel\n20;3,5\n1000;0\n10000;-2,5\n")
        assertEquals(3.5, semi.points[0].second, 1e-9)
        assertEquals(-2.5, semi.points.last().second, 1e-9)
        val tabs = ok("20\t2\n1000\t0\n")
        assertEquals(2.0, tabs.points[0].second, 1e-9)
    }

    @Test
    fun `leaves out points outside 20 Hz to 20 kHz and says so`() {
        val target = ok("GraphicEQ: 10 0; 20 2; 1000 0; 22000 -3")
        assertEquals(listOf(20.0, 1000.0), target.points.map { it.first })
        assertTrue(target.notes.any { it.contains("2 points were outside") })
    }

    @Test
    fun `a number that does not parse still rejects the file`() {
        assertTrue(TargetCurveImport.parse("20 2\n1000 x\n") is TargetCurveImport.Result.Error)
        assertTrue(TargetCurveImport.parse("20,2\n1000\n") is TargetCurveImport.Result.Error)
        assertTrue(TargetCurveImport.parse("hello\nworld\n") is TargetCurveImport.Result.Error)
    }

    @Test
    fun `interpolates the target onto the measurement centres`() {
        val points = ok("GraphicEQ: 100 -4; 10000 4").points
        val db = TargetCurveImport.targetDb(points, doubleArrayOf(100.0, 1000.0, 10000.0))
        assertEquals(-4.0, db[0], 1e-9)
        assertEquals(0.0, db[1], 1e-9)
        assertEquals(4.0, db[2], 1e-9)
    }

    @Test
    fun `the custom key routes the imported curve through Targets`() {
        val points = ok("GraphicEQ: 100 -4; 10000 4").points
        CustomTarget.points = points
        try {
            val centres = doubleArrayOf(100.0, 1000.0, 10000.0)
            val viaTargets = Targets.targetCurve(CustomTarget.KEY, centres)
            assertEquals(TargetCurveImport.targetDb(points, centres).toList(), viaTargets.toList())
        } finally {
            CustomTarget.points = null
        }
    }

    @Test
    fun `the custom key is flat when nothing is imported`() {
        CustomTarget.points = null
        val db = Targets.targetCurve(CustomTarget.KEY, doubleArrayOf(100.0, 1000.0))
        assertEquals(0.0, db[0], 1e-9)
        assertEquals(0.0, db[1], 1e-9)
    }

    @Test
    fun `a decimal comma with a space separator is read as one point, not four numbers`() {
        val points = ok("20,5 -3,2\n1000 0\n10000 1,5\n").points
        // 20.5 Hz is in the file; the misread 20 Hz / 5 dB point must not be.
        assertTrue(points.any { kotlin.math.abs(it.first - 20.5) < 1e-9 })
        assertTrue(points.none { kotlin.math.abs(it.first - 20.0) < 1e-9 })
    }
}

