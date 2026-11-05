package tv.corebuilds.eq.apply

/** Device-name rules shared by matching and pause explanations, without Android dependencies. */
internal object ProfileOutputIdentity {
    /** A kind label such as "Bluetooth" is not the identity of a particular listening device. */
    fun specificName(kindLabel: String, name: String?): String? {
        val clean = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return clean.takeUnless { it.equals(kindLabel, ignoreCase = true) }
            ?.lowercase(java.util.Locale.ROOT)
    }

    /**
     * Matching cannot identify a device when Android supplied only a generic
     * label and all same-kind profiles need a specific name. This is different
     * from identifying a new device that genuinely has no matching profile.
     * An existing generic profile retains its documented matching behavior.
     */
    fun needsDeviceName(kindLabel: String, routeName: String?, profileNames: List<String?>): Boolean {
        if (specificName(kindLabel, routeName) != null) return false
        return profileNames.isNotEmpty() && profileNames.all { specificName(kindLabel, it) != null }
    }
}
