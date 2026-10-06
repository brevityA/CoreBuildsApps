package tv.corebuilds.eq.dsp

/**
 * Night mode: dynamic range compression and dialogue enhancement layered
 * on top of room correction for low-volume / late-night viewing.
 *
 * Two independent effects, each toggleable:
 *
 * 1. **Compression** — the DynamicsProcessing limiter runs with more
 *    aggressive settings (lower threshold, higher ratio) so loud passages
 *    are pulled down and the viewer does not ride the volume remote.
 *
 * 2. **Dialogue lift** — a modest 2 dB boost in the 1–4 kHz SII band,
 *    using the same presence overlay the "dialogue" target already
 *    defines. At low volume the consonants that carry intelligibility
 *    are the first to disappear under upward masking from room modes.
 *
 * Night mode is composed *after* room correction and loudness
 * compensation, so it does not interfere with either.
 */
object NightMode {

    /**
     * Limiter settings for night mode. Compared to the protection-only
     * limiter (threshold 0 dB, ratio 10:1), night mode pulls the
     * threshold down and uses a gentler knee so the transition into
     * compression is less audible.
     */
    val NIGHT_LIMITER_THRESHOLD_DB = -12f
    val NIGHT_LIMITER_RATIO = 4f
    val NIGHT_LIMITER_ATTACK_MS = 5f
    val NIGHT_LIMITER_RELEASE_MS = 50f

    /**
     * Dialogue enhancement: a 2 dB presence boost in the SII band.
     * The frequencies and Q match the "dialogue" target's presence box
     * (2200–4500 Hz) so the effect is consistent with what measurement
     * already produces for the dialogue target.
     */
    val DIALOGUE_LIFT_DB = 2.0
    val DIALOGUE_LIFT_CENTRE_HZ = 3200.0
    val DIALOGUE_LIFT_Q = 0.8

    /** The dialogue-lift filter as a PeakingFilter for composition with ManualEq. */
    fun dialogueFilter(): PeakingFilter =
        PeakingFilter(DIALOGUE_LIFT_CENTRE_HZ, DIALOGUE_LIFT_Q, DIALOGUE_LIFT_DB)

    /**
     * The dialogue-lift magnitude response at arbitrary frequencies,
     * for graph rendering and headroom calculation.
     */
    fun dialogueResponseDb(freqs: DoubleArray): DoubleArray =
        Peaking.peakingMagnitudeDb(freqs, DIALOGUE_LIFT_CENTRE_HZ, DIALOGUE_LIFT_Q, DIALOGUE_LIFT_DB)
}
