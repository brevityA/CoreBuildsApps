package tv.corebuilds.eq.dsp

import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.tanh

/**
 * Use-case-specific target curves optimized for different content types.
 *
 * Extends the base [Targets] system with curves tailored for:
 * - **Movies:** Dialogue-first optimization with center channel clarity
 * - **Anime:** Music + dialogue balance with wide dynamic range preservation
 * - **TV Shows:** Natural dialogue with reduced fatigue
 * - **Gaming:** Low-latency spatial cues and impact
 * - **Music:** Flat reference with optional bass enhancement
 *
 * Each curve is derived from research into the acoustic characteristics
 * of the content type and industry mixing practices.
 */
object UseCaseTargets {

    /**
     * Movie-optimized target curve.
     *
     * **Characteristics:**
     * - Dialogue clarity boost at 2.5 kHz (presence) and 5 kHz (articulation)
     * - Male vocal fullness at 120 Hz, female at 240 Hz
     * - Reduced 2-4 kHz to prevent nasally quality
     * - Gentle bass roll-off below 60 Hz (subwoofer handles LFE)
     * - Slight treble lift for air and detail
     *
     * **Research basis:**
     * - 70% of movie dialogue is in the center channel
     * - Dialogue intelligibility peaks at 2-5 kHz (SII band)
     * - Home theater dialogue clarity issues often stem from room modes
     *   below 300 Hz and presence region deficiencies
     */
    fun movieTarget(freqs: DoubleArray): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 630.0

        for (i in freqs.indices) {
            val f = freqs[i]

            // Bass management: gentle roll-off below 60 Hz (subwoofer territory)
            val bass = if (f < 60.0) {
                -3.0 * (1.0 - tanh((f - 30.0) / 20.0))
            } else {
                0.0
            }

            // Male vocal fullness (120 Hz)
            val maleFullness = 1.5 * bellShape(f, 120.0, 1.0)

            // Female vocal fullness (240 Hz)
            val femaleFullness = 1.0 * bellShape(f, 240.0, 1.0)

            // Reduce nasally region (2-4 kHz) slightly
            val nasalCut = -1.5 * boxShape(f, 2000.0, 4000.0)

            // Dialogue presence boost (2.5 kHz) - critical for intelligibility
            val presence = 2.5 * bellShape(f, 2500.0, 0.8)

            // Articulation boost (5 kHz) - helps with consonants
            val articulation = 1.5 * bellShape(f, 5000.0, 0.9)

            // Gentle treble lift for air (8-12 kHz)
            val air = if (f > 8000.0) {
                1.0 * tanh((f - 8000.0) / 4000.0)
            } else {
                0.0
            }

            out[i] = bass + maleFullness + femaleFullness + nasalCut + presence + articulation + air
        }

        // Normalize to 0 dB at pivot frequency
        val atPivot = interpolate(pivotHz, freqs, out)
        for (i in out.indices) {
            out[i] -= atPivot
        }
        return out
    }

    /**
     * Anime-optimized target curve.
     *
     * **Characteristics:**
     * - Balanced music and dialogue (OSTs are central to anime)
     * - Enhanced low-mid warmth (200-400 Hz) for orchestral fullness
     * - Presence boost at 3 kHz for dialogue clarity over music
     * - Wide dynamic range preservation (less compression-friendly)
     * - Enhanced bass impact (80-120 Hz) for action sequences
     * - Sparkling highs (10+ kHz) for sound effects and cymbals
     *
     * **Research basis:**
     * - Anime mixes dialogue, music, and effects in a complex layering
     * - Frequency ducking: music reduced 2-5 kHz to make room for vocals
     * - Battle sound effects span full spectrum (low rumble + high whoosh)
     * - Close-mic'd dialogue with intimate quality (breath sounds preserved)
     * - Often mixed in 5.1/7.1 with wide dynamic range
     */
    fun animeTarget(freqs: DoubleArray): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 630.0

        for (i in freqs.indices) {
            val f = freqs[i]

            // Enhanced bass impact for action (80-120 Hz)
            val bassImpact = 2.5 * bellShape(f, 100.0, 0.7)

            // Low-mid warmth for orchestral fullness (200-400 Hz)
            val orchestralWarmth = 2.0 * boxShape(f, 200.0, 400.0)

            // Gentle reduction in music-dominant midrange (500-1000 Hz)
            // to make space for dialogue
            val midScoop = -1.0 * boxShape(f, 500.0, 1000.0)

            // Dialogue presence boost (3 kHz) - cuts through music
            val dialoguePresence = 3.0 * bellShape(f, 3000.0, 0.9)

            // Vocal articulation (4.5 kHz)
            val vocalArticulation = 1.5 * bellShape(f, 4500.0, 1.0)

            // Enhanced highs for sound effects and cymbals (8-14 kHz)
            val sparkle = if (f > 8000.0) {
                2.0 * tanh((f - 8000.0) / 3000.0)
            } else {
                0.0
            }

            out[i] = bassImpact + orchestralWarmth + midScoop + dialoguePresence + vocalArticulation + sparkle
        }

        // Normalize to 0 dB at pivot frequency
        val atPivot = interpolate(pivotHz, freqs, out)
        for (i in out.indices) {
            out[i] -= atPivot
        }
        return out
    }

    /**
     * TV show target curve.
     *
     * **Characteristics:**
     * - Natural dialogue without fatigue
     * - Reduced harshness in 2-4 kHz region
     * - Gentle bass (less than movies, more than news)
     * - Smooth midrange for long listening sessions
     *
     * **Optimized for:** Sitcoms, dramas, reality TV, news
     */
    fun tvShowTarget(freqs: DoubleArray): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 630.0

        for (i in freqs.indices) {
            val f = freqs[i]

            // Gentle bass (not as much as movies)
            val bass = 1.5 * bellShape(f, 80.0, 0.8)

            // Warm midrange (300-500 Hz)
            val warmth = 1.0 * boxShape(f, 300.0, 500.0)

            // Reduce harshness (2-4 kHz)
            val harshnessCut = -2.0 * boxShape(f, 2000.0, 4000.0)

            // Gentle presence (2.5 kHz) for dialogue
            val presence = 1.5 * bellShape(f, 2500.0, 1.0)

            out[i] = bass + warmth + harshnessCut + presence
        }

        // Normalize to 0 dB at pivot frequency
        val atPivot = interpolate(pivotHz, freqs, out)
        for (i in out.indices) {
            out[i] -= atPivot
        }
        return out
    }

    /**
     * Gaming target curve.
     *
     * **Characteristics:**
     * - Enhanced low-end impact (60-100 Hz) for explosions and immersion
     * - Spatial cue preservation (2-5 kHz) for directional audio
     * - Reduced midrange mud (200-400 Hz) for clarity
     * - Crisp highs for footsteps and environmental cues
     * - Minimal processing for low latency
     *
     * **Optimized for:** FPS, action, RPG, competitive gaming
     */
    fun gamingTarget(freqs: DoubleArray): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 630.0

        for (i in freqs.indices) {
            val f = freqs[i]

            // Low-end impact (60-100 Hz)
            val impact = 3.0 * bellShape(f, 80.0, 0.6)

            // Reduce midrange mud (200-400 Hz)
            val mudCut = -2.0 * boxShape(f, 200.0, 400.0)

            // Spatial cues (2-5 kHz) - critical for directional audio
            val spatialCues = 2.5 * boxShape(f, 2000.0, 5000.0)

            // Crisp highs for footsteps (4-8 kHz)
            val footsteps = 1.5 * bellShape(f, 6000.0, 1.0)

            out[i] = impact + mudCut + spatialCues + footsteps
        }

        // Normalize to 0 dB at pivot frequency
        val atPivot = interpolate(pivotHz, freqs, out)
        for (i in out.indices) {
            out[i] -= atPivot
        }
        return out
    }

    /**
     * Music target curve.
     *
     * **Characteristics:**
     * - Flat reference baseline (respect the mastering engineer's intent)
     * - Optional gentle bass enhancement
     * - Smooth midrange for vocal clarity
     * - Natural treble without harshness
     *
     * **Optimized for:** Music listening across all genres
     */
    fun musicTarget(freqs: DoubleArray, bassEnhancement: Boolean = false): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 1000.0  // Music uses 1 kHz as reference, not 630 Hz

        for (i in freqs.indices) {
            val f = freqs[i]

            // Optional bass enhancement
            val bass = if (bassEnhancement) {
                2.0 * bellShape(f, 60.0, 0.7)
            } else {
                0.0
            }

            // Gentle midrange warmth (400-800 Hz)
            val warmth = 0.5 * boxShape(f, 400.0, 800.0)

            // Slight presence lift (3 kHz) for vocal clarity
            val presence = 1.0 * bellShape(f, 3000.0, 1.0)

            // Gentle treble roll-off to prevent fatigue
            val trebleRoll = if (f > 10000.0) {
                -1.0 * tanh((f - 10000.0) / 5000.0)
            } else {
                0.0
            }

            out[i] = bass + warmth + presence + trebleRoll
        }

        // Normalize to 0 dB at pivot frequency
        val atPivot = interpolate(pivotHz, freqs, out)
        for (i in out.indices) {
            out[i] -= atPivot
        }
        return out
    }

    /**
     * Bell-shaped curve (peaking EQ) centered at [centerHz] with given [q].
     * Returns a value from 0 to 1.
     */
    private fun bellShape(f: Double, centerHz: Double, q: Double): Double {
        val logRatio = log2(max(f, 1e-9) / centerHz)
        val bandwidth = 1.0 / q
        val exponent = -0.5 * (logRatio / (bandwidth / 2.0)) * (logRatio / (bandwidth / 2.0))
        return Math.exp(exponent)
    }

    /**
     * Box-shaped curve (flat region) from [loHz] to [hiHz] with smooth edges.
     * Returns a value from 0 to 1.
     */
    private fun boxShape(f: Double, loHz: Double, hiHz: Double): Double {
        val center = Math.sqrt(loHz * hiHz)
        val bandwidth = log2(hiHz / loHz)
        val logRatio = log2(max(f, 1e-9) / center)

        return when {
            f < loHz * 0.707 -> 0.0  // Below -3 dB point
            f > hiHz * 1.414 -> 0.0  // Above +3 dB point
            f in loHz..hiHz -> 1.0   // Flat region
            else -> {
                // Smooth transition
                val normalized = (logRatio / (bandwidth / 2.0)).coerceIn(-1.0, 1.0)
                0.5 * (1.0 + cos(Math.PI * normalized))
            }
        }
    }

    private fun interpolate(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (xs.isEmpty()) return 0.0
        if (x <= xs[0]) return ys[0]
        if (x >= xs[xs.size - 1]) return ys[ys.size - 1]
        for (i in 0 until xs.size - 1) {
            if (x in xs[i]..xs[i + 1]) {
                val fraction = (x - xs[i]) / (xs[i + 1] - xs[i])
                return ys[i] + fraction * (ys[i + 1] - ys[i])
            }
        }
        return ys[0]
    }
}
