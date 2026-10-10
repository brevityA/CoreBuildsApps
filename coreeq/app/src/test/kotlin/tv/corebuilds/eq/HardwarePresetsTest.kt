package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.dsp.HardwarePresets
import tv.corebuilds.eq.dsp.ManualEq

class HardwarePresetsTest {

    private val bar800 = HardwarePresets.MODELS.single { it.id == "jbl-bar-800" }

    @Test
    fun theJblBar800IsFoundUnderEachNameAndroidMightReport() {
        for (name in listOf("JBL BAR 800", "JBL Bar800", "BAR 800", "JBLBAR800", "jbl_bar-800", "JBL Bar 800 Soundbar")) {
            assertEquals("name: $name", bar800, HardwarePresets.matchAny(listOf(name)))
        }
    }

    @Test
    fun aLongerModelNumberOrAnotherProductIsNotTheBar800() {
        for (name in listOf("JBL Bar 8000", "BAR 8001", "Samsung HW-Q990D", "Living Room TV", "", "  ")) {
            assertNull("name: '$name'", HardwarePresets.matchAny(listOf(name)))
        }
        assertNull(HardwarePresets.matchAny(listOf(null)))
    }

    @Test
    fun aSoundbarNameIsOnlyEvidenceOnOutputsThatCanCarryOne() {
        assertEquals(bar800, HardwarePresets.detect(OutputRoute.HDMI_ARC, "JBL BAR 800", emptyList()))
        assertEquals(bar800, HardwarePresets.detect(OutputRoute.BLUETOOTH, "JBL BAR 800", emptyList()))
        assertEquals(bar800, HardwarePresets.detect(OutputRoute.HDMI, "HDMI", listOf("TV", "JBL BAR 800")))

        // The TV's own speakers are never a soundbar, even if a name claims otherwise.
        assertNull(HardwarePresets.detect(OutputRoute.SPEAKER, "JBL BAR 800", listOf("JBL BAR 800")))
        assertNull(HardwarePresets.detect(OutputRoute.UNKNOWN, "JBL BAR 800", emptyList()))
        assertNull(HardwarePresets.detect(null, "JBL BAR 800", emptyList()))
    }

    @Test
    fun aConnectedSoundbarIsFoundEvenWhenTheRouteNameIsGeneric() {
        // Android may report only the generic port name as the route. Another
        // device of the same kind, in the connected list, still identifies it.
        assertEquals(bar800, HardwarePresets.detect(OutputRoute.HDMI_ARC, "HDMI ARC", listOf("Samsung Q80", "JBL BAR 800")))
        assertNull(HardwarePresets.detect(OutputRoute.HDMI_ARC, "Samsung Q80", listOf("Samsung Q80")))
    }

    @Test
    fun everyShippedPresetIsUsableInTheManualEqAndTheRightSize() {
        assertTrue(HardwarePresets.PRESETS.isNotEmpty())
        assertEquals(HardwarePresets.PRESETS.size, HardwarePresets.PRESETS.map { it.id }.toSet().size)
        for (preset in HardwarePresets.PRESETS) {
            assertTrue("name length: ${preset.name}", preset.name.length <= 32)
            assertTrue("gain range: ${preset.id}", preset.filters.all { it.gain in ManualEq.MIN_GAIN_DB..ManualEq.MAX_GAIN_DB })
            assertTrue("fixed bands only: ${preset.id}", preset.filters.all { f -> ManualEq.BAND_CENTRES_HZ.any { kotlin.math.abs(it - f.fc) < 0.01 } })
            assertTrue("sane Q: ${preset.id}", preset.filters.all { it.q in 0.4..4.0 })
            assertTrue("already sanitised: ${preset.id}", ManualEq.sameFilters(ManualEq.sanitize(preset.filters), preset.filters))
            assertNotNull(HardwarePresets.modelForPreset(preset.id))
        }
    }

    @Test
    fun hardwarePresetNamesDoNotCollideWithBuiltIns() {
        val builtIn = ManualEq.BUILT_IN_PRESETS.map { it.name.lowercase() }.toSet()
        for (preset in HardwarePresets.PRESETS) {
            assertTrue("collides with built-in: ${preset.name}", preset.name.lowercase() !in builtIn)
        }
    }

    @Test
    fun theBar800PresetsCutTheBassItsReviewsCallHeavyAndLiftSpeech() {
        val dialogue = bar800.presets.single { it.id == "jbl-bar-800-dialogue" }
        // Band indices: 0 = 40 Hz, 1 = 63 Hz, 5 = 1 kHz, 6 = 2 kHz.
        assertTrue(ManualEq.gainAtBand(dialogue.filters, 5) > 0.0)
        assertTrue(ManualEq.gainAtBand(dialogue.filters, 6) > 0.0)
        val music = bar800.presets.single { it.id == "jbl-bar-800-music" }
        assertTrue(ManualEq.gainAtBand(music.filters, 1) < 0.0)
        val late = bar800.presets.single { it.id == "jbl-bar-800-late-night" }
        assertEquals(-3.0, ManualEq.gainAtBand(late.filters, 0), 0.0)
    }
}
