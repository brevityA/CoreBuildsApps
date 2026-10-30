package tv.corebuilds.eq

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.SurroundOutput
import tv.corebuilds.eq.apply.SurroundOutput.OnAction

/**
 * 1.10.0: the Surround sound setting Stereo speakers needs. Core EQ changes a
 * system setting here, so the rules are pinned: only on an output that could
 * carry a bitstream, only to Never, and only put back while it is still Never.
 */
class SurroundOutputTest {

    @Test
    fun `the modes are AudioManager's numbers and the global setting's`() {
        assertEquals(AudioManager.ENCODED_SURROUND_OUTPUT_AUTO, SurroundOutput.AUTO)
        assertEquals(AudioManager.ENCODED_SURROUND_OUTPUT_NEVER, SurroundOutput.NEVER)
        assertEquals(AudioManager.ENCODED_SURROUND_OUTPUT_ALWAYS, SurroundOutput.ALWAYS)
        assertEquals(AudioManager.ENCODED_SURROUND_OUTPUT_MANUAL, SurroundOutput.MANUAL)
        assertTrue(SurroundOutput.ADB_NEVER_COMMAND.endsWith("encoded_surround_output 1"))
        assertEquals(
            "adb shell appops set tv.corebuilds.eq.debug WRITE_SETTINGS allow",
            SurroundOutput.adbAllowCommand("tv.corebuilds.eq.debug")
        )
    }

    @Test
    fun `every mode but Never may let a bitstream through, and unread claims nothing`() {
        assertFalse(SurroundOutput.mayBypass(SurroundOutput.NEVER))
        for (mode in listOf(SurroundOutput.AUTO, SurroundOutput.ALWAYS, SurroundOutput.MANUAL)) {
            assertTrue(SurroundOutput.mayBypass(mode))
        }
        assertFalse(SurroundOutput.mayBypass(null))
    }

    @Test
    fun `switching on changes the setting only where it changes something`() {
        val api31 = SurroundOutput.PUBLIC_API_SDK
        // Bluetooth, USB, analogue: Android folds 5.1 itself, whatever the setting.
        assertEquals(OnAction.PCM_ONLY, SurroundOutput.onAction(SurroundOutput.AUTO, api31, true, bitstreamOutput = false))
        assertEquals(OnAction.NOTHING, SurroundOutput.onAction(SurroundOutput.NEVER, api31, true, true))
        assertEquals(OnAction.SET_NEVER, SurroundOutput.onAction(SurroundOutput.AUTO, api31, true, true))
        assertEquals(OnAction.SET_NEVER, SurroundOutput.onAction(SurroundOutput.MANUAL, api31, true, true))
        assertEquals(OnAction.ASK_PERMISSION, SurroundOutput.onAction(SurroundOutput.ALWAYS, api31, false, true))
        // Android 11 has no public switch; an unread setting is not guessed at.
        assertEquals(OnAction.EXPLAIN, SurroundOutput.onAction(SurroundOutput.AUTO, 30, true, true))
        assertEquals(OnAction.EXPLAIN, SurroundOutput.onAction(null, api31, true, true))
        assertEquals(OnAction.NOTHING, SurroundOutput.onAction(SurroundOutput.NEVER, 30, false, true))
    }

    @Test
    fun `switching off puts back the replaced mode, never a later choice`() {
        assertEquals(SurroundOutput.AUTO, SurroundOutput.restoreTo(SurroundOutput.AUTO, SurroundOutput.NEVER))
        assertEquals(SurroundOutput.MANUAL, SurroundOutput.restoreTo(SurroundOutput.MANUAL, SurroundOutput.NEVER))
        // Somebody set it to Always since: that stays.
        assertNull(SurroundOutput.restoreTo(SurroundOutput.AUTO, SurroundOutput.ALWAYS))
        // Core EQ changed nothing, or the setting cannot be read.
        assertNull(SurroundOutput.restoreTo(null, SurroundOutput.NEVER))
        assertNull(SurroundOutput.restoreTo(SurroundOutput.AUTO, null))
        assertNull(SurroundOutput.restoreTo(SurroundOutput.NEVER, SurroundOutput.NEVER))
        assertNull(SurroundOutput.restoreTo(7, SurroundOutput.NEVER))
    }

    @Test
    fun `labels are the menu's words`() {
        assertEquals("Auto", SurroundOutput.label(SurroundOutput.AUTO))
        assertEquals("Never", SurroundOutput.label(SurroundOutput.NEVER))
        assertEquals("Always", SurroundOutput.label(SurroundOutput.ALWAYS))
        assertEquals("Manual", SurroundOutput.label(SurroundOutput.MANUAL))
        assertEquals("unknown", SurroundOutput.label(null))
    }
}
