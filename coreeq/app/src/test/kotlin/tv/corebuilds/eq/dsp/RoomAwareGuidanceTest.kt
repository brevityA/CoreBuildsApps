package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.RoomAwareGuidance.Guidance.Kind
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile

class RoomAwareGuidanceTest {

    /** A profile whose correction is [corrections] at the band centres named, 0 dB at the rest. */
    private fun profile(transitionHz: Double = 300.0, corrections: Map<Double, Double> = emptyMap(), manualOnly: Boolean = false) =
        Profile(
            id = "test",
            name = "Test",
            timestampMs = 0L,
            target = "flat",
            micType = "remote mic",
            curve = ManualEq.BAND_CENTRES_HZ.map { CurvePoint(it, measuredDb = 0.0, correctionDb = corrections[it] ?: 0.0) },
            transitionHz = transitionHz,
            manualOnly = manualOnly
        )

    private fun band(hz: Double) = ManualEq.BAND_CENTRES_HZ.indexOf(hz)

    private fun manual(vararg bands: Pair<Double, Double>): List<PeakingFilter> =
        bands.fold(emptyList()) { filters, (hz, db) -> ManualEq.withBandGain(filters, band(hz), db) }

    @Test
    fun noCurveOrNoMeasurementMeansNoHints() {
        val empty = Profile(id = "e", name = "E", timestampMs = 0L, target = "flat", micType = "remote mic")
        assertTrue(RoomAwareGuidance.guide(empty, emptyList()).isEmpty())
        assertTrue(RoomAwareGuidance.guide(profile(corrections = mapOf(2_000.0 to -3.0), manualOnly = true), emptyList()).isEmpty())
    }

    @Test
    fun aShapingCorrectionNearItsLimitIsOfferedBackTheOtherWay() {
        val g = RoomAwareGuidance.guide(profile(corrections = mapOf(2_000.0 to -3.0, 4_000.0 to 3.0)), emptyList())
        val cut = g.single { it.bandIndex == band(2_000.0) }
        assertEquals(Kind.EASE_CUT, cut.kind)
        assertEquals(1.0, cut.applyGainDb!!, 0.0)
        assertEquals("Ease the cut at 2 kHz: +1.0 dB", cut.title)
        assertTrue(cut.detail, cut.detail.startsWith("The correction cuts 3.0 dB here"))
        val lift = g.single { it.bandIndex == band(4_000.0) }
        assertEquals(Kind.EASE_LIFT, lift.kind)
        assertEquals(-1.0, lift.applyGainDb!!, 0.0)
        assertEquals("Ease the lift at 4 kHz: -1.0 dB", lift.title)
    }

    @Test
    fun noHintEverAddsTheCorrectionsOwnMoveAgain() {
        // 1.11.0's guidance answered a cut with "a manual trim can soften it" under a
        // SUGGEST_CUT kind, and a lift with "a manual boost can reinforce it": doubling
        // what the correction already does. Every Ease points the other way.
        val corrections = mapOf(500.0 to -6.0, 1_000.0 to 3.0, 2_000.0 to -2.5, 4_000.0 to 2.8, 8_000.0 to -3.0)
        val g = RoomAwareGuidance.guide(profile(corrections = corrections), emptyList())
        val eases = g.filter { it.kind == Kind.EASE_CUT || it.kind == Kind.EASE_LIFT }
        assertTrue(eases.isNotEmpty())
        for (hint in eases) {
            val correction = corrections.getValue(ManualEq.BAND_CENTRES_HZ[hint.bandIndex!!])
            assertTrue(hint.title, hint.applyGainDb!! * correction < 0.0)
        }
    }

    @Test
    fun easeIsAboutAThirdOnTheEditorsGrid() {
        assertEquals(1.0, RoomAwareGuidance.easeDb(-2.5), 0.0) // 0.83 rounds up to the 1 dB floor
        assertEquals(1.0, RoomAwareGuidance.easeDb(-3.0), 0.0)
        assertEquals(1.5, RoomAwareGuidance.easeDb(-4.5), 0.0)
        assertEquals(2.0, RoomAwareGuidance.easeDb(-6.0), 0.0)
        assertEquals(2.0, RoomAwareGuidance.easeDb(-11.0), 0.0) // capped
        assertEquals(-1.5, RoomAwareGuidance.easeDb(4.5), 0.0)
    }

    @Test
    fun smallShapingIsLeftAlone() {
        val g = RoomAwareGuidance.guide(profile(corrections = mapOf(2_000.0 to -2.4, 4_000.0 to 2.0)), emptyList())
        assertTrue(g.toString(), g.isEmpty())
    }

    @Test
    fun onlyTheThreeLargestEasesAreListed() {
        val g = RoomAwareGuidance.guide(
            profile(corrections = mapOf(500.0 to -2.6, 1_000.0 to -3.5, 2_000.0 to 3.0, 4_000.0 to -2.8, 8_000.0 to 2.7)),
            emptyList()
        )
        assertEquals(listOf(band(1_000.0), band(2_000.0), band(4_000.0)), g.map { it.bandIndex })
    }

    @Test
    fun modesBelowTheTransitionBecomeOneCautionWithNothingToApply() {
        val g = RoomAwareGuidance.guide(
            profile(transitionHz = 300.0, corrections = mapOf(63.0 to -6.0, 125.0 to 4.0, 250.0 to -2.0)),
            emptyList()
        )
        val modal = g.single()
        assertEquals(Kind.MODAL, modal.kind)
        assertFalse(modal.actionable)
        assertNull(modal.bandIndex)
        assertEquals("Leave 63 Hz and 125 Hz to the correction", modal.title)
        assertTrue(modal.detail, modal.detail.contains("300 Hz transition"))
        assertTrue(modal.detail, modal.detail.contains("63 Hz -6.0 dB, 125 Hz +4.0 dB"))
    }

    @Test
    fun aManualBandPushingTheSameWayAsTheCorrectionIsOfferedForUndo() {
        val corrections = mapOf(63.0 to 5.5, 500.0 to -7.0, 1_000.0 to -7.0)
        val g = RoomAwareGuidance.guide(
            profile(corrections = corrections),
            manual(63.0 to 3.0, 500.0 to -1.5, 1_000.0 to 2.0)
        )
        val lift = g.single { it.bandIndex == band(63.0) }
        assertEquals(Kind.STACKED, lift.kind)
        assertEquals(0.0, lift.applyGainDb!!, 0.0)
        assertEquals("Undo your +3.0 dB at 63 Hz", lift.title)
        assertTrue(lift.detail, lift.detail.contains("+8.5 dB"))
        val cut = g.single { it.bandIndex == band(500.0) }
        assertEquals(Kind.STACKED, cut.kind)
        assertTrue(cut.detail, cut.detail.contains("-8.5 dB, more than the room asked for"))
        // +2 dB against a 7 dB cut eases it: the listener's call, not a hint.
        assertTrue(g.none { it.bandIndex == band(1_000.0) })
    }

    @Test
    fun aBandTheListenerHasSetGetsNoEaseOrModalHint() {
        val g = RoomAwareGuidance.guide(
            profile(corrections = mapOf(63.0 to -6.0, 2_000.0 to -3.0)),
            manual(63.0 to -0.5, 2_000.0 to 0.5)
        )
        assertTrue(g.toString(), g.isEmpty())
    }

    @Test
    fun stackedComesFirstThenEaseThenTheModalCaution() {
        val g = RoomAwareGuidance.guide(
            profile(corrections = mapOf(63.0 to 5.5, 125.0 to -5.0, 2_000.0 to -3.0)),
            manual(63.0 to 3.0)
        )
        assertEquals(listOf(Kind.STACKED, Kind.EASE_CUT, Kind.MODAL), g.map { it.kind })
        assertEquals("Leave 125 Hz to the correction", g.last().title)
    }

    @Test
    fun bandsAreNamedAsTheEditorLabelsThem() {
        assertEquals("40 Hz", RoomAwareGuidance.hz(40.0))
        assertEquals("1 kHz", RoomAwareGuidance.hz(1_000.0))
        assertEquals("6.3 kHz", RoomAwareGuidance.hz(6_300.0))
        assertEquals("287 Hz", RoomAwareGuidance.hz(287.4))
    }

    @Test
    fun anOffCentreFilterCountsAtTheBandsItShapes() {
        // An imported filter at 2.2 kHz sits between the 2 kHz and 4 kHz bands. It shapes
        // 2 kHz, so an ease hint there would add a second trim on top of it.
        val room = profile(corrections = mapOf(2_000.0 to -3.0))
        assertTrue(RoomAwareGuidance.guide(room, emptyList()).any { it.kind == Kind.EASE_CUT && it.bandIndex == band(2_000.0) })
        val imported = listOf(PeakingFilter(2_200.0, 1.4, -3.0))
        assertFalse(RoomAwareGuidance.guide(room, imported).any { it.kind == Kind.EASE_CUT && it.bandIndex == band(2_000.0) })
        // A centred band's own overlap does not count at its neighbours.
        assertEquals(0.0, RoomAwareGuidance.manualAt(manual(1_000.0 to 3.0), band(2_000.0), 2_000.0), 1e-9)
        // Far away it does not reach, and the band keeps its hint.
        val far = listOf(PeakingFilter(60.0, 1.4, -3.0))
        assertTrue(RoomAwareGuidance.guide(room, far).any { it.kind == Kind.EASE_CUT && it.bandIndex == band(2_000.0) })
    }
}
