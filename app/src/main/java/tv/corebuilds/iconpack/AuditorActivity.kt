package tv.corebuilds.iconpack

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
 * the device details - on the phone that scans it. Pressing a row first tries
 * the one-press path: [IconRequestSender] POSTs the same three fields to the
 * Core Builds request broker, which files the issue as a bot - no GitHub
 * account, no phone at all - and only any failure (offline, no broker URL
 * baked in yet) drops to the QR panel as before.
 *
 * Visibility, deliberately: the scan needs no new permission. The manifest's
 * `<queries>` carries the two launcher categories as intent filters, which
 * makes every launchable package visible to [queryIntentActivities] - the
 * same Play-safe grammar the launcher detection already lives under, and the
 * reason this screen does not reach for QUERY_ALL_PACKAGES.
 *
 * The diff target is the bundled appfilter asset, and "unmapped" means
 * exactly what the launcher will show - at component level: a row is hidden
 * only when its own MAIN activity is named in appfilter.xml, not merely when
 * its package appears anywhere. A mapping whose activity drifted after an app
 * update - package present, activity stale - leaves the app un-iconned on the
 * launcher, and it is exactly the report this screen is for; hiding the
 * package (the pre-1.9.2 behavior, which is why `com.rma.speedtesttv`
 * vanished from one reporter's audit while still showing its stock icon)
 * made that class of drift unreportable from the couch.
 * The deep link is the generated `audit_issue_url_fmt` string and the broker
 * endpoint the generated `audit_request_endpoint` string, both built from the
 * issue forms by tools/build_issue_prefills.py; this screen only URL-encodes
 * its three arguments and substitutes them, so a renamed form field is a
 * generator failure in CI, never a silently empty box on GitHub.
 *
 * Two states, one screen: the list, and the QR panel for the picked app. Back
 * leaves the QR panel before it leaves the screen, the same grammar the
 * wallpapers screen's selection mode uses, because a back press that abandons
 * the list along with the code wastes the scan that just failed.
 */
class AuditorActivity : TvActivity() {

    /**
     * [mapped] is true when appfilter names this package under some other
     * activity: an icon exists and is not applying, which is the not-applying
     * form's report, not a new-icon request.
     */
    data class AuditItem(val label: String, val pkg: String, val activity: String,
                         val mapped: Boolean = false) {
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

        list.layoutManager = LinearLayoutManager(this)
        // A list that animates its rows on first layout drops the focus the
        // first row just received; there is nothing here worth animating.
        list.itemAnimator = null

        // Off the main thread: the scan reads and regex-walks the whole
        // appfilter asset (~1800 components) and loads a label per
        // launchable app, and that is not frame-budget work on a TV CPU.
        Thread {
            val items = scan()
            runOnUiThread { if (!isFinishing && !isDestroyed) showScan(items) }
        }.start()
    }

    private fun showScan(items: List<AuditItem>) {
        if (items.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        findViewById<TextView>(R.id.audit_count).text =
            getString(R.string.audit_count_fmt, items.size)
        list.adapter = AuditorAdapter(items) { reportOrQr(it) }
        // The adapter lands after the first frame, so focus has already
        // settled elsewhere; put the remote back on the first row.
        list.post {
            list.layoutManager?.findViewByPosition(0)?.requestFocus()
                ?: list.requestFocus()
        }
    }

    /** Launchable rows on this TV whose own MAIN component appfilter misses. */
    private fun scan(): List<AuditItem> {
        val mapped = mappedComponents()
        val mappedPackages = mapped.mapTo(HashSet()) { it.substringBefore('/') }
        val found = LinkedHashMap<String, AuditItem>()
        val seen = HashSet<String>()
        for (category in listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER,
            Intent.CATEGORY_LAUNCHER
        )) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in packageManager.queryIntentActivities(intent, 0)) {
                val pkg = info.activityInfo.packageName
                val activity = normalize(pkg, info.activityInfo.name)
                if (pkg == packageName) continue
                // Settle each package on the first activity seen, before
                // asking whether it is mapped. The leanback pass runs first,
                // so that is the activity a TV launcher shows; if appfilter
                // names it, the app has its icon and the package is done.
                // Checking `in mapped` first let a mapped leanback activity
                // fall through to the same app's unmapped phone-launcher
                // activity, which listed an app whose icon applies fine.
                if (!seen.add(pkg)) continue
                if ("$pkg/$activity" in mapped) continue
                found[pkg] = AuditItem(info.loadLabel(packageManager).toString(),
                                       pkg, activity, pkg in mappedPackages)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    /**
     * Every component `appfilter.xml` names, as `pkg/absolute.Activity`, read
     * from the asset. Keyed on the package too: an absolute activity name
     * alone is not unique, since vendors ship one class under several
     * application IDs.
     */
    private fun mappedComponents(): Set<String> =
        assets.open("appfilter.xml").bufferedReader().use { it.readText() }
            .let { xml ->
                Regex("ComponentInfo\\{([^/]+)/([^}]+)\\}").findAll(xml)
                    .map { m ->
                        val pkg = m.groupValues[1]
                        "$pkg/${normalize(pkg, m.groupValues[2])}"
                    }
                    .toSet()
            }

    /**
     * One spelling for a component, so the appfilter's `pkg/.ui.SplashActivity`
     * relative form compares equal to PackageManager's always-absolute
     * `com.pkg.ui.SplashActivity`. appfilter activity names arrive either as
     * the fully qualified class or as `.QualifiedRest`; both fold to the
     * absolute name PackageManager reports.
     */
    private fun normalize(pkg: String, activity: String): String =
        if (activity.startsWith(".")) pkg + activity else activity

    /** The reporter-side device note central to both request channels. */
    private fun deviceNote(): String = "Scanned on ${Build.MODEL}, Android " +
        "${Build.VERSION.RELEASE}, pack ${BuildConfig.VERSION_NAME}"

    /**
     * The row press. Tries the broker first - one press, one JSON POST, the
     * issue arrives without the reporter owning a GitHub account - and falls
     * back to the QR panel when there is nothing to POST to: no endpoint
     * baked into this APK yet, no network, a busy broker, anything. The guard
     * keeps a double-press from firing two requests; the broker still would
     * fold them into one issue, but the first of the pair should be the one
     * toast the reporter sees.
     */
    private var requestInFlight = false

    private fun reportOrQr(item: AuditItem) {
        val endpoint = getString(R.string.audit_request_endpoint)
        if (endpoint.isBlank()) {
            showQr(item)
            return
        }
        if (requestInFlight) return
        requestInFlight = true
        Toast.makeText(this, R.string.audit_request_sending,
                       Toast.LENGTH_SHORT).show()
        IconRequestSender.send(endpoint, item.label, item.component,
                               deviceNote()) { issue ->
            requestInFlight = false
            if (issue > 0) {
                Toast.makeText(this,
                               getString(R.string.audit_request_sent_fmt, issue),
                               Toast.LENGTH_LONG).show()
            } else if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, R.string.audit_request_offline,
                               Toast.LENGTH_SHORT).show()
                showQr(item)
            }
        }
    }

    private fun showQr(item: AuditItem) {
        val note = deviceNote()
        // Two QR payloads, one grammar: with an interstitial baked in, the
        // phone lands on OUR page first - a .github.io URL the GitHub app
        // cannot claim with intent filters - which always shows the values
        // plainly and offers both doors (GitHub form, or straight-to-broker
        // on phones with no account). Without one, the classic prefilled
        // issue form as before. See docs/icon-request/index.html.
        val landing = getString(R.string.audit_qr_landing)
        val url = if (landing.isBlank()) {
            getString(if (item.mapped) R.string.audit_mapping_url_fmt
                      else R.string.audit_issue_url_fmt,
                      enc(item.label), enc(item.component), enc(note))
        } else {
            val endpoint = getString(R.string.audit_request_endpoint)
            "$landing?app_name=${enc(item.label)}" +
                "&component=${enc(item.component)}" +
                "&device=${enc(note)}" +
                if (endpoint.isBlank()) "" else "&endpoint=${enc(endpoint)}"
        }
        findViewById<ImageView>(R.id.audit_qr_view)
            .setImageBitmap(QrBitmap.render(url, 720))
        findViewById<TextView>(R.id.audit_qr_url).text = url
        findViewById<TextView>(R.id.audit_qr_app_label).text = item.label
        findViewById<TextView>(R.id.audit_qr_component).text = item.component
        // PackageManager still holds this app's launcher icon (it was just
        // scanned), so a photograph of the panel gives the triage the one
        // thing no text channel can prefill: what the icon looks like now.
        val iconView = findViewById<ImageView>(R.id.audit_qr_icon)
        val icon = runCatching { packageManager.getApplicationIcon(item.pkg) }
            .getOrNull()
        if (icon != null) iconView.setImageDrawable(icon)
        iconView.visibility = if (icon != null) View.VISIBLE else View.GONE
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
