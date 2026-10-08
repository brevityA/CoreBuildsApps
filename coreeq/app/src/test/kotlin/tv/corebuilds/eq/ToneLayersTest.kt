package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.LowVolumeBass
import tv.corebuilds.eq.apply.ToneLayers
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile

/**
 * 1.3.0's Dialogue boost and Low-volume bass: the shapes the research set,
 * the cases where they stand aside, and that their boost goes through the
 * same headroom as correction.
 */
class ToneLayersTest {

    private fun profile(target: String = "flat", rolloffHz: Double = 40.0, manualOnly: Boolean = false) = Profile(
        id = "t", name = "t", timestampMs = 0L, target = target, micType = "test",
        rolloffHz = rolloffHz, manualOnly = manualOnly,
        curve = listOf(CurvePoint(20.0, 0.0, 0.0), CurvePoint(20_000.0, 0.0, 0.0))
    )

    @Test
    fun `nothing switched on adds nothing anywhere`() {
        assertTrue(ToneLayers.NONE.isEmpty)
        for (hz in listOf(30.0, 100.0, 1000.0, 3000.0, 10_000.0)) {
            assertEquals(0.0, ToneLayers.NONE.responseDb(profile(), hz), 0.0)
        }
    }

    @Test
    fun `dialogue is plus 2 dB across 2_2 to 4_5 kHz and neutral by 6_5 kHz`() {
        for (hz in listOf(2200.0, 3000.0, 4500.0)) assertEquals(2.0, ToneLayers.dialogueDb(hz), 1e-9)
        for (hz in listOf(100.0, 1000.0, 1500.0, 6500.0, 10_000.0)) assertEquals(0.0, ToneLayers.dialogueDb(hz), 1e-9)
        val edge = ToneLayers.dialogueDb(5500.0)
        assertTrue("5.5 kHz is on the way down: $edge", edge > 0.0 && edge < 2.0)
    }

    @Test
    fun `dialogue stands aside where the measured target already carries presence`() {
        val layers = ToneLayers(dialogue = true)
        assertFalse(layers.dialogueApplies(profile("dialogue")))
        assertFalse(layers.dialogueApplies(profile("house")))
        assertTrue(layers.dialogueApplies(profile("flat")))
        assertTrue(layers.dialogueApplies(profile("bk")))
        // A manual-only profile has no measured target curve under it.
        assertTrue(layers.dialogueApplies(profile("dialogue", manualOnly = true)))
        assertEquals(0.0, layers.responseDb(profile("dialogue"), 3000.0), 0.0)
    }

    @Test
    fun `low-volume bass is zero at or above the reference`() {
        assertEquals(0.0, ToneLayers.lowVolumeBassDb(0.0), 0.0)
        assertEquals(0.0, ToneLayers.lowVolumeBassDb(-6.0), 0.0)
        assertEquals(0.0, ToneLayers.lowVolumeBassDb(Double.NaN), 0.0)
    }

    @Test
    fun `low-volume bass grows with the drop, in half-dB steps, capped at 3 dB`() {
        var last = 0.0
        for (drop in 1..60) {
            val lift = ToneLayers.lowVolumeBassDb(drop.toDouble())
            assertTrue("never shrinks as the volume drops: $drop dB -> $lift", lift >= last)
            assertTrue("capped: $lift", lift <= ToneLayers.MAX_LOW_VOLUME_BASS_DB)
            assertEquals("half-dB steps: $lift", 0.0, (lift / ToneLayers.STEP_DB) % 1.0, 1e-9)
            last = lift
        }
        assertEquals(ToneLayers.MAX_LOW_VOLUME_BASS_DB, ToneLayers.lowVolumeBassDb(40.0), 0.0)
        // Half the ISO 226 difference at 63 Hz: about 3.8 dB at 10 dB down, so 1.5 dB.
        assertEquals(1.5, ToneLayers.lowVolumeBassDb(10.0), 0.0)
    }

    @Test
    fun `the shelf lives below 100 Hz and leaves dialogue alone`() {
        val layers = ToneLayers(lowVolumeBassDb = 3.0)
        val p = profile(rolloffHz = 30.0)
        assertTrue(layers.responseDb(p, 50.0) > 2.9)
        assertEquals(1.5, layers.responseDb(p, 100.0), 0.01)
        assertTrue(layers.responseDb(p, 250.0) < 0.1)
        assertEquals(0.0, layers.responseDb(p, 1000.0), 1e-6)
        assertEquals(0.0, layers.responseDb(p, 3000.0), 1e-6)
    }

    @Test
    fun `the shelf never boosts what the speaker cannot play`() {
        val layers = ToneLayers(lowVolumeBassDb = 3.0)
        val p = profile(rolloffHz = 80.0)
        assertEquals(0.0, layers.responseDb(p, 80.0 / Math.sqrt(2.0)), 1e-9)
        assertEquals(0.0, layers.responseDb(p, 40.0), 1e-9)
        assertTrue(layers.responseDb(p, 70.0) < layers.responseDb(p, 80.0))
    }

    @Test
    fun `a layer's boost is taken out of headroom, never added on top`() {
        val p = profile()
        val centres = listOf(100.0, 1000.0, 3000.0)
        val plain = BandMapping.gainsDb(p, centres)
        assertEquals(listOf(0.0, 0.0, 0.0), plain.toList())
        val withDialogue = BandMapping.gainsDb(p, centres, ToneLayers(dialogue = true))
        assertEquals(0.0, withDialogue[2], 1e-9)
        assertEquals(-2.0, withDialogue[1], 1e-9)
        assertTrue(withDialogue.all { it <= 0.0 })
        assertEquals(2.0, BandMapping.headroomDb(p, centres, ToneLayers(dialogue = true)), 1e-9)
    }

    @Test
    fun `an output with no reference records its first reported volume and adds nothing yet`() {
        val first = LowVolumeBass.settle(storedDb = null, nowDb = -22.0)
        assertTrue(first.capture)
        assertEquals(-22.0, first.referenceDb!!, 0.0)
        assertEquals(0.0, first.liftDb, 0.0)
    }

    @Test
    fun `turning that output down from its reference adds lift, and a known reference is kept`() {
        val settled = LowVolumeBass.settle(storedDb = -12.0, nowDb = -22.0)
        assertFalse(settled.capture)
        assertEquals(-12.0, settled.referenceDb!!, 0.0)
        assertEquals(ToneLayers.lowVolumeBassDb(10.0), settled.liftDb, 0.0)
        assertTrue(settled.liftDb > 0.0)
    }

    @Test
    fun `no reported volume records nothing and adds nothing`() {
        val unknown = LowVolumeBass.settle(storedDb = null, nowDb = null)
        assertFalse(unknown.capture)
        assertEquals(null, unknown.referenceDb)
        assertEquals(0.0, unknown.liftDb, 0.0)
        assertEquals(0.0, LowVolumeBass.settle(storedDb = -12.0, nowDb = null).liftDb, 0.0)
    }

    @Test
    fun `status words name only what is applied`() {
        assertEquals("", ToneLayers.NONE.label(profile()))
        assertEquals("dialogue +2 dB", ToneLayers(dialogue = true).label(profile()))
        assertEquals("", ToneLayers(dialogue = true).label(profile("dialogue")))
        assertEquals(
            "dialogue +2 dB + low-volume bass +1.5 dB",
            ToneLayers(dialogue = true, lowVolumeBassDb = 1.5).label(profile())
        )
    }
}
