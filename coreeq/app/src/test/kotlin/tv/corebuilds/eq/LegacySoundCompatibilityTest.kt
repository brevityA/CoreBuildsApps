package tv.corebuilds.eq

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.DpBandLayout
import tv.corebuilds.eq.apply.ToneLayers
import tv.corebuilds.eq.dsp.NightMode
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.mode.ContentType
import tv.corebuilds.eq.ui.EnhancedAudioPrefs
import kotlin.math.abs

/**
 * A user upgrading from 1.13.2 keeps the configured sound they had (review
 * point 1 on #474). Every expected value comes from [LegacyGolden113], computed
 * by the 1.13.2 code itself, not from this branch's own functions; reproduce it
 * with `python3 tools/regen_core_eq_legacy_golden.py --check`.
 *
 * What this proves: the stored settings 1.13.2 wrote are read into the new
 * model, and the new model configures the same limiter parameters and the
 * same band gains (on DynamicsProcessing, on a five-band Equalizer, on both A/B
 * sides) to within 1e-9 dB. What it does not prove: anything audible. No TV
 * played these values; that is hardware check 1 on the PR.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacySoundCompatibilityTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val dp = DpBandLayout.specs().map { it.centerHz }
    private fun enhanced() = app.getSharedPreferences(EnhancedAudioPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun clean() {
        enhanced().edit().clear().commit()
        app.getSharedPreferences("core_eq_profiles", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** What the 1.13.2 code wrote when a viewer had switched these on. */
    private fun storeLegacy(night: Boolean, dialogue: Boolean) {
        enhanced().edit().putBoolean("night_mode_v121", night).putBoolean("dialogue_boost_v130", dialogue).commit()
    }

    /**
     * The layers the service builds now ([tv.corebuilds.eq.apply.EqService.toneLayers]
     * reads `clarityDb` from the prefs), for each 1.13.2 case. The legacy
     * `dialogue` flag is never set here: Clarity alone has to reproduce it.
     */
    private fun layersNow(case: String, prefs: EnhancedAudioPrefs): ToneLayers = when (case) {
        "none" -> ToneLayers(clarityDb = 0.0)
        "dialogue" -> ToneLayers(clarityDb = prefs.clarityDb)
        "dialogue+stereo" -> ToneLayers(clarityDb = prefs.clarityDb, stereoDownmix = true)
        "dialogue+lowbass" -> ToneLayers(clarityDb = prefs.clarityDb, lowVolumeBassDb = 1.5)
        "dialogue+music" -> ToneLayers(clarityDb = prefs.clarityDb, contentType = ContentType.MUSIC)
        "stereo" -> ToneLayers(clarityDb = 0.0, stereoDownmix = true)
        else -> error(case)
    }

    private fun assertBands(label: String, expected: DoubleArray, actual: DoubleArray) {
        assertEquals("$label: band count", expected.size, actual.size)
        val worst = expected.indices.maxOf { abs(expected[it] - actual[it]) }
        assertTrue("$label: differs from 1.13.2 by $worst dB", worst <= 1e-9)
    }

    private fun assertSameSound(profile: Profile, prefs: EnhancedAudioPrefs) {
        for ((case, _) in LegacyGoldenFixtures.dialogueCases) {
            val layers = layersNow(case, prefs)
            for (side in BandMapping.AbSide.entries) {
                assertBands(
                    "${profile.id} $case $side",
                    LegacyGolden113.DP.getValue("${profile.id}|$case|$side"),
                    BandMapping.gainsDb(profile, dp, layers, side)
                )
            }
            val (headroom, configured) = LegacyGolden113.EQ.getValue("${profile.id}|$case")
            val fit = BandMapping.fitToBackend(
                profile, LegacyGoldenFixtures.equalizerCentres, layers,
                -LegacyGoldenFixtures.EQUALIZER_RANGE_DB, LegacyGoldenFixtures.EQUALIZER_RANGE_DB
            )
            assertEquals("${profile.id} $case: Equalizer headroom", headroom, fit.headroomDb, 1e-9)
            assertBands("${profile.id} $case Equalizer", configured, fit.configuredDb)
        }
    }

    @Test fun `night on in 1_13_2 reads as Strong with the identical limiter`() {
        storeLegacy(night = true, dialogue = false)
        val prefs = EnhancedAudioPrefs(app)
        assertEquals(NightMode.Level.STRONG, prefs.nightLevel)
        assertEquals(LegacyGolden113.NIGHT_ON_LIMITER, prefs.limiter())
    }

    @Test fun `night off in 1_13_2 keeps the identical protection limiter`() {
        storeLegacy(night = false, dialogue = false)
        val prefs = EnhancedAudioPrefs(app)
        assertEquals(NightMode.Level.OFF, prefs.nightLevel)
        assertEquals(LegacyGolden113.PROTECTION_LIMITER, prefs.limiter())
    }

    @Test fun `dialogue on in 1_13_2 reads as Clarity plus two with identical bands and headroom`() {
        storeLegacy(night = false, dialogue = true)
        val prefs = EnhancedAudioPrefs(app)
        assertEquals(2.0, prefs.clarityDb, 0.0)
        for (p in LegacyGoldenFixtures.profiles) assertSameSound(p, prefs)
    }

    @Test fun `dialogue off in 1_13_2 reads as Clarity off with identical bands`() {
        storeLegacy(night = false, dialogue = false)
        val prefs = EnhancedAudioPrefs(app)
        assertEquals(0.0, prefs.clarityDb, 0.0)
        for (p in LegacyGoldenFixtures.profiles) {
            for (case in listOf("none", "stereo")) {
                for (side in BandMapping.AbSide.entries) {
                    assertBands("${p.id} $case $side", LegacyGolden113.DP.getValue("${p.id}|$case|$side"),
                        BandMapping.gainsDb(p, dp, layersNow(case, prefs), side))
                }
            }
        }
    }

    @Test fun `profiles saved by 1_13_2 load unchanged and configure the same bands`() {
        app.getSharedPreferences("core_eq_profiles", Context.MODE_PRIVATE).edit()
            .putString("profiles_json", LegacyGolden113.PROFILES_JSON).commit()
        storeLegacy(night = false, dialogue = true)
        // The path the service applies: the stored profile, then its Everyday overlay,
        // where ProfileStore moves manual bands a 1.13.2 profile still carries inline.
        val modes = ContentModeStore(app)
        val loaded = ProfileStore(app).getAllProfiles().associateBy { it.id }
        val prefs = EnhancedAudioPrefs(app)
        assertEquals(LegacyGoldenFixtures.profiles.map { it.id }.toSet(), loaded.keys)
        for (fixture in LegacyGoldenFixtures.profiles) {
            val p = modes.effectiveProfile(loaded.getValue(fixture.id), ContentMode.EVERYDAY)
            assertEquals(fixture.name, p.name)
            assertEquals(fixture.target, p.target)
            assertEquals(fixture.manualOnly, p.manualOnly)
            assertEquals(
                LegacyGolden113.LOADED_MANUAL.getValue(p.id),
                p.manualFilters.sortedBy { it.fc }.joinToString(";") { "${it.fc},${it.q},${it.gain}" }
            )
            for ((case, _) in LegacyGoldenFixtures.dialogueCases) {
                for (side in BandMapping.AbSide.entries) {
                    assertBands("loaded ${p.id} $case $side", LegacyGolden113.LOADED.getValue("${p.id}|$case|$side"),
                        BandMapping.gainsDb(p, dp, layersNow(case, prefs), side))
                }
            }
        }
    }
}
