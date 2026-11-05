package tv.corebuilds.eq.apply

/**
 * Intents are queued; the saved switch is the latest user choice. In particular,
 * an old STOP must not write "off" over a newer enable, and a sticky restart or
 * late RESUME/REAPPLY must not bring an explicitly disabled correction back.
 */
internal object CorrectionServicePolicy {
    enum class Decision { STOP, IGNORE_STALE_STOP, HANDLE }

    fun decide(enabled: Boolean, stopRequested: Boolean): Decision = when {
        !enabled -> Decision.STOP
        stopRequested -> Decision.IGNORE_STALE_STOP
        else -> Decision.HANDLE
    }

    fun canApply(running: Boolean, enabled: Boolean, suspended: Boolean): Boolean =
        running && enabled && !suspended
}
