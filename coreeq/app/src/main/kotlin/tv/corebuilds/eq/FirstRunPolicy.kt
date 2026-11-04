package tv.corebuilds.eq

/** Policy for a one-time, non-blocking TV onboarding screen. */
object FirstRunPolicy {
    /** Show only on a fresh Leanback install; upgrades and phone companion launches stay quiet. */
    fun shouldOffer(
        isLeanback: Boolean,
        alreadyCompleted: Boolean,
        firstInstallTime: Long,
        lastUpdateTime: Long
    ): Boolean = isLeanback && !alreadyCompleted && firstInstallTime > 0L && firstInstallTime == lastUpdateTime
}
