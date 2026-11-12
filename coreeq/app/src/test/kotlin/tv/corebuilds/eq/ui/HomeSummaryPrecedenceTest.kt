package tv.corebuilds.eq.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.NightMode
import tv.corebuilds.eq.ui.HomeSummary.State

/**
 * Home when several conditions hold at once, and as they change
 * (`docs/CORE_EQ_HOME_STATUS.md`). The rules below are written from what each
 * state means, not copied from the order of `stateOf`, so reordering its
 * branches wrongly fails here.
 */
class HomeSummaryPrecedenceTest {

    private val base = HomeSummary.Facts(
        correctionOn = true, serviceRunning = true, measurementPaused = false,
        anyProfile = true, profileForOutput = true, outputKnown = true, outputLabel = "TV speakers",
        appliedEngine = "DynamicsProcessing", playing = false, faultMessage = null, bypass = false,
        night = NightMode.Level.OFF, clarityDb = 0.0, stereoSpeakers = false, lowVolumeBass = false,
        autoLoudness = false, bassBoost = false, loudnessEnhancer = false
    )

    /** Every combination of the facts that decide the state; a profile for the output implies a profile. */
    private fun everyCombination(): List<HomeSummary.Facts> {
        val out = mutableListOf<HomeSummary.Facts>()
        val bools = listOf(false, true)
        for (on in bools) for (running in bools) for (paused in bools) for (any in bools) for (forOutput in bools)
            for (known in bools) for (engine in listOf(null, "DynamicsProcessing", "Equalizer")) for (playing in bools)
                for (fault in listOf(null, "Effect refused")) for (bypass in bools) {
                    if (forOutput && !any) continue
                    out += base.copy(
                        correctionOn = on, serviceRunning = running, measurementPaused = paused, anyProfile = any,
                        profileForOutput = forOutput, outputKnown = known, appliedEngine = engine,
                        playing = playing, faultMessage = fault, bypass = bypass
                    )
                }
        return out
    }

    @Test fun `every combination obeys what each state means`() {
        val all = everyCombination()
        assertEquals(1_152, all.size)
        val reached = mutableSetOf<State>()
        for (f in all) {
            val s = HomeSummary.of(f)
            reached += s.state
            val why = "$f -> ${s.state}: ${s.applied}"
            // The switch is the one certain fact.
            assertEquals(why, !f.correctionOn, s.state == State.OFF)
            // A running, suspended service is measuring, whatever else is stored.
            if (f.correctionOn && f.serviceRunning && f.measurementPaused) assertEquals(why, State.MEASURING, s.state)
            // Without the service, nothing it stored is live.
            if (!f.serviceRunning) assertTrue(why, s.state in setOf(State.OFF, State.NO_PROFILE, State.NOT_RUNNING))
            // Applied needs every precondition.
            if (s.state == State.APPLIED) {
                assertTrue(why, f.correctionOn && f.serviceRunning && !f.measurementPaused && f.profileForOutput &&
                    f.faultMessage == null && f.appliedEngine != null)
            }
            if (s.state == State.FAULT) assertTrue(why, f.serviceRunning && f.faultMessage != null)
            if (s.state == State.PENDING) assertTrue(why, f.appliedEngine == null)
            if (s.state == State.NO_PROFILE_FOR_OUTPUT) assertTrue(why, f.outputKnown && !f.profileForOutput)
            // Only Applied says applied, and it never claims the stream was processed.
            assertEquals(why, s.state == State.APPLIED, s.applied.startsWith("Applied"))
            assertTrue(why, !s.applied.contains("is processed") && !s.applied.contains("confirms"))
            if (s.state == State.APPLIED) assertTrue(why, s.applied.contains("does not confirm"))
            assertEquals(why, s.state == State.APPLIED && f.playing, s.applied.contains("media is playing"))
            // Unknown is said where the output or the service is unknown.
            if (s.state == State.OUTPUT_UNKNOWN || s.state == State.NOT_RUNNING) assertTrue(why, s.applied.startsWith("Unknown:"))
            if (s.state == State.APPLIED) assertEquals(why, !f.outputKnown, s.applied.contains("output not identified"))
        }
        assertEquals("every state is reachable", State.entries.toSet(), reached)
    }

    @Test fun `each state outranks the next when both hold`() {
        val cases = listOf(
            // Off beats a stored fault and a measuring flag.
            base.copy(correctionOn = false, measurementPaused = true, faultMessage = "Effect refused") to State.OFF,
            // Measuring beats having no profile yet (a first measurement).
            base.copy(measurementPaused = true, anyProfile = false, profileForOutput = false) to State.MEASURING,
            // No profile beats a stopped service.
            base.copy(anyProfile = false, profileForOutput = false, serviceRunning = false) to State.NO_PROFILE,
            // A stopped service beats its own stored fault.
            base.copy(serviceRunning = false, faultMessage = "Effect refused") to State.NOT_RUNNING,
            // A live fault beats an unknown output.
            base.copy(faultMessage = "Effect refused", profileForOutput = false, outputKnown = false) to State.FAULT,
            // Unknown output beats "nothing measured for" a guessed output.
            base.copy(profileForOutput = false, outputKnown = false) to State.OUTPUT_UNKNOWN,
            // Nothing measured for this output beats an engine left from the last one.
            base.copy(profileForOutput = false, appliedEngine = "DynamicsProcessing") to State.NO_PROFILE_FOR_OUTPUT,
            // A held engine is Applied, not Pending.
            base.copy(appliedEngine = "Equalizer") to State.APPLIED,
            base.copy(appliedEngine = null) to State.PENDING
        )
        for ((facts, expected) in cases) assertEquals("$facts", expected, HomeSummary.stateOf(facts))
    }

    @Test fun `a viewing session moves through the states it should`() {
        val steps = listOf(
            "correction off" to base.copy(correctionOn = false, appliedEngine = null),
            "switched on, effect not yet configured" to base.copy(appliedEngine = null),
            "effect configured" to base,
            "a film starts" to base.copy(playing = true),
            "Measure suspends the service" to base.copy(measurementPaused = true, playing = false),
            "measurement saved, service resumes" to base,
            "headphones connected, never measured" to base.copy(profileForOutput = false, outputLabel = "Bluetooth"),
            "back to the speakers, before re-apply" to base.copy(appliedEngine = null),
            "re-applied" to base,
            "two outputs at once" to base.copy(profileForOutput = false, outputKnown = false, outputLabel = "multiple or ambiguous audio outputs")
        )
        val expected = listOf(
            State.OFF, State.PENDING, State.APPLIED, State.APPLIED, State.MEASURING, State.APPLIED,
            State.NO_PROFILE_FOR_OUTPUT, State.PENDING, State.APPLIED, State.OUTPUT_UNKNOWN
        )
        assertEquals(expected, steps.map { HomeSummary.stateOf(it.second) })
        assertEquals(
            "Unknown: Android has not identified the output in use (multiple or ambiguous audio outputs), so no profile is applied.",
            HomeSummary.of(steps.last().second).applied
        )
        assertTrue(HomeSummary.of(steps[6].second).applied.contains("nothing measured for Bluetooth"))
    }

    @Test fun `a fault and a lost service are shown, then cleared`() {
        val steps = listOf(
            base.copy(playing = true),
            base.copy(faultMessage = "DynamicsProcessing refused", appliedEngine = null),
            base.copy(appliedEngine = "Equalizer"),
            // The process is killed: the stored engine and playing flag remain, the service does not.
            base.copy(serviceRunning = false, appliedEngine = "Equalizer", playing = true),
            base.copy(appliedEngine = null),
            base
        )
        assertEquals(
            listOf(State.APPLIED, State.FAULT, State.APPLIED, State.NOT_RUNNING, State.PENDING, State.APPLIED),
            steps.map { HomeSummary.stateOf(it) }
        )
        assertEquals("Not applied: DynamicsProcessing refused", HomeSummary.of(steps[1]).applied)
    }

    @Test fun `an untagged profile on an unnamed output says the output is unknown`() {
        val line = HomeSummary.of(base.copy(outputKnown = false, outputLabel = null)).applied
        assertTrue(line, line.startsWith("Applied by DynamicsProcessing · nothing is playing · output not identified by Android."))
    }
}
