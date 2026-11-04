package tv.corebuilds.eq.display

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The numbers behind [TestPatternView], kept out of the view so they can be checked (1.9.0).
 *
 * A test pattern is only worth looking at if the value it prints is the value it draws, and the
 * quickest way to get that wrong is to compute both inside a `drawX(canvas, w, h)` method where
 * nothing can assert on them. Everything numeric lives here: the two ladders, the gamma patch
 * codes, the PQ codes and the separability rule the ladders have to obey.
 *
 * Nothing in this file touches the Android SDK, so the repository harness can run it.
 */
object DisplayMath {

    /**
     * Black-level ladder: sixteen bars, two 8-bit codes apart.
     *
     * One-code steps were the old pattern and they were unusable: code 0 and code 1 differ by
     * 0.4% of white, so "make bar 2 just visible" named two bars nobody can separate. Two codes
     * (0.8%) is the finest step that survives a normal television in a dim room.
     */
    val BLACK_LADDER: List<Int> = (0 until 16).map { it * 2 }

    /**
     * White-level ladder: eleven bars from 200 to 255, ending 250 / 253 / 255.
     *
     * The test is "253 separates from 255", so 253 has to be two codes from 255 — which rules
     * out 252 and 254, one code away from it and therefore not separable by the same argument
     * that killed the old one-code black ladder. A one-code ramp from 240 (the old pattern) put
     * sixteen indistinguishable bars on screen and made the instruction untestable.
     */
    val WHITE_LADDER: List<Int> = listOf(200, 210, 220, 230, 235, 240, 245, 248, 250, 253, 255)

    /** Gamma values drawn as patches, twice: power law and BT.1886. */
    val GAMMAS: List<Double> = listOf(1.8, 2.0, 2.2, 2.4, 2.6)

    /** ST.2084 reference luminances drawn by the HDR pattern, in nits. */
    val HDR_NITS: List<Double> = listOf(0.01, 1.0, 10.0, 100.0, 400.0, 1000.0)

    /** BT.1886 grading room: 0.01 nit black, 100 nit white. */
    private const val BT1886_BLACK_NITS = 0.01
    private const val BT1886_WHITE_NITS = 100.0

    /**
     * The 8-bit code that blends into a 50% field when the display follows [gamma].
     *
     * The display's transfer is `L = (code/255)^2.2`-ish for the 8-bit signal it receives, so the
     * code that *should* show relative luminance `L_rel` is `255·L_rel^(1/2.2)`. For a pure power
     * law `L_rel = 0.5^gamma`. BT.1886 adds the standard's black offset: with
     * `L(V) = a·(V + b)^gamma` normalised so white is 1, `b = k/(1-k)` and `a = (1-k)^gamma`
     * where `k = (Lb/Lw)^(1/gamma)`. The black offset *lifts* mid-grey — a 0.01 nit black takes
     * the γ2.4 patch from 120 to 123 — which is why the two rows on screen are close but not
     * identical.
     *
     * The old code used `255·0.5^(1/gamma)`, which is the *inverse* ratio in the wrong place: it
     * put the γ2.4 patch at 191 instead of 120, i.e. a near-white square that made every display
     * look like it was at γ1.8.
     */
    fun gammaPatchCode(gamma: Double, bt1886: Boolean = false): Int {
        val relative = if (bt1886) {
            val k = Math.pow(BT1886_BLACK_NITS / BT1886_WHITE_NITS, 1.0 / gamma)
            val b = k / (1.0 - k)
            Math.pow(1.0 - k, gamma) * Math.pow(0.5 + b, gamma)
        } else {
            Math.pow(0.5, gamma)
        }
        return (255.0 * Math.pow(relative, 1.0 / 2.2)).roundToInt().coerceIn(0, 255)
    }

    /**
     * The 8-bit PQ code for [nits] (SMPTE ST 2084 §5.1, the inverse EOTF).
     *
     * PQ is a signal encoding: 100 nits is code 128, 1000 nits is code 192. On an SDR output the
     * same codes are just a bottom-weighted grey ramp, which is why the pattern's caption
     * changes with the display instead of claiming a luminance test it cannot perform.
     */
    fun pqCode(nits: Double): Int {
        val y = (nits / 10_000.0).coerceAtLeast(0.0)
        val m1 = 2610.0 / 16_384.0
        val m2 = 2523.0 / 32.0
        val c1 = 3424.0 / 4096.0
        val c2 = 2413.0 / 128.0
        val c3 = 2392.0 / 128.0
        val p = Math.pow(y, m1)
        val n = Math.pow((c1 + c2 * p) / (1.0 + c3 * p), m2)
        return (255.0 * n).roundToInt().coerceIn(0, 255)
    }

    /** Two codes are separable in 8-bit steps when they are at least two apart. */
    fun codesAreSeparable(first: Int, second: Int): Boolean = abs(first - second) >= 2

    /** `0.01`, `1`, `100`, `1000`: nits without a pointless decimal. */
    fun formatNits(nits: Double): String =
        if (nits >= 1.0) nits.roundToInt().toString() else nits.toString()

    /**
     * True when [codes] runs strictly one way (up for the black and white ladders, down for the
     * gamma patches) with every step separable. Direction matters: a descending ladder is still
     * a ladder, and requiring it to ascend would have rejected the gamma patches for no reason.
     */
    fun ladderIsSeparable(codes: List<Int>): Boolean {
        if (codes.size < 2) return true
        val ascending = codes.last() > codes.first()
        return codes.zipWithNext().all { (a, b) ->
            (if (ascending) b > a else b < a) && codesAreSeparable(a, b)
        }
    }
}
