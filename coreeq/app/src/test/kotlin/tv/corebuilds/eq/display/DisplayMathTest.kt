package tv.corebuilds.eq.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers a test pattern draws (1.9.0).
 *
 * These exist because the old view got them wrong in ways no screenshot would show: a gamma
 * patch at code 191 labelled γ2.4, and two ladders whose adjacent bars were a single 8-bit
 * code apart — i.e. the same colour. A test pattern that prints a number it does not draw is
 * worse than no pattern, so the arithmetic is pinned here.
 */
class DisplayMathTest {

    private val delta = 2

    @Test
    fun `a gamma 2_2 patch lands on 50 percent grey`() {
        val code = DisplayMath.gammaPatchCode(2.2)
        println("power γ2.2 -> $code, γ2.4 -> ${DisplayMath.gammaPatchCode(2.4)}")
        assertTrue("γ2.2 must blend into a 128 field, got $code", code in (128 - delta)..(128 + delta))
    }

    @Test
    fun `a gamma 2_4 patch is darker than 50 percent, not brighter`() {
        val power = DisplayMath.gammaPatchCode(2.4)
        println("power γ2.4 -> $power")
        // 0.5^2.4 = 0.19 of white, which is code ~120 in an 8-bit 2.2 display.
        assertTrue("γ2.4 must sit near 120, got $power", power in 117..123)
        // The bug this replaces: 255 * 0.5^(1/2.4) = 191, a near-white square.
        assertTrue("must not be the old inverted formula", power < 150)
    }

    @Test
    fun `bt1886 is the same curve plus the black offset, which lifts mid-grey a little`() {
        // A 0.01 nit black adds a small constant to every luminance, so the BT.1886 patch is
        // slightly *brighter* than the plain power-law one — the opposite of the intuitive
        // guess, and the reason the two rows on screen are close but not identical.
        for (gamma in DisplayMath.GAMMAS) {
            val power = DisplayMath.gammaPatchCode(gamma)
            val bt = DisplayMath.gammaPatchCode(gamma, bt1886 = true)
            println("γ$gamma: power $power, BT.1886 $bt")
            assertTrue("BT.1886 lifts mid-grey at γ$gamma ($power -> $bt)", bt >= power)
            assertTrue(
                "the offset must not move the patch more than a few codes (γ$gamma: $power vs $bt)",
                bt - power <= 6
            )
        }
        // At the grading-room gamma the two rows must still be distinguishable from each other
        // step, or drawing both would be pointless.
        assertTrue(DisplayMath.gammaPatchCode(2.4, bt1886 = true) > DisplayMath.gammaPatchCode(2.4))
    }

    @Test
    fun `a higher gamma means a darker patch`() {
        val codes = DisplayMath.GAMMAS.map { DisplayMath.gammaPatchCode(it) }
        println("gamma ladder: $codes")
        for (i in 1 until codes.size) {
            assertTrue("patches must get darker as gamma rises: $codes", codes[i] < codes[i - 1])
        }
        assertTrue("the whole ladder must be distinguishable", DisplayMath.ladderIsSeparable(codes))
    }

    @Test
    fun `the black ladder is separable from top to bottom`() {
        val ladder = DisplayMath.BLACK_LADDER
        println("black ladder: $ladder")
        assertEquals("sixteen bars", 16, ladder.size)
        assertEquals("bar 0 is absolute black", 0, ladder.first())
        assertTrue("every step must be at least two codes", DisplayMath.ladderIsSeparable(ladder))
    }

    @Test
    fun `the white ladder keeps 253 and 255 apart and still reaches white`() {
        val ladder = DisplayMath.WHITE_LADDER
        println("white ladder: $ladder")
        assertEquals("ends at full white", 255, ladder.last())
        assertTrue("253 must be on the ladder", ladder.contains(253))
        assertTrue("254 is one code from 253, so it cannot be on a separable ladder", !ladder.contains(254))
        assertEquals("253 and 255 are two codes apart", 2, ladder.last() - ladder[ladder.size - 2])
        assertTrue("every step must be at least two codes", DisplayMath.ladderIsSeparable(ladder))
        assertTrue("the ladder must span a visible range", ladder.last() - ladder.first() >= 50)
    }

    @Test
    fun `a single code step is not separable, which is why the old ladders failed`() {
        assertFalse(DisplayMath.codesAreSeparable(252, 253))
        assertFalse(DisplayMath.codesAreSeparable(0, 1))
        assertTrue(DisplayMath.codesAreSeparable(252, 254))
    }

    @Test
    fun `pq codes come from ST 2084 and put 100 nits at half signal`() {
        val hundred = DisplayMath.pqCode(100.0)
        val thousand = DisplayMath.pqCode(1000.0)
        val dim = DisplayMath.pqCode(0.01)
        println("PQ: 0.01 nit $dim, 100 nits $hundred, 1000 nits $thousand")
        assertTrue("100 nits is code ~128, got $hundred", hundred in 126..130)
        assertTrue("1000 nits is code ~192, got $thousand", thousand in 190..195)
        assertTrue("0.01 nits is nearly black, got $dim", dim in 0..10)
    }

    @Test
    fun `pq is monotonic and starts at zero`() {
        assertEquals("no light is code 0", 0, DisplayMath.pqCode(0.0))
        val codes = DisplayMath.HDR_NITS.map { DisplayMath.pqCode(it) }
        println("PQ ladder: $codes")
        for (i in 1 until codes.size) {
            assertTrue("PQ codes must rise with luminance: $codes", codes[i] > codes[i - 1])
        }
    }

    @Test
    fun `nits are printed without a pointless decimal`() {
        assertEquals("0.01", DisplayMath.formatNits(0.01))
        assertEquals("1", DisplayMath.formatNits(1.0))
        assertEquals("1000", DisplayMath.formatNits(1000.0))
    }
}
