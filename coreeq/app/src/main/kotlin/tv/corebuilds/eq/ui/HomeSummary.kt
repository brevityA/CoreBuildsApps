package tv.corebuilds.eq.ui

import tv.corebuilds.eq.dsp.NightMode

/**
 * Home's two-line answer (1.14.0): what is actually applied right now, and
 * which listening effects are switched on. It separates the two because they
 * differ: "Night: Strong" is a request; whether a limiter runs it depends on
 * the engine the TV accepted.
 *
 * Only facts Core EQ holds are used. Where Android cannot tell (which output
 * is in use, whether a playing stream passes through the effect) the line
 * says unknown or says what is not confirmed; nothing here is a live meter.
 * Android-free so every state is unit-tested (`HomeSummaryTest`).
 */
object HomeSummary {

    enum class State { OFF, MEASURING, NO_PROFILE, NOT_RUNNING, FAULT, OUTPUT_UNKNOWN, NO_PROFILE_FOR_OUTPUT, APPLIED, PENDING }

    data class Facts(
        val correctionOn: Boolean,
        val serviceRunning: Boolean,
        val measurementPaused: Boolean,
        val anyProfile: Boolean,
        /** The profile chosen for the output in use; null when nothing matches it. */
        val profileForOutput: Boolean,
        /** Android named the output in use. */
        val outputKnown: Boolean,
        val outputLabel: String?,
        /** The engine the service last applied with ("DynamicsProcessing", "Equalizer"), or null. */
        val appliedEngine: String?,
        val playing: Boolean,
        val faultMessage: String?,
        val bypass: Boolean,
        val night: NightMode.Level,
        val clarityDb: Double,
        val stereoSpeakers: Boolean,
        val lowVolumeBass: Boolean,
        val autoLoudness: Boolean,
        val bassBoost: Boolean,
        val loudnessEnhancer: Boolean
    )

    data class Summary(val state: State, val applied: String, val listening: String)

    const val DYNAMICS_PROCESSING = "DynamicsProcessing"

    fun of(f: Facts): Summary = Summary(stateOf(f), appliedLine(f), listeningLine(f))

    fun stateOf(f: Facts): State = when {
        !f.correctionOn -> State.OFF
        f.serviceRunning && f.measurementPaused -> State.MEASURING
        !f.anyProfile -> State.NO_PROFILE
        !f.serviceRunning -> State.NOT_RUNNING
        f.faultMessage != null -> State.FAULT
        !f.profileForOutput && !f.outputKnown -> State.OUTPUT_UNKNOWN
        !f.profileForOutput -> State.NO_PROFILE_FOR_OUTPUT
        f.appliedEngine != null -> State.APPLIED
        else -> State.PENDING
    }

    private fun appliedLine(f: Facts): String = when (stateOf(f)) {
        State.OFF -> "Correction is off. Nothing Core EQ does is applied."
        State.MEASURING -> "Paused while a room is measured. It resumes when the measurement ends, is cancelled or fails."
        State.NO_PROFILE -> "Nothing to apply yet. Next: measure this room, or import a profile in Profiles."
        State.NOT_RUNNING -> "Unknown: the correction service is not running. Next: switch Correction off and on."
        State.FAULT -> "Not applied: ${f.faultMessage}"
        State.OUTPUT_UNKNOWN -> "Unknown: Android has not identified the output in use, so no profile is applied."
        State.NO_PROFILE_FOR_OUTPUT ->
            "Paused: nothing measured for ${f.outputLabel ?: "this output"}. Next: measure it, or switch back to a measured output."
        State.APPLIED -> buildString {
            append("Applied by ").append(f.appliedEngine)
            append(if (f.playing) " · media is playing" else " · nothing is playing")
            if (f.bypass) append(" · A/B: you are hearing the bypass side")
            append(". Android does not confirm which stream passes through it.")
        }
        State.PENDING -> "Pending: correction is on and waiting for a player or a route check."
    }

    /** The listening effects switched on, as requested; night says when its engine cannot run it. */
    fun listeningLine(f: Facts): String {
        val words = buildList {
            if (f.night != NightMode.Level.OFF) {
                val unavailable = f.appliedEngine != null && f.appliedEngine != DYNAMICS_PROCESSING
                add("night ${f.night.title.lowercase()}" + if (unavailable) " (not active on ${f.appliedEngine})" else "")
            }
            if (f.clarityDb > 0.0) add(String.format(java.util.Locale.US, "clarity +%.0f dB", f.clarityDb))
            if (f.stereoSpeakers) add("stereo speakers")
            if (f.autoLoudness) add("auto loudness") else if (f.lowVolumeBass) add("low-volume bass")
            if (f.bassBoost) add("bass boost (Android effect)")
            if (f.loudnessEnhancer) add("loudness (Android effect)")
        }
        return if (words.isEmpty()) "Listening: no extras (Extra effects to add some)." else "Listening: " + words.joinToString(" · ") + "."
    }
}
