package tv.corebuilds.iconpack

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Core Builds Plus: the Google Play build's one-time unlock. It adds six
 * things and holds nothing else back: priority on the icon requests the
 * auditor files, My requests (each request's progress), live loop rotation
 * ([LoopRotation]), the night loop ([DayNight]), the choice of what
 * Projectivy rotates by day and by night ([ProjectivyWalls]), and the Plus
 * wallpaper series. Every icon, banner and the main wallpaper collection ship
 * to everyone, in Projectivy's rotation too, and the GitHub builds have no
 * Plus at all.
 *
 * PlusStore exists twice, one per distribution: src/play/java talks to Google
 * Play Billing, and src/github/java (debug, release, candidate) is a no-op
 * whose [PlusStore.AVAILABLE] is false. Both compile against these types.
 *
 * The extras unlock on this TV's own word (a stored, Play-confirmed purchase
 * token, re-read from Play at every app start by [PlusStore.sync]); only
 * priority is checked by the request broker, because only priority reaches
 * other people.
 */
object Plus {

    /**
     * Whether Plus controls appear at all: only in the Google Play build, and
     * only once Plus is on sale (a request broker is baked in, since priority
     * is part of what it sells) or already owned.
     */
    fun shown(context: Context): Boolean =
        PlusStore.AVAILABLE &&
            (context.getString(R.string.audit_request_endpoint).isNotBlank() || owned(context))

    /** Owned and confirmed by Google Play on this TV. */
    fun owned(context: Context): Boolean =
        PlusStore.AVAILABLE && PlusStore.purchaseToken(context) != null

    /**
     * A Plus control was pressed. True when the caller may go ahead; otherwise
     * the Plus card opens, so a locked control explains itself instead of
     * doing nothing.
     */
    fun unlocked(activity: Activity): Boolean {
        if (owned(activity)) return true
        activity.startActivity(Intent(activity, SupportActivity::class.java))
        return false
    }
}

sealed class PlusState {
    /** Waiting for Google Play to answer. */
    object Loading : PlusState()

    /** Not owned, and on sale at [price], already formatted by Google Play. */
    data class ForSale(val price: String) : PlusState()

    /** Bought, but Google Play has not confirmed the payment yet. */
    object Pending : PlusState()

    /** Owned and confirmed: the extras are unlocked on this TV. */
    object Owned : PlusState()

    /** Billing cannot run here; [reason] says why, for the screen. */
    data class Unavailable(val reason: String) : PlusState()
}

/** One screen's connection to the store; [close] it with the screen. */
interface PlusSession {
    fun buy()
    fun close()
}
