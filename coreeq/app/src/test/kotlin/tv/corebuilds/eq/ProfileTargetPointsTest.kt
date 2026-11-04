package tv.corebuilds.eq

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.CustomTarget
import tv.corebuilds.eq.dsp.Targets
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore

/**
 * A profile measured against an imported target keeps that curve. It used to save
 * only the key "custom", so the Profiles graph drew whatever was imported now: a
 * different curve after the next import, and a flat line after a restart, before
 * Measure had restored the import. The correction itself was never affected.
 */
class ProfileTargetPointsTest {

    private val measuredAgainst = listOf(100.0 to 4.0, 1_000.0 to 0.0, 10_000.0 to -6.0)
    private val importedLater = listOf(100.0 to -8.0, 1_000.0 to 0.0, 10_000.0 to 5.0)
    private val freqs = doubleArrayOf(100.0, 1_000.0, 10_000.0)

    private fun profile(target: String, points: List<Pair<Double, Double>>) = Profile(
        id = "profile-1", name = "Den", timestampMs = 1_700_000_000_000L,
        target = target, micType = "remote microphone",
        curve = listOf(CurvePoint(20.0, 0.0, 0.0), CurvePoint(20_000.0, 0.0, 0.0)),
        targetPoints = points
    )

    private fun roundTrip(p: Profile): Profile =
        ProfileStore.parseProfile(JSONObject(Formats.exportProfileJson(p)))

    private fun pointsJson(vararg points: Pair<Any, Any>): JSONArray {
        val array = JSONArray()
        for ((hz, db) in points) array.put(JSONObject().put("hz", hz).put("db", db))
        return array
    }

    @After
    fun clearImport() {
        CustomTarget.points = null
    }

    @Test
    fun `an imported target is saved with the profile and read back`() {
        assertEquals(measuredAgainst, roundTrip(profile(CustomTarget.KEY, measuredAgainst)).targetPoints)
    }

    @Test
    fun `the graph draws the curve the profile was measured against, not the current import`() {
        CustomTarget.points = importedLater
        val drawn = roundTrip(profile(CustomTarget.KEY, measuredAgainst)).targetDb(freqs)!!
        assertArrayEquals(doubleArrayOf(4.0, 0.0, -6.0), drawn, 1e-9)
    }

    @Test
    fun `with nothing imported, as after a restart, the saved curve still draws`() {
        val drawn = profile(CustomTarget.KEY, measuredAgainst).targetDb(freqs)!!
        assertArrayEquals(doubleArrayOf(4.0, 0.0, -6.0), drawn, 1e-9)
    }

    @Test
    fun `an imported-target profile without its curve draws no target rather than a wrong one`() {
        CustomTarget.points = importedLater
        assertNull(profile(CustomTarget.KEY, emptyList()).targetDb(freqs))
    }

    @Test
    fun `built-in targets write no points and are still rebuilt from their key`() {
        val harman = profile("harman", emptyList())
        assertFalse(JSONObject(Formats.exportProfileJson(harman)).has("target_points"))
        assertArrayEquals(Targets.targetCurve("harman", freqs), harman.targetDb(freqs)!!, 1e-12)
    }

    @Test
    fun `points in a backup on a built-in target are ignored, not kept and written back`() {
        val json = JSONObject(Formats.exportProfileJson(profile("harman", emptyList())))
        json.put("target_points", pointsJson(100.0 to 3.0, 1_000.0 to 0.0))
        assertTrue(ProfileStore.parseProfile(json).targetPoints.isEmpty())
    }

    @Test
    fun `damaged points in a backup are dropped and gains held to the budget`() {
        val json = JSONObject(Formats.exportProfileJson(profile(CustomTarget.KEY, measuredAgainst)))
        json.put("target_points", pointsJson(
            10_000.0 to 40.0,   // out of order, over the +6 dB budget
            100.0 to -3.0,
            "x" to 0.0,         // not a number
            5.0 to 0.0,         // below 20 Hz
            100.0 to 9.0        // repeated frequency
        ))
        assertEquals(
            listOf(100.0 to -3.0, 10_000.0 to CorrectionLimits.MAX_BOOST_DB),
            ProfileStore.parseProfile(json).targetPoints
        )
    }

    @Test
    fun `fewer than two usable points read back as no curve, and draw no target`() {
        val json = JSONObject(Formats.exportProfileJson(profile(CustomTarget.KEY, measuredAgainst)))
        json.put("target_points", pointsJson(1_000.0 to 0.0, "x" to 1.0))
        val back = ProfileStore.parseProfile(json)
        assertTrue(back.targetPoints.isEmpty())
        assertNull(back.targetDb(freqs))
    }
}
