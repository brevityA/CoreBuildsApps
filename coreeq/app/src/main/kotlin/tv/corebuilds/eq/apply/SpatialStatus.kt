package tv.corebuilds.eq.apply

/**
 * Read-only description of the platform spatializer (virtual surround), for the
 * setup check.
 *
 * Android exposes the spatializer's level, enabled and available state to apps.
 * It exposes no call to switch it on or off, and no way to apply it to one app's
 * output. So Core EQ shows the state and does not change it. Pure Kotlin, so the
 * wording is unit-tested without Android.
 */
object SpatialStatus {

    /** The Spatializer API arrived with Android 12L. Core EQ's minimum is lower, so it is gated. */
    const val MIN_SDK = 32

    /**
     * One plain-text line for the setup check.
     *
     * - [sdk]: Build.VERSION.SDK_INT.
     * - [supported]: the device reports any spatialization level (null if not read).
     * - [enabled]: the user's spatial audio setting (null if not read).
     * - [available]: spatialization is available for the current output (null if not read).
     */
    fun detail(sdk: Int, supported: Boolean?, enabled: Boolean?, available: Boolean?): String = when {
        sdk < MIN_SDK ->
            "Not readable on this Android version. It needs Android 12L (API $MIN_SDK) or later, so Core EQ cannot tell whether spatial audio is on."
        supported == false ->
            "Android reports no spatial audio processing on this TV."
        else -> {
            val state = if (enabled == true) "on" else "off"
            val where = if (available == true) "available for this output" else "not available for this output"
            "Spatial audio is $state and $where. Core EQ can only read this: Android gives apps no switch for it, " +
                "so Core EQ cannot turn it on or off, or apply it to its own output."
        }
    }
}
