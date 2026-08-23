package dev.corebuilds.shift

import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors

/**
 * Core Shift's user-facing live-wallpaper application.
 *
 * The bundled catalog renders immediately and the remote Core Motion prequel
 * feed is refreshed in the background. APK updates and wallpaper/content
 * updates are separate, so new Series 2/3 generations do not require a new
 * APK every time.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: LiveAdapter
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var contentBanner: LinearLayout
    private lateinit var contentText: TextView
    private lateinit var contentButton: Button
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var pending: Pair<LiveEntry, Int>? = null
    private var pendingUpdate: UpdateChecker.Result.Available? = null
    private var installOffered = false
    private var spectrum: ValueAnimator? = null
    private var contentRefreshing = false
    private var currentEntries: List<LiveEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // The header rule runs the wallpaper suite's ramp continuously. It is
        // the only always-on animation in the app; everything else is
        // focus-driven, which keeps idle CPU near zero on weak TV SoCs.
        spectrum = CoreSpectrum.bindSweep(
            findViewById(R.id.spectrum_rule),
            periodMs = 11_000L,
            cornerRadiusPx = 2f * resources.displayMetrics.density,
        )

        list = findViewById(R.id.live_list)
        empty = findViewById(R.id.empty)
        contentBanner = findViewById(R.id.content_banner)
        contentText = findViewById(R.id.content_text)
        contentButton = findViewById(R.id.content_btn)
        list.layoutManager = LinearLayoutManager(this)

        // Rows lift on focus. ViewGroup sorts children by Z since API 21, so
        // the raised card draws over its neighbours without custom ordering.
        list.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 200L
            removeDuration = 160L
            moveDuration = 200L
            // Status/content updates are payload binds; a change animation on
            // top of them would cross-fade two copies of the row.
            changeDuration = 0L
            supportsChangeAnimations = false
        }
        list.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(this, R.anim.live_layout_stagger)

        val bundled = LiveCatalog.load(this)
        val cachedPrequels = RemoteLiveCatalog.cached(this)
        currentEntries = LiveCatalog.merge(bundled, cachedPrequels)
        adapter = LiveAdapter(currentEntries) { entry, pos -> download(entry, pos) }
        list.adapter = adapter
        list.scheduleLayoutAnimation()
        updateEmptyState(currentEntries)

        if (cachedPrequels.isNotEmpty()) {
            showCachedContent(cachedPrequels.size)
        }
        contentButton.setOnClickListener { refreshContent() }

        // Content refresh is automatic on every app start and can also be
        // retried from the banner. It never goes through browser CORS.
        refreshContent()
        checkForUpdate()
    }

    private fun refreshContent() {
        if (contentRefreshing) return
        contentRefreshing = true
        contentButton.isEnabled = false
        contentButton.text = getString(R.string.content_refreshing)
        if (currentEntries.isEmpty()) {
            empty.text = getString(R.string.content_checking)
        }

        RemoteLiveCatalog.refresh(this, currentEntries) { result ->
            if (!isFinishing && !isDestroyed) {
                contentRefreshing = false
                contentButton.isEnabled = true
                contentButton.text = getString(R.string.content_refresh)
                currentEntries = result.entries
                adapter.submit(result.entries)
                updateEmptyState(result.entries)

                when {
                    result.networkSucceeded && result.newEntries > 0 ->
                        showUpdatedContent(result.prequelCount, result.newEntries)
                    result.fromCache -> showCachedContent(result.prequelCount)
                    result.prequelCount > 0 -> showAvailableContent(result.prequelCount)
                }
            }
        }
    }

    private fun updateEmptyState(entries: List<LiveEntry>) {
        val hasEntries = entries.isNotEmpty()
        list.visibility = if (hasEntries) View.VISIBLE else View.GONE
        empty.visibility = if (hasEntries) View.GONE else View.VISIBLE
        if (!hasEntries && !contentRefreshing) {
            empty.text = getString(R.string.empty)
        }
    }

    private fun showCachedContent(count: Int) {
        contentBanner.visibility = View.VISIBLE
        val suffix = if (count == 1) "" else "s"
        contentText.text = getString(R.string.content_cached_fmt, count, suffix)
    }

    private fun showAvailableContent(count: Int) {
        contentBanner.visibility = View.VISIBLE
        contentText.text = getString(R.string.content_available_fmt, count)
    }

    private fun showUpdatedContent(total: Int, added: Int) {
        contentBanner.visibility = View.VISIBLE
        val suffix = if (added == 1) "" else "s"
        contentText.text = getString(R.string.content_updated_fmt, added, suffix)
        // Keep the total in the accessibility description without adding a
        // second visual line to the compact TV banner.
        contentBanner.contentDescription =
            getString(R.string.content_available_fmt, total)
    }

    private fun checkForUpdate() {
        UpdateChecker.check(this) { result ->
            when (result) {
                is UpdateChecker.Result.Available -> showUpdateAvailable(result)
                is UpdateChecker.Result.UpToDate -> { }
                is UpdateChecker.Result.Failed -> { }
            }
        }
    }

    private fun showUpdateAvailable(update: UpdateChecker.Result.Available) {
        pendingUpdate = update
        val banner: LinearLayout = findViewById(R.id.update_banner)
        val text: TextView = findViewById(R.id.update_text)
        val btn: Button = findViewById(R.id.update_btn)

        text.text = getString(R.string.update_available, update.versionName)
        banner.visibility = View.VISIBLE

        btn.setOnClickListener { startUpdateDownload(update) }
    }

    private fun startUpdateDownload(update: UpdateChecker.Result.Available) {
        val text: TextView = findViewById(R.id.update_text)
        val btn: Button = findViewById(R.id.update_btn)
        btn.isEnabled = false
        text.text = getString(R.string.update_downloading)

        UpdateInstaller.download(this, update.apkUrl) { event ->
            when (event) {
                is UpdateInstaller.Event.Progress -> {
                    if (event.total > 0) {
                        val pct = (event.received * 100 / event.total).toInt()
                        text.text = "$pct%"
                    }
                }
                is UpdateInstaller.Event.Ready -> {
                    text.text = getString(R.string.update_installing)
                    promptInstall(event.file)
                }
                is UpdateInstaller.Event.Failed -> {
                    text.text = getString(R.string.update_failed_fmt, event.reason)
                    btn.isEnabled = true
                    btn.text = getString(R.string.update_btn)
                }
            }
        }
    }

    private fun promptInstall(file: File) {
        if (!UpdateInstaller.canInstall(this)) {
            val text: TextView = findViewById(R.id.update_text)
            text.text = getString(R.string.update_permission)
            if (!UpdateInstaller.requestInstallPermission(this)) {
                Toast.makeText(this, getString(R.string.update_permission), Toast.LENGTH_LONG).show()
            }
            return
        }
        installOffered = true
        UpdateInstaller.install(this, file)
    }

    override fun onResume() {
        super.onResume()
        val updateFile = File(cacheDir, "updates/coreshift-update.apk")
        if (updateFile.exists() && UpdateInstaller.canInstall(this) && !installOffered) {
            installOffered = true
            UpdateInstaller.install(this, updateFile)
        }
    }

    private fun download(entry: LiveEntry, pos: Int) {
        val perm = LiveDownloader.storagePermission()
        if (perm != null && !LiveDownloader.hasStoragePermission(this)) {
            pending = entry to pos
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_WRITE)
            return
        }
        startDownload(entry, pos)
    }

    private fun startDownload(entry: LiveEntry, pos: Int) {
        adapter.markBusy(pos)
        io.execute {
            val result = LiveDownloader.download(this, entry)
            main.post {
                if (isFinishing || isDestroyed) return@post
                when (result) {
                    is LiveDownloader.Result.Saved ->
                        adapter.markSaved(pos, getString(R.string.saved_hint))
                    is LiveDownloader.Result.NeedsPermission ->
                        adapter.markFailed(pos, getString(R.string.permission_needed))
                    is LiveDownloader.Result.Failed -> {
                        adapter.markFailed(pos, getString(R.string.download_failed_fmt, result.reason))
                        Toast.makeText(
                            this, getString(R.string.download_failed_fmt, result.reason),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_WRITE) return
        val pending = pending ?: return
        this.pending = null
        if (grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startDownload(pending.first, pending.second)
        } else {
            adapter.markFailed(pending.second, getString(R.string.permission_needed))
        }
    }

    override fun onDestroy() {
        spectrum?.cancel()
        spectrum = null
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_WRITE = 102
    }
}
