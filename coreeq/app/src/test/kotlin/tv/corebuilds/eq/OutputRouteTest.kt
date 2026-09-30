package tv.corebuilds.eq

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.apply.OutputRoute.BLUETOOTH
import tv.corebuilds.eq.apply.OutputRoute.HDMI
import tv.corebuilds.eq.apply.OutputRoute.HDMI_ARC
import tv.corebuilds.eq.apply.OutputRoute.SPEAKER
import tv.corebuilds.eq.export.Profile

/** Correction follows the output: a profile belongs to the chain it was measured on. */
class OutputRouteTest {

    private fun profile(id: String, at: Long, kind: String?) =
        Profile(id = id, name = id, timestampMs = at, target = "flat", micType = "test", outputKind = kind)

    private val speakers = profile("speakers", 100, SPEAKER)
    private val soundbar = profile("soundbar", 200, HDMI_ARC)
    private val legacy = profile("legacy", 50, null)

    @Test
    fun outputTypesMapToTheChainTheyAre() {
        assertEquals(SPEAKER, OutputRoute.kindOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(HDMI_ARC, OutputRoute.kindOf(AudioDeviceInfo.TYPE_HDMI_ARC))
        assertEquals(HDMI_ARC, OutputRoute.kindOf(29)) // TYPE_HDMI_EARC, API 31
        assertEquals(HDMI, OutputRoute.kindOf(AudioDeviceInfo.TYPE_HDMI))
        assertEquals(BLUETOOTH, OutputRoute.kindOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(BLUETOOTH, OutputRoute.kindOf(26)) // TYPE_BLE_HEADSET
        assertNull(OutputRoute.kindOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertNull(OutputRoute.kindOf(AudioDeviceInfo.TYPE_REMOTE_SUBMIX))
    }

    @Test
    fun theOutputMediaWouldTakeWins() {
        val tvWithSoundbar = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_HDMI_ARC)
        assertEquals(HDMI_ARC, OutputRoute.pickKind(tvWithSoundbar))
        assertEquals(BLUETOOTH, OutputRoute.pickKind(tvWithSoundbar + AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(HDMI, OutputRoute.pickKind(listOf(AudioDeviceInfo.TYPE_HDMI))) // a streaming box
        assertEquals(SPEAKER, OutputRoute.pickKind(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)))
        assertNull(OutputRoute.pickKind(listOf(AudioDeviceInfo.TYPE_REMOTE_SUBMIX)))
        assertNull(OutputRoute.pickKind(emptyList()))
    }

    @Test
    fun theChosenProfileAppliesOnItsOwnOutput() {
        val pick = OutputRoute.pick(listOf(speakers, soundbar), "speakers", SPEAKER)
        assertEquals("speakers", pick.profile?.id)
        assertFalse(pick.switched)
    }

    @Test
    fun switchingToTheSoundbarAppliesTheSoundbarsProfile() {
        val pick = OutputRoute.pick(listOf(speakers, soundbar), "speakers", HDMI_ARC)
        assertEquals("soundbar", pick.profile?.id)
        assertTrue(pick.switched)
    }

    @Test
    fun anOutputNothingWasMeasuredOnGetsNoCorrection() {
        // The bug this fixes: the speakers' curve applied to Bluetooth headphones.
        val pick = OutputRoute.pick(listOf(speakers, soundbar), "speakers", BLUETOOTH)
        assertNull(pick.profile)
    }

    @Test
    fun theNewestProfileForAnOutputWins() {
        val newer = profile("soundbar-2", 300, HDMI_ARC)
        assertEquals("soundbar-2", OutputRoute.pick(listOf(speakers, soundbar, newer), "speakers", HDMI_ARC).profile?.id)
    }

    @Test
    fun profilesFromBeforeOutputsWereRecordedStillApplyEverywhere() {
        // An update must not switch off correction someone already has.
        assertEquals("legacy", OutputRoute.pick(listOf(legacy), "legacy", BLUETOOTH).profile?.id)
        assertEquals("legacy", OutputRoute.pick(listOf(legacy), null, HDMI).profile?.id)
    }

    @Test
    fun anUnknownOutputKeepsTheChosenProfile() {
        assertEquals("soundbar", OutputRoute.pick(listOf(speakers, soundbar), "soundbar", null).profile?.id)
        assertNull(OutputRoute.pick(emptyList(), null, SPEAKER).profile)
    }

    @Test
    fun labelsSayWhatAPersonCallsIt() {
        assertEquals("TV speakers", OutputRoute.label(SPEAKER))
        assertEquals("any output", OutputRoute.label(null))
    }
}
