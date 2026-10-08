package tv.corebuilds.eq.apply

/**
 * When the "profile applied" toast shows (1.3.0): once each time the applied
 * profile or mode changes, never on a plain reapply.
 *
 * The service reapplies often without anything a viewer would call a change:
 * a volume step under Low-volume bass, an Extra effects reload, a discovered
 * player attaching. Only a different profile (an output switch, a new
 * measurement, a choice in Profiles) or a different mode is announced. When
 * nothing is applied (an output with no profile of its own), the next
 * application is announced again, even if it is the same profile.
 *
 * Kept free of Android types so the rule is unit-tested (`AppliedNoticeTest`).
 */
class AppliedNotice {
    private var lastShown: String? = null

    /** [profileId] in [mode] is now applied; true when the notice should show. */
    fun onApplied(profileId: String, mode: String): Boolean {
        val key = "$profileId\u0000$mode"
        if (key == lastShown) return false
        lastShown = key
        return true
    }

    /** Nothing is applied any more, so whatever applies next is news. */
    fun reset() {
        lastShown = null
    }
}
