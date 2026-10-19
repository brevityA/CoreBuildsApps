package tv.corebuilds.eq.ui

import android.graphics.Bitmap
import io.nayuki.qrcodegen.QrCode

/**
 * Text to scannable bitmap, via the vendored Nayuki encoder. The same wrapper
 * as the icon pack's `tv.corebuilds.iconpack.QrBitmap`, for the same reasons.
 *
 * Error correction sits at M rather than L: the code is photographed off a TV
 * panel from a sofa, at an angle, sometimes off a screen that pulses its
 * backlight. The quiet zone is the ISO/IEC 18004 minimum of four modules,
 * drawn into the bitmap rather than left to the view behind it, because a dark
 * card swallowing the quiet zone is the classic "scanner sees nothing"
 * failure. Modules are pure black on pure white for the camera's threshold.
 */
object QrBitmap {

    private const val QUIET = 4

    /**
     * Render [text] as a square bitmap around [targetPx] a side, rounded down
     * to a whole number of pixels per module so every module edge lands on a
     * pixel boundary.
     */
    fun render(text: String, targetPx: Int): Bitmap {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val total = qr.size + QUIET * 2
        val scale = maxOf(1, targetPx / total)
        val dim = total * scale
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val pixels = IntArray(dim * dim)
        for (y in 0 until dim) {
            for (x in 0 until dim) {
                val mx = x / scale - QUIET
                val my = y / scale - QUIET
                val dark = mx in 0 until qr.size && my in 0 until qr.size &&
                    qr.getModule(mx, my)
                pixels[y * dim + x] = if (dark) black else white
            }
        }
        return Bitmap.createBitmap(pixels, dim, dim, Bitmap.Config.ARGB_8888)
    }
}
