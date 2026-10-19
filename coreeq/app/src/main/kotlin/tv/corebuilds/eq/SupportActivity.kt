package tv.corebuilds.eq

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import tv.corebuilds.eq.ui.QrBitmap

/**
 * GitHub Sponsors and Ko-fi as two codes a phone camera reads off the TV,
 * opened from the Capability screen's Support card.
 *
 * Codes rather than links: plenty of Android TV boxes ship without a browser,
 * and nobody fills in a payment page with a remote. They are drawn here from
 * the URLs in strings.xml, so opening this screen makes no network request.
 * Every Core EQ feature ships whether anyone gives or not, and the screen
 * says so.
 */
class SupportActivity : TvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)

        // Drawn at the view's own pixel size: QrBitmap rounds down to whole
        // pixels per module, so the scale-up to fit is a few percent at most.
        val side = resources.getDimensionPixelSize(R.dimen.cb_support_qr)
        show(R.id.support_sponsors_qr, R.id.support_sponsors_url,
             getString(R.string.support_sponsors_url), side)
        show(R.id.support_kofi_qr, R.id.support_kofi_url,
             getString(R.string.support_kofi_url), side)

        val done = findViewById<Button>(R.id.btn_support_done)
        done.setOnClickListener { finish() }
        done.requestFocus()
    }

    private fun show(qrId: Int, urlId: Int, url: String, side: Int) {
        findViewById<ImageView>(qrId).setImageBitmap(QrBitmap.render(url, side))
        // The address under each code, for a phone whose camera does not read
        // QR codes: short enough to type, so the scheme is left off.
        findViewById<TextView>(urlId).text = url.removePrefix("https://")
    }
}
