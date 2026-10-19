package tv.corebuilds.iconpack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Build facts, and the complete list of what this app sends anywhere.
 *
 * The second half is the point. Icon packs conventionally use this screen to
 * ask for consent to collect the installed-app list; this one states the
 * opposite, and states it in a form a reader can check against the source:
 * exactly two requests leave the device, both user-initiated, neither
 * carrying anything about the device. If that list ever grows, this screen is
 * the thing that has to change first — it is deliberately written as a
 * complete enumeration rather than a reassuring summary, so it cannot quietly
 * stay true while the behaviour drifts.
 *
 * Counts are read from generated resources and the bundled catalog, never
 * typed in: a hand-maintained "940 icons" goes stale on the next tranche and
 * nothing in the build would catch it.
 *
 * Since 2.4.0 the screen also carries an on-demand update check. The home
 * screen's bar only appears when the startup check finds a newer build, and
 * only when checks are on in Settings; a user who turned them off, pressed
 * Later, or simply wants to know "am I current?" had no way to ask. This row
 * is the same [UpdateChecker] and [UpdateInstaller], driven by a press, with
 * the answer written into the row rather than a toast. It is still the first
 * item on the network list - one request, user-initiated.
 */
class AboutActivity : TvActivity() {

    private var available: UpdateChecker.Result.Available? = null
    private var downloadedApk: File? = null
    private var installOffered = false
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.about_version).text = BuildConfig.VERSION_NAME
        findViewById<TextView>(R.id.about_build).text = BuildConfig.VERSION_CODE.toString()

        // icon_count is written into icon_pack.xml by tools/build_icons.py, so
        // it cannot disagree with what actually shipped. The wallpaper figure
        // is counted from the bundled catalog for the same reason. The design
        // also showed a mapped-component count; there is no generated resource
        // for it and inventing one here would be a number maintained by hand,
        // so it is omitted rather than guessed.
        findViewById<TextView>(R.id.about_icons).text =
            resources.getInteger(R.integer.icon_count).toString()
        findViewById<TextView>(R.id.about_wallpapers).text =
            WallpaperCatalog.load(this).size.toString()

        // The list is the whole list only if it follows the build: with a
        // request broker baked in, pressing a Missing icons row sends the
        // device model, so the line appears and "no device model" goes.
        if (getString(R.string.audit_request_endpoint).isNotBlank()) {
            findViewById<View>(R.id.about_network_request).visibility = View.VISIBLE
            findViewById<TextView>(R.id.about_network_none).setText(R.string.about_network_none_request)
        }
        if (Plus.shown(this)) {
            findViewById<View>(R.id.about_network_tracker).visibility = View.VISIBLE
        }

        findViewById<View>(R.id.about_source).setOnClickListener {
            open(getString(R.string.about_source_url))
        }
        findViewById<View>(R.id.about_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.about_launcher).setOnClickListener {
            startActivity(Intent(this, LauncherCompatActivity::class.java))
        }
        findViewById<View>(R.id.about_support).setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }
        // Play builds update through Play only (Device and Network Abuse
        // policy): the row opens this app's listing and never fetches an APK.
        findViewById<View>(R.id.about_update).setOnClickListener {
            if (Distribution.PLAY) openPlayListing() else onUpdatePressed()
        }
        findViewById<View>(R.id.about_back).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        // Back from the install-permission screen with the APK already on
        // disk: the same no-second-press rule the home screen's bar keeps.
        val file = downloadedApk ?: return
        if (!file.exists()) {
            downloadedApk = null
            return
        }
        if (UpdateInstaller.canInstall(this) && !installOffered) {
            installOffered = true
            findViewById<View>(R.id.about_update).requestFocus()
            startInstall(file)
        }
    }

    /** One row, three jobs, decided by how far the flow has got. */
    private fun onUpdatePressed() {
        if (busy) return
        downloadedApk?.let { if (it.exists()) { startInstall(it); return } }
        available?.let { startDownload(it); return }
        checkForUpdate()
    }

    /** The verdict beside the Version fact, in the brand's two truth colours. */
    private fun badge(text: String, amber: Boolean) {
        findViewById<TextView>(R.id.about_version_badge).apply {
            this.text = text
            setTextColor(androidx.core.content.ContextCompat.getColor(this@AboutActivity,
                if (amber) R.color.cb_warning else R.color.cb_signal_cyan))
            visibility = View.VISIBLE
        }
    }

    private fun setRow(label: String, sub: String) {
        findViewById<TextView>(R.id.about_update_label).text = label
        findViewById<TextView>(R.id.about_update_sub).text = sub
    }

    private fun checkForUpdate() {
        busy = true
        setRow(getString(R.string.update_checking), getString(R.string.about_update_sub))
        UpdateChecker.check(this) { result ->
            busy = false
            when (result) {
                is UpdateChecker.Result.Available -> {
                    available = result
                    badge(getString(R.string.about_badge_available_fmt, result.versionName), amber = true)
                    val sub = result.highlights.takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ")
                        ?: getString(R.string.update_sub_download)
                    setRow(getString(R.string.update_download, result.versionName), sub)
                }
                is UpdateChecker.Result.UpToDate -> {
                    badge(getString(R.string.about_badge_latest), amber = false)
                    setRow(
                        getString(R.string.about_update_up_to_date),
                        getString(R.string.about_update_up_to_date_sub_fmt, result.versionName)
                    )
                }
                is UpdateChecker.Result.Failed -> setRow(
                    getString(R.string.about_update_label),
                    getString(R.string.about_update_check_failed_sub_fmt, result.reason)
                )
            }
        }
    }

    private fun startDownload(update: UpdateChecker.Result.Available) {
        if (update.apkUrl.isBlank()) {
            setRow(getString(R.string.about_update_label),
                getString(R.string.update_failed_fmt, "no apkUrl in version.json"))
            return
        }
        busy = true
        val label = findViewById<TextView>(R.id.about_update_label)
        UpdateInstaller.download(this, update.apkUrl, update.versionCode, update.apkSha256) { event ->
            when (event) {
                is UpdateInstaller.Event.Progress ->
                    label.text = progressLabel(event.received, event.total)
                is UpdateInstaller.Event.Ready -> {
                    busy = false
                    downloadedApk = event.file
                    installOffered = false
                    setRow(getString(R.string.update_install, update.versionName),
                        getString(R.string.update_sub_install))
                    startInstall(event.file)
                }
                is UpdateInstaller.Event.Failed -> {
                    busy = false
                    setRow(getString(R.string.update_download, update.versionName),
                        getString(R.string.update_failed_fmt, event.reason))
                }
            }
        }
    }

    private fun startInstall(file: File) {
        if (!UpdateInstaller.canInstall(this)) {
            installOffered = false
            // The sub-line carries the instruction for as long as it takes to
            // find the toggle; onResume re-offers the install on the way back.
            findViewById<TextView>(R.id.about_update_sub).text =
                getString(R.string.update_need_permission)
            if (!UpdateInstaller.requestInstallPermission(this)) {
                findViewById<TextView>(R.id.about_update_sub).text =
                    getString(R.string.update_permission_missing)
            }
            return
        }
        // Mark it offered before the installer opens: coming back from a
        // cancelled install fires onResume, which would otherwise launch it
        // again. The row stays pressable for a deliberate retry.
        installOffered = true
        try {
            UpdateInstaller.install(this, file)
        } catch (e: android.content.ActivityNotFoundException) {
            installOffered = false
            findViewById<TextView>(R.id.about_update_sub).text =
                getString(R.string.update_no_installer)
        } catch (e: Exception) {
            installOffered = false
            findViewById<TextView>(R.id.about_update_sub).text =
                getString(R.string.update_failed_fmt, e.message ?: "installer refused")
        }
    }

    private fun progressLabel(received: Long, total: Long): String {
        val rec = formatBytes(received)
        return if (total > 0) {
            getString(R.string.update_progress_of_fmt, rec, formatBytes(total))
        } else {
            getString(R.string.update_progress_fmt, rec)
        }
    }

    private fun formatBytes(n: Long): String = when {
        n >= 1_000_000 -> "%.1f MB".format(n / 1_000_000.0)
        n >= 1_000 -> "%.0f KB".format(n / 1_000.0)
        else -> "${n}B"
    }

    /**
     * Hand a URL to whatever can show it.
     *
     * Plenty of Android TV boxes ship without a browser, and an unhandled
     * ACTION_VIEW crashes rather than doing nothing. Catching it and saying so
     * is the difference between "this box has no browser" and "the icon pack
     * crashed".
     */
    private fun openPlayListing() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Distribution.playListing(packageName))))
        } catch (e: android.content.ActivityNotFoundException) {
            open(Distribution.playListingWeb(packageName))
        }
    }

    private fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.about_no_browser, url), Toast.LENGTH_LONG)
                .show()
        }
    }
}
