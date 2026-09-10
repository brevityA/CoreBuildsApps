package tv.corebuilds.pixelneon

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import kotlin.math.abs

/**
 * Content-based seed palette from a wallpaper bitmap.
 *
 * Android TV OS does not broadcast wallpaper-driven Material You schemes
 * ([developer.android.com/design/ui/tv/guides/styles/color-system][1]).
 * Launchers that do (Monet) extract the same [WallpaperColors] seed we show
 * here, so the chips preview what tiles will become after Set.
 *
 * On API 27+ this is the platform quantizer. Below that, a 32×32 chromatic
 * sample — TV boxes on 21–26 never ran Monet anyway.
 *
 * Not a live theme overlay: icon rasters stay catalog colours. Call off the
 * main thread; [WallpaperColors.fromBitmap] copies internally.
 *
 * [1]: https://developer.android.com/design/ui/tv/guides/styles/color-system
 */
object WallpaperSeed {

    data class Palette(
        val seed: Int,
        val secondary: Int,
        val tertiary: Int,
        val container: Int,
        val onSeed: Int,
    ) {
        fun swatches(): IntArray = intArrayOf(seed, secondary, tertiary, container, onSeed)
    }

    fun from(bitmap: Bitmap): Palette {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val colors = WallpaperColors.fromBitmap(bitmap)
            val seed = colors.primaryColor.toArgb()
            val secondary = colors.secondaryColor?.toArgb() ?: shiftHue(seed, 32f)
            val tertiary = colors.tertiaryColor?.toArgb() ?: shiftHue(seed, -40f)
            return assemble(seed, secondary, tertiary)
        }
        return fromSample(bitmap)
    }

    private fun fromSample(bitmap: Bitmap): Palette {
        val w = 32
        val h = 32
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        try {
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            var best = 0
            var bestScore = -1f
            var second = 0
            var secondScore = -1f
            for (p in pixels) {
                if (((p ushr 24) and 0xFF) < 200) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                if (luma < 18f || luma > 245f) continue
                val score = (max - min).toFloat() * (1f - abs(luma - 140f) / 255f)
                if (score > bestScore) {
                    second = best
                    secondScore = bestScore
                    best = p or (0xFF shl 24)
                    bestScore = score
                } else if (score > secondScore) {
                    second = p or (0xFF shl 24)
                    secondScore = score
                }
            }
            if (bestScore < 0f) {
                return assemble(CORE_CYAN, BUILD_BLUE, DUSK_VIOLET)
            }
            val seed = best
            val secondary = if (secondScore > 0f) second else shiftHue(seed, 32f)
            return assemble(seed, secondary, shiftHue(seed, -40f))
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun assemble(seed: Int, secondary: Int, tertiary: Int): Palette {
        val container = mix(seed, NIGHT, 0.22f)
        val onSeed = if (luma(seed) > 160f) CTA_INK else INK
        return Palette(seed, secondary, tertiary, container, onSeed)
    }

    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + degrees + 360f) % 360f
        return Color.HSVToColor(hsv)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val u = 1f - t
        return (0xFF shl 24) or
            (((Color.red(a) * t + Color.red(b) * u).toInt() and 0xFF) shl 16) or
            (((Color.green(a) * t + Color.green(b) * u).toInt() and 0xFF) shl 8) or
            ((Color.blue(a) * t + Color.blue(b) * u).toInt() and 0xFF)
    }

    private fun luma(c: Int): Float =
        0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)

    private const val CORE_CYAN = 0xFF00E5FF.toInt()
    private const val BUILD_BLUE = 0xFF4FACFE.toInt()
    private const val DUSK_VIOLET = 0xFF8A4890.toInt()
    private const val NIGHT = 0xFF0D1117.toInt()
    private const val INK = 0xFFE6EDF3.toInt()
    private const val CTA_INK = 0xFF04202B.toInt()
}
