package tv.corebuilds.eq.ui

import tv.corebuilds.eq.R
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.export.Profile

/**
 * What the Profiles route line may say about the chosen profile. It reports
 * matching only: whether the chosen profile is the one for the output in use.
 * Matching is not application. Whether the service holds an effect is on
 * Home (`HomeSummary`), from the service's own facts; this line never claims
 * it. Review finding on #474.
 */
object ProfileRouteLine {
    enum class Kind { NONE, LEGACY, UNKNOWN_OUTPUT, MATCHES_OUTPUT, OTHER_OUTPUT }

    /** [matched] is the profile `OutputRoute.pick` returns for the output in use. */
    fun kindOf(chosen: Profile?, matched: Profile?, outputKind: String?): Kind = when {
        chosen == null -> Kind.NONE
        chosen.outputKind == null -> Kind.LEGACY
        // Ambiguous and unconfirmed routes are not "another output".
        !OutputRoute.isIdentified(outputKind) -> Kind.UNKNOWN_OUTPUT
        matched?.id == chosen.id -> Kind.MATCHES_OUTPUT
        else -> Kind.OTHER_OUTPUT
    }

    /** MATCHES_OUTPUT takes the output in use; OTHER_OUTPUT takes it and the profile's own output. */
    fun stringRes(kind: Kind): Int = when (kind) {
        Kind.NONE -> R.string.profile_route_none
        Kind.LEGACY -> R.string.profile_route_legacy
        Kind.UNKNOWN_OUTPUT -> R.string.profile_route_unknown_output
        Kind.MATCHES_OUTPUT -> R.string.profile_route_matches
        Kind.OTHER_OUTPUT -> R.string.profile_route_not_here
    }
}
