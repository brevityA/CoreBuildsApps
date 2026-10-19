package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the use-case-specific target curves so the acoustic
 * optimizations for movies, anime, TV, gaming, and music cannot drift.
 */
class UseCaseTargetsTest {

    private val testFreqs = doubleArrayOf(
        40.0, 63.0, 100.0, 200.0, 400.0, 630.0, 1000.0,
        2000.0, 3000.0, 5000.0, 8000.0, 12000.0
    )

    private fun indexOfFrequency(hz: Double): Int =
        testFreqs.indices.first { testFreqs[it] == hz }

    @Test
    fun `movie target has dialogue presence boost at 2-3 kHz`() {
        val curve = UseCaseTargets.movieTarget(testFreqs)
        val idx2k = indexOfFrequency(2000.0)
        val idx3k = indexOfFrequency(3000.0)
        val idx1k = indexOfFrequency(1000.0)

        // Presence region should be boosted relative to 1 kHz
        assertTrue("2 kHz should be boosted", curve[idx2k] > curve[idx1k])
        assertTrue("3 kHz should be boosted", curve[idx3k] > curve[idx1k])
    }

    @Test
    fun `movie target has male vocal fullness at 100-150 Hz`() {
        val curve = UseCaseTargets.movieTarget(testFreqs)
        val idx100 = indexOfFrequency(100.0)
        val idx200 = indexOfFrequency(200.0)

        // 100 Hz should have some boost for male vocals
        assertTrue("100 Hz should have vocal fullness", curve[idx100] > 0.0)
    }

    @Test
    fun `anime target has enhanced bass impact at 80-120 Hz`() {
        val curve = UseCaseTargets.animeTarget(testFreqs)
        val idx100 = indexOfFrequency(100.0)
        val idx1k = indexOfFrequency(1000.0)

        // Anime target should have significant bass boost for action
        assertTrue("100 Hz should be boosted for anime action", curve[idx100] > 1.5)
    }

    @Test
    fun `anime target has dialogue presence at 3 kHz`() {
        val curve = UseCaseTargets.animeTarget(testFreqs)
        val idx3k = indexOfFrequency(3000.0)
        val idx1k = indexOfFrequency(1000.0)

        // Dialogue presence should cut through music
        assertTrue("3 kHz should be boosted for dialogue", curve[idx3k] > curve[idx1k] + 1.0)
    }

    @Test
    fun `anime target has sparkling highs`() {
        val curve = UseCaseTargets.animeTarget(testFreqs)
        val idx12k = indexOfFrequency(12000.0)
        val idx8k = indexOfFrequency(8000.0)

        // High frequencies should be enhanced for sound effects
        assertTrue("12 kHz should be boosted", curve[idx12k] > curve[idx8k])
    }

    @Test
    fun `gaming target has low-end impact`() {
        val curve = UseCaseTargets.gamingTarget(testFreqs)
        val idx100 = indexOfFrequency(100.0)

        // Gaming should have strong bass for explosions
        assertTrue("100 Hz should have impact", curve[idx100] > 2.0)
    }

    @Test
    fun `gaming target has spatial cue preservation`() {
        val curve = UseCaseTargets.gamingTarget(testFreqs)
        val idx2k = indexOfFrequency(2000.0)
        val idx5k = indexOfFrequency(5000.0)

        // Spatial cues in 2-5 kHz should be preserved/enhanced
        assertTrue("2 kHz should be enhanced", curve[idx2k] > 1.0)
        assertTrue("5 kHz should be enhanced", curve[idx5k] > 1.0)
    }

    @Test
    fun `music target is relatively flat without bass enhancement`() {
        val curve = UseCaseTargets.musicTarget(testFreqs, bassEnhancement = false)

        // Without bass enhancement, the curve should be fairly flat
        // (within ±2 dB across most of the range)
        val midRange = curve.slice(4..7)  // 400 Hz to 2 kHz
        val maxDeviation = midRange.maxOrNull()!! - midRange.minOrNull()!!
        assertTrue("Music target should be fairly flat, max deviation: $maxDeviation",
            maxDeviation < 3.0)
    }

    @Test
    fun `music target with bass enhancement boosts low end`() {
        val flat = UseCaseTargets.musicTarget(testFreqs, bassEnhancement = false)
        val enhanced = UseCaseTargets.musicTarget(testFreqs, bassEnhancement = true)
        val idx100 = indexOfFrequency(100.0)

        assertTrue("Bass enhancement should boost 100 Hz",
            enhanced[idx100] > flat[idx100] + 1.0)
    }

    @Test
    fun `TV show target reduces harshness in 2-4 kHz`() {
        val curve = UseCaseTargets.tvShowTarget(testFreqs)
        val idx2k = indexOfFrequency(2000.0)
        val idx1k = indexOfFrequency(1000.0)

        // TV show target should reduce harshness
        assertTrue("2 kHz should be reduced for less fatigue",
            curve[idx2k] < curve[idx1k])
    }

    @Test
    fun `all targets are normalized to zero at pivot`() {
        val movieCurve = UseCaseTargets.movieTarget(testFreqs)
        val animeCurve = UseCaseTargets.animeTarget(testFreqs)
        val gamingCurve = UseCaseTargets.gamingTarget(testFreqs)
        val musicCurve = UseCaseTargets.musicTarget(testFreqs)

        // Movie/Anime/Gaming use 630 Hz as pivot
        val idx630 = indexOfFrequency(630.0)
        assertEquals("Movie should be 0 dB at 630 Hz", 0.0, movieCurve[idx630], 0.5)
        assertEquals("Anime should be 0 dB at 630 Hz", 0.0, animeCurve[idx630], 0.5)
        assertEquals("Gaming should be 0 dB at 630 Hz", 0.0, gamingCurve[idx630], 0.5)

        // Music uses 1 kHz as pivot
        val idx1k = indexOfFrequency(1000.0)
        assertEquals("Music should be 0 dB at 1 kHz", 0.0, musicCurve[idx1k], 0.5)
    }

    @Test
    fun `all targets produce finite values`() {
        val curves = listOf(
            UseCaseTargets.movieTarget(testFreqs),
            UseCaseTargets.animeTarget(testFreqs),
            UseCaseTargets.tvShowTarget(testFreqs),
            UseCaseTargets.gamingTarget(testFreqs),
            UseCaseTargets.musicTarget(testFreqs)
        )

        for (curve in curves) {
            for (value in curve) {
                assertTrue("All values should be finite", value.isFinite())
                assertTrue("All values should be reasonable (±15 dB)", value in -15.0..15.0)
            }
        }
    }
}
