package tv.corebuilds.eq

// Not compiled in this repository. tools/regen_core_eq_legacy_golden.py copies
// this file, with coreeq/app/src/test/kotlin/tv/corebuilds/eq/LegacyGoldenFixtures.kt,
// into a temporary worktree of Core EQ 1.13.2 (commit 19b8834f) and runs it
// there, so every value it prints is computed by the 1.13.2 code. It uses only
// APIs that exist at that commit. The output is a plain text record that the
// script turns into LegacyGolden113.kt.

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.DpBandLayout
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.ui.EnhancedAudioPrefs
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyGoldenCaptureTest {

    /** Exact round-trip decimal for every double, so the record loses nothing. */
    private fun arr(a: DoubleArray) = a.joinToString(", ", "doubleArrayOf(", ")") { java.lang.Double.toString(it) }

    @Test fun capture() {
        val out = StringBuilder()
        val dp = DpBandLayout.specs().map { it.centerHz }

        // The limiter 1.13.2 configured with its night switch off and on.
        for (night in listOf(false, true)) {
            val l = EnhancedAudioPrefs.limiterFor(night)
            out.appendLine("LIMITER night=$night ${l.enabled} ${l.linked} ${l.attackMs} ${l.releaseMs} ${l.ratio} ${l.thresholdDb} ${l.kneeWidthDb} ${l.postGainDb}")
        }

        // Band gains for each fixture and listening case: DynamicsProcessing on both
        // A/B sides, and a five-band Equalizer fit with its headroom.
        for (p in LegacyGoldenFixtures.profiles) {
            for ((name, layers) in LegacyGoldenFixtures.dialogueCases) {
                for (side in BandMapping.AbSide.entries) {
                    out.appendLine("DP ${p.id} $name $side ${arr(BandMapping.gainsDb(p, dp, layers, side))}")
                }
                val eq = BandMapping.fitToBackend(
                    p, LegacyGoldenFixtures.equalizerCentres, layers,
                    -LegacyGoldenFixtures.EQUALIZER_RANGE_DB, LegacyGoldenFixtures.EQUALIZER_RANGE_DB
                )
                out.appendLine("EQ ${p.id} $name ${eq.headroomDb} ${arr(eq.configuredDb)}")
            }
        }

        // The fixtures as 1.13.2 stores them.
        val json = JSONArray()
        for (p in LegacyGoldenFixtures.profiles) json.put(JSONObject(Formats.exportProfileJson(p)))
        out.appendLine("JSON " + json.toString())

        // The load path 1.13.2 runs: stored JSON -> ProfileStore (which moves inline
        // manual bands into the Everyday overlay) -> the Everyday effective profile.
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("core_eq_profiles", Context.MODE_PRIVATE).edit()
            .putString("profiles_json", json.toString()).commit()
        val modes = ContentModeStore(app)
        for (stored in ProfileStore(app).getAllProfiles().sortedBy { it.id }) {
            val eff = modes.effectiveProfile(stored, ContentMode.EVERYDAY)
            out.appendLine("LOADEDMANUAL ${eff.id} " + eff.manualFilters.sortedBy { it.fc }.joinToString(";") { "${it.fc},${it.q},${it.gain}" })
            for ((name, layers) in LegacyGoldenFixtures.dialogueCases) {
                for (side in BandMapping.AbSide.entries) {
                    out.appendLine("LOADED ${eff.id} $name $side ${arr(BandMapping.gainsDb(eff, dp, layers, side))}")
                }
            }
        }

        // Relative to the module directory, which Gradle uses as the working directory.
        File("build/legacy-golden.txt").writeText(out.toString())
    }
}
