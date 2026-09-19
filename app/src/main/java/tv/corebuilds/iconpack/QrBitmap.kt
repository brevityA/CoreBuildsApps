package tv.corebuilds.iconpack

import android.graphics.Bitmap
import io.nayuki.qrcodegen.QrCode

/**
 * Text to scannable bitmap, via the vendored Nayuki encoder.
 *
 * Error correction sits at M rather than L: the code is photographed off a TV
 * panel from a sofa, at an angle, sometimes off a screen that pulses its
 * backlight - the extra redundancy is the difference between a scan and a
 * squint. M still keeps the module count low for the short issue URLs this
 * app encodes.
 *
 * The quiet zone is the ISO/IEC 18004 minimum of four modules, drawn as part
 * of the bitmap rather than left to the view behind it: a dark app chrome
 * swallowing the quiet zone is the classic "scanner sees nothing" failure.
 * Modules are pure black on pure white for the same reason - the pack's own
 * colours are for the pack, not for a code a camera has to threshold.
 */
object QrBitmap {

    private const val QUIET = 4

    /**
     * Render [text] as a square bitmap around [targetPx] a side. The size is
     * rounded down to a whole number of pixels per module; a code that cannot
     * be sampled on exact module boundaries is a code that decodes badly.
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
