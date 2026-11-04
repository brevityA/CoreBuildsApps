package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile

class RoomAwareGuidanceTest {

    private fun profile(
        transitionHz: Double = 400.0,
        vararg points: Pair<Double, Double>
    ): Profile {
        val curve = points.map { (hz, db) ->
            CurvePoint(hz, measuredDb = 0.0, correctionDb = db)
        }
        return Profile(
            id = "test",
            name = "Test",
            timestampMs = 0L,
            target = "flat",
            micType = "measurement mic",
            curve = curve,
            transitionHz = transitionHz
        )
    }

    @Test
    fun emptyCurveReturnsNothing() {
        val p = profile(transitionHz = 400.0)
        assertEquals(0, RoomAwareGuidance.guide(p, emptyList()).size)
    }

    @Test
    fun strongCutAboveTransitionSuggestsCut() {
        // 4 kHz is above the 400 Hz transition; a 5 dB cut suggests trimming.
        val p = profile(
            transitionHz = 400.0,
            4000.0 to -5.0
        )
        val g = RoomAwareGuidance.guide(p, emptyList())
        assertTrue(g.any { it.fc == 4000.0 && it.kind == RoomAwareGuidance.Guidance.Kind.SUGGEST_CUT })
    }

    @Test
    fun strongLiftAboveTransitionSuggestsBoost() {
        // 2 kHz is above the 400 Hz transition; a 5 dB lift suggests boosting.
        val p = profile(
            transitionHz = 400.0,
            2000.0 to 5.0
        )
        val g = RoomAwareGuidance.guide(p, emptyList())
        assertTrue(g.any { it.fc == 2000.0 && it.kind == RoomAwareGuidance.Guidance.Kind.SUGGEST_BOOST })
    }

    @Test
    fun smallCorrectionAboveTransitionSuggestsNothing() {
        // 2 dB cut at 4 kHz is below the 4 dB threshold.
        val p = profile(transitionHz = 400.0, 4000.0 to -2.0)
        val g = RoomAwareGuidance.guide(p, emptyList())
        assertTrue(g.all { it.fc != 4000.0 })
    }

    @Test
    fun modalBandCorrectionWarnsInsteadOfSuggests() {
        // 125 Hz is below the 400 Hz transition: warn, don't suggest.
        val p = profile(transitionHz = 400.0, 125.0 to 5.0)
        val g = RoomAwareGuidance.guide(p, emptyList())
        val at125 = g.firstOrNull { it.fc == 125.0 }
        assertNotNull(at125)
        assertEquals(RoomAwareGuidance.Guidance.Kind.WARN_AVOID, at125!!.kind)
    }

    @Test
    fun alreadyManualFilteredBandIsSkipped() {
        val p = profile(transitionHz = 400.0, 4000.0 to -5.0)
        val manual = listOf(PeakingFilter(4000.0, 1.0, -2.0))
        val g = RoomAwareGuidance.guide(p, manual)
        assertTrue(g.none { it.fc == 4000.0 })
    }

    @Test
    fun highTransitionAddsInfo() {
        val p = profile(transitionHz = 800.0, 4000.0 to -5.0)
        val g = RoomAwareGuidance.guide(p, emptyList())
        assertTrue(g.any { it.kind == RoomAwareGuidance.Guidance.Kind.INFO && it.message.contains("800") })
    }

    @Test
    fun guidanceIsSortedByImportance() {
        val p = profile(
            transitionHz = 400.0,
            125.0 to 5.0,    // warn (weight 1)
            4000.0 to -5.0   // suggest cut (weight 2)
        )
        val g = RoomAwareGuidance.guide(p, emptyList())
        assertTrue(g.size >= 2)
        // First should be the higher-weight suggestion (SUGGEST_CUT at 4000)
        assertEquals(4000.0, g[0].fc, 0.0)
        assertEquals(RoomAwareGuidance.Guidance.Kind.SUGGEST_CUT, g[0].kind)
        // Second should be the warning at 125 Hz
        assertEquals(125.0, g[1].fc, 0.0)
        assertEquals(RoomAwareGuidance.Guidance.Kind.WARN_AVOID, g[1].kind)
    }

    @Test
    fun theWordingFollowsTheSignOfTheCorrection() {
        // correctionDb is added to the measurement: negative is a cut where the
        // room is loud, positive a lift where it is weak. 1.11.0 read it the
        // other way round and called a 5 dB lift a cut.
        val g = RoomAwareGuidance.guide(
            profile(
                transitionHz = 400.0,
                125.0 to -5.0,   // modal peak, corrected by a cut
                250.0 to 5.0,    // modal dip, corrected by a lift
                2000.0 to 5.0,   // lift above the transition
                4000.0 to -6.0   // cut above the transition
            ),
            emptyList()
        )
        fun at(hz: Double) = g.single { it.fc == hz }.message
        assertTrue(at(125.0), at(125.0).startsWith("Room peak at 125 Hz"))
        assertTrue(at(250.0), at(250.0).startsWith("Room dip at 250 Hz"))
        assertTrue(at(2000.0), at(2000.0).startsWith("Correction lifts 5 dB at 2000 Hz"))
        assertTrue(at(4000.0), at(4000.0).startsWith("Correction cuts 6 dB at 4000 Hz"))
    }

    private fun assertNotNull(obj: Any?) {
        if (obj == null) throw AssertionError("expected non-null")
    }
}
