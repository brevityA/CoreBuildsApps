package tv.corebuilds.eq.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.PlatformBand
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore

/**
 * A measured profile for the screens to show, so they are tested and rendered
 * the way a person with a measurement sees them, not only as empty states.
 *
 * The curve is the reference DSP's own output (`tools/core_eq_dsp.py`, Dialogue
 * target against its synthetic room, the run `tools/build_core_eq_mockups.py`
 * draws): measured response aligned to 0 dB over 500 Hz–2 kHz, and the
 * correction it computed. The name, RT60 and score are example data.
 */
object ScreenSeed {

    private val centres = doubleArrayOf(
        49.6, 62.5, 78.7, 99.2, 125.0, 157.5, 198.4, 250.0, 315.0, 396.9, 500.0,
        630.0, 793.7, 1000.0, 1259.9, 1587.4, 2000.0, 2519.8, 3174.8, 4000.0, 5039.7, 6349.6
    )
    private val measured = doubleArrayOf(
        14.7, 16.4, 3.4, 10.2, 13.7, 8.2, 6.3, 6.5, 4.9, 4.2, 2.9,
        1.8, 1.1, 0.2, -1.1, -2.1, -2.8, -4.3, -5.6, -5.9, -7.3, -8.7
    )
    private val correction = doubleArrayOf(
        -1.5, -6.4, -6.9, -4.9, -6.9, -4.9, -3.4, -4.1, -2.8, -2.4, -1.5,
        -0.9, -0.4, 0.2, 1.0, 1.7, 3.0, 3.0, 3.0, 3.0, 2.2, 0.7
    )

    fun livingRoom(id: String = "seed-living-room", name: String = "Living room"): Profile = Profile(
        id = id,
        name = name,
        timestampMs = 1_793_750_000_000L,
        target = "dialogue",
        micType = "remote mic",
        volumeM3 = 54.0,
        rt60Seconds = 0.42,
        transitionHz = 385.0,
        qualityScore = 86,
        snrDb = 38.0,
        filters = listOf(
            PeakingFilter(49.6, 2.0, 1.36), PeakingFilter(67.6, 2.0, -6.0), PeakingFilter(125.9, 1.08, -6.0),
            PeakingFilter(204.3, 0.52, 2.87), PeakingFilter(285.8, 0.67, -4.55), PeakingFilter(2842.8, 0.68, 3.39)
        ),
        platformBands = listOf(
            PlatformBand(60.0, -545), PlatformBand(230.0, -382), PlatformBand(910.0, -2),
            PlatformBand(3600.0, 300), PlatformBand(14000.0, 0)
        ),
        curve = centres.indices.map { CurvePoint(centres[it], measured[it], correction[it]) }
    )

    /** One measured profile, chosen as active, plus an older one for the Profiles list. */
    fun measuredRoom() {
        val store = ProfileStore(ApplicationProvider.getApplicationContext<Application>())
        store.saveProfile(livingRoom("seed-bedroom", "Bedroom").copy(timestampMs = 1_793_000_000_000L, qualityScore = 71))
        store.saveProfile(livingRoom(), setAsActive = true)
    }
}
