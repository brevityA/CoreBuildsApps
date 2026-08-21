package tv.corebuilds.iconpack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Browsable grid of the Core Builds wallpaper collection.
 *
 * Catalog comes from the bundled manifest (assets/manifest/wallpapers.json), a
 * copy of the repo's Wallpapers/manifest.json. Thumbnails are bundled so the
 * grid is instant; full 4K images download on demand from the preview screen.
 *
 * Long-press (or the header Export button's long-press hint) enters selection
 * mode for bulk export to Pictures/CoreBuilds, where launchers like Monet can
 * auto-rotate the folder. The header Export button also starts with all visible
 * wallpapers selected if not already in selection mode.
 *
 * TV-first D-pad flow: export/back → series chips → grid.
 */
class WallpapersActivity : AppCompatActivity() {

    private lateinit var all: List<Wallpaper>
    private lateinit var adapter: WallpaperAdapter
    private lateinit var count: TextView
    private lateinit var selectionBar: View
    private lateinit var selectionCount: TextView
    private lateinit var exportSelected: TextView

    private var series: String? = null

    private val requestStorage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startExport(adapter.selectedItems()) else
                toast(getString(R.string.wp_storage_permission_denied))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpapers)

        all = WallpaperCatalog.load(this)
        count = findViewById(R.id.wp_count)
        selectionBar = findViewById(R.id.wp_selection_bar)
        selectionCount = findViewById(R.id.wp_selection_count)
        exportSelected = findViewById(R.id.wp_export_selected)
        count.text = getString(R.string.wp_count_fmt, all.size)

        adapter = WallpaperAdapter(all) { item ->
            startActivity(
                Intent(this, WallpaperPreviewActivity::class.java).apply {
                    putExtra(WallpaperPreviewActivity.EXTRA_URL, item.url)
                    putExtra(WallpaperPreviewActivity.EXTRA_TITLE, item.title)
                }
            )
        }
        // Keep the action bar in sync as selection changes from the adapter's
        // long-press/toggle path.
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = refreshSelectionUi()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = refreshSelectionUi()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = refreshSelectionUi()
        })

        findViewById<RecyclerView>(R.id.wp_grid).apply {
            layoutManager = GridLayoutManager(this@WallpapersActivity, spanForScreen())
            adapter = this@WallpapersActivity.adapter
            setHasFixedSize(true)
        }

        findViewById<TextView>(R.id.wp_back).setOnClickListener {
            if (adapter.selectionMode) exitSelectionMode() else finish()
        }

        findViewById<TextView>(R.id.wp_export).setOnClickListener { onHeaderExport() }
        findViewById<TextView>(R.id.wp_select_all).setOnClickListener {
            adapter.selectAll()
            refreshSelectionUi()
        }
        findViewById<TextView>(R.id.wp_clear).setOnClickListener {
            adapter.clearSelection()
            refreshSelectionUi()
        }
        exportSelected.setOnClickListener {
            val picked = adapter.selectedItems()
            if (picked.isNotEmpty()) beginExport(picked)
        }

        bindChips()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (adapter.selectionMode) {
            exitSelectionMode()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun onHeaderExport() {
        if (!adapter.selectionMode) {
            // First press: select everything currently visible and enter mode.
            adapter.enterSelectionMode()
            adapter.selectAll()
            refreshSelectionUi()
            exportSelected.requestFocus()
        } else {
            val picked = adapter.selectedItems()
            if (picked.isNotEmpty()) beginExport(picked)
        }
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        refreshSelectionUi()
        findViewById<TextView>(R.id.wp_export).requestFocus()
    }

    private fun refreshSelectionUi() {
        val inMode = adapter.selectionMode
        selectionBar.visibility = if (inMode) View.VISIBLE else View.GONE
        val n = adapter.selectedCount()
        selectionCount.text = getString(R.string.wp_selected_fmt, n)
        exportSelected.isEnabled = n > 0
        exportSelected.alpha = if (n > 0) 1f else 0.5f
        if (inMode) {
            exportSelected.text = getString(R.string.wp_export_n_fmt, n)
        }
        count.text = if (inMode) {
            getString(R.string.wp_selected_fmt, n)
        } else {
            getString(R.string.wp_count_fmt, all.size)
        }
    }

    private fun bindChips() {
        val present = all.map { it.series }.distinct()
        val labels = mutableListOf(getString(R.string.chip_all))
        val keys = mutableListOf<String?>(null)
        for (s in present) {
            labels += WallpaperCatalog.seriesLabel(s)
            keys += s
        }
        findViewById<RecyclerView>(R.id.wp_chips).apply {
            layoutManager = LinearLayoutManager(
                this@WallpapersActivity, LinearLayoutManager.HORIZONTAL, false
            )
            adapter = WallpaperChipAdapter(labels, keys, null) { key ->
                series = key
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val filtered = all.filter { series == null || it.series == series }
        adapter.submit(filtered)
        if (!adapter.selectionMode) {
            count.text = getString(R.string.wp_count_fmt, all.size)
        }
    }

    private fun beginExport(wallpapers: List<Wallpaper>) {
        val perm = WallpaperSetter.storagePermission()
        if (perm != null && !WallpaperSetter.hasStoragePermission(this)) {
            requestStorage.launch(perm)
            return
        }
        startExport(wallpapers)
    }

    private fun startExport(wallpapers: List<Wallpaper>) {
        val arr = ArrayList(wallpapers)
        startActivity(
            Intent(this, ExportProgressActivity::class.java).apply {
                putParcelableArrayListExtra(ExportProgressActivity.EXTRA_WALLPAPERS, arr)
            }
        )
    }

    private fun spanForScreen(): Int {
        val dp = resources.configuration.screenWidthDp
        return (dp / 220).coerceIn(3, 6)
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
}
