package tv.corebuilds.iconpack

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.URLEncoder

/**
 * The on-device missing-app auditor.
 *
 * The couch problem it solves: someone installs a regional IPTV app, it sits
 * un-iconned next to 943 mapped tiles, and the only way to report it used to
 * involve ADB or a phone keyboard. This screen lists every launchable app on
 * the TV that `appfilter.xml` never names, and pressing one draws a QR code
 * that opens a prefilled icon-request issue - app name, exact component, and
 * the device details - on the phone that scans it.
 *
 * Visibility, deliberately: the scan needs no new permission. The manifest's
 * `<queries>` carries the two launcher categories as intent filters, which
 * makes every launchable package visible to [queryIntentActivities] - the
 * same Play-safe grammar the launcher detection already lives under, and the
 * reason this screen does not reach for QUERY_ALL_PACKAGES.
 *
 * The diff target is the bundled appfilter asset, so "unmapped" means exactly
 * what the launcher will show: no component of that package has a drawable.
 * The deep link is the generated `audit_issue_url_fmt` string, built from the
 * issue forms by tools/build_issue_prefills.py; this screen only URL-encodes
 * its three arguments and substitutes them, so a renamed form field is a
 * generator failure in CI, never a silently empty box on GitHub.
 *
 * Two states, one screen: the list, and the QR panel for the picked app. Back
 * leaves the QR panel before it leaves the screen, the same grammar the
 * wallpapers screen's selection mode uses, because a back press that abandons
 * the list along with the code wastes the scan that just failed.
 */
class AuditorActivity : AppCompatActivity() {

    data class AuditItem(val label: String, val pkg: String, val activity: String) {
        val component: String get() = "$pkg/$activity"
    }

    private lateinit var list: RecyclerView
    private lateinit var qrPanel: View
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auditor)

        list = findViewById(R.id.audit_list)
        qrPanel = findViewById(R.id.audit_qr_panel)
        empty = findViewById(R.id.audit_empty)

        findViewById<View>(R.id.audit_back).setOnClickListener {
            if (qrPanel.visibility == View.VISIBLE) showList() else finish()
        }

        // Package queries are served from the system's cached tables; on the
        // boxes this targets the whole scan measures in single-digit
        // milliseconds, which is cheaper than the worker-thread machinery
        // that would make the number unmeasurable.
        list.layoutManager = LinearLayoutManager(this)
        // A list that animates its rows on first layout drops the focus the
        // first row just received; there is nothing here worth animating.
        list.itemAnimator = null

        val items = scan()
        if (items.isEmpty()) {
            empty.visibility = View.VISIBLE
        } else {
            findViewById<TextView>(R.id.audit_count).text =
                getString(R.string.audit_count_fmt, items.size)
            list.adapter = AuditorAdapter(items) { showQr(it) }
        }
    }

    /** Launchable packages on this TV that no appfilter component names. */
    private fun scan(): List<AuditItem> {
        val mapped = mappedPackages()
        val found = LinkedHashMap<String, AuditItem>()
        for (category in listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER,
            Intent.CATEGORY_LAUNCHER
        )) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in packageManager.queryIntentActivities(intent, 0)) {
                val pkg = info.activityInfo.packageName
                if (pkg == packageName || pkg in mapped) continue
                // containsKey, not putIfAbsent: the leanback pass runs first
                // and a package with both a TV and a mobile launcher must keep
                // the TV activity in the request - that is the component the
                // pack needs. (putIfAbsent would say the same thing but is
                // API 24; this pack still starts at 21.)
                if (!found.containsKey(pkg)) {
                    found[pkg] = AuditItem(info.loadLabel(packageManager).toString(),
                                           pkg, info.activityInfo.name)
                }
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    /** Every package `appfilter.xml` names, read from the bundled asset. */
    private fun mappedPackages(): Set<String> =
        assets.open("appfilter.xml").bufferedReader().use { it.readText() }
            .let { xml ->
                Regex("ComponentInfo\\{([^/]+)/").findAll(xml)
                    .map { it.groupValues[1] }
                    .toSet()
            }

    private fun showQr(item: AuditItem) {
        val note = "Scanned on ${Build.MODEL}, Android " +
            "${Build.VERSION.RELEASE}, pack ${BuildConfig.VERSION_NAME}"
        val url = getString(
            R.string.audit_issue_url_fmt,
            enc(item.label), enc(item.component), enc(note)
        )
        findViewById<ImageView>(R.id.audit_qr_view)
            .setImageBitmap(QrBitmap.render(url, 720))
        findViewById<TextView>(R.id.audit_qr_url).text = url
        list.visibility = View.GONE
        empty.visibility = View.GONE
        qrPanel.visibility = View.VISIBLE
        // The panel holds nothing focusable - a QR code is not a control -
        // so focus returns to the one thing a D-pad can still press.
        findViewById<View>(R.id.audit_back).requestFocus()
    }

    private fun showList() {
        qrPanel.visibility = View.GONE
        if (list.adapter == null) {
            empty.visibility = View.VISIBLE
        } else {
            list.visibility = View.VISIBLE
            list.requestFocus()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
