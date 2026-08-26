package dev.corebuilds.shift

import android.animation.ValueAnimator
import android.content.Intent
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
 * The bundled catalog and procedural preview seeds render immediately. The
 * remote Core Motion prequel feed is refreshed in the background so production
 * MP4s can arrive independently from an APK update.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: LiveAdapter
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var contentBanner: LinearLayout
    private lateinit var contentText: TextView
    private lateinit var contentButton: Button
    private lateinit var motionStage: CoreMotionPreviewView
    private lateinit var stageTitle: TextView
    private lateinit var stageMeta: TextView
    private lateinit var quality1080: Button
    private lateinit var quality4k: Button
    private lateinit var qualityStatus: TextView
    private lateinit var speedSlow: Button
    private lateinit var speedNormal: Button
    private lateinit var speedFast: Button
    private lateinit var length10: Button
    private lateinit var length20: Button
    private lateinit var length30: Button
    private var previewSpeed = 1f
    private var previewLength = 20f
    private val uiPrefs by lazy { getSharedPreferences("core_shift_ui", MODE_PRIVATE) }
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var pending: Triple<LiveEntry, Int, QualityTier>? = null
    private var selectedQuality = QualityTier.HD_1080
    private var pendingUpdate: UpdateChecker.Result.Available? = null
    private var installOffered = false
    private var spectrum: ValueAnimator? = null
    private var contentRefreshing = false
    private var currentEntries: List<LiveEntry> = emptyList()
    private var focusRunnable: Runnable? = null
    private var focusRetries = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        motionStage = findViewById(R.id.motion_stage)
        stageTitle = findViewById(R.id.stage_title)
        stageMeta = findViewById(R.id.stage_meta)
        quality1080 = findViewById(R.id.quality_1080)
        quality4k = findViewById(R.id.quality_4k)
        qualityStatus = findViewById(R.id.quality_status)
        quality1080.setOnClickListener { setQuality(QualityTier.HD_1080) }
        quality4k.setOnClickListener { setQuality(QualityTier.UHD_4K) }
        findViewById<Button>(R.id.btn_screensaver).setOnClickListener { openScreensaverSettings() }
        speedSlow = findViewById(R.id.speed_slow)
        speedNormal = findViewById(R.id.speed_normal)
        speedFast = findViewById(R.id.speed_fast)
        length10 = findViewById(R.id.length_10)
        length20 = findViewById(R.id.length_20)
        length30 = findViewById(R.id.length_30)
        speedSlow.setOnClickListener { setPreviewMotion(speed = .5f) }
        speedNormal.setOnClickListener { setPreviewMotion(speed = 1f) }
        speedFast.setOnClickListener { setPreviewMotion(speed = 2f) }
        length10.setOnClickListener { setPreviewMotion(seconds = 10f) }
        length20.setOnClickListener { setPreviewMotion(seconds = 20f) }
        length30.setOnClickListener { setPreviewMotion(seconds = 30f) }
        selectedQuality = if (uiPrefs.getString(PREF_QUALITY, "1080p") == "4K") {
            QualityTier.UHD_4K
        } else {
            QualityTier.HD_1080
        }
        previewSpeed = uiPrefs.getFloat(PREF_SPEED, 1f)
        previewLength = uiPrefs.getFloat(PREF_LENGTH, 20f)
        motionStage.setPlayback(previewSpeed, previewLength)
        updatePreviewControls()
        list.layoutManager = LinearLayoutManager(this)

        list.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 200L
            removeDuration = 160L
            moveDuration = 200L
            changeDuration = 0L
            supportsChangeAnimations = false
        }
        list.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(this, R.anim.live_layout_stagger)

        val bundled = LiveCatalog.load(this)
        val seeds = LiveCatalog.loadPrequelSeeds(this)
        val cachedPrequels = RemoteLiveCatalog.cached(this)
        currentEntries = LiveCatalog.merge(
            LiveCatalog.merge(bundled, seeds),
            cachedPrequels,
        )
        adapter = LiveAdapter(
            currentEntries,
            onDownload = { entry, pos, quality -> download(entry, pos, quality) },
            onPreview = { entry -> openPreview(entry) },
        )
        adapter.setQuality(selectedQuality)
        updateQualityUi()
        list.adapter = adapter
        list.scheduleLayoutAnimation()
        updateEmptyState(currentEntries)
        // Start in the library, not on an off-screen control row. This makes
        // D-pad Down/OK immediately browse and open a wallpaper.
        requestInitialFocus()

        val firstPrequel = currentEntries.firstOrNull { it.scene != null }
        if (firstPrequel != null) {
            selectStage(firstPrequel)
            showPreviewContent(seeds.size)
        }
        contentButton.setOnClickListener { refreshContent() }

        // The stage is a real animated procedural preview, not a static poster.
        // The production MP4 feed still refreshes independently in the background.
        refreshContent()
        checkForUpdate()
    }

    private fun setPreviewMotion(speed: Float = previewSpeed, seconds: Float = previewLength) {
        previewSpeed = speed.coerceIn(.5f, 2f)
        previewLength = seconds.coerceIn(10f, 30f)
        uiPrefs.edit()
            .putFloat(PREF_SPEED, previewSpeed)
            .putFloat(PREF_LENGTH, previewLength)
            .apply()
        motionStage.setPlayback(previewSpeed, previewLength)
        updatePreviewControls()
    }

    private fun updatePreviewControls() {
        speedSlow.text = if (previewSpeed == .5f) "0.5× ✓" else getString(R.string.speed_slow)
        speedNormal.text = if (previewSpeed == 1f) getString(R.string.speed_normal_selected) else getString(R.string.speed_normal)
        speedFast.text = if (previewSpeed == 2f) "2× ✓" else getString(R.string.speed_fast)
        length10.text = if (previewLength == 10f) "10s ✓" else getString(R.string.length_10)
        length20.text = if (previewLength == 20f) getString(R.string.length_20_selected) else getString(R.string.length_20)
        length30.text = if (previewLength == 30f) "30s ✓" else getString(R.string.length_30)
    }

    private fun openScreensaverSettings() {
        val candidates = listOf(
            android.provider.Settings.ACTION_DREAM_SETTINGS,
            "android.settings.DREAM_SETTINGS",
            "com.android.tv.settings.DISPLAY",
        )
        for (action in candidates) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
        }
        Toast.makeText(this, getString(R.string.dream_open_failed), Toast.LENGTH_LONG).show()
    }

    private fun setQuality(next: QualityTier) {
        selectedQuality = next
        uiPrefs.edit().putString(PREF_QUALITY, next.label).apply()
        adapter.setQuality(next)
        updateQualityUi()
    }

    private fun updateQualityUi() {
        quality1080.text = if (selectedQuality == QualityTier.HD_1080) {
            getString(R.string.quality_1080_selected)
        } else {
            getString(R.string.quality_1080)
        }
        quality4k.text = if (selectedQuality == QualityTier.UHD_4K) {
            getString(R.string.quality_4k_selected)
        } else {
            getString(R.string.quality_4k)
        }
        quality1080.alpha = if (selectedQuality == QualityTier.HD_1080) 1f else .72f
        quality4k.alpha = if (selectedQuality == QualityTier.UHD_4K) 1f else .72f
        val fourKCount = currentEntries.count { it.hasTier(QualityTier.UHD_4K) }
        quality4k.isEnabled = fourKCount > 0
        qualityStatus.text = getString(
            R.string.delivery_tier_fmt,
            selectedQuality.label,
        )
    }

    private fun hasActionableFocus(): Boolean {
        val focused = currentFocus ?: return false
        return focused.isShown && focused.isEnabled &&
            focused !== motionStage && focused !== list
    }

    private fun requestInitialFocus() {
        if (hasActionableFocus() || currentEntries.isEmpty()) return
        focusRunnable?.let { list.removeCallbacks(it) }
        focusRetries = 0
        val runnable = object : Runnable {
            override fun run() {
                if (hasActionableFocus()) {
                    focusRunnable = null
                    return
                }
                val holder = list.findViewHolderForAdapterPosition(0)
                if (holder != null) {
                    holder.itemView.findViewById<Button>(R.id.btn_preview)?.requestFocus()
                    focusRunnable = null
                } else if (focusRetries < 10) {
                    focusRetries += 1
                    list.postDelayed(this, 120L)
                } else {
                    focusRunnable = null
                }
            }
        }
        focusRunnable = runnable
        list.post(runnable)
    }

    private fun selectStage(entry: LiveEntry) {
        val scene = entry.scene ?: return
        motionStage.setScene(scene, entry.accent)
        stageTitle.text = entry.title
        stageMeta.text = entry.specLabel
    }

    private fun openPreview(entry: LiveEntry) {
        if (entry.scene != null) {
            selectStage(entry)
            startActivity(
                Intent(this, ProceduralPreviewActivity::class.java).apply {
                    putExtra(ProceduralPreviewActivity.EXTRA_SCENE, entry.scene)
                    putExtra(ProceduralPreviewActivity.EXTRA_ACCENT, entry.accent)
                    putExtra(ProceduralPreviewActivity.EXTRA_TITLE, entry.title)
                    putExtra(ProceduralPreviewActivity.EXTRA_SPEED, previewSpeed)
                    putExtra(ProceduralPreviewActivity.EXTRA_LENGTH, previewLength)
                },
            )
        } else {
            startActivity(
                Intent(this, PreviewActivity::class.java).apply {
                    putExtra(
                        PreviewActivity.EXTRA_URL,
                        entry.urlFor(selectedQuality) ?: entry.url1080p,
                    )
                    putExtra(PreviewActivity.EXTRA_TITLE, entry.title)
                    putExtra(PreviewActivity.EXTRA_QUALITY, selectedQuality.label)
                },
            )
        }
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
                // Refresh must never steal focus from a user who is already
                // browsing. Only seed focus if the window has no actionable
                // descendant (for example, on the very first load).
                if (!hasActionableFocus()) requestInitialFocus()
                result.entries.firstOrNull { it.scene != null }?.let { selectStage(it) }

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

    private fun showPreviewContent(count: Int) {
        contentBanner.visibility = View.VISIBLE
        contentText.text = getString(R.string.content_preview_fmt, count)
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
        if (!hasActionableFocus()) requestInitialFocus()
    }

    private fun download(entry: LiveEntry, pos: Int, quality: QualityTier) {
        if (!entry.mediaAvailable) return
        if (!entry.hasTier(quality)) return
        val perm = LiveDownloader.storagePermission()
        if (perm != null && !LiveDownloader.hasStoragePermission(this)) {
            pending = Triple(entry, pos, quality)
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_WRITE)
            return
        }
        startDownload(entry, pos, quality)
    }

    private fun startDownload(entry: LiveEntry, pos: Int, quality: QualityTier) {
        adapter.markBusy(pos)
        adapter.setStatus(pos, getString(R.string.download_starting_fmt, quality.label))
        io.execute {
            var lastPercent = -1
            val result = LiveDownloader.download(this, entry, quality) { received, total ->
                if (total > 0L) {
                    val percent = ((received * 100L) / total).toInt().coerceIn(0, 100)
                    if (percent == 100 || percent - lastPercent >= 5) {
                        lastPercent = percent
                        main.post {
                            if (!isFinishing && !isDestroyed) {
                                adapter.setStatus(
                                    pos,
                                    getString(R.string.download_progress_fmt, quality.label, percent),
                                )
                            }
                        }
                    }
                }
            }
            main.post {
                if (isFinishing || isDestroyed) return@post
                when (result) {
                    is LiveDownloader.Result.Saved ->
                        adapter.markSaved(pos, getString(R.string.download_saved_fmt, quality.label))
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
            startDownload(pending.first, pending.second, pending.third)
        } else {
            adapter.markFailed(pending.second, getString(R.string.permission_needed))
        }
    }

    override fun onDestroy() {
        focusRunnable?.let { list.removeCallbacks(it) }
        focusRunnable = null
        spectrum?.cancel()
        spectrum = null
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_WRITE = 102
        private const val PREF_QUALITY = "quality"
        private const val PREF_SPEED = "preview_speed"
        private const val PREF_LENGTH = "preview_length"
    }
}
