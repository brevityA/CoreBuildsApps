package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/**
 * The shipped in-room target curves: shape, normalisation and smoothness.
 *
 * The smoothness case exists because a curve helper with a hard step is
 * invisible in a spot check but audible as a shelf edge in a correction. A
 * removed helper (`UseCaseTargets.boxShape`, 1.7.0) returned 1.0 inside its
 * band and 0.0 outside with a vertical edge between them, while this file's
 * `boxDb` interpolates over half an octave; nothing caught it because the
 * damaged curves were never applied. These tests make the same class of defect
 * fail loudly for the curves that *are* applied.
 */
class TargetsTest {

    private val grid = DoubleArray(241) { 20.0 * 2.0.pow(it / 24.0) } // 1/24 octave, 20 Hz - 20 kHz

    private fun curve(kind: String) = Targets.targetCurve(kind, grid)

    @Test
    fun everyTargetReadsZeroAtItsPivot() {
        for (kind in listOf("room", "olive", "dialogue", "house")) {
            val db = curve(kind)
            val atPivot = db[grid.indexOfFirst { it >= Targets.PIVOT_HZ }]
            assertEquals("$kind at the pivot", 0.0, atPivot, 0.5)
        }
    }

    @Test
    fun flatIsExactlyZeroEverywhere() {
        for (value in curve("flat")) assertEquals(0.0, value, 0.0)
    }

    @Test
    fun noTargetHasAStepBiggerThanOneDecibelPerTwentyFourthOctave() {
        for (kind in listOf("room", "olive", "dialogue", "house", "bk")) {
            val db = curve(kind)
            var worst = 0.0
            for (i in 1 until db.size) worst = maxOf(worst, abs(db[i] - db[i - 1]))
            assertTrue(
                "$kind steps $worst dB between adjacent 1/24-octave points",
                worst <= 1.0
            )
        }
    }

    @Test
    fun thePresenceBoxIsSmoothAtBothEdges() {
        val shape = Targets.boxDb(grid, 2200.0, 4500.0)
        // Full inside, zero well outside, and nothing in between jumps.
        val inside = grid.indexOfFirst { it >= 2200.0 }
        val outside = grid.indexOfLast { it <= 1500.0 }
        assertEquals(1.0, shape[inside], 1e-9)
        assertEquals(0.0, shape[outside], 1e-9)
        for (i in 1 until shape.size) {
            assertTrue(
                "boxDb jumped ${abs(shape[i] - shape[i - 1])} at ${grid[i]} Hz",
                abs(shape[i] - shape[i - 1]) < 0.35
            )
        }
    }

    @Test
    fun theBkCurveFallsSixDecibelsAcrossTheAudioBand() {
        val db = curve("bk")
        val at160 = db[grid.indexOfFirst { it >= 160.0 }]
        val at20k = db.last()
        // The span is the fixed part; the absolute values are normalised at the
        // 630 Hz pivot, so B&K reads +1.7 dB at 160 Hz and -4.3 dB at 20 kHz.
        assertEquals(1.7, at160, 0.2)
        assertEquals(-6.0, at20k - at160, 0.4)
    }

    @Test
    fun oliveCarriesMoreBassThanRoomAndLessTreble() {
        val room = curve("room")
        val olive = curve("olive")
        val i60 = grid.indexOfFirst { it >= 60.0 }
        val i5k = grid.indexOfFirst { it >= 5000.0 }
        assertTrue("olive must carry more bass at 60 Hz", olive[i60] > room[i60] + 2.0)
        assertTrue("olive must shelve the treble down at 5 kHz", olive[i5k] < room[i5k] - 1.0)
    }

    @Test
    fun dialogueAddsPresenceWithoutLiftingSibilance() {
        val room = curve("room")
        val dialogue = curve("dialogue")
        val i3k = grid.indexOfFirst { it >= 3100.0 }
        val i7k = grid.indexOfFirst { it >= 7000.0 }
        assertTrue("dialogue must lift the 3 kHz region", dialogue[i3k] > room[i3k] + 1.0)
        assertTrue("dialogue must stay neutral above 6 kHz", abs(dialogue[i7k] - room[i7k]) < 1.0)
    }

    @Test
    fun theHouseCurveTakesItsBassFromTheCaller() {
        val none = Targets.targetCurve("house", grid, bassBoostDb = 0.0)
        val fat = Targets.targetCurve("house", grid, bassBoostDb = 6.0)
        val i60 = grid.indexOfFirst { it >= 60.0 }
        assertTrue("a 6 dB house shelf must raise 60 Hz", fat[i60] > none[i60] + 4.0)
    }

    @Test
    fun anUnknownTargetIsRefusedRatherThanSilentlyFlat() {
        assertThrows(IllegalArgumentException::class.java) {
            Targets.targetCurve("loudness", grid)
        }
    }

    @Test
    fun aShelfReachesItsGainWellAboveTheCorner() {
        val value = Targets.shelfDb(doubleArrayOf(20_000.0), 2500.0, -2.4)[0]
        assertEquals(-2.4, value, 0.01)
        assertEquals(0.0, Targets.shelfDb(doubleArrayOf(20.0), 2500.0, -2.4)[0], 0.01)
    }

    @Test
    fun curveValuesStayInsideTheSignalBudget() {
        // A target is a shape, not a level: none of them may ask for more than
        // the corrector's own boost cap or cut budget at any point.
        for (kind in listOf("room", "olive", "dialogue", "house", "bk")) {
            for (value in curve(kind)) {
                assertTrue("$kind asked for $value dB", abs(value) <= DspConstants.MAX_CUT_DB)
            }
        }
    }

}
