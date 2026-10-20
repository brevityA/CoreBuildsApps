package tv.corebuilds.iconpack

import android.os.Bundle
import android.view.View
import android.widget.TextView

/**
 * Core Builds Premium: what the one-off purchase unlocks, and the button that
 * buys it.
 *
 * Only the Play build can reach this screen. [Premium.GATED] is false in the
 * GitHub / Downloader build, where every series and every loop is already
 * free, so nothing there links here and the settings row this screen hangs off
 * is hidden.
 *
 * The screen exists because Play will not take a purchase that buys nothing:
 * the good has to be visible, described and priced at the point of sale. It is
 * also the honest place to say what stays free, so a buyer is never guessing
 * whether the icon pack itself is about to be paywalled — it is not, and that
 * sentence is on the card, not in a store listing.
 */
class PremiumActivity : TvActivity() {

    private lateinit var state: TextView
    private lateinit var price: TextView
    private lateinit var buy: TextView
    private lateinit var note: TextView

    private var owned = false

    private val listener = Billing.Listener { s, priceText, reason -> render(s, priceText, reason) }

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        state = findViewById(R.id.premium_state)
        price = findViewById(R.id.premium_price)
        buy = findViewById(R.id.premium_buy)
        note = findViewById(R.id.premium_note)

        // Counted from the live catalogue rather than typed in: a series added
        // to the pack is counted here the moment it ships, and the "N walls"
        // sentence can never disagree with the grid the user just came from.
        val walls = WallpaperCatalog.load(this)
        val premiumWalls = walls.count { !Premium.isFree(it) }
        val freeWalls = walls.size - premiumWalls
        findViewById<TextView>(R.id.premium_included).text =
            getString(R.string.premium_included_fmt, premiumWalls, LiveLoop.LOOPS.size)
        findViewById<TextView>(R.id.premium_free_note).text =
            getString(R.string.premium_free_note, Premium.FREE_SERIES.size, freeWalls)

        findViewById<View>(R.id.premium_back).setOnClickListener { finish() }
        buy.setOnClickListener {
            if (owned) {
                finish()
            } else {
                state.text = getString(R.string.premium_state_opening)
                Billing.buy(this, listener)
            }
        }
        // Restore is the same query Play answers on connect, made explicit:
        // a buyer who reinstalled has no cached entitlement, and pressing this
        // is how they get it back without buying again.
        findViewById<View>(R.id.premium_restore).setOnClickListener {
            state.text = getString(R.string.premium_state_checking)
            Billing.refresh(this, listener)
        }

        buy.requestFocus()
        render(if (Premium.unlocked(this)) Billing.State.OWNED else Billing.State.CONNECTING, "", "")
    }

    override fun onResume() {
        super.onResume()
        // Returning from Play's sheet, or from a restore on another device:
        // ask Play what it says now rather than trusting the cache alone.
        Billing.refresh(this, listener)
    }

    override fun onDestroy() {
        Billing.forget(listener)
        super.onDestroy()
    }

    private fun render(s: Billing.State, priceText: String, reason: String) {
        if (isFinishing || isDestroyed) return
        owned = s == Billing.State.OWNED
        when (s) {
            Billing.State.CONNECTING -> {
                state.text = getString(R.string.premium_state_connecting)
                buy.visibility = View.VISIBLE
                buy.text = getString(R.string.premium_buy)
            }
            Billing.State.OWNED -> {
                state.text = getString(R.string.premium_state_owned)
                buy.text = getString(R.string.premium_owned_done)
                price.text = getString(R.string.premium_owned_sub)
                note.text = getString(R.string.premium_owned_note)
            }
            Billing.State.NOT_OWNED -> {
                state.text = getString(R.string.premium_state_locked)
                buy.text = getString(R.string.premium_buy)
                price.text = if (priceText.isBlank()) {
                    getString(R.string.premium_price_unknown)
                } else {
                    getString(R.string.premium_price_fmt, priceText)
                }
            }
            Billing.State.UNAVAILABLE -> {
                // The common case on an Android TV box with no Play Store: the
                // purchase cannot be made from this device at all. Say that in
                // words and leave the free pack working, rather than showing a
                // button that fails silently.
                state.text = getString(R.string.premium_state_unavailable)
                buy.text = getString(R.string.premium_buy_unavailable)
                price.text = getString(R.string.premium_unavailable_sub)
                note.text = if (reason.isBlank()) {
                    getString(R.string.premium_note)
                } else {
                    getString(R.string.premium_unavailable_reason_fmt, reason)
                }
            }
        }
    }
}
