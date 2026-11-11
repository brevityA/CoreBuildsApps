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
import tv.corebuilds.eq.ManualEqActivity
import tv.corebuilds.eq.MeasureActivity
import tv.corebuilds.eq.ProfilesActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.companion.CompanionServer
import tv.corebuilds.eq.dsp.ImportResult
import tv.corebuilds.eq.dsp.SweepResult
import tv.corebuilds.eq.ui.screens.ScreenHarness

/**
 * Profile provenance (roadmap milestone C): which build created a profile,
 * recorded its sweep and analysed it ([ProfileOrigin]), and which last saved
 * it, recorded only by the build that did each and never guessed for older
 * profiles. Robolectric runs the real store, screens and creation paths; no TV
 * storage, microphone, phone or file picker is involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class ProfileProvenanceTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun raw() = app.getSharedPreferences("core_eq_profiles", Context.MODE_PRIVATE)

    private val built = AppBuild("1.14.0", 30)
    private val later = AppBuild("1.15.0", 31)
    private val tvOrigin = ProfileOrigin.tvMeasurement(built)
    private val thisBuild get() = AppBuild(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

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

    private fun measured(id: String = "p-new", origin: ProfileOrigin? = tvOrigin) =
        LegacyGoldenFixtures.dialogueTarget.copy(id = id, name = "New room", timestampMs = 1_800_000_000_000L, origin = origin)

    private fun line(p: Profile, label: String): String? =
        ProfileDetails.lines(p, { it ?: "any output" }).firstOrNull { it.label == label }?.value

    @Test fun `1_13_2 profiles load with nothing recorded and opening them writes nothing`() {
        install113()
        val before = raw().getString("profiles_json", null)
        val store = ProfileStore(app, later)
        val loaded = store.getAllProfiles()
        store.getActiveProfile()
        assertEquals(3, loaded.size)
        for (p in loaded) {
            assertNull("${p.id} origin", p.origin)
            assertNull("${p.id} lastSavedBy", p.lastSavedBy)
            assertEquals(ProfileDetails.NOT_RECORDED, line(p, "Origin"))
            assertEquals(ProfileDetails.NOT_RECORDED, line(p, "Last saved with"))
            // Nothing else about an unrecorded origin is guessed.
            assertNull(line(p, "Analysed with"))
            assertNull(line(p, "Profile created with"))
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
        val text = raw().getString("profiles_json", null)!!
        val stored = storedObjects()
        val saved = ProfileStore(app, later).getAllProfiles().first { it.id == "p-new" }
        assertEquals(tvOrigin, saved.origin)
        assertEquals(later, saved.lastSavedBy)
        for ((id, original) in legacyObjects) {
            assertFalse("$id gained provenance", stored.getValue(id).has("provenance"))
            assertTrue("$id changed when another profile was saved", same(original, stored.getValue(id)))
            // The 1.13.2 text was written by Android's JSON writer; an untouched entry is written back verbatim.
            assertTrue("$id is not verbatim", text.contains(original.toString()))
        }
    }

    @Test fun `saving never invents an origin`() {
        install113()
        ProfileStore(app, later).saveProfile(measured(origin = null))
        val saved = ProfileStore(app, later).getAllProfiles().first { it.id == "p-new" }
        assertNull(saved.origin)
        assertEquals(later, saved.lastSavedBy)
    }

    @Test fun `rename keeps the origin and records who saved it`() {
        install113()
        ProfileStore(app, built).saveProfile(measured())
        val renamed = ProfileStore(app, later).renameProfile("p-new", "Den")!!
        assertEquals(tvOrigin, renamed.origin)
        assertEquals(later, renamed.lastSavedBy)
        val legacyBefore = storedObjects().getValue(LegacyGoldenFixtures.manualOnly.id)
        val legacyRenamed = ProfileStore(app, later).renameProfile(LegacyGoldenFixtures.manualOnly.id, "Old manual")!!
        assertNull("an unknown origin stays unknown", legacyRenamed.origin)
        assertEquals(later, legacyRenamed.lastSavedBy)
        // A rename writes the name and who saved it; every other stored field is as it was.
        val legacyAfter = storedObjects().getValue(LegacyGoldenFixtures.manualOnly.id)
        val changed = (legacyBefore.keys().asSequence() + legacyAfter.keys().asSequence()).toSet()
            .filter { canon(legacyBefore.opt(it)) != canon(legacyAfter.opt(it)) }.toSet()
        assertEquals(setOf("name", "provenance"), changed)
        assertFalse("no origin is added on rename", legacyAfter.getJSONObject("provenance").has("origin"))
        val reloaded = ProfileStore(app, later).getAllProfiles().associateBy { it.id }
        assertEquals(renamed, reloaded.getValue("p-new"))
        assertEquals(legacyRenamed, reloaded.getValue(LegacyGoldenFixtures.manualOnly.id))
    }

    @Test fun `duplicate copies the origin and leaves the original as it was`() {
        install113()
        ProfileStore(app, built).saveProfile(measured())
        val originalBefore = storedObjects().getValue("p-new")
        val copy = ProfileStore(app, later).duplicateProfile("p-new", newId = "p-copy")!!
        assertEquals("the copy is the same measurement: its origin is copied as it is", tvOrigin, copy.origin)
        assertEquals(later, copy.lastSavedBy)
        assertTrue("the original must not be re-stamped", same(originalBefore, storedObjects().getValue("p-new")))
        val legacyCopy = ProfileStore(app, later).duplicateProfile(LegacyGoldenFixtures.roomWithManual.id, newId = "legacy-copy")!!
        assertNull(legacyCopy.origin)
    }

    @Test fun `export carries the origin and the last save`() {
        val phone = ProfileOrigin.phoneMeasurement(built, phoneBuild = null)
        val root = JSONObject(Formats.exportProfileJson(measured(origin = phone).copy(lastSavedBy = built)))
        val provenance = root.getJSONObject("provenance")
        val origin = provenance.getJSONObject("origin")
        assertEquals("phone_measurement", origin.getString("kind"))
        assertEquals("1.14.0", origin.getJSONObject("created_by").getString("version"))
        assertEquals(30, origin.getJSONObject("analysed_by").getInt("code"))
        assertFalse("an unreported phone build is left out, not filled in", origin.has("captured_by"))
        assertEquals(30, provenance.getJSONObject("last_saved_by").getInt("code"))
    }

    /**
     * The rule for import: the file's origin is kept as it is; "last saved"
     * becomes the importing build, because it is the build that saved the
     * profile into this TV's list. The exporter's value is not kept.
     */
    @Test fun `import keeps the file's origin and records this build as the last save`() {
        val exported = Formats.exportProfileJson(measured().copy(lastSavedBy = built))
        val imported = ProfileExchange.parse(exported).profile
        assertEquals(tvOrigin, imported.origin)
        // The Import action in Profiles saves it through the store.
        ProfileStore(app, later).saveProfile(imported, setAsActive = true)
        val saved = ProfileStore(app, later).getAllProfiles().first { it.id == imported.id }
        assertEquals(tvOrigin, saved.origin)
        assertEquals(later, saved.lastSavedBy)
        assertEquals("Core EQ 1.15.0 (31)", line(saved, "Last saved with"))
        assertEquals("Core EQ 1.14.0 (30)", line(saved, "Analysed with"))
    }

    @Test fun `a file without provenance imports with the origin not recorded`() {
        val legacyFile = legacyObjects.getValue(LegacyGoldenFixtures.dialogueTarget.id).toString()
        val imported = ProfileExchange.parse(legacyFile).profile
        assertNull(imported.origin)
        ProfileStore(app, later).saveProfile(imported)
        assertNull(ProfileStore(app, later).getAllProfiles().first { it.id == imported.id }.origin)
    }

    @Test fun `profiles without provenance are written with the same fields as 1_13_2 wrote`() {
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
        fun originWith(build: Any?, kind: Any? = "tv_measurement") =
            JSONObject().put("origin", JSONObject().put("kind", kind ?: JSONObject.NULL).put("created_by", build ?: JSONObject.NULL))
        val badBuilds = listOf(
            JSONObject().put("version", "1.14.0"),
            JSONObject().put("version", "1.14.0").put("code", 0),
            JSONObject().put("version", "1.14.0").put("code", -3),
            JSONObject().put("version", "   ").put("code", 30),
            JSONObject().put("version", 1.14).put("code", 30),
            JSONObject().put("version", "1.14.0").put("code", "30"),
            JSONObject().put("app", "Other EQ").put("version", "1.0").put("code", 3),
            "1.14.0",
            null
        )
        for (b in badBuilds) assertNull("created_by $b", parseWith(originWith(b)).origin)
        for (k in listOf(null, "", "TV Measurement", 3, "x".repeat(41))) {
            assertNull("kind $k", parseWith(originWith(built.toJson(), k)).origin)
        }
        assertNull("not an object", parseWith("not an object").origin)
        // The draft shape (created_by directly under provenance) was never released: not an origin.
        assertNull(parseWith(JSONObject().put("created_by", built.toJson())).origin)

        val future = parseWith(
            JSONObject()
                .put("origin", tvOrigin.toJson().put("room_mic_serial_hash", "ab12"))
                .put("last_saved_by", later.toJson())
                .put("synced_by", JSONObject().put("version", "2.0"))
        )
        assertEquals(tvOrigin, future.origin)
        assertEquals(later, future.lastSavedBy)
        // A kind a newer build invented is kept as recorded, not mapped onto one of ours.
        val newer = parseWith(JSONObject().put("origin", JSONObject().put("kind", "usb_measurement").put("created_by", later.toJson())))
        assertEquals("usb_measurement", newer.origin?.kind)
        assertEquals("\"usb_measurement\", recorded by a build newer than this one", line(newer, "Origin"))
        // A partial measurement record names what it has and says what it lacks.
        val partial = parseWith(JSONObject().put("origin", JSONObject().put("kind", "tv_measurement").put("created_by", built.toJson())))
        assertEquals(ProfileDetails.NOT_RECORDED, line(partial, "Analysed with"))
    }

    @Test fun `a TV sweep saved from Measure is recorded, analysed and saved by this build`() {
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
        assertEquals(ProfileOrigin.tvMeasurement(thisBuild), saved.origin)
        assertEquals(thisBuild, saved.lastSavedBy)
        assertEquals(thisBuild.label(), line(saved, "Sweep recorded with"))
        assertEquals(thisBuild.label(), line(saved, "Analysed with"))
    }

    @Test fun `a REW import is imported by this build and never said to be measured by it`() {
        ActivityScenario.launch(MeasureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val imp = ImportResult(
                    centresHz = doubleArrayOf(500.0, 1000.0, 2000.0), measuredDb = DoubleArray(3), targetDb = DoubleArray(3),
                    correctionDb = DoubleArray(3), nullMask = BooleanArray(3), minPhaseGateVerified = true,
                    transitionHz = 300.0, rolloffHz = 40.0, floorHz = 40.0, filters = emptyList(), preampDb = 0.0, pointsRead = 3
                )
                MeasureActivity::class.java.getDeclaredMethod("saveImportProfile", ImportResult::class.java, Long::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                    .invoke(activity, imp, 1_800_000_000_000L)
            }
        }
        val saved = ProfileStore(app).getAllProfiles().single()
        assertEquals(ProfileOrigin.rewImport(thisBuild), saved.origin)
        assertNull(saved.origin!!.capturedBy)
        assertNull(saved.origin!!.analysedBy)
        assertEquals(thisBuild.label(), line(saved, "Imported with"))
        assertEquals("outside Core EQ; the software and its version are not recorded", line(saved, "Measured with"))
        assertNull("no Core EQ build is named as having recorded it", line(saved, "Sweep recorded with"))
        assertFalse(saved.origin!!.summary().contains("measured by"))
    }

    @Test fun `a phone measurement names the TV build that analysed it and leaves the phone build unrecorded`() {
        val serverClass = CompanionServer::class.java
        val sessionClass = serverClass.declaredClasses.first { it.simpleName == "MeasurementSession" }
        val phaseClass = serverClass.declaredClasses.first { it.simpleName == "MeasurementPhase" }
        val firstPhase = phaseClass.enumConstants!!.first()
        val session = sessionClass.declaredConstructors.first { it.parameterCount == 12 }.apply { isAccessible = true }
            .newInstance("s-1", "dialogue", null, 0L, false, null, null, null, firstPhase, null, null, null)
        val build = serverClass.declaredMethods.first { it.name == "profileFromMeasurement" && it.parameterCount == 7 }
            .apply { isAccessible = true }
        val profile = CompanionServer(app).use { server ->
            build.invoke(server, sweepResult(), session, null, CaptureInfo(captureDeviceModel = "Phone X"), 10.0, 1_800_000_000_000L, null) as Profile
        }
        assertEquals(ProfileOrigin.phoneMeasurement(thisBuild, phoneBuild = null), profile.origin)
        assertEquals(thisBuild, profile.origin!!.analysedBy)
        assertNull("the upload does not report the phone's build", profile.origin!!.capturedBy)
        assertEquals("phone's Core EQ build not reported", line(profile, "Sweep recorded with"))
        assertEquals(thisBuild.label(), line(profile, "Analysed with"))
    }

    @Test fun `a manual-only profile is created by this build and claims no measurement`() {
        ActivityScenario.launch(ManualEqActivity::class.java).use { ScreenHarness.idle() }
        val saved = ProfileStore(app).getAllProfiles().single()
        assertTrue(saved.manualOnly)
        assertEquals(ProfileOrigin.manual(thisBuild), saved.origin)
        assertEquals(thisBuild.label(), line(saved, "Created with"))
        assertNull(line(saved, "Analysed with"))
        assertNull(line(saved, "Measured with"))
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
