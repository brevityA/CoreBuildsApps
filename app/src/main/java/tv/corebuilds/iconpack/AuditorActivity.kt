package tv.corebuilds.iconpack

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
 * The scan itself is [AppAudit], shared with the home screen's Missing icons
 * row so the two counts can never disagree. The diff target is the bundled
 * appfilter asset, at component level: a row is hidden only when its own MAIN
 * activity is named in appfilter.xml, not merely when its package appears
 * anywhere. A mapping whose activity drifted after an app update - package
 * present, activity stale - leaves the app un-iconned on the launcher; hiding
 * the package (the pre-1.9.2 behavior, which is why `com.rma.speedtesttv`
 * vanished from one reporter's audit while still showing its stock icon) made
 * that class of drift unreportable from the couch. Since 2.1.3 those rows are
 * their own section, ICON NOT APPLYING, with the component the pack maps
 * instead, and they file the not-applying form rather than a new-icon request:
 * until then they sat in one list under "no icon yet", which was wrong for an
 * app the pack already draws.
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
        findViewById<View>(R.id.audit_request_all).setOnClickListener { requestAll() }

        list.layoutManager = LinearLayoutManager(this)
        // A list that animates its rows on first layout drops the focus the
        // first row just received; there is nothing here worth animating.
        list.itemAnimator = null

        // Say what is happening until the scan lands. The screen used to stay
        // blank until then, which on a slow box with many apps reads as a
        // screen that does not work.
        findViewById<TextView>(R.id.audit_count).setText(R.string.audit_scanning)

        // Which launcher this TV is on, before any row is pressed: half of
        // "not applying" is the launcher, not the mapping - phone launchers
        // take the glyphs, Fire TV builds ship different activities, and a
        // launcher without an inbound apply needs the walk. Detection is one
        // resolveActivity, so it runs on the main thread before the scan.
        val home = ApplyIconPack.detectDefault(this)
        val launcherNote = findViewById<TextView>(R.id.audit_launcher_note)
        launcherNote.text = if (home != null) {
            getString(R.string.launcher_compat_here_fmt, home.displayName,
                      ApplyIconPack.artText(this, home.art))
        } else {
            getString(R.string.launcher_compat_here_unknown)
        }
        launcherNote.setOnClickListener {
            startActivity(Intent(this, LauncherCompatActivity::class.java))
        }

        // Off the main thread: the scan reads and regex-walks the whole
        // appfilter asset (~1800 components) and loads a label per
        // launchable app, and that is not frame-budget work on a TV CPU.
        // AppAudit keeps the parsed asset for the process, so a scan after
        // the home screen's count only re-reads PackageManager.
        // An exception here (an app with broken resources, a failed binder
        // call) used to end the whole app from this thread; it now ends the
        // scan with its cause on screen.
        Thread {
            val result = runCatching { AppAudit.scan(this) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold({ showScan(it) }, { showScanError(it) })
            }
        }.start()
    }

    private fun showScanError(error: Throwable) {
        findViewById<TextView>(R.id.audit_count).visibility = View.GONE
        val cause = error.javaClass.simpleName + (error.message?.let { ": $it" } ?: "")
        empty.text = getString(R.string.audit_scan_failed_fmt, cause)
        empty.visibility = View.VISIBLE
    }

    private fun showScan(result: AppAudit.Result) {
        val count = findViewById<TextView>(R.id.audit_count)
        if (result.items.isEmpty()) {
            count.visibility = View.GONE
            empty.text = getString(R.string.audit_empty_fmt, result.checked)
            empty.visibility = View.VISIBLE
            return
        }
        count.text = getString(R.string.audit_count_fmt, result.checked)
        auditItems = result.items
        showRequestAll()
        val adapter = AuditorAdapter(result.items, packageManager) { reportOrQr(it) }
        list.adapter = adapter
        // The adapter lands after the first frame, so focus has already
        // settled elsewhere; put the remote on the first row, past the
        // section heading above it.
        list.post {
            list.layoutManager?.findViewByPosition(adapter.firstRowPosition)?.requestFocus()
                ?: list.requestFocus()
        }
    }

    /**
     * The reporter-side device note central to both request channels.
     *
     * The detected launcher goes with it: the form's device field already
     * expects one (its placeholder ends "· Projectivy 4.70"), and GitHub
     * prefills text inputs but not dropdowns - so the launcher rides in the
     * device line rather than a box the reporter has to fill by hand. When
     * HOME is not in the known list the note stays what it was, because a
     * guessed launcher name is worse than a missing one.
     */
    private fun deviceNote(): String =
        "Scanned on ${Build.MODEL}, Android " +
            "${Build.VERSION.RELEASE}, pack ${BuildConfig.VERSION_NAME}" +
            (ApplyIconPack.detectDefault(this)?.let { ", ${it.displayName}" } ?: "")

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

    private fun reportOrQr(item: AppAudit.Item) {
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
                               deviceNote(), item.mapped,
                               PlusStore.purchaseToken(this)) { issue ->
            requestInFlight = false
            if (issue > 0) {
                // My requests (Core Builds Plus) lists it; a no-op off Play.
                RequestLog.record(this, issue, item.label, item.component, item.mapped)
                showRequestAll()
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

    // ---- Request all (Core Builds Plus) -------------------------------------

    private var auditItems: List<AppAudit.Item> = emptyList()

    /** Until when a first press stays armed; the second press inside it sends. */
    private var armedUntil = 0L

    private fun pendingBatch(): List<AppAudit.Item> =
        BatchRequest.pending(auditItems, { it.component },
                             RequestLog.all(this).map { it.component }.toSet())

    /**
     * The header's Request all: Google Play only (with Plus on sale or owned),
     * only with a broker baked in, and only while some missing app has not
     * been requested from this TV. Locked, it reads "Plus" and opens the Plus
     * card, like every other Plus control.
     */
    private fun showRequestAll() {
        val button = findViewById<TextView>(R.id.audit_request_all)
        val pending = pendingBatch()
        button.visibility = if (Plus.shown(this) &&
            getString(R.string.audit_request_endpoint).isNotBlank() &&
            pending.isNotEmpty()) View.VISIBLE else View.GONE
        button.text = when {
            !Plus.owned(this) -> getString(R.string.audit_request_all_locked)
            SystemClock.elapsedRealtime() <= armedUntil ->
                getString(R.string.audit_request_all_confirm_fmt, pending.size)
            else -> getString(R.string.audit_request_all_fmt, pending.size)
        }
    }

    /**
     * Two presses, no dialog: the first arms the button and says what will be
     * sent, the second (within [ARM_MS]) sends it. Up to ten public issues is
     * not something a stray press of the remote should file.
     */
    private fun requestAll() {
        if (!Plus.unlocked(this) || requestInFlight) return
        val pending = pendingBatch()
        val token = PlusStore.purchaseToken(this)
        if (pending.isEmpty() || token == null) {
            showRequestAll()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now > armedUntil) {
            armedUntil = now + ARM_MS
            showRequestAll()
            Toast.makeText(this, getString(R.string.audit_request_all_explain_fmt, pending.size,
                                           BatchRequest.MAX, BatchRequest.PER_DAY),
                           Toast.LENGTH_LONG).show()
            findViewById<View>(R.id.audit_request_all).postDelayed({ showRequestAll() }, ARM_MS + 50)
            return
        }
        armedUntil = 0L
        requestInFlight = true
        Toast.makeText(this, R.string.audit_request_sending, Toast.LENGTH_SHORT).show()
        val labels = pending.associate { it.component to it }
        IconRequestSender.sendBatch(
            BatchRequest.url(getString(R.string.audit_request_endpoint)),
            pending.map { IconRequestSender.BatchApp(it.label, it.component, it.mapped) },
            deviceNote(), token,
        ) { outcome ->
            requestInFlight = false
            when (outcome) {
                is IconRequestSender.BatchOutcome.Filed -> {
                    for (r in outcome.results) {
                        val item = labels[r.component] ?: continue
                        // My requests lists each one; a no-op off Play.
                        RequestLog.record(this, r.issue, item.label, item.component, item.mapped)
                    }
                    val filed = outcome.results.count { it.issue > 0 && !it.duplicate }
                    val voted = outcome.results.count { it.issue > 0 && it.duplicate }
                    val failed = outcome.results.count { it.issue <= 0 } + (pending.size - outcome.results.size)
                    Toast.makeText(this, getString(R.string.audit_request_all_done_fmt,
                                                   filed, voted, failed),
                                   Toast.LENGTH_LONG).show()
                }
                is IconRequestSender.BatchOutcome.Refused -> Toast.makeText(this,
                    if (outcome.status == 0) getString(R.string.audit_request_all_offline)
                    else getString(R.string.audit_request_all_refused_fmt,
                                   outcome.error.ifBlank { "HTTP ${outcome.status}" }),
                    Toast.LENGTH_LONG).show()
            }
            if (!isFinishing && !isDestroyed) showRequestAll()
        }
    }

    private fun showQr(item: AppAudit.Item) {
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
                (if (item.mapped) "&mapped=1" else "") +
                if (endpoint.isBlank()) "" else "&endpoint=${enc(endpoint)}"
        }
        findViewById<ImageView>(R.id.audit_qr_view)
            .setImageBitmap(QrBitmap.render(url, 640))
        findViewById<TextView>(R.id.audit_qr_url).text = url
        findViewById<TextView>(R.id.audit_qr_kind).setText(
            if (item.mapped) R.string.audit_qr_kind_report else R.string.audit_qr_kind_request)
        findViewById<TextView>(R.id.audit_qr_app_label).text = item.label
        findViewById<TextView>(R.id.audit_qr_component).text = item.component
        val mappedAs = findViewById<TextView>(R.id.audit_qr_mapped)
        if (item.mapped && item.mappedAs.isNotEmpty()) {
            mappedAs.text = getString(R.string.audit_mapped_as_fmt,
                                      item.mappedAs.joinToString(", ") {
                                          AuditorAdapter.shortActivity(item.pkg, it)
                                      })
            mappedAs.visibility = View.VISIBLE
        } else {
            mappedAs.visibility = View.GONE
        }
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
        findViewById<View>(R.id.audit_count).visibility = View.GONE
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
            findViewById<View>(R.id.audit_count).visibility = View.VISIBLE
            list.visibility = View.VISIBLE
            list.requestFocus()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val ARM_MS = 5_000L
    }
}
