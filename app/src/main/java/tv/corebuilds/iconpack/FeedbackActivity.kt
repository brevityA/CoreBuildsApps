package tv.corebuilds.iconpack

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.net.URLEncoder

/**
 * About → Send feedback (Google Play build): the GitHub feedback form as a QR
 * for a phone, with this build and this TV already filled in, so a tester's
 * report says what it was tested on and keeps a public record of it. A TV
 * often has no browser, which is why the form is a code to scan, not a link
 * to open. The app sends nothing itself: the phone opens the form.
 *
 * The link is the generated `feedback_url_fmt` (tools/build_issue_prefills.py,
 * from .github/ISSUE_TEMPLATE/3.feedback.yml), never a hand-written URL.
 */
class FeedbackActivity : TvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        val back = findViewById<View>(R.id.feedback_back)
        back.setOnClickListener { finish() }
        back.requestFocus()

        val link = url(this)
        findViewById<TextView>(R.id.feedback_url).text = link
        // Drawn at the view's own pixel size, as Support's codes are.
        val side = resources.getDimensionPixelSize(R.dimen.cb_support_qr)
        findViewById<ImageView>(R.id.feedback_qr).setImageBitmap(QrBitmap.render(link, side))
    }

    companion object {
        /** "2.7.0 (64) · Google Play": what the form's build field asks for. */
        fun buildLine(): String =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                if (Distribution.PLAY) "Google Play" else "GitHub"

        /** The model and Android version, as the auditor's device note names them. */
        fun deviceLine(): String = "${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

        fun url(context: Context): String =
            context.getString(R.string.feedback_url_fmt, enc(buildLine()), enc(deviceLine()))

        private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
