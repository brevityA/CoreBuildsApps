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
    val playbackStarted: Boolean = false,
    val anchorHasUnidentifiedPlayer: Boolean = false
)

data class ModeDecision(
    val mode: ContentMode,
    val reason: String,
    val activePackages: Set<String>,
    val conflictingPackages: Set<String> = emptySet(),
    val hasUnidentifiedPlayer: Boolean = false
)

/** Pure arbitration rules so conflicts and override expiry can be unit-tested without Android. */
object ContentModeResolver {

    /** Incomplete/unsupported scans can neither start nor expire a temporary override. */
    fun reconcileTemporaryOverride(
        override: ModeOverride?,
        activePackages: Set<String>,
        hasUnidentifiedPlayer: Boolean = false,
        snapshotComplete: Boolean
    ): ModeOverride? = if (snapshotComplete) {
        advanceTemporaryOverride(override, activePackages, hasUnidentifiedPlayer)
    } else {
        override
    }

    /** A temporary override lasts for the current set of detected playback apps. */
    fun advanceTemporaryOverride(
        override: ModeOverride?,
        activePackages: Set<String>,
        hasUnidentifiedPlayer: Boolean = false
    ): ModeOverride? {
        if (override == null || override.sticky) return override
        if (!override.playbackStarted) {
            return if (activePackages.isEmpty() && !hasUnidentifiedPlayer) {
                override
            } else {
                override.copy(
                    anchorPackages = activePackages.toSet(),
                    playbackStarted = true,
                    anchorHasUnidentifiedPlayer = hasUnidentifiedPlayer
                )
            }
        }
        return if (activePackages == override.anchorPackages &&
            hasUnidentifiedPlayer == override.anchorHasUnidentifiedPlayer
        ) {
            override
        } else {
            null
        }
    }

    fun resolve(
        activePackages: Set<String>,
        rules: Map<String, ContentMode>,
        automatic: Boolean,
        selectedMode: ContentMode,
        override: ModeOverride? = null,
        hasUnidentifiedPlayer: Boolean = false
    ): ModeDecision {
        val active = activePackages.filter { it.isNotBlank() }.toSortedSet()
        if (override != null) {
            return ModeDecision(override.mode, "Manual override", active, hasUnidentifiedPlayer = hasUnidentifiedPlayer)
        }
        if (!automatic) {
            return ModeDecision(selectedMode, "Manual selection", active, hasUnidentifiedPlayer = hasUnidentifiedPlayer)
        }

        // An active app without a rule has the documented Everyday default. It
        // must not silently inherit a different app's Movie / Gaming curve.
        val demands = active.map { pkg -> rules[pkg] ?: ContentMode.EVERYDAY }.toMutableList()
        if (hasUnidentifiedPlayer) demands += ContentMode.EVERYDAY
        val demandModes = demands.distinct()
        val explicitModes = active.mapNotNull { rules[it] }.distinct()
        val hasUnmappedApp = active.any { it !in rules }

        return when {
            demandModes.size > 1 -> {
                val reason = when {
                    explicitModes.size > 1 -> "Conflicting app rules; using Everyday"
                    hasUnidentifiedPlayer -> "Unknown player alongside an app rule; using Everyday"
                    else -> "Unmapped player alongside an app rule; using Everyday"
                }
                ModeDecision(
                    ContentMode.EVERYDAY,
                    reason,
                    active,
                    conflictingPackages = active,
                    hasUnidentifiedPlayer = hasUnidentifiedPlayer
                )
            }
            demandModes.singleOrNull() == ContentMode.EVERYDAY -> {
                val reason = when {
                    active.any { rules[it] == ContentMode.EVERYDAY } -> "Automatic app rule"
                    hasUnidentifiedPlayer -> "Unknown player; using Everyday"
                    else -> "No matching app rule; using Everyday"
                }
                ModeDecision(ContentMode.EVERYDAY, reason, active, hasUnidentifiedPlayer = hasUnidentifiedPlayer)
            }
            demandModes.size == 1 -> ModeDecision(
                demandModes.single(),
                "Automatic app rule",
                active,
                hasUnidentifiedPlayer = hasUnidentifiedPlayer
            )
            else -> ModeDecision(
                ContentMode.EVERYDAY,
                "No matching app rule; using Everyday",
                active,
                hasUnidentifiedPlayer = hasUnidentifiedPlayer
            )
        }
    }
}
