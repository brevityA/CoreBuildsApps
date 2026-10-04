package tv.corebuilds.eq.mode

/** User-facing listening modes. Room correction remains the base; a mode is only a tone overlay. */
enum class ContentMode(val key: String, val title: String) {
    MOVIE_TV("movie_tv", "Movie / TV"),
    EVERYDAY("everyday", "Everyday"),
    GAMING("gaming", "Gaming");

    companion object {
        fun fromKey(key: String?): ContentMode = entries.firstOrNull { it.key == key } ?: EVERYDAY
    }
}

data class ModeOverride(
    val mode: ContentMode,
    val sticky: Boolean,
    val anchorPackages: Set<String> = emptySet(),
    val playbackStarted: Boolean = false
)

data class ModeDecision(
    val mode: ContentMode,
    val reason: String,
    val activePackages: Set<String>,
    val conflictingPackages: Set<String> = emptySet()
)

/** Pure arbitration rules so conflicts and override expiry can be unit-tested without Android. */
object ContentModeResolver {

    /** A temporary override lasts for the current set of detected playback apps. */
    fun advanceTemporaryOverride(override: ModeOverride?, activePackages: Set<String>): ModeOverride? {
        if (override == null || override.sticky) return override
        if (!override.playbackStarted) {
            return if (activePackages.isEmpty()) {
                override
            } else {
                override.copy(anchorPackages = activePackages.toSet(), playbackStarted = true)
            }
        }
        return if (activePackages == override.anchorPackages) override else null
    }

    fun resolve(
        activePackages: Set<String>,
        rules: Map<String, ContentMode>,
        automatic: Boolean,
        selectedMode: ContentMode,
        override: ModeOverride? = null
    ): ModeDecision {
        val active = activePackages.filter { it.isNotBlank() }.toSortedSet()
        if (override != null) {
            return ModeDecision(override.mode, "Manual override", active)
        }
        if (!automatic) {
            return ModeDecision(selectedMode, "Manual selection", active)
        }

        val requested = active.mapNotNull { pkg -> rules[pkg]?.let { pkg to it } }
        val requestedModes = requested.map { it.second }.distinct()
        return when {
            requestedModes.size > 1 -> ModeDecision(
                ContentMode.EVERYDAY,
                "Conflicting app rules; using Everyday",
                active,
                requested.mapTo(sortedSetOf()) { it.first }
            )
            requestedModes.size == 1 -> ModeDecision(
                requestedModes.single(),
                "Automatic app rule",
                active
            )
            else -> ModeDecision(ContentMode.EVERYDAY, "No matching app rule; using Everyday", active)
        }
    }
}
