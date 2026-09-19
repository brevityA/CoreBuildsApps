package tv.corebuilds.iconpack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlin.math.floor
import kotlin.math.max
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
        count.text = getString(R.string.wp_sub_fmt, all.size)

        adapter = WallpaperAdapter(all) { item ->
            val visible = adapter.currentItems()
            val index = visible.indexOfFirst { it.url == item.url }.coerceAtLeast(0)
            startActivity(
                Intent(this, WallpaperPreviewActivity::class.java).apply {
                    putParcelableArrayListExtra(
                        WallpaperPreviewActivity.EXTRA_WALLPAPERS, ArrayList(visible)
                    )
                    putExtra(WallpaperPreviewActivity.EXTRA_INDEX, index)
                }
            )
        }
        // Keep the action bar in sync as selection changes from the adapter's
        // long-press/toggle path.
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = refreshSelectionUi()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = refreshSelectionUi()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = refreshSelectionUi()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = refreshSelectionUi()
        })

        findViewById<RecyclerView>(R.id.wp_grid).apply {
            layoutManager = GridLayoutManager(this@WallpapersActivity, spanForScreen())
            adapter = this@WallpapersActivity.adapter
            setHasFixedSize(true)
            // Same guard as the main icon grid: the default change animation
            // detaches and cross-fades a rebound tile ViewHolder, which drops
            // D-pad focus (and its highlight) on long-press selection. The
            // tile owns its own focus animation instead.
            itemAnimator = null
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
        bindBackNavigation()

        // Deterministic starting point, same contract as the main screen:
        // without it Android can leave focus on the decor view and the menu
        // opens with no highlighted chip and no response to the first D-pad
        // press. The "All" chip is always laid out at position 0.
        findViewById<RecyclerView>(R.id.wp_chips).post {
            if (currentFocus == null || currentFocus === window.decorView) {
                val chips = findViewById<RecyclerView>(R.id.wp_chips)
                val firstChip = chips.layoutManager?.findViewByPosition(0)
                (firstChip ?: chips).requestFocus()
            }
        }
    }

    private fun bindBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.selectionMode) exitSelectionMode() else finish()
            }
        })
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
            getString(R.string.wp_sub_fmt, all.size)
        }
        // The selection bar is a stop in the chain only while it is on screen;
        // naming it while hidden would park the cursor on an invisible row, the
        // same hole MainActivity.syncFocusChain closes on the home screen.
        val barStop = if (inMode) R.id.wp_select_all else R.id.wp_chips
        findViewById<View>(R.id.wp_back).nextFocusDownId = barStop
        findViewById<View>(R.id.wp_chips).nextFocusUpId = barStop
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
            // The sheet's filter row stood on its side: a vertical list in the
            // rail, so the chip row stops costing the grid a row of thumbs.
            layoutManager = LinearLayoutManager(
                this@WallpapersActivity, LinearLayoutManager.VERTICAL, false
            )
            // The main screen sets this on its chip row. Without it, the
            // default RecyclerView change animation replaces the chip the
            // user just pressed with a fresh ViewHolder and drops the D-pad
            // highlight on the press that moved it.
            itemAnimator = null
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

    /**
     * Columns for the thumb grid: the right pane's width over one tile's pitch,
     * the same arithmetic as MainActivity.spanForScreen - pane from the panel
     * minus gutters, rail and gap; pitch from cb_wp_thumb plus the tile's
     * padding, label gap and label line. Dimens all the way down, so a 4K panel
     * reporting a larger dp box keeps the sheet's three columns.
     */
    private fun spanForScreen(): Int {
        val density = resources.displayMetrics.density
        fun dpOf(id: Int) = resources.getDimensionPixelSize(id) / density
        val pane = resources.configuration.screenWidthDp -
            2 * dpOf(R.dimen.cb_gutter_side) -
            dpOf(R.dimen.cb_rail_width) - dpOf(R.dimen.cb_space_md)
        val pitch = dpOf(R.dimen.cb_wp_thumb) + 2 * dpOf(R.dimen.cb_card_padding) +
            dpOf(R.dimen.cb_space_sm) + dpOf(R.dimen.cb_text_label) * 1.2f
        return max(2, floor(pane / pitch).toInt())
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
}
