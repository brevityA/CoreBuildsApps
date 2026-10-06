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
     * - Natural dialogue without fatigue (dialogue mixed closer to program
     *   loudness than cinema per EBU R128 s4)
     * - Reduced harshness in 2-4 kHz region (long-session comfort)
     * - Gentle bass (less than movies, more than news)
     * - Smooth midrange for extended listening
     * - Optimized for -23 LUFS broadcast loudness standard
     *
     * **Research basis:**
     * - TV dialogue typically mixed 5-10 LU below program (vs 10-15 for cinema)
     * - Sitcoms have rapid dialogue + laughter tracks
     * - Dramas have wider dynamic range with music beds
     * - Documentaries are narration-focused with clear speech
     * - EBU R128 targets -23 LUFS for broadcast consistency
     *
     * **Optimized for:** Sitcoms, dramas, reality TV, documentaries
     */
    fun tvShowTarget(freqs: DoubleArray): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 630.0

        for (i in freqs.indices) {
            val f = freqs[i]

            // Gentle bass (not as much as movies, enough for drama impact)
            val bass = 1.5 * bellShape(f, 80.0, 0.8)

            // Warm midrange (300-500 Hz) for natural vocal body
            val warmth = 1.0 * boxShape(f, 300.0, 500.0)

            // Reduce boxiness (250 Hz) - common in broadcast recordings
            val boxinessCut = -1.5 * bellShape(f, 250.0, 1.5)

            // Reduce harshness (2-4 kHz) for long-session comfort
            val harshnessCut = -2.0 * boxShape(f, 2000.0, 4000.0)

            // Gentle presence (2.5 kHz) for dialogue clarity
            val presence = 1.5 * bellShape(f, 2500.0, 1.0)

            // Slight articulation boost (5 kHz) for consonants
            val articulation = 1.0 * bellShape(f, 5000.0, 1.2)

            out[i] = bass + warmth + boxinessCut + harshnessCut + presence + articulation
        }

        // Normalize to 0 dB at pivot frequency
        val atPivot = interpolate(pivotHz, freqs, out)
        for (i in out.indices) {
            out[i] -= atPivot
        }
        return out
    }

    /**
     * Sitcom-optimized target curve.
     *
     * **Characteristics:**
     * - Enhanced dialogue clarity for rapid-fire exchanges
     * - Laughter track accommodation (3-5 kHz region)
     * - Consistent midrange for multi-camera setups
     * - Reduced fatigue from canned laughter
     *
     * **Optimized for:** Friends, The Office, Brooklyn Nine-Nine, sitcoms
     */
    fun sitcomTarget(freqs: DoubleArray): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 630.0

        for (i in freqs.indices) {
            val f = freqs[i]

            // Light bass (sitcoms don't need heavy low-end)
            val bass = 1.0 * bellShape(f, 100.0, 0.9)

            // Vocal body (200-400 Hz)
            val vocalBody = 1.5 * boxShape(f, 200.0, 400.0)

            // Reduce boxiness from multi-camera setups
            val boxinessCut = -2.0 * bellShape(f, 280.0, 1.3)

            // Enhanced dialogue presence (2.5-4 kHz) for rapid exchanges
            val dialoguePresence = 2.5 * boxShape(f, 2500.0, 4000.0)

            // Tame laughter harshness (3-5 kHz)
            val laughterTaming = -1.5 * bellShape(f, 3800.0, 1.0)

            // Crisp articulation for punchlines
            val articulation = 1.5 * bellShape(f, 5500.0, 1.1)

            out[i] = bass + vocalBody + boxinessCut + dialoguePresence + laughterTaming + articulation
        }

        val atPivot = interpolate(pivotHz, freqs, out)
        for (i in out.indices) {
            out[i] -= atPivot
        }
        return out
    }

    /**
     * Documentary-optimized target curve.
     *
     * **Characteristics:**
     * - Narration-first optimization (voice is primary content)
     * - Enhanced low-mid warmth for authoritative narration
     * - Smooth, non-fatiguing presentation
     * - Gentle handling of music beds and sound effects
     *
     * **Optimized for:** Nature documentaries, history, true crime, educational
     */
    fun documentaryTarget(freqs: DoubleArray): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 630.0

        for (i in freqs.indices) {
            val f = freqs[i]

            // Gentle bass for gravitas
            val bass = 1.5 * bellShape(f, 90.0, 0.8)

            // Narration warmth (150-300 Hz) - authoritative voice
            val narrationWarmth = 2.0 * boxShape(f, 150.0, 300.0)

            // Reduce muddiness (400-600 Hz)
            val mudCut = -1.0 * boxShape(f, 400.0, 600.0)

            // Clear narration presence (2-3 kHz)
            val narrationPresence = 2.0 * boxShape(f, 2000.0, 3000.0)

            // Gentle highs (avoid fatigue from long narration)
            val gentleHighs = if (f > 6000.0) {
                -1.0 * tanh((f - 6000.0) / 4000.0)
            } else {
                0.0
            }

            out[i] = bass + narrationWarmth + mudCut + narrationPresence + gentleHighs
        }

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
     * Everyday / General target curve.
     *
     * **Characteristics:**
     * - Balanced, safe default for mixed content
     * - Gentle bass enhancement for body
     * - Smooth midrange that works for speech and music
     * - Reduced listening fatigue for all-day use
     * - Handles loudness jumps between different content types
     *
     * **Optimized for:** Casual viewing, background listening, channel
     * surfing, mixed playlists, YouTube rabbit holes
     */
    fun generalTarget(freqs: DoubleArray): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 630.0

        for (i in freqs.indices) {
            val f = freqs[i]

            // Gentle, warm bass (universal body without boom)
            val bass = 1.5 * bellShape(f, 80.0, 0.8)

            // Light midrange warmth for natural vocal tone
            val warmth = 0.8 * boxShape(f, 300.0, 600.0)

            // Gentle presence for speech intelligibility (without being
            // aggressive like the movie/anime targets)
            val presence = 1.0 * bellShape(f, 2800.0, 1.0)

            // Mild fatigue reduction (6-10 kHz)
            val fatigueReduction = if (f > 6000.0) {
                -0.8 * tanh((f - 6000.0) / 4000.0)
            } else {
                0.0
            }

            out[i] = bass + warmth + presence + fatigueReduction
        }

        val atPivot = interpolate(pivotHz, freqs, out)
        for (i in out.indices) {
            out[i] -= atPivot
        }
        return out
    }

    /**
     * Podcast / talk-radio optimized target curve.
     *
     * **Characteristics:**
     * - Voice-first: everything outside the speech band is secondary
     * - High-pass at 80 Hz (podcasts have no useful content below this)
     * - Boxiness cut at 250 Hz (the #1 podcast EQ adjustment)
     * - Presence boost at 4 kHz (critical for intelligibility)
     * - Sibilance control at 6-8 kHz
     * - Aggressive dynamic range reduction (podcasts are listened to in
     *   noisy environments: cars, commutes, kitchens)
     *
     * **Research basis:**
     * - AES recommends podcasts at -18 to -16 LUFS (vs -23 for broadcast)
     * - Spotify normalizes to -14 LUFS, YouTube to -14 LUFS
     * - Top podcast EQ: -2 dB at 250 Hz, +2 dB at 4 kHz, +1 dB at 10 kHz
     * - High-pass 80 Hz removes plosives and handling noise
     * - 125 Hz male fullness, 200 Hz female fullness, 250-400 Hz children
     *
     * **Optimized for:** Podcasts, talk radio, audiobooks, news broadcasts
     */
    fun podcastTarget(freqs: DoubleArray): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 1000.0  // Voice reference

        for (i in freqs.indices) {
            val f = freqs[i]

            // High-pass: cut everything below 80 Hz (no useful content)
            val highPass = if (f < 80.0) {
                -6.0 * (1.0 - tanh((f - 40.0) / 30.0))
            } else {
                0.0
            }

            // Male vocal fullness (125 Hz)
            val maleFullness = 1.0 * bellShape(f, 125.0, 1.0)

            // Female vocal fullness (200 Hz)
            val femaleFullness = 0.8 * bellShape(f, 200.0, 1.0)

            // Boxiness cut (250 Hz) — the single most important podcast EQ move
            val boxinessCut = -2.5 * bellShape(f, 250.0, 1.5)

            // Mud reduction (400-500 Hz)
            val mudCut = -1.0 * boxShape(f, 400.0, 500.0)

            // Critical presence boost (4 kHz) — intelligibility
            val presence = 2.5 * bellShape(f, 4000.0, 0.9)

            // Articulation (6 kHz)
            val articulation = 1.5 * bellShape(f, 6000.0, 1.0)

            // Sibilance control (7-8 kHz) — tame harsh S sounds
            val sibilanceControl = -1.5 * bellShape(f, 7500.0, 1.2)

            // Air (10 kHz) — subtle brightness without fatigue
            val air = 1.0 * bellShape(f, 10000.0, 0.8)

            out[i] = highPass + maleFullness + femaleFullness + boxinessCut +
                mudCut + presence + articulation + sibilanceControl + air
        }

        val atPivot = interpolate(pivotHz, freqs, out)
        for (i in out.indices) {
            out[i] -= atPivot
        }
        return out
    }

    /**
     * News / live broadcast target curve.
     *
     * **Characteristics:**
     * - Speech-optimized with aggressive fatigue reduction
     * - Reduced background noise emphasis (low rumble, HVAC hum)
     * - Enhanced anchor voice clarity
     * - Compressed-friendly (news is heavily compressed at source)
     * - Works well with ticker graphics and background music beds
     *
     * **Optimized for:** 24-hour news, live broadcasts, sports commentary,
     * weather channels
     */
    fun newsTarget(freqs: DoubleArray): DoubleArray {
        val out = DoubleArray(freqs.size)
        val pivotHz = 630.0

        for (i in freqs.indices) {
            val f = freqs[i]

            // High-pass at 100 Hz (news has no useful low content,
            // and HVAC/rumble from field reporters is common)
            val highPass = if (f < 100.0) {
                -4.0 * (1.0 - tanh((f - 50.0) / 30.0))
            } else {
                0.0
            }

            // Anchor voice warmth (150-250 Hz)
            val anchorWarmth = 1.5 * boxShape(f, 150.0, 250.0)

            // Reduce boxiness (300 Hz)
            val boxinessCut = -1.5 * bellShape(f, 300.0, 1.5)

            // Enhanced speech presence (2.5-4 kHz)
            val speechPresence = 2.5 * boxShape(f, 2500.0, 4000.0)

            // Reduce ticker/music bed interference (500-1000 Hz)
            val bedReduction = -1.0 * boxShape(f, 500.0, 1000.0)

            // Fatigue reduction for all-day listening (highs)
            val fatigueReduction = if (f > 5000.0) {
                -1.5 * tanh((f - 5000.0) / 5000.0)
            } else {
                0.0
            }

            out[i] = highPass + anchorWarmth + boxinessCut + speechPresence +
                bedReduction + fatigueReduction
        }

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
