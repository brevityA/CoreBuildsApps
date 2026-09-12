package tv.corebuilds.iconpack

import android.Manifest
import android.app.WallpaperManager
import android.content.ClipData
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
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Applies a downloaded wallpaper file to the home screen, hands it to
 * launchers that keep their own wallpaper library, and copies wallpaper files
 * into shared storage for launchers that run their own rotation.
 *
 * Three delivery paths, picked by the caller:
 *  - [apply] writes the **system** wallpaper via [WallpaperManager]. Stock
 *    Android TV / Google TV and launchers that theme from the system wallpaper
 *    pick it up. Amazon Fire TV blocks third-party wallpaper writes, so we fall
 *    back to saving into Pictures and opening the system crop/set intent.
 *  - [monetShareIntent] hands the file straight to **Monet Launcher**. Monet
 *    never reads the system wallpaper (no `WallpaperManager` reference in the
 *    v1.0.84 APK); its exported `WallpaperShareActivity` accepts
 *    `ACTION_SEND` / `ACTION_SEND_MULTIPLE` `image/*` and copies the file into
 *    Monet's own background library, then themes from it. Custom backgrounds
 *    are a Monet Premium feature — Monet shows its own toast either way.
 *  - [copyFileToPictures] copies the original bytes to `Pictures/CoreBuilds`
 *    for launchers that rotate from a folder (Monet "Choose folder", etc.).
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
            val setAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.N || wm.isSetWallpaperAllowed
            val supported = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || wm.isWallpaperSupported
            setAllowed && supported
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
    /**
     * Apply a wallpaper [file] as the system home wallpaper directly using an InputStream.
     * This avoids loading the entire 4K uncompressed bitmap into the app's heap space,
     * drastically reducing memory usage and preventing OOMs on low-RAM Android TVs.
     * Also prevents quality loss from JPEG re-encoding during fallback.
     */
    fun apply(context: Context, file: File): Result {
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
                    file.inputStream().use { stream ->
                        wm.setStream(stream, null, false, WallpaperManager.FLAG_SYSTEM)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    file.inputStream().use { stream ->
                        wm.setStream(stream)
                    }
                }
                Result.Set("system")
            } catch (e: SecurityException) {
                Log.w(TAG, "direct set denied, falling back to gallery", e)
                copyFileToPictures(context, file)
            } catch (e: Exception) {
                Log.w(TAG, "direct set failed, falling back to gallery", e)
                copyFileToPictures(context, file)
            }
        } else {
            copyFileToPictures(context, file)
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
                file.inputStream().use { inp ->
                    dest.outputStream().use { out -> inp.copyTo(out, 64 * 1024) }
                }
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

    // ---- Monet Launcher ------------------------------------------------------

    /** Monet Launcher package (Klevico). */
    const val MONET_PACKAGE = "com.klevico.monet"

    /**
     * Monet's exported share target. Decompiled from Monet v1.0.84
     * (versionCode 118, 2026-09-12): `<intent-filter>` for ACTION_SEND,
     * ACTION_SEND_MULTIPLE and ACTION_ATTACH_DATA with `image/*` and
     * `video/*`; category DEFAULT; `excludeFromRecents`, own task affinity.
     * It reads `intent.data`, EXTRA_STREAM (single or list) and `clipData`,
     * resolves the MIME through ContentResolver → extension → `intent.type`,
     * and needs FLAG_GRANT_READ_URI_PERMISSION on content URIs.
     */
    const val MONET_SHARE_ACTIVITY = "com.klevico.monet.WallpaperShareActivity"

    /** True when an installed Monet exposes the share target (v1.0.72+). */
    fun canShareToMonet(context: Context): Boolean = try {
        val probe = Intent(Intent.ACTION_SEND)
            .setClassName(MONET_PACKAGE, MONET_SHARE_ACTIVITY)
            .setType("image/*")
        context.packageManager.resolveActivity(probe, 0) != null
    } catch (_: Exception) {
        false
    }

    /**
     * A `content://` URI for a cached wallpaper [file] via this pack's
     * FileProvider (`xml/file_paths.xml` exposes `cache/wallpapers/`). Lets us
     * hand the cached download to Monet without first exporting it to Pictures.
     * Returns null when [file] is outside the exported paths.
     */
    fun contentUri(context: Context, file: File): Uri? = try {
        FileProvider.getUriForFile(context, UpdateInstaller.AUTHORITY, file)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "file is outside FileProvider paths: $file", e)
        null
    }

    fun mimeFor(name: String): String =
        if (name.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"

    /**
     * Explicit ACTION_SEND into Monet's [MONET_SHARE_ACTIVITY]. Explicit so no
     * chooser appears on a TV remote flow. Monet copies the bytes into its own
     * `files/backgrounds/` and toasts "Set as background" — or "Custom
     * backgrounds are part of Monet Premium." on the free tier.
     */
    fun monetShareIntent(uri: Uri, mime: String = "image/jpeg"): Intent =
        Intent(Intent.ACTION_SEND).apply {
            setClassName(MONET_PACKAGE, MONET_SHARE_ACTIVITY)
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("wallpaper", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Explicit ACTION_SEND_MULTIPLE into Monet. Monet adds every image to its
     * background library ("%d wallpapers added") and keeps the current one;
     * the user picks or rotates from Monet → Settings → Background → Gallery.
     */
    fun monetShareIntent(uris: List<Uri>): Intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            setClassName(MONET_PACKAGE, MONET_SHARE_ACTIVITY)
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            if (uris.isNotEmpty()) {
                val clip = ClipData.newRawUri("wallpaper", uris.first())
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                clipData = clip
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Projectivy Launcher specific static wallpaper apply intent.
     * Found via community research (Android TV intent sniffing).
     */
    fun setProjectivyIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            setClassName("com.spocky.projengmenu", "com.spocky.projengmenu.ui.launcherActivities.SetBackgroundActivity")
            putExtra("uri", uri.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
