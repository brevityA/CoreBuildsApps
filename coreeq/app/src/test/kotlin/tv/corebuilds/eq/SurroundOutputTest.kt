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

    /** A writer that records what it was asked to write and whether it pretends to succeed. */
    private class Writer(private val succeeds: Boolean) {
        val written = mutableListOf<Int>()
        fun write(mode: Int): Boolean {
            written += mode
            return succeeds
        }
    }

    @Test
    fun `switching off puts back the replaced mode, never a later choice`() {
        val writer = Writer(succeeds = true)
        // Nothing is left owed once the write went through, and the mode is what was written.
        assertNull(SurroundOutput.restore(SurroundOutput.AUTO, SurroundOutput.NEVER, writer::write))
        assertNull(SurroundOutput.restore(SurroundOutput.MANUAL, SurroundOutput.NEVER, writer::write))
        assertEquals(listOf(SurroundOutput.AUTO, SurroundOutput.MANUAL), writer.written)
        // Somebody set it to Always since: that stays.
        assertNull(SurroundOutput.restore(SurroundOutput.AUTO, SurroundOutput.ALWAYS, writer::write))
        // Core EQ changed nothing, or the setting cannot be read.
        assertNull(SurroundOutput.restore(null, SurroundOutput.NEVER, writer::write))
        assertNull(SurroundOutput.restore(SurroundOutput.AUTO, null, writer::write))
        assertNull(SurroundOutput.restore(SurroundOutput.NEVER, SurroundOutput.NEVER, writer::write))
        assertNull(SurroundOutput.restore(7, SurroundOutput.NEVER, writer::write))
        // Nothing above was written: no mode was restored, so none was passed to the writer.
        assertEquals(2, writer.written.size)
    }

    /**
     * The regression CodeRabbit found: a refused write used to be treated as a restore, so the
     * saved mode was forgotten and the user's own setting was lost with it.
     */
    @Test
    fun `a write Android refuses keeps the saved mode so the next visit retries`() {
        val refused = Writer(succeeds = false)
        assertEquals(
            "the saved mode must stay owed",
            SurroundOutput.AUTO,
            SurroundOutput.restore(SurroundOutput.AUTO, SurroundOutput.NEVER, refused::write)
        )
        assertEquals("and it must have been attempted", listOf(SurroundOutput.AUTO), refused.written)

        // Once the permission is back, the same saved mode goes through and is forgotten.
        val allowed = Writer(succeeds = true)
        assertNull(SurroundOutput.restore(SurroundOutput.AUTO, SurroundOutput.NEVER, allowed::write))
        // A mode that is no longer Core EQ's to restore is dropped without a write at all.
        val untouched = Writer(succeeds = true)
        assertNull(SurroundOutput.restore(SurroundOutput.AUTO, SurroundOutput.ALWAYS, untouched::write))
        assertTrue("nothing should be written", untouched.written.isEmpty())
    }

    @Test
    fun `labels are the menu's words`() {
        assertEquals("Auto", SurroundOutput.label(SurroundOutput.AUTO))
        assertEquals("Never", SurroundOutput.label(SurroundOutput.NEVER))
        assertEquals("Always", SurroundOutput.label(SurroundOutput.ALWAYS))
        assertEquals("Manual", SurroundOutput.label(SurroundOutput.MANUAL))
        assertEquals("unknown", SurroundOutput.label(null))
    }

    @Test
    fun `the status line tells granted-and-kept-old apart from not-yet-asked`() {
        val api = SurroundOutput.PUBLIC_API_SDK
        // #413 finding 5: the write permission is in hand, and the setting is still
        // not Never — asking for the permission again would be a lie.
        assertEquals(
            SurroundOutput.Status.NEEDS_MANUAL,
            SurroundOutput.status(
                stereoOn = true, restoreOwed = false, mode = SurroundOutput.AUTO,
                sdk = api, canWrite = true, bitstreamOutput = true
            )
        )
        // Permission not yet granted: the ask is still the truth.
        assertEquals(
            SurroundOutput.Status.ASK_PERMISSION,
            SurroundOutput.status(
                stereoOn = true, restoreOwed = false, mode = SurroundOutput.ALWAYS,
                sdk = api, canWrite = false, bitstreamOutput = true
            )
        )
    }

    @Test
    fun `the status line covers the rest of the matrix`() {
        val api = SurroundOutput.PUBLIC_API_SDK
        fun decide(
            stereoOn: Boolean,
            restoreOwed: Boolean,
            mode: Int?,
            sdk: Int = api,
            canWrite: Boolean = true,
            bitstream: Boolean = true
        ) = SurroundOutput.status(stereoOn, restoreOwed, mode, sdk, canWrite, bitstream)

        assertEquals(SurroundOutput.Status.HIDDEN, decide(false, false, SurroundOutput.AUTO))
        assertEquals(SurroundOutput.Status.OFF_RESTORE_STUCK, decide(false, true, SurroundOutput.AUTO))
        assertEquals(SurroundOutput.Status.PCM_ONLY, decide(true, false, SurroundOutput.AUTO, bitstream = false))
        // An owed restore note while on is the activity's wording job, not a different state.
        assertEquals(SurroundOutput.Status.SETTLED, decide(true, true, SurroundOutput.NEVER))
        assertEquals(SurroundOutput.Status.UNREADABLE, decide(true, false, null))
        assertEquals(SurroundOutput.Status.MANUAL_ONLY, decide(true, false, SurroundOutput.AUTO, sdk = 30))
        assertEquals(SurroundOutput.Status.NEEDS_MANUAL, decide(true, false, SurroundOutput.MANUAL))
    }
}
