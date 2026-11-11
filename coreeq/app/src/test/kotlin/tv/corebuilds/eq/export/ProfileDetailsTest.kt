package tv.corebuilds.eq.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ProfileDetailsTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val label: (String?) -> String = { kind -> if (kind == "speaker") "TV speakers" else kind ?: "unknown" }

    private fun value(lines: List<ProfileDetails.Line>, name: String): String =
        lines.first { it.label == name }.value

    /** A profile saved before outputs, capture provenance and the ceiling were recorded. */
    private fun legacy() = Profile(
        id = "old", name = "Old room", timestampMs = 1_700_000_000_000L,
        target = "flat", micType = "remote mic"
    )

    @Test fun missingMetadataReadsNotRecordedNeverInvalid() {
        val lines = ProfileDetails.lines(legacy(), label, utc)
        assertTrue(value(lines, "Output").startsWith(ProfileDetails.NOT_RECORDED))
        assertEquals(ProfileDetails.NOT_RECORDED, value(lines, "Capture"))
        assertEquals(ProfileDetails.NOT_RECORDED, value(lines, "Reverberation (RT60)"))
        assertEquals(ProfileDetails.NOT_RECORDED, value(lines, "Signal-to-noise"))
        assertEquals("not entered", value(lines, "Room volume"))
        assertTrue(value(lines, "Corrected up to").contains("not recorded"))
        assertFalse(ProfileDetails.text(lines).contains("invalid", ignoreCase = true))
        assertEquals("2023-11-14 22:13", value(lines, "Measured"))
    }

    @Test fun outputNamesKindAndNameOnce() {
        val tagged = legacy().copy(outputKind = "speaker", outputName = "TV speakers")
        assertEquals("TV speakers", value(ProfileDetails.lines(tagged, label, utc), "Output"))
        val bar = legacy().copy(outputKind = "hdmi_arc", outputName = "Sonos Beam")
        assertEquals("hdmi_arc · Sonos Beam", value(ProfileDetails.lines(bar, label, utc), "Output"))
    }

    @Test fun repeatAgreementIsNeverCalledQuality() {
        val metrics = RepeatabilityMetrics(
            comparedBands = 18, lowFrequencyHz = 80.0, highFrequencyHz = 6_000.0,
            levelOffsetDb = 0.4, medianAbsDifferenceDb = 0.6, rmsDifferenceDb = 0.9, p95AbsDifferenceDb = 1.8
        )
        val repeated = legacy().copy(
            repeatabilityCheck = RepeatabilityCheck(metrics, emptyList(), note = "Same-position repeat average refused."),
            measurementNotes = listOf("Same-position repeat average: two identified captures were level-aligned.")
        )
        val lines = ProfileDetails.lines(repeated, label, utc)
        val repeat = value(lines, "Repeat at same spot")
        assertTrue(repeat.contains("18 bands") && repeat.contains("0.9 dB"))
        assertTrue(repeat.contains("not a quality grade"))
        assertFalse(ProfileDetails.text(lines).contains("good", ignoreCase = true))
        // Notes travel as their own lines, so the average and the refusal are both readable.
        assertTrue(lines.any { it.label == "Note" && it.value.startsWith("Same-position repeat average") })
        assertTrue(lines.any { it.label == "Repeat note" && it.value.contains("refused") })
    }

    @Test fun manualProfileClaimsNoMeasurement() {
        val manual = legacy().copy(manualOnly = true, stimulus = "manual_eq")
        val lines = ProfileDetails.lines(manual, label, utc)
        assertEquals("manual profile (no room measurement)", value(lines, "Kind"))
        assertTrue(lines.any { it.label == "Created" })
        assertFalse(lines.any { it.label == "Reverberation (RT60)" || it.label == "Capture" })
    }

    @Test fun cleanNameAndCopyName() {
        assertEquals("Living room", ProfileStore.cleanName("  Living \t  room  "))
        assertEquals(null, ProfileStore.cleanName("   "))
        assertEquals(ProfileStore.MAX_NAME_LENGTH, ProfileStore.cleanName("x".repeat(200))!!.length)
        assertEquals("Den (copy)", ProfileStore.copyName("Den", setOf("Den")))
        assertEquals("Den (copy 2)", ProfileStore.copyName("Den", setOf("Den", "Den (copy)")))
        assertTrue(ProfileStore.copyName("y".repeat(60), emptySet()).length <= ProfileStore.MAX_NAME_LENGTH)
    }
}
