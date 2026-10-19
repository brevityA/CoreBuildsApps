package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.corebuilds.eq.dsp.CorrectionImport
import tv.corebuilds.eq.dsp.ManualEq

class PowerampEqImportTest {

    @Test
    fun importsANativeParametricPresetAndUsesItsEmbeddedName() {
        val result = CorrectionImport.parseAny(
            """
            [
              {
                "name": "Living Room",
                "preamp": -3.5,
                "parametric": true,
                "bands": [
                  {"type":0,"channels":0,"frequency":90,"q":0.8,"gain":0,"color":0},
                  {"type":1,"channels":0,"frequency":10000,"q":0.6,"gain":0,"color":0},
                  {"type":3,"channels":0,"frequency":1000,"q":1.0,"gain":3.0,"color":0}
                ]
              }
            ]
            """.trimIndent()
        )

        assertEquals("Poweramp parametric JSON", result.source)
        assertEquals("Living Room", result.presetName)
        assertEquals(-3.5, result.preampDb!!, 1e-9)
        assertTrue(result.filters.any { it.fc == 1000.0 && it.gain == 3.0 })
        assertTrue(result.maxErrorDb < 0.1)
        assertTrue(result.notes.any { it.contains("preamp") })
        assertTrue(result.notes.any { it.contains("no schema version") })
    }

    @Test
    fun acceptsDatedParametricFixtureButListsUnmappedTypeCodes() {
        val result = CorrectionImport.parseAny(fixture("parametric_github_2026.json"))

        assertEquals("Poweramp parametric JSON", result.source)
        assertEquals("Soundcore Q21i NC - Final", result.presetName)
        assertTrue(result.filters.isNotEmpty())
        assertTrue(result.maxErrorDb.isFinite())
        assertTrue(result.notes.any { it.contains("type 0") })
        assertTrue(result.notes.any { it.contains("type 1") })
        assertTrue(result.notes.any { it.contains("type 2") })
        assertTrue(result.notes.any { it.contains("no schema version") })
        assertTrue(result.notes.any { it.contains("preamp") })
    }

    @Test
    fun olderPublishedParametricFixtureWithNoSupportedTypesIsRejectedWithDetails() {
        val error = importError(fixture("parametric_forum_2023.json"))

        assertTrue(error.message!!.contains("No supported active Poweramp EQ bands"))
        assertTrue(error.message!!.contains("band #3 (type 0)"))
        assertTrue(error.message!!.contains("band #4 (type 1)"))
    }

    @Test
    fun datedGistWithOnlyTypeTwoActiveIsRejectedRatherThanGuessed() {
        val error = importError(fixture("parametric_gist_2023.json"))

        assertTrue(error.message!!.contains("No supported active Poweramp EQ bands"))
        assertTrue(error.message!!.contains("band #3 (type 2)"))
    }

    @Test
    fun publicGraphicFixtureIsInterpolatedFromFixedBandGains() {
        val result = CorrectionImport.parseAny(fixture("graphic_pa-ceq-3.0.json"))

        assertEquals("Poweramp graphic JSON", result.source)
        assertEquals("PA-CEQ 3.0", result.presetName)
        assertTrue(result.filters.any { it.gain > 0.0 })
        assertTrue(result.maxErrorDb.isFinite())
        assertTrue(result.notes.any { it.contains("interpolated and refit") })
    }

    @Test
    fun stereoOnlyPeakingBandsAreAveragedIntoTheSharedCoreEqCurve() {
        val result = CorrectionImport.parseAny(
            """
            {"name":"Stereo","parametric":true,"bands":[
              {"type":3,"channels":1,"frequency":1000,"q":1,"gain":4,"color":0},
              {"type":3,"channels":2,"frequency":1000,"q":1,"gain":2,"color":0}
            ]}
            """.trimIndent()
        )

        assertEquals(3.0, ManualEq.responseDb(result.filters, 1000.0), 0.1)
        assertTrue(result.notes.any { it.contains("averaged") && it.contains("both channels") })
    }

    @Test
    fun shelvesAreIncludedAndApproximationRemainsFinite() {
        val result = CorrectionImport.parseAny(
            """
            {"parametric":true,"bands":[
              {"type":4,"channels":0,"frequency":150,"q":0.7071,"gain":3,"color":0},
              {"type":5,"channels":0,"frequency":5000,"q":0.7071,"gain":2,"color":0}
            ]}
            """.trimIndent()
        )

        assertTrue(result.filters.any { it.gain > 0.0 })
        assertTrue(result.maxErrorDb.isFinite())
        assertTrue(result.filters.all { it.gain in ManualEq.MIN_GAIN_DB..ManualEq.MAX_GAIN_DB })
    }

    @Test
    fun unsupportedTonePassAndUnresolvedTypesAreRejectedInsteadOfMisreadAsPeaks() {
        val error = importError(
            """
            [{"parametric":true,"bands":[
              {"type":0,"channels":0,"frequency":90,"q":0.8,"gain":2,"color":0},
              {"type":1,"channels":0,"frequency":10000,"q":0.6,"gain":1,"color":0},
              {"type":2,"channels":0,"frequency":500,"q":1,"gain":3,"color":0}
            ]}]
            """.trimIndent()
        )

        assertTrue(error.message!!.contains("type 0"))
        assertTrue(error.message!!.contains("type 1"))
        assertTrue(error.message!!.contains("type 2"))
    }

    @Test
    fun unknownActiveTypeIsListedWhenOtherSupportedBandsCanBeImported() {
        val result = CorrectionImport.parseAny(
            """
            {"parametric":true,"bands":[
              {"type":3,"channels":0,"frequency":1000,"q":1,"gain":2},
              {"type":77,"channels":0,"frequency":2000,"q":1,"gain":4}
            ]}
            """.trimIndent()
        )

        assertTrue(result.filters.isNotEmpty())
        assertTrue(result.notes.any { it.contains("type 77") && it.contains("unknown to Core EQ") })
    }

    @Test
    fun multiplePowerampPresetsMustBeExportedIndividually() {
        expectImportError("""[{"parametric":false,"bands":[]},{"parametric":false,"bands":[]}]""", "2 presets")
    }

    @Test
    fun invalidJsonAndOversizePowerampFilesAreRejected() {
        expectImportError("{not json}", "could not be parsed")
        expectImportError("{".repeat(CorrectionImport.MAX_CHARS + 1), "64 KB")
    }

    @Test
    fun plainTextStillUsesTheExistingAutoEqImporter() {
        val result = CorrectionImport.parseAny("Filter 1: ON PK Fc 1000 Hz Gain 3.0 dB Q 1.0")
        assertEquals("ParametricEQ", result.source)
        assertTrue(result.filters.any { it.fc == 1000.0 && it.gain == 3.0 })
    }

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/poweramp/$name")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw AssertionError("Missing Poweramp fixture: $name")

    private fun importError(json: String): IllegalArgumentException {
        try {
            CorrectionImport.parseAny(json)
            fail("Expected import to fail")
        } catch (e: IllegalArgumentException) {
            return e
        }
        throw AssertionError("Expected import to fail")
    }

    private fun expectImportError(json: String, expectedMessage: String) {
        val error = importError(json)
        assertTrue(
            "Unexpected message: ${error.message}",
            error.message!!.contains(expectedMessage, ignoreCase = true)
        )
    }
}
