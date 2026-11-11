package tv.corebuilds.eq.export

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
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
import org.robolectric.shadows.ShadowDialog
import tv.corebuilds.eq.BuildConfig
import tv.corebuilds.eq.LegacyGolden113
import tv.corebuilds.eq.LegacyGoldenFixtures
import tv.corebuilds.eq.MeasureActivity
import tv.corebuilds.eq.ProfilesActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.dsp.SweepResult
import tv.corebuilds.eq.ui.screens.ScreenHarness

/**
 * Profile provenance (roadmap milestone C): who created a profile's correction
 * and who last saved it, recorded only by the build that did it and never
 * guessed for older profiles. Robolectric runs the real store and screens; no
 * TV storage or file picker is involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class ProfileProvenanceTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun raw() = app.getSharedPreferences("core_eq_profiles", Context.MODE_PRIVATE)

    private val origin = AppBuild("1.14.0", 30)
    private val later = AppBuild("1.15.0", 31)

    @Before fun tv() {
        ScreenHarness.prepareTv()
        raw().edit().clear().commit()
    }

    /** The state a real 1.13.2 install is in: its profiles, and both one-time migrations already done. */
    private fun install113() {
        raw().edit()
            .putString("profiles_json", LegacyGolden113.PROFILES_JSON)
            .putBoolean("demo_profiles_purged", true)
            .putBoolean("manual_eq_migrated_to_modes", true)
            .commit()
    }

    private fun storedObjects(): Map<String, JSONObject> {
        val arr = JSONArray(raw().getString("profiles_json", "[]"))
        return (0 until arr.length()).associate { arr.getJSONObject(it).let { o -> o.getString("id") to o } }
    }

    private val legacyObjects: Map<String, JSONObject> by lazy {
        val arr = JSONArray(LegacyGolden113.PROFILES_JSON)
        (0 until arr.length()).associate { arr.getJSONObject(it).let { o -> o.getString("id") to o } }
    }

    /** Key-order-independent JSON equality (Android's org.json has no similar()). */
    private fun canon(v: Any?): Any? = when (v) {
        is JSONObject -> v.keys().asSequence().sorted().associateWith { canon(v.opt(it)) }
        is JSONArray -> (0 until v.length()).map { canon(v.opt(it)) }
        is Int, is Long -> (v as Number).toDouble()
        is Float -> v.toDouble()
        else -> if (v == JSONObject.NULL) null else v
    }
    private fun same(a: JSONObject, b: JSONObject): Boolean = canon(a) == canon(b)

    private fun measured(id: String = "p-new", createdBy: AppBuild? = origin) =
        LegacyGoldenFixtures.dialogueTarget.copy(id = id, name = "New room", timestampMs = 1_800_000_000_000L, createdBy = createdBy)

    @Test fun `1_13_2 profiles load with nothing recorded and opening them writes nothing`() {
        install113()
        val before = raw().getString("profiles_json", null)
        val store = ProfileStore(app, later)
        val loaded = store.getAllProfiles()
        store.getActiveProfile()
        assertEquals(3, loaded.size)
        for (p in loaded) {
            assertNull("${p.id} createdBy", p.createdBy)
            assertNull("${p.id} lastSavedBy", p.lastSavedBy)
            val lines = ProfileDetails.lines(p, { it ?: "any output" })
            assertEquals(ProfileDetails.NOT_RECORDED, lines.first { it.label == ProfileDetails.originLabel(p) }.value)
            assertEquals(ProfileDetails.NOT_RECORDED, lines.first { it.label == "Last saved with" }.value)
        }
        assertEquals("constructing, listing and reading details must not rewrite profiles", before, raw().getString("profiles_json", null))
    }

    @Test fun `browsing Profiles and opening Details writes nothing`() {
        install113()
        val before = raw().getString("profiles_json", null)
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ScreenHarness.idle()
                val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_profiles)
                list.layoutManager!!.findViewByPosition(0)!!.performClick()
                ScreenHarness.idle()
                activity.findViewById<Button>(R.id.btn_profile_details).performClick()
                ScreenHarness.idle()
                (ShadowDialog.getLatestDialog() as AlertDialog).dismiss()
                ScreenHarness.idle()
            }
        }
        // Choosing a row records the chosen id (a separate key); the profiles themselves are untouched.
        assertEquals(before, raw().getString("profiles_json", null))
    }

    @Test fun `saving one profile stamps only that profile`() {
        install113()
        ProfileStore(app, later).saveProfile(measured())
        val stored = storedObjects()
        val saved = ProfileStore(app, later).getAllProfiles().first { it.id == "p-new" }
        assertEquals(origin, saved.createdBy)
        assertEquals(later, saved.lastSavedBy)
        for ((id, original) in legacyObjects) {
            assertFalse("$id gained provenance", stored.getValue(id).has("provenance"))
            assertTrue("$id changed when another profile was saved", same(original, stored.getValue(id)))
        }
    }

    @Test fun `saving never invents an origin`() {
        install113()
        ProfileStore(app, later).saveProfile(measured(createdBy = null))
        val saved = ProfileStore(app, later).getAllProfiles().first { it.id == "p-new" }
        assertNull(saved.createdBy)
        assertEquals(later, saved.lastSavedBy)
    }

    @Test fun `rename keeps the origin and records who saved it`() {
        install113()
        ProfileStore(app, origin).saveProfile(measured())
        val renamed = ProfileStore(app, later).renameProfile("p-new", "Den")!!
        assertEquals(origin, renamed.createdBy)
        assertEquals(later, renamed.lastSavedBy)
        val legacyRenamed = ProfileStore(app, later).renameProfile(LegacyGoldenFixtures.manualOnly.id, "Old manual")!!
        assertNull("an unknown origin stays unknown", legacyRenamed.createdBy)
        assertEquals(later, legacyRenamed.lastSavedBy)
        val reloaded = ProfileStore(app, later).getAllProfiles().associateBy { it.id }
        assertEquals(renamed, reloaded.getValue("p-new"))
        assertEquals(legacyRenamed, reloaded.getValue(LegacyGoldenFixtures.manualOnly.id))
    }

    @Test fun `duplicate copies the origin and leaves the original as it was`() {
        install113()
        ProfileStore(app, origin).saveProfile(measured())
        val originalBefore = storedObjects().getValue("p-new")
        val copy = ProfileStore(app, later).duplicateProfile("p-new", newId = "p-copy")!!
        assertEquals(origin, copy.createdBy)
        assertEquals(later, copy.lastSavedBy)
        assertTrue("the original must not be re-stamped", same(originalBefore, storedObjects().getValue("p-new")))
        val legacyCopy = ProfileStore(app, later).duplicateProfile(LegacyGoldenFixtures.roomWithManual.id, newId = "legacy-copy")!!
        assertNull(legacyCopy.createdBy)
    }

    @Test fun `export carries both records and import keeps the file's origin`() {
        val exported = Formats.exportProfileJson(measured().copy(lastSavedBy = origin))
        val root = JSONObject(exported)
        val provenance = root.getJSONObject("provenance")
        assertEquals("1.14.0", provenance.getJSONObject("created_by").getString("version"))
        assertEquals(30, provenance.getJSONObject("last_saved_by").getInt("code"))

        val imported = ProfileExchange.parse(exported).profile
        assertEquals(origin, imported.createdBy)
        assertEquals(origin, imported.lastSavedBy)
        // The Import action saves it: the origin stays, the save is this build's.
        ProfileStore(app, later).saveProfile(imported, setAsActive = true)
        val saved = ProfileStore(app, later).getAllProfiles().first { it.id == imported.id }
        assertEquals(origin, saved.createdBy)
        assertEquals(later, saved.lastSavedBy)
    }

    @Test fun `a file without provenance imports with the origin not recorded`() {
        val legacyFile = legacyObjects.getValue(LegacyGoldenFixtures.dialogueTarget.id).toString()
        val imported = ProfileExchange.parse(legacyFile).profile
        assertNull(imported.createdBy)
        ProfileStore(app, later).saveProfile(imported)
        assertNull(ProfileStore(app, later).getAllProfiles().first { it.id == imported.id }.createdBy)
    }

    @Test fun `profiles without provenance serialize exactly as 1_13_2 stored them`() {
        for ((id, original) in legacyObjects) {
            val roundTrip = JSONObject(Formats.exportProfileJson(ProfileStore.parseProfile(original)))
            assertFalse(roundTrip.has("provenance"))
            assertTrue("$id changed in a parse/export round trip", same(original, roundTrip))
        }
    }

    @Test fun `malformed or partial records read as not recorded, unknown fields are ignored`() {
        fun parseWith(provenance: Any): Profile {
            val obj = JSONObject(legacyObjects.getValue(LegacyGoldenFixtures.dialogueTarget.id).toString())
            obj.put("provenance", provenance)
            return ProfileStore.parseProfile(obj)
        }
        val bad = listOf(
            JSONObject().put("created_by", JSONObject().put("version", "1.14.0")),
            JSONObject().put("created_by", JSONObject().put("version", "1.14.0").put("code", 0)),
            JSONObject().put("created_by", JSONObject().put("version", "1.14.0").put("code", -3)),
            JSONObject().put("created_by", JSONObject().put("version", "   ").put("code", 30)),
            JSONObject().put("created_by", JSONObject().put("version", 1.14).put("code", 30)),
            JSONObject().put("created_by", JSONObject().put("version", "1.14.0").put("code", "30")),
            JSONObject().put("created_by", JSONObject().put("app", "Other EQ").put("version", "1.0").put("code", 3)),
            JSONObject().put("created_by", "1.14.0"),
            "not an object"
        )
        for (b in bad) assertNull("$b", parseWith(b).createdBy)
        val future = parseWith(
            JSONObject()
                .put("created_by", origin.toJson().put("device_class", "tv"))
                .put("last_saved_by", later.toJson())
                .put("measured_by_phone", JSONObject().put("version", "2.0"))
        )
        assertEquals(origin, future.createdBy)
        assertEquals(later, future.lastSavedBy)
    }

    @Test fun `a sweep saved from Measure is created and saved by this build`() {
        ActivityScenario.launch(MeasureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                MeasureActivity::class.java.getDeclaredField("result").apply {
                    isAccessible = true
                    set(activity, sweepResult())
                }
                val save = activity.findViewById<Button>(R.id.btn_measure_save)
                save.visibility = View.VISIBLE
                save.performClick()
                ScreenHarness.idle()
            }
        }
        val saved = ProfileStore(app).getAllProfiles().single()
        val thisBuild = AppBuild(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        assertEquals(thisBuild, saved.createdBy)
        assertEquals(thisBuild, saved.lastSavedBy)
        assertEquals("Measured with", ProfileDetails.originLabel(saved))
    }

    private fun sweepResult(): SweepResult = SweepResult(
        centresHz = doubleArrayOf(500.0, 1000.0, 2000.0, 4000.0),
        measuredDb = DoubleArray(4), targetDb = DoubleArray(4), correctionDb = DoubleArray(4),
        nullMask = BooleanArray(4), minPhaseOk = BooleanArray(4) { true },
        rt60Seconds = 0.3, schroederHz = null, transitionHz = 300.0,
        rolloffHz = 40.0, floorHz = 40.0, snrDb = 40.0, latencyMs = 0.0,
        filters = emptyList(), preampDb = 0.0, bandSnrDb = DoubleArray(4) { 40.0 },
        phaseGate = BooleanArray(4) { true }, confidence = DoubleArray(4) { 1.0 },
        fMaxHz = 8000.0
    )
}
