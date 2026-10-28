package tv.corebuilds.iconpack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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
 * The live loops ([LiveLoop]: Motion, Deep Space, Cinema, Icon Echo) ride the same grid with
 * a LIVE badge, together under the Live chip (a set of their own since 2.4.0,
 * so a series chip shows stills only) and in the All grid; see also the New
 * chip. They preview like stills and set through the system live picker; a
 * bulk save sends them to Movies/CoreBuilds, where Projectivy's and Monet's
 * video pickers find them - so Live, Export and Select all save every loop
 * in one go instead of one preview at a time.
 *
 * The Google Play build adds the Core Premium walls (series 15, Spectra; Plus
 * in code) once Premium is on sale or owned: a Premium chip after All, a
 * PREMIUM badge, and without Premium or its trial a press opens the Premium
 * card instead of the preview. An export leaves them out unless Premium is
 * bought. They are not part of New, which stays the main collection's
 * release.
 *
 * Favourites are starred in the preview ([Prefs.wallpaperFavourites]); once
 * there is one, a Favourites chip follows All (and Plus), and starred tiles
 * wear a star. Coming back from the preview re-reads the set, so a star
 * given or taken there shows on return without moving the cursor.
 *
 * TV-first D-pad flow: export/back → series chips → grid.
 */
class WallpapersActivity : TvActivity() {

    private lateinit var all: List<Wallpaper>
    private lateinit var adapter: WallpaperAdapter
    private lateinit var count: TextView
    private lateinit var selectionBar: View
    private lateinit var selectionCount: TextView
    private lateinit var exportSelected: TextView

    private var series: String? = null
    private var favourites: Set<String> = emptySet()

    private val requestStorage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Through beginExport, not straight to startExport: the export
            // the prompt interrupted still leaves locked Plus walls out.
            if (granted) beginExport(adapter.selectedItems()) else
                toast(getString(R.string.wp_storage_permission_denied))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpapers)
        // Opening Wallpapers is the user action that fetches a newer render of
        // the live loop the home screen is playing; the old one plays until then.
        LiveLoopDownloader.refreshActive(this)

        val plusWalls = if (Plus.shown(this)) WallpaperCatalog.loadPlus(this) else emptyList()
        all = WallpaperCatalog.load(this) + plusWalls + LiveLoop.asWallpapers()
        count = findViewById(R.id.wp_count)
        selectionBar = findViewById(R.id.wp_selection_bar)
        selectionCount = findViewById(R.id.wp_selection_count)
        exportSelected = findViewById(R.id.wp_export_selected)
        count.text = getString(R.string.wp_sub_fmt, all.size)

        adapter = WallpaperAdapter(all) { item ->
            if (item.plus && !Plus.unlocked(this)) return@WallpaperAdapter
            // The preview pages through its list, so it gets only what opens.
            val on = Plus.active(this)
            val visible = adapter.currentItems().filter { !it.plus || on }
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

        // Opening the browser is seeing the release: the home rail stops
        // advertising "N new" from here on.
        WallpaperCatalog.newestSince(mainWalls())?.let { Prefs.setWallpapersNewSeen(this, it) }
        favourites = Prefs.wallpaperFavourites(this)
        adapter.setFavourites(favourites)
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

    /**
     * The preview is where stars change. Only a change does anything here:
     * tiles re-star in place, the chip list is rebuilt for the new count, and
     * the grid re-filters only while it is showing favourites.
     */
    override fun onResume() {
        super.onResume()
        val now = Prefs.wallpaperFavourites(this)
        if (now == favourites) return
        favourites = now
        adapter.setFavourites(now)
        // Un-starring the last favourite takes its chip away; the grid falls
        // back to All rather than sitting on a filter nobody can pick. The
        // RecyclerView keeps focus through the re-filter on its own (it
        // moves to a neighbour when the focused tile left with its star).
        val showingFavourites = series == FAV_KEY
        if (showingFavourites && favouriteWalls().isEmpty()) series = null
        bindChips()
        if (showingFavourites) applyFilter()
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
        // The still series, in catalogue order. The loops' own key is not a
        // series folder; it gets the Live chip below, after New. The Plus
        // series has its own chip, straight after All.
        val present = all.filter { !it.isLive && !it.plus }.map { it.series }.distinct()
        val labels = mutableListOf(getString(R.string.chip_all))
        val keys = mutableListOf<String?>(null)
        if (all.any { it.plus }) {
            labels += getString(R.string.chip_plus)
            keys += PLUS_KEY
        }
        // Only once something is starred: an empty chip is a dead stop.
        val starred = favouriteWalls().size
        if (starred > 0) {
            labels += getString(R.string.chip_favourites_fmt, starred)
            keys += FAV_KEY
        }
        // What this release added or re-rendered, first: with 132 walls in
        // series order, a new series sits under a hundred old ones and a user
        // who updated for the wallpapers would scroll past them.
        val fresh = WallpaperCatalog.newWalls(mainWalls())
        if (fresh.isNotEmpty()) {
            labels += getString(R.string.chip_new_fmt, fresh.size)
            keys += NEW_KEY
        }
        // Every moving wallpaper in one place, whichever series it belongs
        // to - the chip a TV user reaches for to save the loops in one go.
        if (all.any { it.isLive }) {
            labels += getString(R.string.chip_live)
            keys += LIVE_KEY
        }
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
            // Rebuilt on return from the preview when the stars changed, so
            // it starts on the filter already showing, not on All.
            adapter = WallpaperChipAdapter(labels, keys, series) { key ->
                series = key
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val filtered = when (series) {
            null -> all
            LIVE_KEY -> all.filter { it.isLive }
            PLUS_KEY -> all.filter { it.plus }
            FAV_KEY -> favouriteWalls()
            NEW_KEY -> WallpaperCatalog.newWalls(mainWalls())
            else -> all.filter { it.series == series }
        }
        adapter.submit(filtered)
        if (!adapter.selectionMode) {
            count.text = getString(R.string.wp_count_fmt, all.size)
        }
    }

    /** Starred walls still in the catalogue, in catalogue order. */
    private fun favouriteWalls(): List<Wallpaper> = all.filter { it.cacheName in favourites }

    /** Everything but the Plus walls: what New and the home rail count. */
    private fun mainWalls(): List<Wallpaper> = all.filter { !it.plus }

    private fun beginExport(picked: List<Wallpaper>) {
        // Stills and loops travel together: WallpaperExporter sends each to
        // its own shared folder (Pictures / Movies), so a mixed selection
        // is one progress screen, not two. Plus walls go only with the
        // purchase: a copy in Pictures outlasts the trial.
        val owned = Plus.owned(this)
        val wallpapers = picked.filter { !it.plus || owned }
        val held = picked.size - wallpapers.size
        if (held > 0) toast(getString(R.string.wp_plus_held_fmt, held))
        if (wallpapers.isEmpty()) return
        beginExportAll(wallpapers)
    }

    private fun beginExportAll(wallpapers: List<Wallpaper>) {
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

    companion object {
        /** Chip key for "every live loop"; never a real series folder name. */
        private const val LIVE_KEY = "__live__"
        /** Chip key for "new in the latest release"; never a series folder name. */
        private const val NEW_KEY = "__new__"
        /** Chip key for the Core Builds Plus walls; never a series folder name. */
        private const val PLUS_KEY = "__plus__"
        /** Chip key for the starred walls; never a series folder name. */
        private const val FAV_KEY = "__favourites__"
    }
}
