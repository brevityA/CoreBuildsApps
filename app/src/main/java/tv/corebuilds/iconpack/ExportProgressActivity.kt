package tv.corebuilds.iconpack

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
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
        retry.setOnClickListener { runExport(failed.mapNotNull { fn -> wallpapers.firstOrNull { it.cacheName == fn } }) }
        findViewById<TextView>(R.id.export_cancel).setOnClickListener { finish() }

        runExport(wallpapers)
    }

    private fun runExport(targets: List<Wallpaper>) {
        if (targets.isEmpty()) return
        showRunning()
        WallpaperExporter.export(this, targets) { event ->
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
                    // The caller in WallpapersActivity gated this; reaching here
                    // on API <=28 means the user revoked it. Surface plainly.
                    state.text = getString(R.string.wp_storage_permission)
                    progress.isIndeterminate = true
                }
                is WallpaperExporter.Event.Failed -> {
                    state.text = getString(R.string.wp_export_all_failed)
                    title.text = event.reason
                    progress.isIndeterminate = false
                    progress.progress = 0
                    retry.visibility = View.VISIBLE
                    retry.requestFocus()
                }
            }
        }
    }

    private fun showRunning() {
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
        progress.progress = progress.max
        findViewById<View>(R.id.export_cancel).visibility = View.GONE

        val parts = mutableListOf<String>()
        if (savedCount > 0) parts += getString(R.string.wp_export_done_fmt, savedCount)
        if (skippedCount > 0) parts += getString(R.string.wp_export_skipped_fmt, skippedCount)
        state.text = parts.joinToString("  ·  ")

        if (failed.isNotEmpty()) {
            title.text = getString(R.string.wp_export_failed_fmt, failed.size)
            retry.text = getString(R.string.wp_retry_failed)
            retry.visibility = View.VISIBLE
        } else {
            title.text = getString(R.string.wp_export_title)
            retry.visibility = View.GONE
        }

        afterHint.visibility = View.VISIBLE
        afterHint.text = getString(R.string.wp_after_export_hint)

        bindLaunchers()

        done.visibility = View.VISIBLE
        // Focus the first launcher if there is one, else Done.
        val firstTarget = if (launcherRow.visibility == View.VISIBLE) launcherRow else done
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
