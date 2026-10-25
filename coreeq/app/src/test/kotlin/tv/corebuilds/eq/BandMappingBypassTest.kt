package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.ToneLayers
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.CorrectionSwitch

/**
 * The loudness-matched A/B bypass.
 *
 * Turning correction off by deleting the effect changes the level as well as
 * the shape, and the louder side nearly always wins a sighted comparison. The
 * bypass must hold the level where the correction put it.
 */
class BandMappingBypassTest {

    private val centres = listOf(60.0, 120.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0)

    private fun profile(curve: List<CurvePoint>, manual: List<PeakingFilter> = emptyList()) = Profile(
        id = "p",
        name = "Test",
        timestampMs = 0L,
        target = "dialogue",
        micType = "remote",
        curve = curve,
        manualFilters = manual
    )

    private val corrected = profile(
        listOf(
            CurvePoint(60.0, 0.0, -3.0),
            CurvePoint(120.0, 0.0, 2.0),
            CurvePoint(250.0, 0.0, -4.0),
            CurvePoint(500.0, 0.0, 0.5),
            CurvePoint(1000.0, 0.0, 0.0),
            CurvePoint(2000.0, 0.0, -1.0),
            CurvePoint(4000.0, 0.0, 1.5),
            CurvePoint(8000.0, 0.0, 0.0)
        )
    )

    @Test
    fun theBypassSitsAtTheMeanOfWhatTheCorrectionActuallyApplies() {
        // Not the raw requested mean: the corrected side is shifted down by its
        // own peak, so matching the raw mean would leave the bypass one preamp
        // louder than the correction — the exact bias this feature removes.
        val requested = BandMapping.requestedGainsDb(corrected, centres)
        val headroom = BandMapping.headroomDb(corrected, centres)
        val appliedMean = requested.average() - headroom
        assertTrue("the fixture must have a boost to shift", headroom > 0.0)
        assertEquals(
            "bypass must track the applied correction's mean",
            appliedMean, BandMapping.bypassDb(corrected, centres), 1e-9
        )
    }

    @Test
    fun theBypassNeverAsksForPositiveGain() {
        // A profile of pure boosts would otherwise bypass *louder* than the
        // corrected setting and win its own comparison.
        val allBoost = profile(
            listOf(CurvePoint(60.0, 0.0, 3.0), CurvePoint(120.0, 0.0, 4.0), CurvePoint(250.0, 0.0, 2.0),
                CurvePoint(500.0, 0.0, 3.0), CurvePoint(1000.0, 0.0, 5.0), CurvePoint(2000.0, 0.0, 4.0),
                CurvePoint(4000.0, 0.0, 3.0), CurvePoint(8000.0, 0.0, 2.0))
        )
        assertTrue("bypass was ${BandMapping.bypassDb(allBoost, centres)} dB",
            BandMapping.bypassDb(allBoost, centres) <= 0.0)
    }

    @Test
    fun theBypassIsFlatAcrossEveryBand() {
        val fit = BandMapping.fitBypassed(corrected, centres)
        for (value in fit.targetDb) {
            assertEquals(fit.targetDb[0], value, 1e-12)
            assertTrue("bypass band $value dB is positive", value <= 0.0)
        }
    }

    @Test
    fun theBypassTakesNoHeadroomOfItsOwn() {
        val fit = BandMapping.fitBypassed(corrected, centres)
        assertEquals(0.0, fit.headroomDb, 0.0)
        assertEquals(0, fit.rangeLimitedBands)
        assertEquals(0.0, fit.report().rmsCentreGainResidualDb, 0.0)
    }

    @Test
    fun runtimeToneLayersKeepTheComparisonFair() {
        // Dialogue boost and low-volume bass add gain at run time, and they add
        // it to *both* sides' headroom. The figure is not "the bypass gets
        // louder with layers" — a boost at one band can lower the applied mean,
        // because everything is shifted down by the new peak. What must hold is
        // that the two sides still average the same level.
        for (layers in listOf(
            ToneLayers(dialogue = true, lowVolumeBassDb = 3.0),
            ToneLayers(dialogue = true),
            ToneLayers(lowVolumeBassDb = 6.0)
        )) {
            val correction = BandMapping.fitToBackend(corrected, centres, layers)
            val bypass = BandMapping.fitBypassed(corrected, centres, layers)
            assertEquals(
                "layers broke level matching",
                correction.targetDb.average(), bypass.targetDb.average(), 1e-9
            )
            assertTrue("the bypass went positive", bypass.targetDb.max() <= 0.0)
        }
    }

    @Test
    fun aManualOnlyProfileWithPureCutsStillMatchesLevel() {
        val cuts = profile(
            curve = emptyList(),
            manual = listOf(
                PeakingFilter(250.0, 1.0, -3.0),
                PeakingFilter(4000.0, 1.0, -2.0)
            )
        )
        // No boost anywhere, so there is no headroom shift to allow for.
        val requested = BandMapping.requestedGainsDb(cuts, centres)
        assertEquals(0.0, BandMapping.headroomDb(cuts, centres), 1e-12)
        assertEquals(requested.average(), BandMapping.bypassDb(cuts, centres), 1e-9)
        assertTrue(BandMapping.bypassDb(cuts, centres) < 0.0)
    }

    @Test
    fun anEmptyBandListIsRefusedRatherThanDividingByZero() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            BandMapping.bypassDb(corrected, emptyList())
        }
    }

    // ---- the switch the service reads (rules, not storage: see CorrectionSwitch) ----

    @Test
    fun theBypassSwitchIsOffUntilCorrectionIsOn() {
        assertFalse("bypass must not engage with correction off", CorrectionSwitch.active(false, true))
        assertFalse("a request made with correction off must not arm it", CorrectionSwitch.storeRequest(false, true))
        assertTrue("bypass must engage once correction is on", CorrectionSwitch.active(true, true))
        assertTrue(CorrectionSwitch.storeRequest(true, true))
    }

    @Test
    fun switchingCorrectionOffClearsTheBypassRatherThanHidingIt() {
        // Stored was true, correction goes off, then back on.
        val afterOff = CorrectionSwitch.storeAfterCorrectionChange(false, true)
        assertFalse("the bypass was kept while correction was off", afterOff)
        val afterOn = CorrectionSwitch.storeAfterCorrectionChange(true, afterOff)
        // Switching back on must not silently recreate a bypass the listener has
        // since forgotten: that reads as "correction stopped working".
        assertFalse("the bypass survived a correction off/on cycle", CorrectionSwitch.active(true, afterOn))
    }

    @Test
    fun theSwitchReadsBackAsWhatTheServiceWillApply() {
        // The service asks `active`, so the getter and the effect agree by
        // construction; this pins the two states the UI alternates between.
        assertFalse(CorrectionSwitch.active(true, CorrectionSwitch.storeRequest(true, false)))
        assertTrue(CorrectionSwitch.active(true, CorrectionSwitch.storeRequest(true, true)))
        assertFalse(CorrectionSwitch.active(false, CorrectionSwitch.storeRequest(false, false)))
    }

    // ---- the fit the service applies when bypassed ----

    @Test
    fun theServiceBypassPathIsTheSameFitAsFitBypassed() {
        val a = BandMapping.fitBypassed(corrected, centres)
        val b = BandMapping.fitToBackend(corrected, centres, side = BandMapping.AbSide.BYPASS)
        assertEquals(a.headroomDb, b.headroomDb, 0.0)
        assertEquals(a.targetDb.toList(), b.targetDb.toList())
        assertEquals(a.configuredDb.toList(), b.configuredDb.toList())
    }

    @Test
    fun theCorrectedSideIsUnchangedByTheNewParameter() {
        val explicit = BandMapping.fitToBackend(corrected, centres, side = BandMapping.AbSide.CORRECTION)
        val implicit = BandMapping.fitToBackend(corrected, centres)
        assertEquals(implicit.targetDb.toList(), explicit.targetDb.toList())
        assertEquals(implicit.headroomDb, explicit.headroomDb, 0.0)
    }

    @Test
    fun theBypassIsLevelMatchedToTheCorrectionNotToZero() {
        // The correction's own average, so the two sides play at one level.
        val correctedFit = BandMapping.fitToBackend(corrected, centres)
        val bypassFit = BandMapping.fitBypassed(corrected, centres)
        assertEquals(
            "the two sides must average the same level",
            correctedFit.targetDb.average(), bypassFit.targetDb.average(), 1e-9
        )
        assertTrue("a corrected curve must peak at 0 dB", correctedFit.targetDb.max() <= 1e-9)
        assertTrue("the bypass must not be silent", bypassFit.targetDb[0] < -0.5)
    }

    @Test
    fun theBypassIsNotSimplyASilentCurve() {
        // A flat 0 dB "bypass" would be a level change dressed up as a
        // comparison; a flat -30 dB one would be an obvious attenuation.
        val bypass = BandMapping.fitBypassed(corrected, centres)
        assertTrue("bypass at ${bypass.targetDb[0]} dB is not level-matched",
            bypass.targetDb[0] in -6.0..0.0)
    }

    @Test
    fun aBackendGainRangeClampsTheBypassToo() {
        val fit = BandMapping.fitBypassed(corrected, centres, minGainDb = -1.0, maxGainDb = 0.0)
        for (value in fit.configuredDb) {
            assertTrue("configured $value dB is outside the backend range", value in -1.0..0.0)
        }
        assertTrue("the clamp must be reported", fit.rangeLimitedBands > 0)
    }
}
