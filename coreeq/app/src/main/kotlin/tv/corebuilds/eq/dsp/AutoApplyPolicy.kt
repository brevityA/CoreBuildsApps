package tv.corebuilds.eq.dsp

/**
 * Once per device: the first correction for a device may be applied automatically, and every
 * later one is only suggested. A device with no stable key never auto-applies, because the
 * policy cannot tell it apart from the last one.
 */
object AutoApplyPolicy {
    enum class Action { AUTO_APPLY, SUGGEST_ONLY }

    fun decide(deviceKey: String?, appliedKeys: Set<String>): Action {
        if (deviceKey.isNullOrBlank()) return Action.SUGGEST_ONLY
        return if (deviceKey in appliedKeys) Action.SUGGEST_ONLY else Action.AUTO_APPLY
    }

    /** The set after a correction has been applied for [deviceKey]. */
    fun recordApplied(deviceKey: String, appliedKeys: Set<String>): Set<String> =
        if (deviceKey.isBlank()) appliedKeys else appliedKeys + deviceKey
}
