package tv.corebuilds.motion

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

/**
 * Vector live wallpapers bundled with the plugin, served as Lottie — Projectivy
 * renders them itself (tiny, resolution-independent, no video banding), the
 * same path the official sample uses (`Wallpaper(uri, WallpaperType.LOTTIE)`).
 */
object BundledAnimations {

    private data class Lottie(val rawResId: Int, val title: String)

    private val LOTTIES = listOf(
        Lottie(R.raw.core_hex, "Core Hex"),
        Lottie(R.raw.core_diamond, "Core Diamond"),
    )

    /** Build an android.resource:// URI for a raw resource. */
    private fun rawUri(context: Context, rawResId: Int): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath("raw")
            .appendPath(context.resources.getResourceEntryName(rawResId))
            .build()

    /** The bundled Lottie wallpapers, ready to serve to Projectivy. */
    fun list(context: Context): List<Wallpaper> =
        LOTTIES.map { l ->
            Wallpaper(rawUri(context, l.rawResId).toString(), WallpaperType.LOTTIE,
                title = l.title, source = "Core Motion", author = "Core Builds")
        }
}
