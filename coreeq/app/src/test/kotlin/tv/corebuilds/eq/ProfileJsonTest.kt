package tv.corebuilds.eq

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.export.CaptureInfo
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.MicrophoneResponsePoint
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.export.RepeatabilityCheck
import tv.corebuilds.eq.export.RepeatabilityMetrics

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

    @Test
    fun `capture provenance including Android mic response survives a backup round trip`() {
        val capture = CaptureInfo(
            requestedSampleRateHz = 48_000,
            inputSampleRateHz = 44_100,
            analysisSampleRateHz = 16_000,
            audioSource = "VOICE_RECOGNITION",
            inputDeviceName = "TV remote microphone",
            inputDeviceType = "AUDIO_DEVICE_IN_REMOTE_SUBMIX",
            activeMicrophoneCount = 1,
            captureDeviceModel = "Example Phone 12",
            captureOsVersion = "16",
            microphoneDescriptions = listOf("Remote control microphone"),
            processing = listOf("AGC was off, now off", "NoiseSuppressor not exposed"),
            unprocessedAudioSourceSupported = false,
            microphoneResponse = listOf(
                MicrophoneResponsePoint("Remote control microphone", 100.0, -0.5),
                MicrophoneResponsePoint("Remote control microphone", 1_000.0, 0.2)
            )
        )
        val json = JSONObject(Formats.exportProfileJson(profile(78).copy(captureInfo = capture)))
        val captureJson = json.getJSONObject("capture")

        assertEquals(44_100, captureJson.getInt("input_sample_rate_hz"))
        assertEquals(2, captureJson.getJSONArray("microphone_frequency_response").length())
        assertEquals(capture, ProfileStore.parseProfile(json).captureInfo)
    }

    @Test
    fun `repeat comparison saves both measured traces and capture provenance`() {
        val check = RepeatabilityCheck(
            metrics = RepeatabilityMetrics(
                comparedBands = 28,
                lowFrequencyHz = 40.0,
                highFrequencyHz = 6_300.0,
                levelOffsetDb = 0.8,
                medianAbsDifferenceDb = 0.25,
                rmsDifferenceDb = 0.42,
                p95AbsDifferenceDb = 0.91
            ),
            comparisonCurve = listOf(CurvePoint(40.0, -3.2, 1.1), CurvePoint(63.0, -2.7, 0.6)),
            captureInfo = CaptureInfo(
                inputSampleRateHz = 16_000,
                analysisSampleRateHz = 16_000,
                inputDeviceType = "remote microphone"
            ),
            outputKind = "speaker",
            outputName = "TV speakers",
            snrDb = 36.5,
            driftPpm = -18.0,
            note = "Descriptive same-position comparison only; captures are not averaged."
        )
        val back = roundTrip(profile(78).copy(repeatabilityCheck = check))
        assertEquals(check, back.repeatabilityCheck)
        assertEquals(28, back.repeatabilityCheck!!.metrics!!.comparedBands)
        assertEquals(2, back.repeatabilityCheck!!.comparisonCurve.size)
    }

    @Test
    fun `legacy profile omits repeat check and malformed optional metrics are ignored`() {
        val base = JSONObject(Formats.exportProfileJson(profile(78)))
        assertFalse(base.has("repeatability"))
        assertNull(ProfileStore.parseProfile(base).repeatabilityCheck)

        base.put("repeatability", JSONObject()
            .put("metrics", JSONObject()
                .put("compared_bands", 900)
                .put("comparison_range_hz", JSONArray().put(100.0).put(50.0)))
            .put("curve", JSONArray().put(JSONObject()
                .put("hz", -1.0).put("measured_db", 0.0).put("correction_db", 0.0))))
        assertNull(ProfileStore.parseProfile(base).repeatabilityCheck)
    }

    @Test
    fun `old profiles without capture metadata remain valid`() {
        val json = JSONObject(Formats.exportProfileJson(profile(78)))

        assertFalse(json.has("capture"))
        assertNull(ProfileStore.parseProfile(json).captureInfo)
    }

    @Test
    fun `invalid optional microphone response points are ignored not fatal`() {
        val json = JSONObject(Formats.exportProfileJson(profile(78).copy(
            captureInfo = CaptureInfo(inputSampleRateHz = 44_100, analysisSampleRateHz = 16_000)
        )))
        json.getJSONObject("capture").put(
            "microphone_frequency_response",
            JSONArray()
                .put(JSONObject().put("microphone", "mic").put("frequency_hz", -1).put("response_db", 0.0))
                .put(JSONObject().put("microphone", "mic").put("frequency_hz", "not a frequency").put("response_db", 0.0))
                .put(JSONObject().put("frequency_hz", 1_000.0).put("response_db", 0.0))
        )

        val back = ProfileStore.parseProfile(json)
        assertEquals(44_100, back.captureInfo?.inputSampleRateHz)
        assertTrue(back.captureInfo?.microphoneResponse.isNullOrEmpty())
    }
}
