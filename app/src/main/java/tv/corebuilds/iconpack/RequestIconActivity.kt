package tv.corebuilds.iconpack

import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Lists the apps on this television that the pack has no icon for, and hands
 * each one to a phone as a prefilled GitHub issue.
 *
 * The handoff is the hard part. A television has no browser worth typing a URL
 * into, and the value the report actually needs — the launcher component — is
 * the one thing a reporter cannot obtain without adb. So the screen reads the
 * component off the device and puts the finished issue URL on screen as a QR
 * code, which is the only route from a television to a phone that does not ask
 * somebody to transcribe a hundred characters of package name by eye.
 */
class RequestIconActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private var apps: List<UnmappedApps.App> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request)

        findViewById<TextView>(R.id.request_count).setText(R.string.request_scanning)
        // queryIntentActivities plus a 1700-entry appfilter is not main-thread
        // work on a television CPU.
        io.execute {
            val found = try {
                UnmappedApps.scan(this)
            } catch (e: Exception) {
                null
            }
            Handler(mainLooper).post { onScanned(found) }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun onScanned(found: List<UnmappedApps.App>?) {
        if (isFinishing || isDestroyed) return
        val count = findViewById<TextView>(R.id.request_count)
        val list = findViewById<RecyclerView>(R.id.request_list)
        val empty = findViewById<TextView>(R.id.request_empty)

        if (found == null) {
            count.setText(R.string.request_failed)
            empty.setText(R.string.request_failed_body)
            empty.visibility = View.VISIBLE
            list.visibility = View.GONE
            return
        }
        apps = found
        if (found.isEmpty()) {
            count.setText(R.string.request_none)
            empty.setText(R.string.request_none_body)
            empty.visibility = View.VISIBLE
            list.visibility = View.GONE
            findViewById<View>(R.id.request_detail).visibility = View.GONE
            return
        }

        count.text = if (found.size == 1) {
            getString(R.string.request_count_one)
        } else {
            getString(R.string.request_count_fmt, found.size)
        }
        empty.visibility = View.GONE
        list.visibility = View.VISIBLE
        val adapter = RequestAdapter(found) { show(it) }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        show(found.first())
        // Land the remote on the list rather than the decor view, so the first
        // D-pad press moves between apps instead of doing nothing.
        list.post {
            list.layoutManager?.findViewByPosition(0)?.requestFocus() ?: list.requestFocus()
        }
    }

    private fun show(app: UnmappedApps.App) {
        findViewById<View>(R.id.request_detail).visibility = View.VISIBLE
        findViewById<TextView>(R.id.request_app_name).text = app.label
        findViewById<TextView>(R.id.request_component).text = app.component
        findViewById<TextView>(R.id.request_explain).setText(
            when (app.kind) {
                UnmappedApps.Kind.MISSING -> R.string.request_explain_missing
                UnmappedApps.Kind.MAPPING -> R.string.request_explain_mapping
            }
        )

        val url = issueUrl(app)
        val qr = findViewById<QrView>(R.id.request_qr)
        qr.setContent(url)
        val caption = findViewById<TextView>(R.id.request_caption)
        // Falling back to the bare URL is not much of a fallback on a remote,
        // but an unscannable smudge with no other route off the screen is worse.
        caption.text = if (qr.hasSymbol()) {
            getString(R.string.request_scan_hint)
        } else {
            url
        }
    }

    /**
     * The prefilled issue URL for one app.
     *
     * Mirrored by `build_url` in tools/check_qr.py, which feeds the real corpus
     * to the QR verification gate — both read the generated `issue_forms.xml`
     * rather than a literal, so a renamed form or field id moves both together.
     *
     * Parameters are shed, in order, until the URL fits [BUDGET]: the title
     * first, since the form supplies its own prefix; then the app name, which is
     * on screen for the reporter to retype. Never the component. Past a version
     * 11 symbol a QR gets materially harder for a phone to lock onto across a
     * room, and a shorter URL is worth more than a prefilled title.
     *
     * No `labels` parameter: GitHub applies the form's own labels when
     * `template=` is used, so sending them again would only spend budget.
     */
    private fun issueUrl(app: UnmappedApps.App): String {
        val endpoint = getString(R.string.issue_endpoint)
        val fieldApp = getString(R.string.issue_field_app)
        val fieldComponent = getString(R.string.issue_field_component)
        val missing = app.kind == UnmappedApps.Kind.MISSING
        val template = getString(
            if (missing) R.string.issue_missing_template else R.string.issue_mapping_template
        )
        val title = getString(
            if (missing) R.string.issue_missing_title else R.string.issue_mapping_title
        )

        val head = "$endpoint?template=${enc(template)}"
        val tail = "&$fieldComponent=${enc(app.component)}"
        val name = "&$fieldApp=${enc(app.label)}"
        val titled = "&title=${enc(title + app.label)}"

        val full = head + titled + name + tail
        if (fits(full)) return full
        val withoutTitle = head + name + tail
        if (fits(withoutTitle)) return withoutTitle
        return head + tail
    }

    private fun fits(url: String): Boolean =
        url.toByteArray(Charsets.UTF_8).size <= BUDGET

    private fun enc(value: String): String = try {
        URLEncoder.encode(value, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        // UTF-8 is guaranteed present on every Android runtime; this branch
        // exists only because the checked exception does.
        value
    }

    private companion object {
        /**
         * Bytes a version 11 symbol carries at ECC M. Kept in step with BUDGET
         * in tools/check_qr.py, which fails the build if a URL this screen can
         * produce would need a larger symbol.
         */
        const val BUDGET = 251
    }
}
