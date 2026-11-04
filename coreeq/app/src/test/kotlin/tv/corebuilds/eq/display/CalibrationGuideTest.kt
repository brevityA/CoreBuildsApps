package tv.corebuilds.eq.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.display.CalibrationGuide.PatternId

/**
 * The calibration guide's model (1.9.0).
 *
 * What is worth pinning: the guide is a coherent graph (every branch leads somewhere that
 * exists, every step is reachable, the last step ends), every step has at least one answer
 * that finishes it, the display notes say only what the reported facts support, and no step
 * sends the user to a picture menu for a fault that is not a setting.
 */
class CalibrationGuideTest {

    private val steps = CalibrationGuide.STEPS

    @Test
    fun `the guide is a graph that actually holds together`() {
        val ids = steps.map { it.id }
        assertEquals("step ids are unique", ids.size, ids.toSet().size)
        assertEquals("the first step is the picture mode", "mode", steps.first().id)
        assertEquals("the last step is the record step", "record", steps.last().id)
        assertNull("nothing comes after the last step", CalibrationGuide.next("record"))

        for (step in steps) {
            assertTrue("${step.id} has no title", step.title.isNotBlank())
            assertTrue("${step.id} has no instruction", step.instruction.length > 40)
            assertTrue("${step.id} has no answers", step.branches.size >= 2 || step.id == "record")
            assertTrue(
                "${step.id} has no answer that resolves it",
                step.happyPath == null || step.branches.any { it.label == step.happyPath }
            )
            for (branch in step.branches) {
                assertTrue("${step.id}: blank branch label", branch.label.isNotBlank())
                assertTrue("${step.id}: blank branch advice", branch.advice.length > 20)
                branch.nextStepId?.let { next ->
                    assertNotNull("${step.id} → $next does not exist", CalibrationGuide.step(next))
                }
            }
        }
    }

    @Test
    fun `every step is reachable from the first one along the ordinary path`() {
        var current = CalibrationGuide.step(CalibrationGuide.FIRST_STEP_ID)
        val visited = mutableListOf<String>()
        while (current != null) {
            visited += current.id
            current = CalibrationGuide.next(current.id)
        }
        assertEquals("the ordinary path must visit every step", steps.map { it.id }, visited)
    }

    @Test
    fun `every step offers an answer that moves on`() {
        for (step in steps) {
            assertTrue(
                "${step.id} has no resolved branch",
                step.branches.any { it.resolved }
            )
        }
    }

    @Test
    fun `overscan comes before anything judged against the screen edge`() {
        val ids = steps.map { it.id }
        assertTrue(ids.indexOf("overscan") < ids.indexOf("black"))
        assertTrue(ids.indexOf("overscan") < ids.indexOf("sharpness"))
        // Motion is last of the picture steps, after the panel is settled.
        assertTrue(ids.indexOf("motion") > ids.indexOf("sharpness"))
    }

    @Test
    fun `the pattern steps carry the pattern that shows the fault`() {
        assertEquals(PatternId.BLACK_LEVEL, CalibrationGuide.step("black")!!.pattern)
        assertEquals(PatternId.WHITE_LEVEL, CalibrationGuide.step("white")!!.pattern)
        assertEquals(PatternId.GAMMA, CalibrationGuide.step("gamma")!!.pattern)
        assertEquals(PatternId.OVERSCAN, CalibrationGuide.step("overscan")!!.pattern)
        assertEquals(PatternId.MOTION_JUDDER, CalibrationGuide.step("motion")!!.pattern)
        assertNull("the mode step is instruction only", CalibrationGuide.step("mode")!!.pattern)
        // Every pattern the guide names is one the view can draw.
        val drawn = PatternId.entries.toSet()
        for (step in steps) step.pattern?.let { assertTrue("$it is not drawable", it in drawn) }
    }

    @Test
    fun `a fault that is not a setting says so instead of sending the user to a menu`() {
        val motion = CalibrationGuide.step("motion")!!
        val smear = motion.branches.first { it.label.contains("smear") }
        assertTrue("panel response time", smear.advice.contains("response time"))
        assertTrue("must not send them to a setting", smear.advice.contains("not a setting"))

        val uniformity = CalibrationGuide.step("uniformity")!!
        val blotches = uniformity.branches.first { it.label.contains("blotch") }
        assertTrue("DSE is the panel", blotches.advice.contains("panel"))
    }

    @Test
    fun `a branch that keeps adjusting does not advance`() {
        val black = CalibrationGuide.step("black")!!
        val tooHigh = black.branches.first { it.label.contains("glows") }
        assertNull("keep adjusting must not move on", tooHigh.nextStepId)
        assertFalse(tooHigh.resolved)
    }

    @Test
    fun `the facts line reports what was given and nothing more`() {
        val facts = CalibrationGuide.DisplayFacts(1920, 1080, 59.94, listOf("HDR10", "HLG"))
        val line = CalibrationGuide.factsLine(facts)
        println(line)
        assertTrue(line.contains("1920×1080"))
        assertTrue(line.contains("59.94 Hz"))
        assertTrue(line.contains("HDR10"))
        assertFalse("no invented rate", line.contains("60.00 Hz"))

        val unknown = CalibrationGuide.factsLine(CalibrationGuide.DisplayFacts(0, 0, 0.0))
        assertTrue(unknown.contains("unknown"))
        assertTrue(unknown.contains("no HDR"))
    }

    @Test
    fun `a 1080p interface on a 4K panel is called Android's resolution choice`() {
        val facts = CalibrationGuide.DisplayFacts(
            widthPx = 1920, heightPx = 1080, refreshHz = 60.0,
            hdrTypes = listOf("HDR10"),
            panelWidthPx = 3840, panelHeightPx = 2160
        )
        val notes = CalibrationGuide.factsNotes(facts)
        println(notes.joinToString("\n"))
        val ui = notes.firstOrNull { it.contains("4K panel") }
        assertNotNull("the upscaling must be explained", ui)
        assertTrue("it must not be blamed on a picture setting", ui!!.contains("not a picture setting"))
        assertTrue("and the pattern limit must be stated", ui.contains("1-pixel"))
    }

    @Test
    fun `a native 4K interface gets no resolution note`() {
        val facts = CalibrationGuide.DisplayFacts(
            widthPx = 3840, heightPx = 2160, refreshHz = 60.0,
            hdrTypes = listOf("HDR10"), panelWidthPx = 3840, panelHeightPx = 2160
        )
        assertTrue(CalibrationGuide.factsNotes(facts).none { it.contains("4K panel") })
    }

    @Test
    fun `no HDR is reported as no HDR, not as a fault of the TV`() {
        val facts = CalibrationGuide.DisplayFacts(1920, 1080, 60.0)
        val note = CalibrationGuide.factsNotes(facts).first { it.contains("HDR") }
        assertTrue(note.contains("no HDR"))
        assertTrue("names the likely cause without asserting it", note.contains("If the TV does support"))
    }

    @Test
    fun `an odd refresh rate is called out and a film multiple is not`() {
        val odd = CalibrationGuide.DisplayFacts(1920, 1080, 50.0, listOf("HDR10"))
        assertTrue(CalibrationGuide.factsNotes(odd).any { it.contains("24p") })

        val film = CalibrationGuide.DisplayFacts(1920, 1080, 48.0, listOf("HDR10"))
        assertTrue("48 Hz is a multiple of 24", film.refreshIsFilmMultiple)
        assertTrue(CalibrationGuide.factsNotes(film).none { it.contains("24p") })

        val normal = CalibrationGuide.DisplayFacts(1920, 1080, 59.94, listOf("HDR10"))
        assertTrue(CalibrationGuide.factsNotes(normal).none { it.contains("judder unevenly") })
    }

    @Test
    fun `the guide never claims to measure anything`() {
        for (step in steps) {
            val text = step.instruction + " " + step.branches.joinToString(" ") { it.advice }
            for (forbidden in listOf("we measure", "calibrated to", "measured your", "guaranteed")) {
                assertFalse("${step.id} says '$forbidden'", text.lowercase().contains(forbidden))
            }
        }
    }
}
