package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.Profile

class ManualEqTest {

    @Test
    fun manualControlsClampSnapAndRemoveZeroGainFilters() {
        val clamped = ManualEq.withBandGain(emptyList(), 2, 8.0)
        assertEquals(1, clamped.size)
        assertEquals(125.0, clamped.single().fc, 0.0)
        assertEquals(6.0, clamped.single().gain, 0.0)

        val snapped = ManualEq.withBandGain(emptyList(), 3, -5.7)
        assertEquals(-5.5, snapped.single().gain, 0.0)
        assertTrue(ManualEq.withBandGain(snapped, 3, 0.0).isEmpty())
    }

    @Test
    fun manualResponseIsExactAtTheFilterCentreAndZeroOutsideTrustedSpan() {
        val filters = listOf(PeakingFilter(1_000.0, 1.0, 4.0))
        assertEquals(4.0, ManualEq.responseDb(filters, 1_000.0), 1e-9)
        assertEquals(0.0, ManualEq.responseDb(filters, 8_001.0), 0.0)
        assertEquals(0.0, ManualEq.responseDb(filters, 39.0), 0.0)
    }

    @Test
    fun legacyMigrationPreservesUntouchedBandsAndPrefersExistingOverlayValues() {
        val legacy = listOf(
            PeakingFilter(1_000.0, 1.0, 2.0),
            PeakingFilter(2_000.0, 1.0, -1.0)
        )
        val existingEveryday = listOf(
            PeakingFilter(1_000.0, 1.2, 4.0),
            PeakingFilter(4_000.0, 1.0, 1.0)
        )

        val merged = ManualEq.mergeLegacyWithOverlay(legacy, existingEveryday)
        assertEquals(3, merged.size)
        assertEquals(4.0, ManualEq.gainAtBand(merged, 5), 0.0)
        assertEquals(-1.0, ManualEq.gainAtBand(merged, 6), 0.0)
        assertEquals(1.0, ManualEq.gainAtBand(merged, 7), 0.0)
    }

    @Test
    fun allBuiltInPresetsAreUsableAndWithinTheManualGainLimit() {
        assertEquals("Flat", ManualEq.BUILT_IN_PRESETS.first().name)
        assertTrue(ManualEq.BUILT_IN_PRESETS.size >= 4)
        for (preset in ManualEq.BUILT_IN_PRESETS) {
            assertTrue(preset.name.isNotBlank())
            assertTrue(preset.filters.all { it.gain in ManualEq.MIN_GAIN_DB..ManualEq.MAX_GAIN_DB })
        }
        assertFalse(ManualEq.BUILT_IN_PRESETS.first { it.id == "speech-clarity" }.filters.isEmpty())
    }

    @Test
    fun manualBoostIsAppliedAndHeadroomShiftedAcrossEveryEngineBand() {
        val profile = Profile(
            id = "manual-test",
            name = "Manual",
            timestampMs = 1L,
            target = "flat",
            micType = "not measured",
            manualFilters = listOf(PeakingFilter(1_000.0, 1.0, 3.0)),
            manualOnly = true
        )
        val centres = listOf(1_000.0, 2_000.0, 10_000.0)

        assertEquals(3.0, BandMapping.headroomDb(profile, centres), 1e-9)
        val applied = BandMapping.gainsDb(profile, centres)
        assertEquals(0.0, applied[0], 1e-9)
        assertTrue(applied[1] < 0.0)
        assertEquals(-3.0, applied[2], 1e-9)
        assertTrue(applied.all { it <= 0.0 })
    }

    @Test
    fun recommendedPreampTracksManualLayerWithoutLosingRoomReserve() {
        val room = Profile(
            id = "room-preamp",
            name = "Room",
            timestampMs = 1L,
            target = "flat",
            micType = "not measured",
            preampDb = -2.0
        )
        val boosted = room.copy(manualFilters = listOf(PeakingFilter(1_000.0, 1.0, 3.0)))

        assertEquals(-3.0, Formats.recommendedPreampDb(boosted), 1e-9)
        assertEquals(-2.0, Formats.recommendedPreampDb(boosted.copy(manualFilters = emptyList())), 1e-9)
    }

    @Test
    fun graphicEqHeadroomUsesTheExportedCurveAndPreservesTheRoomReserve() {
        val room = Profile(
            id = "graphic-curve-headroom",
            name = "Room",
            timestampMs = 1L,
            target = "flat",
            micType = "not measured",
            preampDb = -0.5,
            filters = listOf(PeakingFilter(1_000.0, 1.0, 0.5)),
            curve = listOf(CurvePoint(1_000.0, 0.0, 6.0)),
            manualFilters = listOf(PeakingFilter(1_000.0, 1.0, 0.5))
        )

        val graphic = Formats.exportGraphicEq(room)
        assertTrue(graphic.startsWith("Preamp: -6.50 dB\nGraphicEQ: "))
        assertEquals(-6.5, Formats.graphicEqPreampDb(room)!!, 1e-9)
        assertTrue(graphic.contains("1000 +6.50"))

        val roomReserve = Formats.exportGraphicEq(room.copy(preampDb = -8.0))
        assertTrue(roomReserve.startsWith("Preamp: -8.00 dB\nGraphicEQ: "))
        assertEquals(-8.0, Formats.graphicEqPreampDb(room.copy(preampDb = -8.0))!!, 1e-9)
        assertEquals(null, Formats.graphicEqPreampDb(room.copy(manualFilters = emptyList())))
    }

    @Test
    fun exportsCarryTheManualFiltersAndTheirHeadroom() {
        val profile = Profile(
            id = "manual-export",
            name = "Manual",
            timestampMs = 1L,
            target = "flat",
            micType = "not measured",
            manualFilters = listOf(PeakingFilter(1_000.0, 1.0, 3.0)),
            manualOnly = true
        )

        val parametric = Formats.exportParametricTxt(profile)
        assertTrue(parametric.startsWith("Preamp: -3.00 dB"))
        assertTrue(parametric.contains("manual EQ filters are included"))
        assertTrue(parametric.contains("Filter 1: ON PK Fc 1000 Hz Gain +3.00 dB Q 1.00"))

        val graphic = Formats.exportGraphicEq(profile)
        assertTrue(graphic.startsWith("Preamp: -3.00 dB\nGraphicEQ: "))
        assertTrue(graphic.contains("1000 +3.00"))
    }
}
