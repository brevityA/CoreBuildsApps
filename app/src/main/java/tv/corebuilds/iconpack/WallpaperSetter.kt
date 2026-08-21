package tv.corebuilds.iconpack

import android.Manifest
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * Applies a downloaded wallpaper bitmap to the home screen, and copies
 * wallpaper files into shared storage for launchers that run their own
 * wallpaper rotation (Monet, etc.).
 *
 * Monet Launcher extracts its Material You palette from the system wallpaper,
 * so setting it through [WallpaperManager] is the path that actually re-themes
 * the launcher. On stock Android TV / Google TV this "just works". Amazon Fire
 * TV blocks third-party wallpaper writes, so we fall back to saving the image
 * into the device Pictures folder and opening the system crop/set intent — the
 * same workaround Monet's own docs recommend.
 */
object WallpaperSetter {

    private const val TAG = "CoreBuilds/Wallpaper"
    private val RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/CoreBuilds"

    sealed class Result {
        data class Set(val by: String) : Result()
        data class SavedToGallery(val uri: Uri) : Result()
        data class NeedsPermission(val permission: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    /** True when the platform lets a non-system app set the wallpaper directly. */
    fun canSetDirectly(context: Context): Boolean =
        try {
            val wm = WallpaperManager.getInstance(context)
            // isSetWallpaperAllowed() returns false on locked-down devices
            // (notably Fire TV), where the write is a no-op or throws.
            wm.isSetWallpaperAllowed && wm.isWallpaperSupported
        } catch (e: Exception) {
            false
        }

    /**
     * Permission required to write to shared storage on API 28 and below
     * (Fire TV / older Shield / Android TV 9 boxes). Scoped storage on API 29+
     * needs no runtime permission for MediaStore inserts into Pictures.
     */
    fun storagePermission(): String? =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) Manifest.permission.WRITE_EXTERNAL_STORAGE
        else null

    fun hasStoragePermission(context: Context): Boolean {
        val p = storagePermission() ?: return true
        return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }

    fun requiredPermission(): String? =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) Manifest.permission.SET_WALLPAPER
        else null

    fun hasSetPermission(context: Context): Boolean {
        val p = requiredPermission() ?: return true
        return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Apply [bitmap] as the system home wallpaper. The caller owns the bitmap
     * and may recycle it after this returns.
     */
    fun apply(context: Context, bitmap: Bitmap): Result {
        val perm = requiredPermission()
        if (perm != null &&
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.NeedsPermission(perm)
        }
        return if (canSetDirectly(context)) {
            try {
                val wm = WallpaperManager.getInstance(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM)
                } else {
                    @Suppress("DEPRECATION")
                    wm.setBitmap(bitmap)
                }
                Result.Set("system")
            } catch (e: SecurityException) {
                Log.w(TAG, "direct set denied, falling back to gallery", e)
                saveBitmapToPictures(context, bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "direct set failed, falling back to gallery", e)
                saveBitmapToPictures(context, bitmap)
            }
        } else {
            saveBitmapToPictures(context, bitmap)
        }
    }

    /**
     * Copy an original wallpaper [file] (typically the cached download) into
     * `Pictures/CoreBuilds/`, preserving its bytes — no bitmap decode, no
     * re-encode. Used by both bulk export and single Save.
     *
     * On API ≤ 28 the caller must hold [storagePermission]; otherwise returns
     * [Result.NeedsPermission].
     */
    fun copyFileToPictures(
        context: Context,
        file: File,
        displayName: String = file.name
    ): Result {
        val perm = storagePermission()
        if (perm != null &&
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.NeedsPermission(perm)
        }
        if (!file.exists() || file.length() <= 0L) {
            return Result.Failed("Source file is missing or empty")
        }
        val mime = if (displayName.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return Result.Failed("MediaStore returned no uri")
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out, 64 * 1024) }
                    } ?: return Result.Failed("Could not open output stream")
                } catch (e: Exception) {
                    // Don't leave a pending (invisible) row behind.
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    throw e
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                Result.SavedToGallery(uri)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "CoreBuilds"
                )
                if (!dir.exists() && !dir.mkdirs()) {
                    return Result.Failed("Could not create Pictures/CoreBuilds")
                }
                val dest = File(dir, displayName)
                file.inputStream().use { it.copyTo(dest.outputStream(), 64 * 1024) }
                Result.SavedToGallery(Uri.fromFile(dest))
            }
        } catch (e: Exception) {
            Log.e(TAG, "copy to pictures failed", e)
            Result.Failed(e.message ?: "Could not save wallpaper")
        }
    }

    /**
     * Check whether a wallpaper named [displayName] has already been exported
     * at the same [sizeBytes]. Cheap idempotency guard for bulk export — avoids
     * re-copying files that are already in shared storage.
     *
     * Returns true only on API 29+ where we can query MediaStore by size+name.
     * On older devices this conservatively returns false.
     */
    fun alreadyExported(context: Context, displayName: String, sizeBytes: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val proj = arrayOf(MediaStore.Images.Media._ID)
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                proj,
                "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.SIZE}=?",
                arrayOf(displayName, sizeBytes.toString()),
                null
            )?.use { it.count > 0 } == true
        } catch (e: Exception) {
            false
        }
    }

    /** Build an ACTION_ATTACH_DATA intent for the system crop/setter fallback. */
    fun setIntent(uri: Uri, mime: String = "image/jpeg"): Intent =
        Intent(Intent.ACTION_ATTACH_DATA)
            .setDataAndType(uri, mime)
            .putExtra("mimeType", mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun saveBitmapToPictures(context: Context, bitmap: Bitmap): Result {
        val perm = storagePermission()
        if (perm != null &&
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.NeedsPermission(perm)
        }
        val displayName = "CoreBuilds-${System.currentTimeMillis()}.jpg"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return Result.Failed("MediaStore returned no uri")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                } ?: return Result.Failed("Could not open output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                Result.SavedToGallery(uri)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "CoreBuilds"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, displayName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                Result.SavedToGallery(Uri.fromFile(file))
            }
        } catch (e: Exception) {
            Log.e(TAG, "save to pictures failed", e)
            Result.Failed(e.message ?: "Could not save wallpaper")
        }
    }
}
