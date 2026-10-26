package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        for (name in listOf("JBL Bar 8000", "BAR 8001", "JBL BAR 800MK2", "JBL Bar 800 MK2", "Samsung HW-Q990D", "Living Room TV", "", "  ")) {
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

    private val bar20 = HardwarePresets.MODELS.single { it.id == "jbl-bar-2-0-all-in-one" }

    @Test
    fun theBar20ModelOnlyMatchesExplicitRevisionNames() {
        for (name in listOf("JBL BAR 2.0 ALL-IN-ONE", "BAR2.0 ALL-IN-ONE", "jbl_bar-2-0 ALL-IN-ONE")) {
            assertEquals("name: $name", bar20, HardwarePresets.matchAny(listOf(name)))
        }
        // JBL documents the MK2 Bluetooth pairing name as the same bare model name,
        // so the generic label must not imply the original revision.
        assertNull(HardwarePresets.matchAny(listOf("JBL BAR 2.0")))
        assertNull(HardwarePresets.matchAny(listOf("JBL BAR 2.0 ALL-IN-ONE MK2")))
        assertNull(HardwarePresets.matchAny(listOf("JBLBAR20AIOM2BLKAM")))
        assertNull(HardwarePresets.matchAny(listOf("JBL BAR 2.0 AIO M2")))
        assertNull(HardwarePresets.matchAny(listOf("Bar 2.00 pro")))
    }

    @Test
    fun theTwoJblBarsDoNotMatchEachOther() {
        assertEquals(bar800, HardwarePresets.matchAny(listOf("JBL BAR 800")))
        assertEquals(bar20, HardwarePresets.matchAny(listOf("JBL BAR 2.0 ALL-IN-ONE")))
    }

    @Test
    fun theBar20PresetsStayWithinAOneDecibelSkirtBelowItsSeventyHertzFloor() {
        // A 125 Hz bell's skirt reaches into 40 to 70 Hz, so no positive 125 Hz lift can
        // be exactly zero there. The measured worst case is about 0.8 dB; this guards
        // against a regression beyond that.
        //
        // Deliberately no assertion on how many presets the bar has: that number was a
        // tripwire that fired when the Everyday preset was added, and it says nothing
        // about the property under test. Every preset present is checked instead.
        assertTrue("the bar must ship presets at all", bar20.presets.isNotEmpty())
        for (preset in bar20.presets) {
            var worst = -99.0
            var hz = 40.0
            while (hz <= 70.0) {
                worst = maxOf(worst, ManualEq.responseDb(preset.filters, hz))
                hz *= 1.01
            }
            assertTrue("below 70 Hz: ${preset.id} peaks at $worst dB", worst <= 1.0)
        }
    }

    @Test
    fun theBar20StageAndListeningPresetsCoverBothGroups() {
        val ids = bar20.presets.map { it.id }
        assertTrue(ids.containsAll(listOf("jbl-bar-2-0-movie", "jbl-bar-2-0-dialogue", "jbl-bar-2-0-music", "jbl-bar-2-0-late-night")))
        assertTrue(ids.containsAll(listOf("jbl-bar-2-0-open-stage", "jbl-bar-2-0-centred-vocal", "jbl-bar-2-0-bass-forward")))
        // The everyday curve is the one a name match lands on, so it must be present.
        assertTrue(ids.contains("jbl-bar-2-0-everyday"))
    }

    @Test
    fun noTwoShippedPresetsHaveTheSameToneSoTheNameIsNeverAmbiguous() {
        val all = ManualEq.BUILT_IN_PRESETS + HardwarePresets.PRESETS
        for (i in all.indices) for (j in i + 1 until all.size) {
            assertFalse("${all[i].name} = ${all[j].name}", ManualEq.sameFilters(all[i].filters, all[j].filters))
        }
    }

    private fun model(id: String) = HardwarePresets.MODELS.single { it.id == id }

    @Test
    fun theNewSamsungSonyLgAndJblModelsAreFoundUnderTheNamesTheyReport() {
        val cases = mapOf(
            "samsung-hw-q800h" to listOf("Samsung HW-Q800H", "HW-Q800F", "Q800H", "samsung_q800f"),
            "sony-bravia-theatre-bar-6" to listOf("Sony Bravia Theatre Bar 6", "BRAVIA Theater Bar 6", "theatre-bar-6"),
            "lg-s80tr" to listOf("LG S80TR", "S80TR", "lg_s80tr"),
            "jbl-bar-1000-mk2" to listOf("JBL Bar 1000 MK2", "JBL BAR 1000", "BAR1000")
        )
        for ((id, names) in cases) {
            for (name in names) {
                assertEquals("name: $name", model(id), HardwarePresets.matchAny(listOf(name)))
            }
        }
    }

    @Test
    fun nearMissModelNumbersAreNotTheNewModels() {
        val misses = listOf("Samsung Q80", "HW-Q800HX", "HW-Q8000H", "S80TRX", "LG S8OTR", "JBL Bar 10000", "JBL Bar 1300", "Sony Theatre Bar 60")
        for (name in misses) {
            assertNull("name: $name", HardwarePresets.matchAny(listOf(name)))
        }
    }

    @Test
    fun eachNewModelHasThreeDistinctPresetsWithinTheManualEqRules() {
        for (id in listOf("samsung-hw-q800h", "sony-bravia-theatre-bar-6", "lg-s80tr", "jbl-bar-1000-mk2")) {
            val presets = model(id).presets
            assertEquals(id, 3, presets.size)
            assertTrue(presets.all { it.name.length <= 32 })
        }
    }

    @Test
    fun theSonyAndJblPresetsCutTheBassAt63Hz() {
        // The two reviews that describe bass drowning the mids both ask for a cut at 63 Hz.
        for (id in listOf("sony-bravia-theatre-bar-6", "jbl-bar-1000-mk2")) {
            for (preset in model(id).presets) {
                assertTrue("${preset.id} should cut 63 Hz", ManualEq.gainAtBand(preset.filters, 1) <= 0.0)
            }
        }
    }

    @Test
    fun theLgBassLiftAppearsOnlyInTheFilmAndMusicPresets() {
        // HomeTheaterReview: the LG bass rolls off below about 70 Hz, so only the film and music
        // presets lift it. The dialogue preset must not add bass.
        val lg = model("lg-s80tr")
        assertTrue(ManualEq.gainAtBand(lg.presets.single { it.id == "lg-s80tr-movie" }.filters, 1) > 0.0)
        assertTrue(ManualEq.gainAtBand(lg.presets.single { it.id == "lg-s80tr-dialogue" }.filters, 1) == 0.0)
    }
}
