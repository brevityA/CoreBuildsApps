package tv.corebuilds.iconpack

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Full-screen preview of one wallpaper.
 *
 * The bundled thumb is shown instantly while the full 4K image downloads from
 * the repo (cached by [WallpaperDownloader]); once decoded it replaces the
 * thumb. The CTA sets it through [WallpaperSetter] — on Fire TV it falls back
 * to saving to Pictures and opening the system setter.
 */
class WallpaperPreviewActivity : AppCompatActivity() {

    private lateinit var url: String
    private lateinit var title: String
    private lateinit var setButton: TextView
    private lateinit var sub: TextView
    private lateinit var image: ImageView

    private var fullBitmap: android.graphics.Bitmap? = null
    private var downloaded: File? = null
    private var loading = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) applyNow() else toast(getString(R.string.wp_permission_denied))
        }

    private val openSetter =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_preview)

        url = intent.getStringExtra(EXTRA_URL).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (url.isEmpty()) { finish(); return }

        image = findViewById(R.id.preview_image)
        setButton = findViewById(R.id.preview_set)
        sub = findViewById(R.id.preview_sub)
        findViewById<TextView>(R.id.preview_title).text = title

        val back = findViewById<TextView>(R.id.preview_back)
        back.setOnClickListener { finish() }
        setButton.setOnClickListener { onSetClicked() }

        setButton.isEnabled = false
        back.requestFocus()
        loadThumb()
        beginDownload()
    }

    private fun loadThumb() {
        val name = url.substringAfterLast('/').substringBeforeLast('.')
        val asset = "${WallpaperCatalog.THUMB_DIR}/$name.jpg"
        Thread {
            val bmp = try {
                assets.open(asset).use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            runOnUiThread { if (bmp != null && fullBitmap == null) image.setImageBitmap(bmp) }
        }.start()
    }

    private fun beginDownload() {
        // Reconstruct a Wallpaper-ish holder for the downloader (only url/cacheName matter).
        val wp = Wallpaper(name = title, series = "", url = url,
            thumbUrl = "", resolution = "3840x2160", thumbAsset = "")
        loading = true
        WallpaperDownloader.fetch(this, wp) { event ->
            when (event) {
                is WallpaperDownloader.Event.Progress -> {
                    val rec = event.received / 1024
                    val tot = event.total
                    sub.text = if (tot > 0) {
                        getString(R.string.wp_downloading_of_fmt, rec, tot / 1024)
                    } else {
                        getString(R.string.wp_downloading_fmt, rec)
                    }
                }
                is WallpaperDownloader.Event.Ready -> {
                    downloaded = event.file
                    decodeAndShow(event.file)
                }
                is WallpaperDownloader.Event.Failed -> {
                    loading = false
                    sub.text = getString(R.string.wp_download_failed_fmt, event.reason)
                    toast(getString(R.string.wp_download_failed_fmt, event.reason))
                }
            }
        }
    }

    private fun decodeAndShow(file: File) {
        Thread {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            runOnUiThread {
                loading = false
                if (bmp == null) {
                    sub.text = getString(R.string.wp_load_failed)
                    return@runOnUiThread
                }
                fullBitmap = bmp
                image.setImageBitmap(bmp)
                setButton.isEnabled = true
                sub.text = if (WallpaperSetter.canSetDirectly(this)) {
                    getString(R.string.wp_sub_set)
                } else {
                    getString(R.string.wp_sub_save)
                }
                setButton.requestFocus()
            }
        }.start()
    }

    private fun onSetClicked() {
        val perm = WallpaperSetter.requiredPermission()
        if (perm != null &&
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(perm)
            return
        }
        applyNow()
    }

    private fun applyNow() {
        val bmp = fullBitmap ?: run {
            if (!loading) toast(getString(R.string.wp_load_failed))
            return
        }
        setButton.isEnabled = false
        setButton.text = getString(R.string.wp_applying)
        Thread {
            val result = WallpaperSetter.apply(this, bmp)
            runOnUiThread {
                setButton.isEnabled = true
                setButton.text = getString(R.string.wp_set_wallpaper)
                when (result) {
                    is WallpaperSetter.Result.Set -> {
                        toast(getString(R.string.wp_set_done))
                        finish()
                    }
                    is WallpaperSetter.Result.SavedToGallery -> {
                        toast(getString(R.string.wp_saved))
                        try {
                            openSetter.launch(WallpaperSetter.setIntent(result.uri))
                        } catch (e: Exception) {
                            toast(getString(R.string.wp_saved_hint))
                        }
                    }
                    is WallpaperSetter.Result.NeedsPermission ->
                        requestPermission.launch(result.permission)
                    is WallpaperSetter.Result.Failed ->
                        toast(getString(R.string.wp_set_failed_fmt, result.reason))
                }
            }
        }.start()
    }

    override fun onDestroy() {
        fullBitmap?.recycle()
        super.onDestroy()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }
}
