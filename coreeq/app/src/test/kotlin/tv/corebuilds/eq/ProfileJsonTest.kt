package tv.corebuilds.eq

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore

/**
 * The 1.3.0 quality score survives the trip every profile takes: written by
 * [Formats.exportProfileJson] (storage and the JSON backup both use it) and
 * read back by [ProfileStore.parseProfile]. A profile without a score, or
 * one from a file that carries a nonsense value, reads back as "not scored",
 * never as 0.
 */
class ProfileJsonTest {

    private fun profile(score: Int?) = Profile(
        id = "profile-1", name = "Living room", timestampMs = 1_700_000_000_000L,
        target = "flat", micType = "remote microphone", rolloffHz = 45.0, snrDb = 31.5,
        qualityScore = score,
        curve = listOf(CurvePoint(20.0, -3.0, 2.0), CurvePoint(20_000.0, 1.0, -1.0))
    )

    private fun roundTrip(p: Profile): Profile =
        ProfileStore.parseProfile(JSONObject(Formats.exportProfileJson(p)))

    @Test
    fun `a measured score is saved and read back unchanged`() {
        for (score in listOf(0, 1, 78, 100)) {
            assertEquals(score, roundTrip(profile(score)).qualityScore)
        }
    }

    @Test
    fun `the rest of the profile survives alongside it`() {
        val back = roundTrip(profile(78))
        assertEquals("profile-1", back.id)
        assertEquals("Living room", back.name)
        assertEquals(31.5, back.snrDb!!, 1e-9)
        assertEquals(45.0, back.rolloffHz, 1e-9)
        assertEquals(2, back.curve.size)
    }

    @Test
    fun `no score is written as no key, and reads back as not scored`() {
        val json = JSONObject(Formats.exportProfileJson(profile(null)))
        assertEquals(false, json.has("quality_score"))
        assertNull(ProfileStore.parseProfile(json).qualityScore)
    }

    @Test
    fun `a backup with a null or out-of-range score reads back as not scored`() {
        val base = JSONObject(Formats.exportProfileJson(profile(78)))
        for (bad in listOf<Any>(JSONObject.NULL, -1, 101, 400)) {
            base.put("quality_score", bad)
            assertNull("quality_score = $bad", ProfileStore.parseProfile(base).qualityScore)
        }
    }
}
