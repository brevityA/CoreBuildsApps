package dev.corebuilds.line

import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.Reader

/**
 * Input guards for the importer. A provider file is untrusted input on a
 * device with a small heap: one line with no newline, one text node the size
 * of the file, or a DTD that expands entities would each end the app with an
 * OutOfMemoryError long before any byte cap on the download is reached (a
 * gzip bomb decompresses past it). These fail the import instead.
 */

/**
 * `readLine()` that keeps at most [maxLine] characters of a line and skips the
 * rest, and splits only on `\n` (dropping a trailing `\r`) — the same rule as
 * `text.split(/\r?\n/)` in playlist.mjs, so a lone `\r` stays in the line on
 * both sides.
 */
class BoundedLineReader(input: Reader, private val maxLine: Int = 16 * 1024) : BufferedReader(input, 64 * 1024) {
    private val sb = StringBuilder()

    override fun readLine(): String? {
        sb.setLength(0)
        var any = false
        while (true) {
            val c = read()
            if (c < 0) return if (any) finish() else null
            any = true
            if (c == '\n'.code) return finish()
            if (sb.length < maxLine) sb.append(c.toChar())
        }
    }

    private fun finish(): String {
        if (sb.isNotEmpty() && sb[sb.length - 1] == '\r') sb.setLength(sb.length - 1)
        return sb.toString()
    }
}

/**
 * Caps the decompressed size, and — for XML — the longest run of bytes
 * between two `<` (a text node the parser would buffer whole) and any
 * `<!ENTITY` before the document element (entity expansion).
 */
class ContentGuard(
    stream: InputStream,
    private val maxBytes: Long,
    private val xml: Boolean,
    private val maxRun: Int = 1 shl 20,
) : FilterInputStream(stream) {
    private var total = 0L
    private var run = 0
    private var inProlog = xml
    private var window = 0L // last 8 bytes, for "<!ENTITY" and "<tv"

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) see(b)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        for (i in off until off + maxOf(n, 0)) see(b[i].toInt() and 0xff)
        return n
    }

    private fun see(b: Int) {
        total += 1
        if (total > maxBytes) throw Importer.ImportError("That file is too large.")
        if (!xml) return
        if (b == '<'.code) run = 0 else if (++run > maxRun) throw Importer.ImportError("The guide file is damaged or incomplete.")
        if (inProlog) {
            window = (window shl 8) or b.toLong()
            if (window == ENTITY) throw Importer.ImportError("That guide file isn't allowed: it declares entities.")
            // "<tv" followed by a space, ">" or newline ends the prolog.
            if ((window shr 8) and 0xffffff == TV && (b == ' '.code || b == '>'.code || b == '\n'.code || b == '\r'.code || b == '\t'.code)) {
                inProlog = false
            }
        }
    }

    companion object {
        private val ENTITY = "<!ENTITY".fold(0L) { acc, c -> (acc shl 8) or c.code.toLong() }
        private val TV = "<tv".fold(0L) { acc, c -> (acc shl 8) or c.code.toLong() }
    }
}
