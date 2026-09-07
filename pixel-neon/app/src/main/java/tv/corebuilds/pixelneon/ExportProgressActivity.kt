package tv.corebuilds.pixelneon

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Full-screen progress + result for a bulk wallpaper export.
 *
 * Progress: indeterminate until the first file lands, then determinate with an
 * "N of M · Current name" label. On completion, shows:
 *  - saved / skipped / failed counts (the receipts voice — name what happened)
 *  - a row of installed launchers so the user can open the one they rotate in,
 *    plus a one-line instruction.
 *  - Retry failed (if any) and Done.
 *
 * The export itself runs in [WallpaperExporter] on a worker thread; this
 * activity is just the surface. It does not survive process death — a TV
 * settings screen that's been backgrounded for minutes may be reclaimed, and
 * that's acceptable for a user-initiated, ~20–60s action.
 */
class ExportProgressActivity : AppCompatActivity() {

    private lateinit var state: TextView
    private lateinit var title: TextView
    private lateinit var progress: ProgressBar
    private lateinit var done: TextView
    private lateinit var retry: TextView
    private lateinit var launcherRow: RecyclerView
    private lateinit var afterHint: TextView

    private var wallpapers: List<Wallpaper> = emptyList()
    private var failed: List<Pair<String, String>> = emptyList()
    private var savedCount = 0
    private var skippedCount = 0
    private var permissionNeeded = false
    private var pendingPermissionTargets: List<Wallpaper> = emptyList()
    private var exportJob: java.util.concurrent.atomic.AtomicBoolean? = null

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val targets = pendingPermissionTargets
            pendingPermissionTargets = emptyList()
            if (granted && targets.isNotEmpty()) {
                runExport(targets)
            } else if (!granted) {
                state.text = getString(R.string.wp_storage_permission_denied)
                retry.requestFocus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export_progress)

        @Suppress("DEPRECATION")
        wallpapers = intent.getParcelableArrayListExtra<Wallpaper>(EXTRA_WALLPAPERS)
            ?.toList().orEmpty()
        if (wallpapers.isEmpty()) { finish(); return }

        title = findViewById(R.id.export_title)
        state = findViewById(R.id.export_state)
        progress = findViewById(R.id.export_progress)
        done = findViewById(R.id.export_done)
        retry = findViewById(R.id.export_retry)
        launcherRow = findViewById(R.id.export_launchers)
        afterHint = findViewById(R.id.export_after_hint)

        title.text = getString(R.string.wp_export_title)
        progress.max = wallpapers.size
        progress.progress = 0
        progress.isIndeterminate = false

        done.setOnClickListener { finish() }
        retry.setOnClickListener { retryFailed() }
        findViewById<TextView>(R.id.export_cancel).setOnClickListener { finish() }

        runExport(wallpapers)
    }

    private fun retryFailed() {
        val targets = failed.mapNotNull { (name, _) ->
            wallpapers.firstOrNull { it.cacheName == name }
        }
        if (targets.isEmpty()) return

        val permission = WallpaperSetter.storagePermission()
        if (permission != null && !WallpaperSetter.hasStoragePermission(this)) {
            pendingPermissionTargets = targets
            requestStoragePermission.launch(permission)
        } else {
            runExport(targets)
        }
    }

    private fun runExport(targets: List<Wallpaper>) {
        if (targets.isEmpty()) return
        // A retry runs only the failed subset; keep the bar scaled to what
        // this run actually carries, not the original batch size.
        progress.max = targets.size
        showRunning()
        exportJob = WallpaperExporter.export(this, targets) { event ->
            when (event) {
                is WallpaperExporter.Event.Progress -> {
                    progress.isIndeterminate = false
                    progress.progress = event.index
                    state.text = getString(
                        R.string.wp_export_progress_fmt,
                        event.index + 1, targets.size, event.currentName
                    )
                }
                is WallpaperExporter.Event.Done -> {
                    // Retry always re-sends every currently-failed item, so the
                    // latest run's failures are authoritative for what's left.
                    savedCount += event.saved.size
                    skippedCount += event.skipped.size
                    failed = event.failed
                    showResult()
                }
                is WallpaperExporter.Event.NeedsStoragePermission -> {
                    // The browser gates this before opening us; reaching here
                    // means access was revoked or lost during the export.
                    permissionNeeded = true
                    state.text = getString(R.string.wp_storage_permission)
                    progress.isIndeterminate = false
                }
                is WallpaperExporter.Event.Failed -> {
                    state.text = getString(R.string.wp_export_all_failed)
                    title.text = event.reason
                    progress.isIndeterminate = false
                    progress.progress = 0
                    failed = targets.map { it.cacheName to event.reason }
                    retry.visibility = View.VISIBLE
                    retry.requestFocus()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exportJob?.set(true)
    }

    private fun showRunning() {
        permissionNeeded = false
        state.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        launcherRow.visibility = View.GONE
        afterHint.visibility = View.GONE
        done.visibility = View.GONE
        retry.visibility = View.GONE
        findViewById<View>(R.id.export_cancel).visibility = View.VISIBLE
    }

    private fun showResult() {
        progress.isIndeterminate = false
        val hasExports = savedCount > 0 || skippedCount > 0
        progress.progress = if (hasExports) progress.max else 0
        findViewById<View>(R.id.export_cancel).visibility = View.GONE

        val parts = mutableListOf<String>()
        if (savedCount > 0) parts += getString(R.string.wp_export_done_fmt, savedCount)
        if (skippedCount > 0) parts += getString(R.string.wp_export_skipped_fmt, skippedCount)
        val receipt = parts.joinToString("  ·  ")
        state.text = when {
            permissionNeeded && receipt.isNotEmpty() ->
                "$receipt  ·  ${getString(R.string.wp_storage_permission)}"
            permissionNeeded -> getString(R.string.wp_storage_permission)
            receipt.isEmpty() -> getString(R.string.wp_export_all_failed)
            else -> receipt
        }

        if (failed.isNotEmpty()) {
            title.text = getString(R.string.wp_export_failed_fmt, failed.size)
            retry.text = getString(R.string.wp_retry_failed)
            retry.visibility = View.VISIBLE
        } else {
            title.text = getString(R.string.wp_export_title)
            retry.visibility = View.GONE
        }

        afterHint.visibility = if (hasExports) View.VISIBLE else View.GONE
        afterHint.text = getString(R.string.wp_after_export_hint)

        if (hasExports) bindLaunchers() else launcherRow.visibility = View.GONE

        done.visibility = View.VISIBLE
        // Failures are actionable before launcher hand-off; otherwise focus
        // the first launcher when present, then fall back to Done.
        val firstTarget = when {
            retry.visibility == View.VISIBLE -> retry
            launcherRow.visibility == View.VISIBLE -> launcherRow
            else -> done
        }
        firstTarget.post { firstTarget.requestFocus() }
    }

    private fun bindLaunchers() {
        // Only offer launchers we can actually open (getLaunchIntentForPackage).
        val installed = ApplyIconPack.installed(this)
            .filter { l -> l.packages.any { p -> packageManager.getLaunchIntentForPackage(p) != null } }
        if (installed.isEmpty()) {
            launcherRow.visibility = View.GONE
            return
        }
        launcherRow.visibility = View.VISIBLE
        launcherRow.layoutManager = LinearLayoutManager(
            this, LinearLayoutManager.HORIZONTAL, false
        )
        val labels = installed.map { getString(R.string.wp_open_launcher, it.displayName) }
        launcherRow.adapter = ChipAdapter(
            labels,
            installed.map { it.key },
            selected = ""
        ) { key ->
            installed.firstOrNull { it.key == key }?.let { ApplyIconPack.openLauncher(this, it) }
        }
    }

    companion object {
        const val EXTRA_WALLPAPERS = "wallpapers"
    }
}
