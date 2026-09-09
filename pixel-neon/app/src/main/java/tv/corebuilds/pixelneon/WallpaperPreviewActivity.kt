package tv.corebuilds.pixelneon

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Full-screen preview of one wallpaper with D-pad left/right navigation.
 *
 * The bundled thumb is shown instantly while the full 4K image downloads from
 * the repo (cached by [WallpaperDownloader]); once decoded it replaces the
 * thumb. Two actions:
 *  - **Set** writes the system wallpaper (Monet re-themes from it).
 *  - **Save** copies the original file to Pictures/CoreBuilds (for launcher
 *    wallpaper rotation).
 *
 * D-pad left/right cycles through the wallpaper list passed from the browser.
 * A generation counter guards against stale download callbacks firing after
 * the user has already moved on.
 */
class WallpaperPreviewActivity : AppCompatActivity() {

    private lateinit var setButton: TextView
    private lateinit var saveButton: TextView
    private lateinit var sub: TextView
    private lateinit var image: ImageView
    private lateinit var titleView: TextView

    private var wallpapers: List<Wallpaper> = emptyList()
    private var currentIndex = 0
    private var generation = 0

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
        titleView = findViewById(R.id.preview_title)
        setButton = findViewById(R.id.preview_set)
        saveButton = findViewById(R.id.preview_save)
        sub = findViewById(R.id.preview_sub)

        @Suppress("DEPRECATION")
        val list = intent.getParcelableArrayListExtra<Wallpaper>(EXTRA_WALLPAPERS)
        if (list != null && list.isNotEmpty()) {
            wallpapers = list
            currentIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, list.size - 1)
        } else {
            val url = intent.getStringExtra(EXTRA_URL).orEmpty()
            val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
            if (url.isEmpty()) { finish(); return }
            wallpapers = listOf(
                Wallpaper(name = title, series = "", url = url,
                    thumbUrl = "", resolution = "",
                    thumbAsset = "${WallpaperCatalog.THUMB_DIR}/${url.substringAfterLast('/').substringBeforeLast('.')}.jpg")
            )
            currentIndex = 0
        }

        val back = findViewById<TextView>(R.id.preview_back)
        back.setOnClickListener { finish() }
        setButton.setOnClickListener { onSetClicked() }
        saveButton.setOnClickListener { onSaveClicked() }

        back.post { back.requestFocus() }
        showWallpaper()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (wallpapers.size <= 1) return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { navigate(-1); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { navigate(1); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun navigate(delta: Int) {
        val next = (currentIndex + delta + wallpapers.size) % wallpapers.size
        if (next == currentIndex) return
        currentIndex = next
        showWallpaper()
    }

    private fun showWallpaper() {
        generation++
        val gen = generation
        val wp = wallpapers[currentIndex]

        image.setImageDrawable(null)
        fullBitmap?.recycle()
        fullBitmap = null
        downloaded = null
        loading = true
        setButton.isEnabled = false
        saveButton.isEnabled = false
        setButton.text = getString(R.string.wp_set_wallpaper)
        saveButton.text = getString(R.string.wp_save)

        titleView.text = wp.title
        if (wallpapers.size > 1) {
            sub.text = getString(R.string.wp_position_fmt, currentIndex + 1, wallpapers.size)
        } else {
            sub.text = ""
        }

        loadThumb(wp, gen)
        beginDownload(wp, gen)
    }

    private fun loadThumb(wp: Wallpaper, gen: Int) {
        val asset = wp.thumbAsset
        Thread {
            val bmp = try {
                assets.open(asset).use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (!destroyed && gen == generation && bmp != null && fullBitmap == null) {
                    image.setImageBitmap(bmp)
                }
            }
        }.start()
    }

    private fun beginDownload(wp: Wallpaper, gen: Int) {
        WallpaperDownloader.fetchUrl(this, wp.url, wp.cacheName) { event ->
            if (destroyed || gen != generation) return@fetchUrl
            when (event) {
                is WallpaperDownloader.Event.Progress -> {
                    val rec = event.received / 1024
                    val tot = event.total
                    val progress = if (tot > 0) {
                        getString(R.string.wp_downloading_of_fmt, rec, tot / 1024)
                    } else {
                        getString(R.string.wp_downloading_fmt, rec)
                    }
                    sub.text = if (wallpapers.size > 1) {
                        "${getString(R.string.wp_position_fmt, currentIndex + 1, wallpapers.size)} · $progress"
                    } else {
                        progress
                    }
                }
                is WallpaperDownloader.Event.Ready -> {
                    downloaded = event.file
                    decodeAndShow(event.file, gen)
                }
                is WallpaperDownloader.Event.Failed -> {
                    loading = false
                    sub.text = getString(R.string.wp_download_failed_fmt, event.reason)
                    toast(getString(R.string.wp_download_failed_fmt, event.reason))
                }
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun decodeAndShow(file: File, gen: Int) {
        Thread {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options, 1920, 1080)
            options.inJustDecodeBounds = false

            val bmp = BitmapFactory.decodeFile(file.absolutePath, options)
            runOnUiThread {
                if (destroyed || gen != generation) {
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
                sub.text = if (wallpapers.size > 1) {
                    val pos = getString(R.string.wp_position_fmt, currentIndex + 1, wallpapers.size)
                    val hint = if (WallpaperSetter.canSetDirectly(this)) {
                        getString(R.string.wp_sub_set)
                    } else {
                        getString(R.string.wp_sub_save)
                    }
                    "$pos · $hint"
                } else {
                    if (WallpaperSetter.canSetDirectly(this)) {
                        getString(R.string.wp_sub_set)
                    } else {
                        getString(R.string.wp_sub_save)
                    }
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
        val file = downloaded ?: run {
            if (!loading) toast(getString(R.string.wp_load_failed))
            return
        }
        setButton.isEnabled = false
        setButton.text = getString(R.string.wp_applying)
        Thread {
            val result = WallpaperSetter.apply(this, file)
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                setButton.isEnabled = true
                setButton.text = getString(R.string.wp_set_wallpaper)
                when (result) {
                    is WallpaperSetter.Result.Set -> {
                        val homePkg = ApplyIconPack.homePackage(this@WallpaperPreviewActivity)
                        if (homePkg == "com.spocky.projengmenu") {
                            Thread {
                                val fallback = WallpaperSetter.copyFileToPictures(this@WallpaperPreviewActivity, downloaded!!, downloaded!!.name)
                                runOnUiThread {
                                    if (destroyed) return@runOnUiThread
                                    if (fallback is WallpaperSetter.Result.SavedToGallery) {
                                        try {
                                            startActivity(WallpaperSetter.setProjectivyIntent(fallback.uri))
                                            toast("Applied to Projectivy Launcher")
                                        } catch (e: Exception) {
                                            toast(getString(R.string.wp_set_done))
                                        }
                                    } else {
                                        toast(getString(R.string.wp_set_done))
                                    }
                                    finish()
                                }
                            }.start()
                        } else {
                            toast(getString(R.string.wp_set_done))
                            finish()
                        }
                    }
                    is WallpaperSetter.Result.SavedToGallery -> {
                        toast(getString(R.string.wp_saved))
                        val homePkg = ApplyIconPack.homePackage(this@WallpaperPreviewActivity)
                        if (homePkg == "com.spocky.projengmenu") {
                            try {
                                startActivity(WallpaperSetter.setProjectivyIntent(result.uri))
                                toast("Applied to Projectivy Launcher")
                            } catch (e: Exception) {
                                toast(getString(R.string.wp_saved_hint))
                            }
                        } else {
                            try {
                                val mime = if (downloaded?.name?.endsWith(".png", ignoreCase = true) == true) "image/png" else "image/jpeg"
                                openSetter.launch(
                                    WallpaperSetter.setIntent(result.uri, mime)
                                )
                            } catch (e: Exception) {
                                if (homePkg == "com.klevico.monet") {
                                    toast("Saved. Set via Monet Settings → Wallpaper → Your own images")
                                } else {
                                    toast(getString(R.string.wp_saved_hint))
                                }
                            }
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
        const val EXTRA_WALLPAPERS = "wallpapers"
        const val EXTRA_INDEX = "index"
    }
}
