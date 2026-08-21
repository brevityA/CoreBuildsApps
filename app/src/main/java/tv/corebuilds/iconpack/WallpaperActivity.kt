package tv.corebuilds.iconpack

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class WallpaperActivity : AppCompatActivity() {

    private lateinit var adapter: WallpaperAdapter
    private var wallpapers = listOf<WallpaperItem>()
    private var series = ALL
    private var previewItem: WallpaperItem? = null

    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    data class WallpaperItem(
        val name: String,
        val series: String,
        val url: String,
        val thumbUrl: String,
        val resolution: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpapers)

        adapter = WallpaperAdapter(
            cacheDir = File(cacheDir, "wallpaper_thumbs").apply { mkdirs() },
            io = io,
            main = main,
            onActivate = { item -> showPreview(item) }
        )

        findViewById<RecyclerView>(R.id.wallpaper_grid).apply {
            layoutManager = GridLayoutManager(this@WallpaperActivity, spanForScreen())
            adapter = this@WallpaperActivity.adapter
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (previewItem != null) {
                    hidePreview()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        loadManifest()
    }

    private fun loadManifest() {
        val status = findViewById<TextView>(R.id.wallpaper_status)
        status.text = getString(R.string.wallpapers_loading)
        status.visibility = View.VISIBLE

        io.execute {
            try {
                val items = fetchManifest()
                main.post {
                    wallpapers = items
                    status.visibility = View.GONE
                    bindChips()
                    applyFilter()
                    updateCount()
                }
            } catch (e: Exception) {
                main.post {
                    status.text = getString(
                        R.string.wallpapers_failed_fmt,
                        e.message ?: "unknown error"
                    )
                }
            }
        }
    }

    private fun fetchManifest(): List<WallpaperItem> {
        val conn = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val arr = json.getJSONArray("wallpapers")
            return (0 until arr.length()).map { i ->
                val w = arr.getJSONObject(i)
                WallpaperItem(
                    name = w.getString("name"),
                    series = w.getString("series"),
                    url = w.getString("url"),
                    thumbUrl = w.getString("thumb"),
                    resolution = w.optString("resolution", "")
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun bindChips() {
        val seriesKeys = wallpapers.map { it.series }.distinct().sorted()
        val labels = mutableListOf(getString(R.string.chip_all))
        val keys = mutableListOf(ALL)
        for (key in seriesKeys) {
            keys += key
            labels += key.substringAfterLast("-")
                .replaceFirstChar { it.uppercaseChar() }
        }
        findViewById<RecyclerView>(R.id.wallpaper_chips).apply {
            layoutManager = LinearLayoutManager(
                this@WallpaperActivity, LinearLayoutManager.HORIZONTAL, false
            )
            adapter = ChipAdapter(labels, keys, ALL) { picked ->
                series = picked
                applyFilter()
                updateCount()
            }
        }
    }

    private fun applyFilter() {
        val filtered = if (series == ALL) wallpapers
        else wallpapers.filter { it.series == series }
        adapter.submit(filtered)
    }

    private fun updateCount() {
        val count = adapter.itemCount
        val total = wallpapers.size
        findViewById<TextView>(R.id.wallpaper_count).text = if (count == total) {
            getString(R.string.wallpaper_count_fmt, total)
        } else {
            getString(R.string.wallpaper_filter_fmt, count, total)
        }
    }

    private fun showPreview(item: WallpaperItem) {
        previewItem = item
        val overlay = findViewById<View>(R.id.preview_overlay)
        val image = findViewById<ImageView>(R.id.preview_image)
        val name = findViewById<TextView>(R.id.preview_name)
        val sub = findViewById<TextView>(R.id.preview_sub)
        val applyBtn = findViewById<TextView>(R.id.preview_apply)
        val backBtn = findViewById<TextView>(R.id.preview_back)
        val progress = findViewById<ProgressBar>(R.id.preview_progress)

        overlay.visibility = View.VISIBLE
        name.text = item.name
        sub.text = item.resolution
        image.setImageBitmap(null)
        progress.visibility = View.VISIBLE
        applyBtn.isEnabled = false

        io.execute {
            try {
                val bitmap = downloadBitmap(item.url)
                main.post {
                    if (previewItem == item) {
                        progress.visibility = View.GONE
                        image.setImageBitmap(bitmap)
                        applyBtn.isEnabled = true
                        applyBtn.setOnClickListener { applyWallpaper(bitmap, item.name) }
                        applyBtn.requestFocus()
                    }
                }
            } catch (e: Exception) {
                main.post {
                    if (previewItem == item) {
                        progress.visibility = View.GONE
                        sub.text = getString(
                            R.string.wallpapers_failed_fmt,
                            e.message ?: "download failed"
                        )
                    }
                }
            }
        }

        backBtn.setOnClickListener { hidePreview() }
    }

    private fun hidePreview() {
        previewItem = null
        findViewById<View>(R.id.preview_overlay).visibility = View.GONE
        findViewById<ImageView>(R.id.preview_image).setImageBitmap(null)
    }

    private fun applyWallpaper(bitmap: Bitmap, name: String) {
        try {
            WallpaperManager.getInstance(this).setBitmap(bitmap)
            toast(getString(R.string.wallpaper_applied_fmt, name))
            hidePreview()
        } catch (e: Exception) {
            toast(
                getString(
                    R.string.wallpaper_apply_failed_fmt,
                    e.message ?: "unknown error"
                )
            )
        }
    }

    private fun downloadBitmap(url: String): Bitmap {
        if (!url.startsWith("https://")) {
            throw IllegalStateException("wallpaper URL must be https")
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = 60_000
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            return conn.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw IllegalStateException("could not decode image")
        } finally {
            conn.disconnect()
        }
    }

    private fun spanForScreen(): Int {
        val dp = resources.configuration.screenWidthDp
        return (dp / 280).coerceIn(2, 5)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val ALL = "ALL"
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/" +
                "main/Wallpapers/manifest.json"
        private const val TIMEOUT_MS = 8000
    }
}
