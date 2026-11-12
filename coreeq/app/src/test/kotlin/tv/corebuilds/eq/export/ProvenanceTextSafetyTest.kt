package tv.corebuilds.eq.export

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.NightMode
import tv.corebuilds.eq.ui.DiagnosticsSummary
import tv.corebuilds.eq.ui.HomeSummary

/**
 * Provenance read from a file is whatever the file says: an imported profile's
 * origin is a claim, not an identity. These pin what such text may contain once
 * read, and what of it reaches Details and the diagnostics summary.
 */
class ProvenanceTextSafetyTest {

    private fun build(version: Any?, code: Any? = 29): JSONObject =
        JSONObject().put("app", "Core EQ").put("version", version).put("code", code)

    @Test fun `a build version is a short single-line version number or nothing`() {
        for (good in listOf("1.13.2", "1.14", "2", "1.14.0-rc1", "1.14.0+build.7")) {
            assertEquals(good, AppBuild.fromJson(build(good))?.versionName)
        }
        val bad = listOf(
            "1.13.2\nApp: 9.9.9",           // an embedded newline would forge a diagnostics line
            "1.13.2\r",
            "1.13.2\u0000",
            "1.13\u0007.2",                 // control characters
            "1.13.2‮",                 // a direction override
            "Sam's TV",                     // a name
            "Living room 1.13.2",
            "192.168.1.20",                 // an address in the shape of a version
            "aa:bb:cc:dd:ee:ff",
            "fe80::1c2b",
            "1.13.2-" + "x".repeat(30),     // over length
            "",
            "   "
        )
        for (value in bad) {
            assertNull("accepted ${JSONObject.quote(value)}", AppBuild.fromJson(build(value)))
        }
    }

    @Test fun `a rejected build makes the origin not recorded rather than repaired`() {
        val origin = JSONObject()
            .put("kind", "tv_measurement")
            .put("created_by", build("1.13.2\nState: applied"))
        assertNull(ProfileOrigin.fromJson(origin))
        // A rejected optional build is dropped on its own; the rest still reads.
        val partial = JSONObject()
            .put("kind", "phone_measurement")
            .put("created_by", build("1.14.0", 30))
            .put("captured_by", build("Sam's phone"))
        val read = ProfileOrigin.fromJson(partial)
        assertNotNull(read)
        assertNull(read!!.capturedBy)
    }

    @Test fun `an origin kind this build does not know is shown, never echoed into diagnostics`() {
        val origin = ProfileOrigin("sams_living_room", AppBuild("1.20.0", 40))
        val summary = origin.summary()
        assertFalse(summary, summary.contains("sams_living_room"))
        assertTrue(summary, summary.contains("Core EQ 1.20.0 (40)"))
        // Details is local to the TV and still names the kind, so it can be reported by hand.
        val lines = ProfileDetails.originLines(origin)
        assertTrue(lines.toString(), lines.any { it.value.contains("sams_living_room") })
    }

    @Test fun `Details says the origin is recorded, not verified`() {
        val lines = ProfileDetails.originLines(ProfileOrigin.tvMeasurement(AppBuild("1.14.0", 30)))
        assertTrue(lines.toString(), lines.any { it.value.contains("not verified") })
        // Nothing recorded: nothing to verify, and no such line.
        assertFalse(ProfileDetails.originLines(null).any { it.value.contains("not verified") })
    }

    @Test fun `the diagnostics profile line is one cleaned line whatever the profile holds`() {
        val home = HomeSummary.of(
            HomeSummary.Facts(
                correctionOn = true, serviceRunning = true, measurementPaused = false,
                anyProfile = true, profileForOutput = true, outputKnown = true, outputLabel = "TV speakers",
                appliedEngine = "DynamicsProcessing", playing = false, faultMessage = null, bypass = false,
                night = NightMode.Level.OFF, clarityDb = 0.0, stereoSpeakers = false, lowVolumeBass = false,
                autoLoudness = false, bassBoost = false, loudnessEnhancer = false
            )
        )
        val facts = DiagnosticsSummary.Facts(
            appVersion = "1.14.0", versionCode = 30, buildType = "debug",
            android = "14", sdkInt = 34, device = "Maker Model X",
            profileKind = "imported measurement (REW)",
            profileOutput = "Sam's Sonos at 192.168.1.20\nApp: 9.9.9",
            profileAgeSeconds = 60,
            profileOrigin = "measurement file imported by Core EQ 1.14.0 (30) for Sam's Sonos aa:bb:cc:dd:ee:ff\u0007",
            outputInUse = "TV speakers", outputEvidence = "track probe",
            home = home,
            lastStatus = null, lastStatusAgeSeconds = null, lastStatusIsError = false,
            measurement = null,
            backgroundWork = "none",
            knownNames = listOf("Sam's Sonos")
        )
        val lines = DiagnosticsSummary.text(facts).lines()
        val profile = lines.single { it.startsWith("Profile: ") }
        assertFalse(profile, profile.contains("Sam's Sonos"))
        assertFalse(profile, profile.contains("192.168.1.20"))
        assertFalse(profile, profile.contains("aa:bb:cc:dd:ee:ff"))
        assertFalse(profile, profile.any { it.isISOControl() })
        // The forged line stayed inside the profile line.
        assertEquals(1, lines.count { it.startsWith("App: ") })
    }
}
