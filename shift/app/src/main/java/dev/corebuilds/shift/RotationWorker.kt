package dev.corebuilds.shift

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File

class RotationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val prefs = RotationPreferences(ctx)

        if (!prefs.rotationEnabled) {
            Log.i(TAG, "rotation disabled, skipping")
            return Result.success()
        }

        val wallpapers = collectWallpapers(ctx)
        if (wallpapers.isEmpty()) {
            Log.w(TAG, "no wallpapers found")
            return Result.success()
        }

        val index = prefs.lastIndex % wallpapers.size
        val next = wallpapers[index]

        return try {
            setWallpaper(ctx, next)
            prefs.lastIndex = index + 1
            Log.i(TAG, "set wallpaper ${index + 1}/${wallpapers.size}: $next")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "failed to set wallpaper: ${e.message}")
            Result.retry()
        }
    }

    private fun collectWallpapers(context: Context): List<WallpaperSource> {
        val sources = mutableListOf<WallpaperSource>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val args = arrayOf("%CoreBuilds%")
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args,
                "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    sources.add(WallpaperSource.MediaStoreUri(uri))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                ),
                "CoreBuilds"
            )
            if (dir.isDirectory) {
                dir.listFiles()
                    ?.filter { it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
                    ?.sorted()
                    ?.forEach { sources.add(WallpaperSource.FilePath(it)) }
            }
        }

        return sources
    }

    private fun setWallpaper(context: Context, source: WallpaperSource) {
        val wm = WallpaperManager.getInstance(context)
        when (source) {
            is WallpaperSource.MediaStoreUri -> {
                context.contentResolver.openInputStream(source.uri)?.use { stream ->
                    wm.setStream(stream)
                }
            }
            is WallpaperSource.FilePath -> {
                val bitmap = BitmapFactory.decodeFile(source.file.absolutePath)
                if (bitmap != null) {
                    wm.setBitmap(bitmap)
                    bitmap.recycle()
                }
            }
        }
    }

    companion object {
        private const val TAG = "CoreShiftRotation"
    }
}

sealed class WallpaperSource {
    data class MediaStoreUri(val uri: android.net.Uri) : WallpaperSource()
    data class FilePath(val file: File) : WallpaperSource() {
        override fun toString(): String = file.name
    }
}
