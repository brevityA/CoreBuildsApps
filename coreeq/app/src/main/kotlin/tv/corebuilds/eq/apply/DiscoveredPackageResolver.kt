package tv.corebuilds.eq.apply

/** Maps a DUMP-reported UID to a package only when PackageManager exposes one unambiguous app. */
internal object DiscoveredPackageResolver {

    /**
     * A shared UID, filtered package-visibility result, or missing package list
     * is not enough to choose an app rule. The session can still be corrected,
     * but its identity remains unknown for mode arbitration.
     */
    fun uniquePackage(packages: Array<String>?, ownPackage: String): String? =
        packages
            ?.asSequence()
            ?.filter { it.isNotBlank() && it != ownPackage }
            ?.distinct()
            ?.singleOrNull()
}
