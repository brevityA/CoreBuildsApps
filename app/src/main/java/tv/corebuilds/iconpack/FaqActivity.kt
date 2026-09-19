package tv.corebuilds.iconpack

import android.os.Bundle
import android.view.View

/**
 * Troubleshooting and FAQ, readable from the sofa.
 *
 * The content is the repo's troubleshooting docs - why a launcher could not
 * see the pack, how direct apply works, what Monet accepts, how to read a
 * component off real hardware - which are Markdown files a couch cannot open.
 * Every card body in strings.xml is a paraphrase of one of those docs, so the
 * screen cannot drift from the evidence: when a doc changes, its card changes
 * in the same edit.
 *
 * Deliberately inert: no row on this screen does anything, so there is no
 * focus strand to guard beyond the back button. The cards are not focusable
 * (nothing to press), which keeps the D-pad on header and scroll only.
 */
class FaqActivity : TvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faq)

        findViewById<View>(R.id.faq_back).setOnClickListener { finish() }
    }
}
