package tv.corebuilds.eq.dsp

/**
 * Acoustic and DSP invariants for Core EQ, matching tools/core_eq_dsp.py.
 */
object DspConstants {
    const val FS = 48000
    const val PINK_SECONDS = 20.0
    const val ESS_SECONDS = 10.0
    const val NFFT = 8192

    const val F_MIN = 40.0
    const val F_MAX = 8000.0

    const val MAX_BOOST_DB = 6.0
    const val MAX_CUT_DB = 12.0
    const val MAX_SHAPING_DB = 3.0
    const val MAX_SLOPE_DB_PER_OCT = 6.0

    const val DEFAULT_TRANSITION_HZ = 400.0
    const val UNKNOWN_ROOM_TRANSITION_HZ = 300.0
    const val NULL_DEPTH_DB = 6.0
    const val ROLLOFF_DROP_DB = 6.0

    const val SMOOTH_FINE_OCTAVES = 1.0 / 6.0
    const val SMOOTH_COARSE_OCTAVES = 1.0

    const val PEAKING_FILTERS = 6
    const val PEAKING_MIN_Q = 0.5
    const val PEAKING_MAX_Q = 4.0

    val ISO_CENTRES_HZ = doubleArrayOf(
        20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0,
        200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0,
        2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0
    )
}
