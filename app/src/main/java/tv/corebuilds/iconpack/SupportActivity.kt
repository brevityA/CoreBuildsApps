package tv.corebuilds.iconpack

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/**
 * Two ways to support Core Builds, as codes a phone camera reads off the TV.
 *
 * Codes rather than buttons: plenty of Android TV boxes ship without a
 * browser, and nobody wants to fill in a payment page with a remote. The
 * codes are drawn here from the URLs in strings.xml and nothing is fetched,
 * so About's list of everything this app sends stays the whole list.
 *
 * Nothing in the app depends on this screen, and every feature ships
 * whether anyone gives or not; the screen says so rather than leaving a
 * reader to wonder.
 *
 * The Google Play build shows Core Premium (Plus in code) in the codes'
 * place: Play's Payments policy keeps a Play app's payments inside Play
 * Billing. Premium is sold only once the request broker is baked in
 * (audit_request_endpoint), because priority requests are part of what it
 * sells. While it is on sale and this TV has not had it, the card also
 * offers the one 24-hour trial ([Plus.startTrial]).
 */
class SupportActivity : TvActivity() {

    private var plus: PlusSession? = null
    private var lastState: PlusState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)

        val back = findViewById<View>(R.id.support_back)
        back.setOnClickListener { finish() }
        back.requestFocus()

        if (PlusStore.AVAILABLE) {
            findViewById<View>(R.id.support_codes).visibility = View.GONE
            findViewById<View>(R.id.support_plus).visibility = View.VISIBLE
            if (getString(R.string.audit_request_endpoint).isBlank()) {
                render(null)
            } else {
                plus = PlusStore.session(this) { render(it) }
            }
            return
        }

        // Drawn at the view's own pixel size: QrBitmap rounds down to whole
        // pixels per module, so the scale-up to fit is a few percent at most.
        val side = resources.getDimensionPixelSize(R.dimen.cb_support_qr)
        show(R.id.support_sponsors_qr, R.id.support_sponsors_url,
             getString(R.string.support_sponsors_url), side)
        show(R.id.support_kofi_qr, R.id.support_kofi_url,
             getString(R.string.support_kofi_url), side)
    }

    override fun onDestroy() {
        plus?.close()
        plus = null
        super.onDestroy()
    }

    /** One Plus state on the card; null means Plus is not on sale in this build yet. */
    private fun render(state: PlusState?) {
        lastState = state
        val status = findViewById<TextView>(R.id.support_plus_status)
        val button = findViewById<TextView>(R.id.support_plus_buy)
        val trial = Plus.trial(Plus.trialStart(this), System.currentTimeMillis())
        when (state) {
            null -> {
                status.setText(R.string.plus_not_yet)
                button.visibility = View.GONE
            }
            PlusState.Loading -> {
                status.setText(R.string.plus_loading)
                button.setText(R.string.plus_loading_button)
            }
            is PlusState.ForSale -> {
                status.text = when (trial) {
                    is Plus.Trial.Running ->
                        getString(R.string.plus_trial_running_fmt, Plus.trialLeftLabel(this, trial.leftMs))
                    Plus.Trial.Ended ->
                        getString(R.string.plus_trial_ended) + " " + getString(R.string.plus_for_sale)
                    Plus.Trial.Unused -> getString(R.string.plus_for_sale)
                }
                button.text = getString(R.string.plus_buy_fmt, state.price)
            }
            PlusState.Pending -> {
                status.setText(R.string.plus_pending)
                button.setText(R.string.plus_pending_button)
            }
            PlusState.Owned -> {
                status.setText(R.string.plus_owned)
                button.setText(R.string.plus_owned_button)
            }
            is PlusState.Unavailable -> {
                status.text = getString(R.string.plus_unavailable_fmt,
                                        state.reason.trimEnd().trimEnd('.'))
                button.setText(R.string.plus_unavailable_button)
            }
        }
        // Only an item on sale is a control. A focused view that stops being
        // focusable hands the cursor to whatever Android picks next, so Back
        // takes it first, deliberately.
        val back = findViewById<View>(R.id.support_back)
        val buyable = state is PlusState.ForSale
        if (!buyable && button.hasFocus()) back.requestFocus()
        button.isFocusable = buyable
        button.isClickable = buyable
        button.setOnClickListener(if (buyable) View.OnClickListener { plus?.buy() } else null)
        if (buyable) button.requestFocus()

        // The trial, offered once, and only beside something to buy after it.
        val tryIt = findViewById<TextView>(R.id.support_plus_trial)
        val offer = buyable && trial is Plus.Trial.Unused
        if (!offer && tryIt.hasFocus()) back.requestFocus()
        tryIt.visibility = if (offer) View.VISIBLE else View.GONE
        tryIt.setOnClickListener(if (offer) View.OnClickListener { startTrial() } else null)
    }

    private fun startTrial() {
        if (Plus.startTrial(this)) {
            Toast.makeText(this, getString(R.string.plus_trial_started_fmt,
                                           Plus.trialLeftLabel(this, Plus.TRIAL_MS)),
                           Toast.LENGTH_LONG).show()
        }
        render(lastState)
    }

    private fun show(qrId: Int, urlId: Int, url: String, side: Int) {
        findViewById<ImageView>(qrId).setImageBitmap(QrBitmap.render(url, side))
        // The address under each code, for a phone whose camera does not read
        // QR codes: short enough to type, so the scheme is left off.
        findViewById<TextView>(urlId).text = url.removePrefix("https://")
    }
}
