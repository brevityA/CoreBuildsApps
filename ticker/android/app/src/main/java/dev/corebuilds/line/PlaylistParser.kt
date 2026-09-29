package dev.corebuilds.line

import java.io.BufferedReader

/**
 * M3U playlist parsing for the native importer.
 *
 * Mirrors `parseM3U` in ticker/lib/playlist.mjs rule for rule — the web
 * build and the dev server use that one, the TV uses this one, and
 * tests/fixtures/playlist-parity.m3u holds both to the same answer.
 *
 * Reads line by line from a stream, so a provider playlist of tens of
 * thousands of entries is never held in memory: only the kept channels are.
 */
object PlaylistParser {
    const val MAX_CHANNELS = 4000
    private const val MAX_NAME = 64
    private const val MAX_URL = 500
    private const val MAX_ID = MAX_NAME * 2
    private const val MAX_GUIDE_URLS = 4

    data class Channel(
        val name: String,
        val url: String,
        val group: String,
        val tvgId: String,
        val tvgName: String,
    )

    data class Result(
        val channels: List<Channel>,
        val guideUrls: List<String>,
        val vodSkipped: Int,
    )

    fun parse(reader: BufferedReader, maxChannels: Int = MAX_CHANNELS, cancelled: () -> Boolean = { false }): Result {
        val channels = ArrayList<Channel>()
        val seenUrls = HashSet<String>()
        val guideUrls = ArrayList<String>()
        var vodSkipped = 0
        var pendingName = ""
        var pendingId = ""
        var pendingTvgName = ""
        var group = ""

        while (true) {
            if (cancelled()) break
            val line = reader.readLine() ?: break
            val s = line.jsTrim()
            if (s.isEmpty()) continue

            if (s.startsWith("#EXTM3U")) {
                for (key in listOf("url-tvg", "x-tvg-url")) {
                    for (u in attr(s, key).split(',')) {
                        val v = u.jsTrim()
                        if (HTTP.containsMatchIn(v) && v !in guideUrls && guideUrls.size < MAX_GUIDE_URLS) guideUrls.add(v.take(MAX_URL))
                    }
                }
                continue
            }
            if (s.startsWith("#EXTINF")) {
                pendingName = extinfName(s)
                pendingId = attr(s, "tvg-id").jsTrim().take(MAX_ID)
                pendingTvgName = collapse(attr(s, "tvg-name")).take(MAX_NAME)
                val title = attr(s, "group-title")
                if (title.isNotEmpty()) group = title.take(40)
                continue
            }
            if (s.startsWith("#EXTGRP:")) {
                group = s.removePrefix("#EXTGRP:").jsTrim().take(40)
                continue
            }
            if (s.startsWith("#")) continue
            if (!HTTP.containsMatchIn(s)) continue
            if (isVodUrl(s)) {
                vodSkipped += 1
                pendingName = ""; pendingId = ""; pendingTvgName = ""
                continue
            }
            if (!seenUrls.add(s)) continue

            val name = (pendingName.ifEmpty { fallbackName(s, channels.size + 1) }).take(MAX_NAME).jsTrim()
            channels.add(Channel(name, s.take(MAX_URL), group, pendingId, pendingTvgName))
            pendingName = ""; pendingId = ""; pendingTvgName = ""
            if (channels.size >= maxChannels) break
        }
        return Result(channels, guideUrls, vodSkipped)
    }

    /** A movie or series entry rather than a live channel (see isVodUrl in playlist.mjs). */
    fun isVodUrl(url: String): Boolean {
        val path = pathOf(url)?.lowercase() ?: return false
        if (VOD_DIR.containsMatchIn(path)) return true
        return VOD_EXT.containsMatchIn(path)
    }

    /**
     * The path of an http(s) URL, cut by hand: java.net.URI rejects a space or
     * a `|` that the browser's URL parser (which playlist.mjs uses) accepts.
     */
    private fun pathOf(url: String): String? {
        val m = AUTHORITY.find(url) ?: return null
        val rest = url.substring(m.range.last + 1)
        val path = rest.substringBefore('#').substringBefore('?')
        return path.ifEmpty { "/" }
    }

    private fun hostOf(url: String): String =
        AUTHORITY.find(url)?.groupValues?.get(1)?.substringAfterLast('@')?.substringBefore(':')?.lowercase().orEmpty()

    /** JavaScript's `\\s`, which is Unicode-wide; Java's is ASCII only. */
    private const val WS = "[\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]"
    private val HTTP = Regex("^https?://", RegexOption.IGNORE_CASE)
    private val AUTHORITY = Regex("^https?://([^/?#]*)", RegexOption.IGNORE_CASE)
    private val VOD_DIR = Regex("/(?:movie|movies|series|vod)/")
    private val VOD_EXT = Regex("\\.(?:mp4|mkv|avi|mov|wmv|flv|webm)$")

    private fun extinfName(line: String): String {
        val comma = line.lastIndexOf(',')
        if (comma != -1 && comma < line.length - 1) {
            val tail = line.substring(comma + 1).jsTrim()
            if (tail.isNotEmpty()) return collapse(tail)
        }
        val tvg = attr(line, "tvg-name")
        return if (tvg.isNotEmpty()) collapse(tvg) else ""
    }

    /** Value of a `key="value"` attribute, tolerant of missing quotes. */
    private fun attr(line: String, key: String): String {
        val (quoted, loose) = attrPatterns.getOrPut(key) {
            val k = Regex.escape(key)
            Regex("$k$WS*=$WS*\"([^\"]*)\"", RegexOption.IGNORE_CASE) to
                Regex("$k$WS*=$WS*([^${WS.removeSurrounding("[", "]")},\"]+)", RegexOption.IGNORE_CASE)
        }
        quoted.find(line)?.let { return it.groupValues[1] }
        return loose.find(line)?.groupValues?.get(1) ?: ""
    }

    private val attrPatterns = java.util.concurrent.ConcurrentHashMap<String, Pair<Regex, Regex>>()

    private fun fallbackName(url: String, n: Int): String {
        val last = (pathOf(url) ?: return "Channel $n").split('/').lastOrNull { it.isNotEmpty() } ?: hostOf(url)
        val decoded = percentDecode(last) ?: return "Channel $n"
        return decoded.replace(STREAM_EXT, "").ifEmpty { "Channel $n" }
    }

    /** decodeURIComponent: UTF-8 percent escapes, `+` left alone, null when malformed. */
    private fun percentDecode(s: String): String? {
        if ('%' !in s) return s
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                val hex = s.substring(i + 1, minOf(i + 3, s.length))
                val b = hex.takeIf { it.length == 2 }?.toIntOrNull(16) ?: return null
                out.write(b)
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i += 1
            }
        }
        val bytes = out.toByteArray()
        val decoder = Charsets.UTF_8.newDecoder()
        return try { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() } catch (_: Exception) { null }
    }

    private val STREAM_EXT = Regex("\\.(m3u8?|ts|mp4)$", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("$WS+")
    private fun collapse(s: String): String = s.replace(SPACES, " ").jsTrim()

    /** String.prototype.trim(): Kotlin's trim() keeps a byte-order mark, JavaScript's drops it. */
    private fun String.jsTrim(): String = trim { it.isWhitespace() || it == '\uFEFF' }
}
