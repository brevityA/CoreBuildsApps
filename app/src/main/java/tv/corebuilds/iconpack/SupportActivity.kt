package tv.corebuilds.iconpack

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/**
 * Two ways to support Core Builds, as codes a phone camera reads off the TV.
 *
 * Codes rather than buttons: plenty of Android TV boxes ship without a
 * browser, and nobody wants to fill in a payment page with a remote. The
 * codes are drawn here from the URLs in strings.xml and nothing is fetched,
 * so About's list of everything this app sends stays the whole list.
 *
 * Nothing in the app depends on this screen and it unlocks nothing; the
 * screen says so rather than leaving a reader to wonder.
 */
class SupportActivity : TvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)

        // Drawn at the view's own pixel size: QrBitmap rounds down to whole
        // pixels per module, so the scale-up to fit is a few percent at most.
        val side = resources.getDimensionPixelSize(R.dimen.cb_support_qr)
        show(R.id.support_sponsors_qr, R.id.support_sponsors_url,
             getString(R.string.support_sponsors_url), side)
        show(R.id.support_kofi_qr, R.id.support_kofi_url,
             getString(R.string.support_kofi_url), side)

        val back = findViewById<View>(R.id.support_back)
        back.setOnClickListener { finish() }
        back.requestFocus()
    }

    private fun show(qrId: Int, urlId: Int, url: String, side: Int) {
        findViewById<ImageView>(qrId).setImageBitmap(QrBitmap.render(url, side))
        // The address under each code, for a phone whose camera does not read
        // QR codes: short enough to type, so the scheme is left off.
        findViewById<TextView>(urlId).text = url.removePrefix("https://")
    }
}
