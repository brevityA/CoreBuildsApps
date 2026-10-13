package tv.corebuilds.eq.apply

/**
 * What the optional playback overlay can honestly say from Android's public
 * playback callback and the effects Core EQ currently holds.
 *
 * Neither playback callback state nor an effect attached to some session proves
 * that the reported stream traverses that effect. The per-session state is
 * deliberately more explicit: Core EQ cannot reliably match the coarse
 * callback to a held session.
 */
enum class PlaybackHudState {
    HIDDEN,
    IDLE,
    OUTPUT_MIX_REPORTED,
    SESSION_MATCH_UNKNOWN
}

/** Pure state table for the playback chip; kept Android-free so tests pin its claims. */
fun playbackHudState(
    enabled: Boolean,
    correctionEnabled: Boolean,
    healthy: Boolean,
    playbackReported: Boolean,
    outputMixConfigured: Boolean,
    sessionEffectConfigured: Boolean
): PlaybackHudState {
    if (!enabled || !correctionEnabled || !healthy) return PlaybackHudState.HIDDEN
    if (!outputMixConfigured && !sessionEffectConfigured) return PlaybackHudState.HIDDEN
    if (!playbackReported) return PlaybackHudState.IDLE
    return if (outputMixConfigured) {
        PlaybackHudState.OUTPUT_MIX_REPORTED
    } else {
        PlaybackHudState.SESSION_MATCH_UNKNOWN
    }
}
