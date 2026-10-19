package tv.corebuilds.iconpack

/**
 * Core Builds Plus: the Google Play build's one-time unlock. With it, every
 * icon request the auditor files is marked priority. Nothing else in the pack
 * is held back: every icon, banner and wallpaper ships to everyone, and the
 * GitHub builds have no Plus at all.
 *
 * PlusStore exists twice, one per distribution: src/play/java talks to Google
 * Play Billing, and src/github/java (debug, release, candidate) is a no-op
 * whose [PlusStore.AVAILABLE] is false. Both compile against these types.
 */
sealed class PlusState {
    /** Waiting for Google Play to answer. */
    object Loading : PlusState()

    /** Not owned, and on sale at [price], already formatted by Google Play. */
    data class ForSale(val price: String) : PlusState()

    /** Bought, but Google Play has not confirmed the payment yet. */
    object Pending : PlusState()

    /** Owned and confirmed: requests from this TV are marked priority. */
    object Owned : PlusState()

    /** Billing cannot run here; [reason] says why, for the screen. */
    data class Unavailable(val reason: String) : PlusState()
}

/** One screen's connection to the store; [close] it with the screen. */
interface PlusSession {
    fun buy()
    fun close()
}
