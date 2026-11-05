package tv.corebuilds.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * What a card draws, decided once per app per scan and kept at card size:
 * a 16:9 [banner] that fills the card, or a square [icon] on a plate tinted
 * from the icon itself. [backdrop] is a tiny softened copy the background
 * scales up behind the home screen; scaling a 48 x 27 image to full screen
 * is the blur, so it costs the same on Android 11 as on 15.
 */
internal class CardArt(
    val banner: Bitmap?,
    val icon: Bitmap?,
    /** The art's average colour: the plate behind an icon, and the glow behind it on the backdrop. */
    val tint: Int,
    val backdrop: Bitmap?,
    /** Where the art came from: the app itself, the chosen icon pack, or the user's pick for this app. */
    val source: ArtSource,
)

internal enum class ArtSource { APP, PACK, CUSTOM }

/** Pixel sizes the art is rendered at, from the card size in Customise. */
internal data class ArtSpec(val cardWidth: Int, val cardHeight: Int, val iconSize: Int, val source: String, val cornerRadius: Float = 0f)

internal object CardArtFactory {
    /** Pack art this much wider than tall is a banner; anything squarer is an icon. */
    private const val BANNER_ASPECT = 1.4f
    private const val BACKDROP_WIDTH = 48
    private const val BACKDROP_HEIGHT = 27

    /**
     * The card for one app: [packArt] (the user's pick or the icon pack's) when
     * there is one, else the app's own banner, else its icon. Art at least 1.4x
     * wider than tall fills the card; squarer art sits on a tinted plate.
     */
    fun create(
        spec: ArtSpec,
        packArt: Drawable?,
        appBanner: () -> Drawable?,
        appIcon: () -> Drawable,
        packSource: ArtSource = ArtSource.PACK,
    ): CardArt {
        if (packArt != null) {
            return if (isBanner(packArt)) bannerArt(spec, packArt, packSource) else iconArt(spec, packArt, packSource)
        }
        appBanner()?.let { return bannerArt(spec, it, ArtSource.APP) }
        return iconArt(spec, appIcon(), ArtSource.APP)
    }

    private fun isBanner(drawable: Drawable): Boolean {
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        return w > 0 && h > 0 && w.toFloat() / h >= BANNER_ASPECT
    }

    private fun bannerArt(spec: ArtSpec, drawable: Drawable, source: ArtSource): CardArt {
        // Rounded in the bitmap itself, so the corners match the card wherever it is drawn.
        val banner = render(drawable, spec.cardWidth, spec.cardHeight, crop = true, cornerRadius = spec.cornerRadius)
        return CardArt(banner, null, averageColor(banner), soften(banner), source)
    }

    private fun iconArt(spec: ArtSpec, drawable: Drawable, source: ArtSource): CardArt {
        val icon = render(drawable, spec.iconSize, spec.iconSize, crop = false)
        return CardArt(null, icon, averageColor(icon), null, source)
    }

    /** Draws [drawable] into a [width] x [height] bitmap: centre-cropped, or fitted inside. */
    fun render(drawable: Drawable, width: Int, height: Int, crop: Boolean, cornerRadius: Float = 0f): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (cornerRadius > 0f) {
            canvas.clipPath(Path().apply {
                addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, Path.Direction.CW)
            })
        }
        val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: width
        val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: height
        val scale = if (crop) maxOf(width.toFloat() / iw, height.toFloat() / ih) else minOf(width.toFloat() / iw, height.toFloat() / ih)
        val dw = (iw * scale).toInt()
        val dh = (ih * scale).toInt()
        val left = (width - dw) / 2
        val top = (height - dh) / 2
        val previous = Rect(drawable.bounds)
        drawable.setBounds(left, top, left + dw, top + dh)
        drawable.draw(canvas)
        drawable.bounds = previous
        return bitmap
    }

    /** The mean colour of the visibly opaque pixels; Night when there are none. */
    fun averageColor(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 12, 12, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val p = small.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
        }
        if (small !== bitmap) small.recycle()
        if (n == 0L) return NIGHT
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** A 48 x 27 copy box-blurred twice; the background scales it up with filtering. */
    fun soften(bitmap: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(bitmap, BACKDROP_WIDTH, BACKDROP_HEIGHT, true)
        val out = if (small.isMutable) small else small.copy(Bitmap.Config.ARGB_8888, true).also { small.recycle() }
        repeat(2) { boxBlur(out, radius = 2) }
        return out
    }

    private fun boxBlur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) tmp[y * w + x] = mean(src, w, h, x, y, radius, horizontal = true)
        for (y in 0 until h) for (x in 0 until w) src[y * w + x] = mean(tmp, w, h, x, y, radius, horizontal = false)
        bitmap.setPixels(src, 0, w, 0, 0, w, h)
    }

    private fun mean(px: IntArray, w: Int, h: Int, x: Int, y: Int, radius: Int, horizontal: Boolean): Int {
        var a = 0; var r = 0; var g = 0; var b = 0; var n = 0
        for (d in -radius..radius) {
            val sx = if (horizontal) (x + d).coerceIn(0, w - 1) else x
            val sy = if (horizontal) y else (y + d).coerceIn(0, h - 1)
            val p = px[sy * w + sx]
            a += Color.alpha(p); r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
        }
        return Color.argb(a / n, r / n, g / n, b / n)
    }

    /** [color] mixed toward [toward] by [amount] (0 keeps it, 1 replaces it). */
    fun mix(color: Int, toward: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun ch(a: Int, b: Int) = (a + (b - a) * t).toInt()
        return Color.rgb(ch(Color.red(color), Color.red(toward)), ch(Color.green(color), Color.green(toward)), ch(Color.blue(color), Color.blue(toward)))
    }

    const val NIGHT = 0xFF0D1117.toInt()
    const val VOID = 0xFF04070F.toInt()
}
