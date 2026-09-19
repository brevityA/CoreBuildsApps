package tv.corebuilds.iconpack

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * One icon PNG out to `Pictures/CoreBuilds/Icons/`, bytes preserved.
 *
 * The bundled drawable is already the finished 512px art, so exporting is a
 * copy, not a render: decode-and-re-encode would spend heap a TV stick does
 * have to spare on producing a worse copy of a file that is sitting right
 * there in the APK. The insert grammar is WallpaperSetter's - MediaStore
 * with IS_PENDING under scoped storage on API 29+, the public Pictures tree
 * below it - with one difference: the subdirectory, because icons and
 * wallpapers landing in one folder makes either of them hard to rotate.
 *
 * Permission shape is the same contract the wallpaper export lives under:
 * scoped storage needs nothing on API 29+, API 28 and below needs the
 * declared WRITE_EXTERNAL_STORAGE (capped at maxSdk 28 in the manifest), and
 * a missing permission is a named result the UI can ask for, never an
 * exception wearing a toast.
 */
object IconExporter {

    private const val RELATIVE_PATH = "Pictures/CoreBuilds/Icons"

    sealed class Result {
        data class Saved(val displayName: String) : Result()
        data class NeedsPermission(val permission: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun storagePermission(): String? = WallpaperSetter.storagePermission()

    fun hasStoragePermission(context: Context): Boolean =
        WallpaperSetter.hasStoragePermission(context)

    /** Export the bundled drawable [drawableName] as `<name>.png`. */
    fun export(context: Context, drawableName: String): Result {
        val perm = storagePermission()
        if (perm != null && !hasStoragePermission(context)) {
            return Result.NeedsPermission(perm)
        }
        val id = context.resources.getIdentifier(
            drawableName, "drawable", context.packageName
        )
        if (id == 0) return Result.Failed("No bundled drawable $drawableName")
        val displayName = "$drawableName.png"
        val stage = File(context.cacheDir, displayName)
        try {
            context.resources.openRawResource(id).use { input ->
                stage.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
            }
        } catch (e: Exception) {
            return Result.Failed("Could not read the bundled icon: ${e.message}")
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                insertScoped(context, stage, displayName)
            } else {
                insertLegacy(context, stage, displayName)
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: "MediaStore insert failed")
        } finally {
            stage.delete()
        }
    }

    private fun insertScoped(context: Context, file: File, displayName: String): Result {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return Result.Failed("MediaStore returned no uri")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out, 64 * 1024) }
            } ?: return Result.Failed("Could not open output stream")
        } catch (e: Exception) {
            // Don't leave a pending (invisible) row behind.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return Result.Saved(displayName)
    }

    @Suppress("DEPRECATION")
    private fun insertLegacy(context: Context, file: File, displayName: String): Result {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "CoreBuilds/Icons"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.Failed("Could not create Pictures/CoreBuilds/Icons")
        }
        val target = File(dir, displayName)
        file.inputStream().use { input ->
            target.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
        }
        return Result.Saved(displayName)
    }
}
