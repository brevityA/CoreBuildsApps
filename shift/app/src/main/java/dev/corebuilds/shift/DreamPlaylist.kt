package dev.corebuilds.shift

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import java.io.File

/**
 * Playlist for the Android TV screensaver.
 *
 * Order: already-downloaded Movies/CoreBuilds files, then the internal live
 * cache, then the bundled Overflight catalog (https, allowlisted). Never
 * invents a URL. Empty list is a valid result — the dream shows night chrome.
 */
object DreamPlaylist {

    fun mediaItems(context: Context): List<MediaItem> {
        val movies = listMovies(context)
        if (movies.isNotEmpty()) return movies
        val cached = listCache(context)
        if (cached.isNotEmpty()) return cached
        return LiveCatalog.load(context)
            .filter { it.mediaAvailable && it.url1080p.startsWith("https://") }
            .map { MediaItem.fromUri(it.url1080p) }
    }

    private fun listCache(context: Context): List<MediaItem> {
        val dir = File(context.cacheDir, "live")
        val files = dir.listFiles { file ->
            file.isFile && file.name.endsWith(".mp4", ignoreCase = true) && file.length() > 20_000
        } ?: return emptyList()
        return files.sortedBy { it.name }.map { MediaItem.fromUri(Uri.fromFile(it)) }
    }

    private fun listMovies(context: Context): List<MediaItem> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listMoviesMediaStore(context)
        } else {
            listMoviesLegacy()
        }
    }

    private fun listMoviesMediaStore(context: Context): List<MediaItem> {
        val items = ArrayList<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%Movies/CoreBuilds%")
        val resolver = context.contentResolver
        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Video.Media.DISPLAY_NAME} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id.toString(),
                )
                items.add(MediaItem.fromUri(uri))
            }
        }
        return items
    }

    @Suppress("DEPRECATION")
    private fun listMoviesLegacy(): List<MediaItem> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "CoreBuilds",
        )
        val files = dir.listFiles { file ->
            file.isFile && file.name.endsWith(".mp4", ignoreCase = true)
        } ?: return emptyList()
        return files.sortedBy { it.name }.map { MediaItem.fromUri(Uri.fromFile(it)) }
    }
}
