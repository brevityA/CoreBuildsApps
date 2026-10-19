package tv.corebuilds.eq.dsp

/**
 * Night mode: the DynamicsProcessing limiter pulled down to compress loud
 * passages for low-volume, late-night viewing, so the viewer does not ride
 * the volume remote.
 *
 * Its 1.2.0 dialogue lift (a single 3.2 kHz peak) was never applied; speech
 * clarity is now the separate Dialogue boost switch
 * ([tv.corebuilds.eq.apply.ToneLayers]), shaped as the research's
 * 2.2–4.5 kHz plateau rather than one peak.
 */
object NightMode {

    /**
     * Limiter settings for night mode. Compared to the protection-only
     * limiter (threshold 0 dB, ratio 10:1), night mode pulls the
     * threshold down and uses a gentler ratio so the transition into
     * compression is less audible.
     */
    const val NIGHT_LIMITER_THRESHOLD_DB = -12f
    const val NIGHT_LIMITER_RATIO = 4f
    const val NIGHT_LIMITER_ATTACK_MS = 5f
    const val NIGHT_LIMITER_RELEASE_MS = 50f
}
