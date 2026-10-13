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
    fun `rejects oversized files`() {
        val points = (0..TargetCurveImport.MAX_POINTS).joinToString("; ") { "${20 + it * 10} 0" }
        assertTrue(TargetCurveImport.parse("GraphicEQ: $points") is TargetCurveImport.Result.Error)
    }

    @Test
    fun `interpolates the target onto the measurement centres`() {
        val points = ok("GraphicEQ: 100 -4; 10000 4").points
        val db = TargetCurveImport.targetDb(points, doubleArrayOf(100.0, 1000.0, 10000.0))
        assertEquals(-4.0, db[0], 1e-9)
        assertEquals(0.0, db[1], 1e-9)
        assertEquals(4.0, db[2], 1e-9)
    }
}
