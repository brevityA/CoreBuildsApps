package tv.corebuilds.eq.apply

/**
 * The limiter the DynamicsProcessing engine will be built with (plan M6).
 *
 * Protection, not loudness: the limiter is there so the correction cannot
 * clip, never to make things louder. It boosts nothing ([postGainDb] is 0),
 * clamps at the top of the headroom ([thresholdDb] ≤ 0), and engages fast.
 *
 * Pure data on purpose: `DpMappingTest` pins the invariants here, and the
 * M6a hardware spike maps this onto `DynamicsProcessing.Limiter` — the
 * platform constructor is the part only a device can prove.
 */
data class LimiterSettings(
    val enabled: Boolean = true,
    val linked: Boolean = true,
    val attackMs: Float = 1f,
    val releaseMs: Float = 50f,
    val ratio: Float = 10f,
    val thresholdDb: Float = -1f,
    val kneeWidthDb: Float = 0f,
    val postGainDb: Float = 0f
) {
    /** True while these settings can only protect, never boost. */
    fun isProtectionOnly(): Boolean =
        enabled &&
            postGainDb <= 0f &&
            thresholdDb <= 0f &&
            ratio >= 1f &&
            attackMs > 0f &&
            releaseMs > 0f
}
