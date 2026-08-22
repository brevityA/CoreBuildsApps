package tv.corebuilds.iconpack

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
 * thumb. Two actions:
 *  - **Set** writes the system wallpaper (Monet re-themes from it).
 *  - **Save** copies the original file to Pictures/CoreBuilds (for launcher
 *    wallpaper rotation).
 *
 * All background work posts back only while the activity is alive, and the
 * decoded bitmap is detached from the ImageView before recycling to avoid
 * "Canvas: trying to use a recycled bitmap".
 */
class WallpaperPreviewActivity : AppCompatActivity() {

    private lateinit var url: String
    private lateinit var title: String
    private lateinit var setButton: TextView
    private lateinit var saveButton: TextView
    private lateinit var sub: TextView
    private lateinit var image: ImageView

    private var fullBitmap: Bitmap? = null
    private var downloaded: File? = null
    private var loading = false
    private var destroyed = false

    private val requestSetPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) applyNow() else toast(getString(R.string.wp_permission_denied))
        }

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) saveNow() else toast(getString(R.string.wp_storage_permission_denied))
        }

    private val openSetter =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_preview)

        image = findViewById(R.id.preview_image)
        url = intent.getStringExtra(EXTRA_URL).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (url.isEmpty()) { finish(); return }
        setButton = findViewById(R.id.preview_set)
        saveButton = findViewById(R.id.preview_save)
        sub = findViewById(R.id.preview_sub)
        findViewById<TextView>(R.id.preview_title).text = title

        val back = findViewById<TextView>(R.id.preview_back)
        back.setOnClickListener { finish() }
        setButton.setOnClickListener { onSetClicked() }
        saveButton.setOnClickListener { onSaveClicked() }

        setButton.isEnabled = false
        saveButton.isEnabled = false
        // Request focus once the view tree is laid out; doing it directly in
        // onCreate is unreliable on TV.
        back.post { back.requestFocus() }
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
            runOnUiThread {
                if (!destroyed && bmp != null && fullBitmap == null) image.setImageBitmap(bmp)
            }
        }.start()
    }

    private fun beginDownload() {
        loading = true
        WallpaperDownloader.fetchUrl(this, url, url.substringAfterLast('/')) { event ->
            if (destroyed) return@fetchUrl
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
                if (destroyed) {
                    bmp?.recycle()
                    return@runOnUiThread
                }
                loading = false
                if (bmp == null) {
                    sub.text = getString(R.string.wp_load_failed)
                    return@runOnUiThread
                }
                fullBitmap = bmp
                image.setImageBitmap(bmp)
                setButton.isEnabled = true
                saveButton.isEnabled = true
                sub.text = if (WallpaperSetter.canSetDirectly(this)) {
                    getString(R.string.wp_sub_set)
                } else {
                    getString(R.string.wp_sub_save)
                }
                setButton.requestFocus()
            }
        }.start()
    }

    // ---- Set -----------------------------------------------------------------

    private fun onSetClicked() {
        val perm = WallpaperSetter.requiredPermission()
        if (perm != null &&
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        ) {
            requestSetPermission.launch(perm)
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
                if (destroyed) return@runOnUiThread
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
                            openSetter.launch(
                                WallpaperSetter.setIntent(result.uri, "image/jpeg")
                            )
                        } catch (e: Exception) {
                            toast(getString(R.string.wp_saved_hint))
                        }
                    }
                    is WallpaperSetter.Result.NeedsPermission ->
                        requestSetPermission.launch(result.permission)
                    is WallpaperSetter.Result.Failed ->
                        toast(getString(R.string.wp_set_failed_fmt, result.reason))
                }
            }
        }.start()
    }

    // ---- Save ----------------------------------------------------------------

    private fun onSaveClicked() {
        val perm = WallpaperSetter.storagePermission()
        if (perm != null &&
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        ) {
            requestStoragePermission.launch(perm)
            return
        }
        saveNow()
    }

    private fun saveNow() {
        val file = downloaded ?: run {
            if (!loading) toast(getString(R.string.wp_load_failed))
            return
        }
        saveButton.isEnabled = false
        saveButton.text = getString(R.string.wp_saving)
        Thread {
            val result = WallpaperSetter.copyFileToPictures(this, file, file.name)
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                saveButton.isEnabled = true
                saveButton.text = getString(R.string.wp_save)
                when (result) {
                    is WallpaperSetter.Result.SavedToGallery ->
                        toast(getString(R.string.wp_save_done))
                    is WallpaperSetter.Result.NeedsPermission ->
                        requestStoragePermission.launch(result.permission)
                    is WallpaperSetter.Result.Failed ->
                        toast(getString(R.string.wp_set_failed_fmt, result.reason))
                    else -> { /* Set can't happen from a file copy */ }
                }
            }
        }.start()
    }

    override fun onDestroy() {
        destroyed = true
        // Detach first so the ImageView never paints a recycled bitmap.
        image.setImageDrawable(null)
        fullBitmap?.recycle()
        fullBitmap = null
        super.onDestroy()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }
}
