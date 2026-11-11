package tv.corebuilds.eq

import tv.corebuilds.eq.apply.ToneLayers
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.mode.ContentType

/**
 * The profiles and listening cases `LegacySoundCompatibilityTest` compares
 * against values captured from Core EQ 1.13.2 (commit 19b8834f). The same
 * source compiled at that commit produced the golden numbers, so the inputs
 * here must stay byte-for-byte what that capture used.
 */
object LegacyGoldenFixtures {

    private val hz = doubleArrayOf(40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0, 1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 8000.0)
    private val corr = doubleArrayOf(-1.5, -6.4, -4.9, 2.4, -3.4, -2.8, -0.9, 0.2, 1.0, 1.7, 3.0, 2.2, 0.7)

    private fun curve() = hz.indices.map { CurvePoint(hz[it], -corr[it], corr[it]) }

    /** Measured, Dialogue target: carries its own presence, so the old Dialogue boost stood aside. */
    val dialogueTarget = Profile(
        id = "legacy-dialogue", name = "Legacy dialogue", timestampMs = 1_793_750_000_000L,
        target = "dialogue", micType = "remote mic", rt60Seconds = 0.42, transitionHz = 385.0,
        rolloffHz = 45.0, curve = curve()
    )

    /** Measured, Room target, with manual EQ on top: the old Dialogue boost applies here. */
    val roomWithManual = dialogueTarget.copy(
        id = "legacy-room", name = "Legacy room", target = "room",
        manualFilters = listOf(PeakingFilter(3_150.0, 1.4, 4.0), PeakingFilter(125.0, 1.0, -3.0))
    )

    /** Manual only, no room measurement. */
    val manualOnly = Profile(
        id = "legacy-manual", name = "Legacy manual", timestampMs = 1_793_000_000_000L,
        target = "flat", micType = "none", manualOnly = true,
        manualFilters = listOf(PeakingFilter(80.0, 0.9, 3.0), PeakingFilter(2_500.0, 1.2, 2.5), PeakingFilter(8_000.0, 0.8, -2.0))
    )

    val profiles = listOf(dialogueTarget, roomWithManual, manualOnly)

    /** Listening cases with the 1.13.2 Dialogue boost switch on (plus two with it off for reference). */
    val dialogueCases: List<Pair<String, ToneLayers>> = listOf(
        "none" to ToneLayers(),
        "dialogue" to ToneLayers(dialogue = true),
        "dialogue+stereo" to ToneLayers(dialogue = true, stereoDownmix = true),
        "dialogue+lowbass" to ToneLayers(dialogue = true, lowVolumeBassDb = 1.5),
        "dialogue+music" to ToneLayers(dialogue = true, contentType = ContentType.MUSIC),
        "stereo" to ToneLayers(stereoDownmix = true)
    )

    /** A five-band platform Equalizer layout with a ±15 dB range. */
    val equalizerCentres = listOf(60.0, 230.0, 910.0, 3_600.0, 14_000.0)
    const val EQUALIZER_RANGE_DB = 15.0
}
