package tv.corebuilds.eq.export

/**
 * The decisions behind the A/B switch, kept apart from the storage that holds
 * them.
 *
 * [ProfileStore] needs a `Context` to reach SharedPreferences, which makes any
 * rule living inside it untestable in this project's Android-free harness — and
 * un-instantiable in a plain JVM unit test, where `Context` is abstract. The
 * storage stays in [ProfileStore]; the three decisions it makes about the flag
 * live here, where they can be checked without a device.
 */
object CorrectionSwitch {

    /**
     * Whether the bypass is in force right now.
     *
     * Only while correction itself is on: a bypass of something that is not
     * playing is not a comparison, it is an absence of correction with no
     * switch on screen to explain it.
     */
    fun active(correctionEnabled: Boolean, stored: Boolean): Boolean = correctionEnabled && stored

    /** What to persist when the listener flips the switch. A request made while correction is off does not arm it. */
    fun storeRequest(correctionEnabled: Boolean, requested: Boolean): Boolean =
        requested && correctionEnabled

    /**
     * What to keep when correction is switched on or off.
     *
     * Switching correction off drops the bypass, so an off/on cycle starts
     * corrected rather than silently resuming flat — which reads as "the
     * correction stopped working" with nothing on screen to say why.
     */
    fun storeAfterCorrectionChange(correctionEnabled: Boolean, stored: Boolean): Boolean =
        correctionEnabled && stored
}
