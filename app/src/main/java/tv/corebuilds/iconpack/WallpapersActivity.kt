package tv.corebuilds.iconpack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
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
 * TV-first D-pad flow: back → series chips → grid. Mirrors MainActivity's
 * night-chrome structure so the surfaces read as one app (Brand Guide §05).
 */
class WallpapersActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, WallpapersActivity::class.java))
        }
    }

    private lateinit var all: List<Wallpaper>
    private lateinit var adapter: WallpaperAdapter
    private lateinit var count: TextView
    private var series: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpapers)

        all = WallpaperCatalog.load(this)
        count = findViewById(R.id.wp_count)
        count.text = getString(R.string.wp_count_fmt, all.size)

        adapter = WallpaperAdapter(all) { item ->
            startActivity(
                android.content.Intent(this, WallpaperPreviewActivity::class.java).apply {
                    putExtra(WallpaperPreviewActivity.EXTRA_URL, item.url)
                    putExtra(WallpaperPreviewActivity.EXTRA_TITLE, item.title)
                }
            )
        }

        findViewById<RecyclerView>(R.id.wp_grid).apply {
            layoutManager = GridLayoutManager(this@WallpapersActivity, spanForScreen())
            adapter = this@WallpapersActivity.adapter
            setHasFixedSize(true)
        }

        findViewById<TextView>(R.id.wp_back).setOnClickListener { finish() }
        bindChips()
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
        count.text = if (series == null) {
            getString(R.string.wp_count_fmt, all.size)
        } else {
            getString(R.string.wp_filter_fmt, filtered.size, all.size)
        }
    }

    private fun spanForScreen(): Int {
        val dp = resources.configuration.screenWidthDp
        return (dp / 220).coerceIn(3, 6)
    }
}
