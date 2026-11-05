package tv.corebuilds.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/**
 * Artwork the user chose for one app, overriding the pack and the app's own art.
 * A pack resource is stored as a reference, so it redraws at any card size and
 * follows pack updates; an image is copied into this launcher's own storage.
 */
internal sealed class CustomArt {
    data class PackResource(val packageName: String, val drawable: String) : CustomArt()
    data class Image(val fileName: String) : CustomArt()

    fun stored(): String = when (this) {
        is PackResource -> "res|$packageName|$drawable"
        is Image -> "file|$fileName"
    }

    companion object {
        fun parse(value: String): CustomArt? {
            val parts = value.split('|')
            return when {
                parts.size == 3 && parts[0] == "res" && parts[1].isNotBlank() && parts[2].isNotBlank() -> PackResource(parts[1], parts[2])
                parts.size == 2 && parts[0] == "file" && parts[1].isNotBlank() && !parts[1].contains('/') -> Image(parts[1])
                else -> null
            }
        }
    }
}

internal object CustomArtStore {
    /** The open icon-pack contract for "pick one icon", which the Core Builds Icon Pack answers. */
    const val PICK_ICON_ACTION = "org.adw.launcher.icons.ACTION_PICK_ICON"

    /** An imported image is kept at most this wide: enough for Large cards at 2x, small on disk. */
    private const val MAX_IMAGE_WIDTH = 960

    private fun dir(context: Context): File = File(context.filesDir, "custom_art").apply { mkdirs() }

    /** The drawable for [art], or null when its pack or file is gone (the app falls back to its default). */
    @SuppressLint("DiscouragedApi")
    fun load(context: Context, art: CustomArt): Drawable? = try {
        when (art) {
            is CustomArt.PackResource -> {
                val resources = context.packageManager.getResourcesForApplication(art.packageName)
                // Looking a pack's drawables up by name is the icon-pack contract.
                val id = resources.getIdentifier(art.drawable, "drawable", art.packageName)
                if (id == 0) null else resources.getDrawable(id, null)
            }
            is CustomArt.Image -> {
                val file = File(dir(context), art.fileName)
                if (!file.isFile) null else BitmapFactory.decodeFile(file.path)?.let { BitmapDrawable(context.resources, it) }
            }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * What a pack's icon browser handed back: a resource reference when it sends
     * one (the Core Builds pack does), else the bitmap it sent, saved here.
     */
    @Suppress("DEPRECATION")
    fun fromPickResult(context: Context, data: Intent?, appPackage: String): CustomArt? {
        data ?: return null
        val resource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
        } else {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
        }
        val name = resource?.resourceName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        if (resource?.packageName != null && name != null) return CustomArt.PackResource(resource.packageName, name)
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra("icon", Bitmap::class.java) ?: data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
        } else {
            data.getParcelableExtra<Bitmap>("icon") ?: data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
        }
        return bitmap?.let { save(context, it, appPackage) }
    }

    /** Copies an image the user picked into this launcher's storage, scaled down to [MAX_IMAGE_WIDTH]. */
    fun importImage(context: Context, uri: Uri, appPackage: String): CustomArt.Image? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_IMAGE_WIDTH) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?.let { save(context, it, appPackage) }
        }
    } catch (_: Exception) {
        null
    }

    private fun save(context: Context, source: Bitmap, appPackage: String): CustomArt.Image? {
        val bitmap = if (source.width > MAX_IMAGE_WIDTH) {
            Bitmap.createScaledBitmap(source, MAX_IMAGE_WIDTH, source.height * MAX_IMAGE_WIDTH / source.width, true)
        } else {
            source
        }
        val name = appPackage.replace(Regex("[^A-Za-z0-9._-]"), "_") + "-" + System.currentTimeMillis() + ".png"
        return try {
            FileOutputStream(File(dir(context), name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            CustomArt.Image(name)
        } catch (_: Exception) {
            null
        }
    }

    /** Deletes an image no app uses any more; pack references have nothing to delete. */
    fun discard(context: Context, art: CustomArt?) {
        if (art is CustomArt.Image) File(dir(context), art.fileName).delete()
    }

    fun clearAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
