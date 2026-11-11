package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.apply.LimiterSettings

/**
 * Night mode: Core EQ's one DynamicsProcessing limiter, retuned to bring loud
 * passages down so a late-night viewer does not ride the volume remote.
 *
 * What it is, plainly: downward compression of the loudest moments. It adds
 * no gain anywhere (post-gain stays 0 dB), so quiet speech is not raised and
 * the whole programme plays a little quieter; turn the TV up to taste. It is
 * not loudness normalisation and it does not isolate dialogue. It runs only
 * on the DynamicsProcessing engine; on the Equalizer fallback there is no
 * limiter to retune, and the status says night mode is not active.
 *
 * It replaces the protection limiter rather than stacking a second one: one
 * stage, so there is one place gain is reduced. Each level's static curve
 * sits at or below the protection limiter's for every input up to
 * [GUARANTEED_UP_TO_DBFS] (`NightModeTest` checks −60…+12 dBFS in 0.25 dB steps),
 * so switching night mode on never weakens protection there. That is a
 * statement about the configured hard-knee curve, not about transients: the
 * attack is 5 ms (protection uses 1 ms), and nothing here measures true peak.
 *
 * Levels (1.14.0):
 * - [Level.OFF]: the protection limiter alone (−1 dB, 10:1, 1 ms / 50 ms).
 * - [Level.LIGHT]: −6 dB threshold, 3:1. Only the loudest moments (effects
 *   peaks, music swells) are brought down; a 100 ms release so the bass does
 *   not pump.
 * - [Level.STRONG]: −12 dB threshold, 4:1, 5 ms / 50 ms — exactly the night
 *   mode that shipped as an on/off switch before 1.14.0, so a viewer who had it
 *   on hears no change.
 *
 * Its 1.2.0 dialogue lift (a single 3.2 kHz peak) was never applied; speech
 * clarity is the separate Clarity control ([tv.corebuilds.eq.apply.ToneLayers]).
 */
object NightMode {

    enum class Level(val key: String, val title: String) {
        OFF("off", "Off"),
        LIGHT("light", "Light"),
        STRONG("strong", "Strong");

        fun next(): Level = entries[(ordinal + 1) % entries.size]
        fun previous(): Level = entries[(ordinal + entries.size - 1) % entries.size]

        companion object {
            fun fromKey(key: String?): Level? = entries.firstOrNull { it.key == key }
        }
    }

    /** The night mode that shipped as a switch until 1.14.0: now [Level.STRONG]. */
    const val NIGHT_LIMITER_THRESHOLD_DB = -12f
    const val NIGHT_LIMITER_RATIO = 4f
    const val NIGHT_LIMITER_ATTACK_MS = 5f
    const val NIGHT_LIMITER_RELEASE_MS = 50f

    const val LIGHT_THRESHOLD_DB = -6f
    const val LIGHT_RATIO = 3f
    const val LIGHT_ATTACK_MS = 5f
    const val LIGHT_RELEASE_MS = 100f

    /** The input level up to which every night curve stays at or below protection. */
    const val GUARANTEED_UP_TO_DBFS = 12.0

    fun limiter(level: Level): LimiterSettings = when (level) {
        Level.OFF -> LimiterSettings()
        Level.LIGHT -> LimiterSettings(
            attackMs = LIGHT_ATTACK_MS,
            releaseMs = LIGHT_RELEASE_MS,
            ratio = LIGHT_RATIO,
            thresholdDb = LIGHT_THRESHOLD_DB
        )
        Level.STRONG -> LimiterSettings(
            attackMs = NIGHT_LIMITER_ATTACK_MS,
            releaseMs = NIGHT_LIMITER_RELEASE_MS,
            ratio = NIGHT_LIMITER_RATIO,
            thresholdDb = NIGHT_LIMITER_THRESHOLD_DB
        )
    }

    /**
     * The configured static curve: output level for a steady input level,
     * hard knee, post-gain applied. A model of the settings, not a measurement
     * of any device's limiter.
     */
    fun staticOutputDb(settings: LimiterSettings, inputDb: Double): Double {
        if (!settings.enabled) return inputDb + settings.postGainDb
        val threshold = settings.thresholdDb.toDouble()
        val out = if (inputDb <= threshold) inputDb else threshold + (inputDb - threshold) / settings.ratio
        return out + settings.postGainDb
    }

    /** Words for the status line and the Listening screen, e.g. "night: light (−6 dB, 3:1)". */
    fun describe(level: Level): String = when (level) {
        Level.OFF -> "night: off"
        Level.LIGHT -> "night: light (−6 dB, 3:1)"
        Level.STRONG -> "night: strong (−12 dB, 4:1)"
    }
}
