package tv.corebuilds.eq.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.NightMode

class DiagnosticsSummaryTest {

    private val home = HomeSummary.of(
        HomeSummary.Facts(
            correctionOn = true, serviceRunning = true, measurementPaused = false,
            anyProfile = true, profileForOutput = true, outputKnown = true, outputLabel = "TV speakers",
            appliedEngine = "DynamicsProcessing", playing = false, faultMessage = null, bypass = false,
            night = NightMode.Level.LIGHT, clarityDb = 2.0, stereoSpeakers = false, lowVolumeBass = false,
            autoLoudness = false, bassBoost = false, loudnessEnhancer = false
        )
    )

    private val facts = DiagnosticsSummary.Facts(
        appVersion = "1.13.2", versionCode = 29, buildType = "debug",
        android = "14", sdkInt = 34, device = "Maker Model X",
        profileKind = "room measurement", profileOutput = "TV speakers", profileAgeSeconds = 2 * 86_400L,
        outputInUse = "TV speakers", outputEvidence = "track probe",
        home = home,
        lastStatus = "Applied 25 bands to the output mix.", lastStatusAgeSeconds = 40, lastStatusIsError = false,
        measurement = DiagnosticsSummary.Measurement("failed", "primary", "No sweep was heard.", 600),
        backgroundWork = "1 route probes · 0 discovery dumps · 2 effect writes (1 skipped, unchanged) · 0 correction pauses"
    )

    @Test fun `every section the brief names is present, in order`() {
        val lines = DiagnosticsSummary.text(facts).lines()
        assertEquals(DiagnosticsSummary.HEADER, lines[0])
        assertEquals("App: 1.13.2 (29, debug)", lines[1])
        assertEquals("Android: 14 (API 34) · Maker Model X", lines[2])
        assertEquals("Output in use: TV speakers (track probe)", lines[3])
        assertEquals("Profile: room measurement · for TV speakers · saved 2 d ago", lines[4])
        assertEquals("State: applied", lines[5])
        assertTrue(lines[6], lines[6].startsWith("Applied by DynamicsProcessing"))
        assertEquals("Listening: night light · clarity +2 dB.", lines[7])
        assertEquals("Last service message, 40 s ago: Applied 25 bands to the output mix.", lines[8])
        assertEquals("Last measurement: failed (primary), 10 min ago: No sweep was heard.", lines[9])
        assertTrue(lines[10], lines[10].startsWith("Background work since start: 1 route probes"))
        assertEquals(11, lines.size)
    }

    @Test fun `nothing recorded is said as nothing recorded`() {
        val text = DiagnosticsSummary.text(
            facts.copy(
                profileKind = null, profileOutput = null, profileAgeSeconds = null,
                outputInUse = null, outputEvidence = null, lastStatus = null, lastStatusAgeSeconds = null,
                measurement = null
            )
        )
        assertTrue(text.contains("Output in use: not identified by Android"))
        assertTrue(text.contains("Profile: none applies or is chosen"))
        assertTrue(text.contains("Last service message: none recorded"))
        assertTrue(text.contains("Last measurement: none recorded"))
    }

    @Test fun `errors are marked as errors`() {
        val text = DiagnosticsSummary.text(facts.copy(lastStatus = "Effect refused", lastStatusIsError = true))
        assertTrue(text.contains("Last service message (error), 40 s ago: Effect refused"))
    }

    @Test fun `addresses and long ids are masked in free text`() {
        val noisy = "Phone 192.168.1.23:8765 paired, key 3f9a0c1d2e4b5a6978ffee00 via aa:bb:cc:dd:ee:ff and fe80:0:0:0:1:2:3:4"
        val text = DiagnosticsSummary.text(facts.copy(lastStatus = noisy, measurement = facts.measurement!!.copy(message = noisy)))
        assertFalse(text, text.contains("192.168"))
        assertFalse(text, text.contains("8765"))
        assertFalse(text, text.contains("3f9a0c1d2e4b"))
        assertFalse(text, text.contains("aa:bb:cc"))
        assertFalse(text, text.contains("fe80"))
        assertTrue(text.contains("Phone [address] paired, key [id] via [address] and [address]"))
        // Versions and decibel figures are not addresses.
        assertEquals("v1.13.2 at -6.5 dB", DiagnosticsSummary.clean("v1.13.2 at  -6.5 dB"))
    }

    @Test fun `free text stays on one bounded line`() {
        val cleaned = DiagnosticsSummary.clean("a\nb\t" + "x".repeat(500))
        assertFalse(cleaned.contains('\n'))
        assertEquals(240, cleaned.length)
        assertTrue(cleaned.endsWith("…"))
    }

    @Test fun `ages round to the unit a person reads`() {
        assertEquals("0 s ago", DiagnosticsSummary.ago(-5))
        assertEquals("89 s ago", DiagnosticsSummary.ago(89))
        assertEquals("2 min ago", DiagnosticsSummary.ago(90))
        assertEquals("2 h ago", DiagnosticsSummary.ago(90 * 60))
        assertEquals("2 d ago", DiagnosticsSummary.ago(36 * 3600))
    }
}
