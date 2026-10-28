package tv.corebuilds.iconpack

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Core Premium (called Plus in code, and in the Play product id
 * `core_builds_plus`): the Google Play build's one-time unlock. It adds six
 * things and holds nothing else back: priority on the icon requests the
 * auditor files, My requests (each request's progress), live loop rotation
 * ([LoopRotation]), the night loop ([DayNight]), the choice of what
 * Projectivy rotates by day and by night ([ProjectivyWalls]), and the Plus
 * wallpaper series. Every icon, banner and the main wallpaper collection ship
 * to everyone, in Projectivy's rotation too, and the GitHub builds have no
 * Plus at all.
 *
 * Each TV can try it once, for [TRIAL_MS], before buying. The trial switches
 * on what the TV decides for itself ([active]); what needs Google's word or
 * makes a lasting copy stays with the purchase ([owned]): priority requests,
 * Request all, and saving a Plus wall to Pictures. The trial is this app's
 * own clock, not a Play offer (Play has trials for subscriptions only, and
 * Plus is bought once), so clearing the app's data can start it again; the
 * extras were never more locked than that.
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

    /** The on-device extras are on: bought, or inside the trial. */
    fun active(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        owned(context) || (PlusStore.AVAILABLE && trial(trialStart(context), now) is Trial.Running)

    /**
     * A Plus control was pressed. True when the caller may go ahead; otherwise
     * the Plus card opens, so a locked control explains itself instead of
     * doing nothing. [purchase] asks for the purchase itself, not the trial.
     */
    fun unlocked(activity: Activity, purchase: Boolean = false): Boolean {
        if (if (purchase) owned(activity) else active(activity)) return true
        activity.startActivity(Intent(activity, SupportActivity::class.java))
        return false
    }

    // ---- The trial ----------------------------------------------------------

    const val TRIAL_MS = 24L * 60 * 60 * 1000

    private const val PREFS = "plus_trial"
    private const val KEY_STARTED = "started_at"

    sealed class Trial {
        /** Never started on this TV: the card offers it. */
        object Unused : Trial()

        /** On, with [leftMs] to go. */
        data class Running(val leftMs: Long) : Trial()

        /** Used up; it is not offered again. */
        object Ended : Trial()
    }

    /**
     * The trial at [now], from when it [started]. A clock set back before the
     * start ends it rather than restarting the 24 hours.
     */
    fun trial(started: Long?, now: Long): Trial = when {
        started == null -> Trial.Unused
        now < started || now >= started + TRIAL_MS -> Trial.Ended
        else -> Trial.Running(started + TRIAL_MS - now)
    }

    fun trialStart(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_STARTED, -1L).takeIf { it >= 0 }

    /** Start the one trial; false if this TV already had it. */
    fun startTrial(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (trialStart(context) != null) return false
        // apply() updates the in-memory copy at once, so the extras that read
        // it straight after see the trial; only the disk write is deferred.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_STARTED, now).apply()
        return true
    }

    /** Whole minutes left, rounded up, so "0 min" never shows while it is on. */
    fun trialMinutesLeft(leftMs: Long): Long = (leftMs + 59_999) / 60_000

    /** Time left as the card and Settings show it: "18 h" over an hour, else "40 min". */
    fun trialLeftLabel(context: Context, leftMs: Long): String {
        val minutes = trialMinutesLeft(leftMs)
        return if (minutes > 60) {
            context.getString(R.string.plus_trial_hours_fmt, ((minutes + 59) / 60).toInt())
        } else {
            context.getString(R.string.plus_trial_minutes_fmt, minutes.toInt())
        }
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
