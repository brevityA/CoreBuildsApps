package tv.corebuilds.eq.export

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.corebuilds.eq.LegacyGoldenFixtures
import tv.corebuilds.eq.ui.screens.ScreenHarness

/**
 * What the profile store keeps when it writes (roadmap milestone C).
 *
 * The guarantee under test, and no stronger one:
 * - an **untouched** profile keeps its stored JSON value: every key, including
 *   keys this build does not know, every value, including values this build
 *   would write differently today, in the same order;
 * - an **edited** profile has only the keys its change affected replaced;
 * - an entry this build **cannot read** is kept where it was;
 * - **reading** (constructing the store, listing, choosing) writes nothing, so
 *   the stored text stays byte-for-byte what it was, however it was formatted.
 *
 * Byte-for-byte across a write is asserted only for text Android's JSON writer
 * produced (as Core EQ's own saves are); hand-formatted text may be reformatted
 * by a write, never changed in value. Robolectric's org.json, not a TV's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class ProfilePreservationTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun prefs() = app.getSharedPreferences("core_eq_profiles", Context.MODE_PRIVATE)
    private fun storedText(): String = prefs().getString("profiles_json", null)!!
    private val build = AppBuild("1.15.0", 31)

    @Before fun clear() {
        ScreenHarness.prepareTv()
        prefs().edit().clear().commit()
    }

    /** A profile as a newer build might have written it: fields this build does not know, and older values. */
    private fun fromNewerBuild(id: String, name: String): JSONObject {
        val obj = JSONObject(
            Formats.exportProfileJson(
                LegacyGoldenFixtures.dialogueTarget.copy(id = id, name = name, outputKind = "hdmi_arc", outputName = "Soundbar")
            )
        )
        obj.put("sound_editor", JSONObject().put("bands", JSONArray().put(JSONObject().put("hz", 120).put("q", 1.4))))
        obj.put("schema_minor", 7)
        obj.getJSONObject("output").put("hdmi_port", 2)
        obj.put("provenance", JSONObject().put("synced_by", JSONObject().put("version", "2.0")))
        // Values this build reads but would write differently today.
        obj.put("sample_rate", 44_100)
        obj.put("gain_limits_db", JSONObject().put("boost", 9).put("cut", -15).put("shaping", 4))
        return obj
    }

    private fun store(vararg elements: Any, migrated: Boolean = true) {
        val arr = JSONArray()
        for (e in elements) arr.put(e)
        prefs().edit()
            .putString("profiles_json", arr.toString())
            .putBoolean("demo_profiles_purged", migrated)
            .putBoolean("manual_eq_migrated_to_modes", migrated)
            .commit()
    }

    private fun storedById(): Map<String, JSONObject> {
        val arr = JSONArray(storedText())
        return (0 until arr.length()).mapNotNull { arr.opt(it) as? JSONObject }
            .filter { it.has("id") }.associateBy { it.getString("id") }
    }

    private fun changedKeys(a: JSONObject, b: JSONObject): Set<String> =
        (a.keys().asSequence() + b.keys().asSequence()).toSet()
            .filter { a.opt(it)?.toString() != b.opt(it)?.toString() }.toSet()

    @Test fun `an untouched profile keeps unknown fields and older values, verbatim`() {
        val newer = fromNewerBuild("p-newer", "Newer build")
        store(newer)
        ProfileStore(app, build).saveProfile(LegacyGoldenFixtures.dialogueTarget.copy(id = "p-other", name = "Other"))
        val text = storedText()
        assertTrue("the untouched entry is written back verbatim", text.contains(newer.toString()))
        val kept = storedById().getValue("p-newer")
        assertEquals(7, kept.getInt("schema_minor"))
        assertEquals(2, kept.getJSONObject("output").getInt("hdmi_port"))
        assertEquals(44_100, kept.getInt("sample_rate"))
        assertEquals(9, kept.getJSONObject("gain_limits_db").getInt("boost"))
        assertTrue(kept.getJSONObject("provenance").has("synced_by"))
        assertEquals(newer.toString(), kept.toString())
    }

    @Test fun `an edit replaces only the keys it changed and keeps the rest`() {
        val newer = fromNewerBuild("p-newer", "Newer build")
        store(newer)
        val renamed = ProfileStore(app, build).renameProfile("p-newer", "Den")!!
        val after = storedById().getValue("p-newer")
        assertEquals(setOf("name", "provenance"), changedKeys(newer, after))
        assertEquals("Den", after.getString("name"))
        // Nested: the provenance object gains last_saved_by and keeps what a newer build put there.
        val provenance = after.getJSONObject("provenance")
        assertEquals(31, provenance.getJSONObject("last_saved_by").getInt("code"))
        assertTrue(provenance.has("synced_by"))
        assertEquals(44_100, after.getInt("sample_rate"))
        assertTrue(after.has("sound_editor"))
        assertEquals(renamed, ProfileStore(app, build).getAllProfiles().single())

        // A content edit: only the notes (and who saved) change.
        val noted = renamed.copy(measurementNotes = listOf("Moved the sofa."))
        ProfileStore(app, build).saveProfile(noted)
        val again = storedById().getValue("p-newer")
        assertEquals(setOf("measurement_notes"), changedKeys(after, again))
        assertEquals(listOf("Moved the sofa."), ProfileStore(app, build).getAllProfiles().single().measurementNotes)
    }

    @Test fun `an entry this build cannot read is kept in place`() {
        val readable = fromNewerBuild("p-a", "A")
        val noId = JSONObject().put("name", "written by something else")
        store(readable, "not a profile", noId, 42)
        assertEquals(1, ProfileStore(app, build).getAllProfiles().size)
        ProfileStore(app, build).saveProfile(LegacyGoldenFixtures.dialogueTarget.copy(id = "p-b", name = "B"))
        val arr = JSONArray(storedText())
        assertEquals(5, arr.length())
        assertEquals("not a profile", arr.get(1))
        assertEquals(noId.toString(), arr.get(2).toString())
        assertEquals(42, arr.get(3))
        assertEquals("p-b", arr.getJSONObject(4).getString("id"))
    }

    @Test fun `opening and reading the store writes nothing, even on the first run of this build`() {
        // Hand-formatted, unknown keys, "1.0" numbers: a write would reformat it. The two one-time
        // migrations have not run, as on an install restored from an old backup.
        val handWritten = "[ {\"id\": \"p-hand\", \"name\": \"Hand written\", \"timestamp_ms\": 1.0E12,\n" +
            "  \"curve\": [], \"filters\": [], \"manual_filters\": [], \"future\": {\"a\": 1.0}} ]"
        prefs().edit().putString("profiles_json", handWritten).commit()
        val store = ProfileStore(app, build)
        store.getAllProfiles()
        store.getActiveProfile()
        store.setActiveProfile("p-hand")
        ProfileStore(app, build).getAllProfiles()
        assertEquals(handWritten, storedText())
    }

    @Test fun `the demo purge removes only the demo profiles`() {
        val mine = fromNewerBuild("p-mine", "Mine")
        val demo = JSONObject(Formats.exportProfileJson(LegacyGoldenFixtures.dialogueTarget.copy(id = "profile-bedroom", name = "Demo")))
        store(mine, demo, migrated = false)
        ProfileStore(app, build)
        val arr = JSONArray(storedText())
        assertEquals(1, arr.length())
        assertEquals(mine.toString(), arr.getJSONObject(0).toString())
    }

    @Test fun `a save or rename that changes nothing writes nothing`() {
        // Hand-formatted, so any write at all would show as reformatted text.
        val handWritten = "[ {\"id\": \"p-hand\", \"name\": \"Den\", \"timestamp_ms\": 1.0E12, \"curve\": [],\n" +
            "  \"provenance\": {\"last_saved_by\": {\"app\": \"Core EQ\", \"version\": \"1.15.0\", \"code\": 31}}} ]"
        prefs().edit().putString("profiles_json", handWritten)
            .putBoolean("demo_profiles_purged", true).putBoolean("manual_eq_migrated_to_modes", true).commit()
        val store = ProfileStore(app, build)
        val p = store.getAllProfiles().single()
        assertEquals(build, p.lastSavedBy)
        store.saveProfile(p)
        store.renameProfile("p-hand", "  Den ")
        assertEquals(handWritten, storedText())
        // A real change is written, in value; the hand formatting does not survive a write.
        store.renameProfile("p-hand", "Lounge")
        assertEquals("Lounge", storedById().getValue("p-hand").getString("name"))
        assertEquals(1.0E12, storedById().getValue("p-hand").getDouble("timestamp_ms"), 0.0)
    }

    @Test fun `delete removes one entry and leaves the others verbatim`() {
        val a = fromNewerBuild("p-a", "A")
        val b = fromNewerBuild("p-b", "B")
        store(a, b)
        assertTrue(ProfileStore(app, build).deleteProfile("p-a"))
        assertEquals(JSONArray().put(b).toString(), storedText())
    }

    @Test fun `a duplicate is written from known fields and unknown ones stay with the original`() {
        val newer = fromNewerBuild("p-newer", "Newer build")
        store(newer)
        ProfileStore(app, build).duplicateProfile("p-newer", newId = "p-copy")!!
        val stored = storedById()
        assertEquals(newer.toString(), stored.getValue("p-newer").toString())
        val copy = stored.getValue("p-copy")
        assertFalse(copy.has("sound_editor"))
        assertFalse(copy.has("schema_minor"))
        assertNull(copy.getJSONObject("output").opt("hdmi_port"))
    }

    @Test fun `the legacy manual-EQ migration changes only the moved filters`() {
        val legacy = JSONObject(Formats.exportProfileJson(LegacyGoldenFixtures.roomWithManual))
            .put("future", JSONObject().put("kept", true))
        prefs().edit()
            .putString("profiles_json", JSONArray().put(legacy).toString())
            .putBoolean("demo_profiles_purged", true)
            .commit()
        ProfileStore(app, build)
        val after = storedById().getValue(LegacyGoldenFixtures.roomWithManual.id)
        // The filters move to the Everyday overlay; the recommended preamp follows them.
        assertTrue(changedKeys(legacy, after).all { it == "manual_filters" || it == "preamp_db" })
        assertTrue("manual_filters" in changedKeys(legacy, after))
        assertTrue(after.getJSONObject("future").getBoolean("kept"))
        assertFalse("migration is not a save by this build", after.has("provenance"))
    }

    @Test fun `patchJson replaces, removes and recurses only where the written values differ`() {
        val target = JSONObject("""{"a":1,"b":{"x":1,"keep":true},"c":[1],"d":"gone","u":"unknown"}""")
        val old = JSONObject("""{"a":1,"b":{"x":1},"c":[1],"d":"gone"}""")
        val new = JSONObject("""{"a":1,"b":{"x":2},"c":[1,2],"e":"added"}""")
        ProfileStore.patchJson(target, old, new)
        assertEquals("""{"a":1,"b":{"x":2,"keep":true},"c":[1,2],"u":"unknown","e":"added"}""", target.toString())
    }
}
