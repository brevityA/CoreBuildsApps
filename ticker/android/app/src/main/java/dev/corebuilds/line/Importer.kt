package dev.corebuilds.line

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

/**
 * Imports the viewer's playlist and TV guide on the device.
 *
 * The web build reads a playlist through the dev server's /api/playlist; the
 * TV has no such server (LineWebClient answers 404 for it) and a provider
 * playlist or guide is far past the 1.5 MB proxy cap anyway. So the import
 * runs here, off the UI thread, one job at a time: stream the download, parse
 * it as it arrives, and write a compact JSON file the web UI reads back.
 *
 * Provider links carry the account (`username=…&password=…`). Nothing here
 * logs a URL, and every error the web UI can see is a fixed phrase.
 */
class Importer(
    private val dir: File,
    private val open: Opener = HttpOpener(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun interface Opener {
        /** A stream of the body at [url], never more than [maxBytes], or an [ImportError]. */
        fun open(url: String, maxBytes: Long, cancelled: () -> Boolean): InputStream
    }

    /** A failure the viewer may read: a fixed phrase, never a URL or a server's words. */
    class ImportError(message: String) : IOException(message)

    enum class Kind(val key: String) { PLAYLIST("playlist"), GUIDE("guide") }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "coreline-import").apply { isDaemon = true }
    }

    private val lock = Any()
    private var state = "idle"
    private var kind: Kind? = null
    private var message = ""
    private var count = 0
    private var finishedAt = 0L
    @Volatile private var cancelled = false

    val playlistFile get() = File(dir, "playlist.json")
    val guideFile get() = File(dir, "guide.json")

    /** Start a playlist import. False when one is already running or the link is not allowed. */
    fun startPlaylist(url: String): Boolean {
        val safe = SafeUrl.check(url)
        if (!safe.ok) {
            refuse(Kind.PLAYLIST, "That link isn't a web address Core Line can open.")
            return false
        }
        return launch(Kind.PLAYLIST) { importPlaylist(safe.url) }
    }

    /** Start a guide import from up to four links (the playlist's own, or one typed in). */
    fun startGuide(urls: List<String>): Boolean {
        val safe = urls.asSequence().map { SafeUrl.check(it) }.filter { it.ok }.map { it.url }
            .distinct().take(MAX_GUIDE_URLS).toList()
        if (safe.isEmpty()) {
            refuse(Kind.GUIDE, "No guide link Core Line can open.")
            return false
        }
        return launch(Kind.GUIDE) { importGuide(safe) }
    }

    /** `{state, kind, message, count, at}` — state is idle, running, done or error. */
    fun status(): String = synchronized(lock) {
        JSONObject()
            .put("state", state)
            .put("kind", kind?.key ?: "")
            .put("message", message)
            .put("count", count)
            .put("at", finishedAt)
            .toString()
    }

    fun read(kind: Kind): String {
        val f = if (kind == Kind.PLAYLIST) playlistFile else guideFile
        return try { if (f.isFile) f.readText() else "" } catch (_: IOException) { "" }
    }

    /** Forget both imports and stop a running one. */
    fun clear() {
        cancelled = true
        playlistFile.delete()
        guideFile.delete()
        synchronized(lock) {
            if (state != "running") { state = "idle"; kind = null; message = ""; count = 0 }
        }
    }

    fun shutdown() {
        cancelled = true
        executor.shutdownNow()
    }

    private fun launch(k: Kind, job: () -> Int): Boolean {
        synchronized(lock) {
            if (state == "running") return false
            state = "running"; kind = k; message = ""; count = 0
            cancelled = false
        }
        executor.execute {
            try {
                val n = job()
                if (cancelled) finish(k, "idle", "", 0)
                else finish(k, "done", "", n)
            } catch (e: ImportError) {
                // A cancel surfaces as a read failure; it is not an error.
                if (cancelled) finish(k, "idle", "", 0)
                else finish(k, "error", e.message ?: GENERIC, 0)
            } catch (_: SocketTimeoutException) {
                finish(k, "error", "The server took too long to answer.", 0)
            } catch (_: IOException) {
                finish(k, "error", "Couldn't download it. Check the link and your connection.", 0)
            } catch (_: OutOfMemoryError) {
                finish(k, "error", "Too big for this device to read.", 0)
            } catch (_: Exception) {
                // Parser and JSON exceptions quote the input; never pass them on.
                finish(k, "error", GENERIC, 0)
            }
        }
        return true
    }

    /** A start refused up front. Recorded only when idle: never over a running job's status. */
    private fun refuse(k: Kind, msg: String) = synchronized(lock) {
        if (state != "running") { state = "error"; kind = k; message = msg; count = 0; finishedAt = now() }
    }

    private fun finish(k: Kind, s: String, msg: String, n: Int) = synchronized(lock) {
        state = s; kind = k; message = msg; count = n; finishedAt = now()
    }

    /* ---- Playlist -------------------------------------------------------- */

    internal fun importPlaylist(url: String): Int {
        val result = open.open(url, MAX_PLAYLIST_BYTES) { cancelled }.use { raw ->
            val body = ContentGuard(maybeGunzip(raw), MAX_PLAYLIST_TEXT_BYTES, xml = false)
            PlaylistParser.parse(BoundedLineReader(InputStreamReader(body, Charsets.UTF_8))) { cancelled }
        }
        if (cancelled) return 0
        if (result.channels.isEmpty()) {
            throw ImportError(
                if (result.vodSkipped > 0) "That playlist only has movies and series, no live channels."
                else "No channels found. Is that an M3U playlist link?",
            )
        }
        val channels = JSONArray()
        for (c in result.channels) {
            channels.put(
                JSONObject().put("name", c.name).put("url", c.url).put("group", c.group)
                    .put("tvgId", c.tvgId).put("tvgName", c.tvgName),
            )
        }
        val json = JSONObject()
            .put("v", 1)
            .put("fetchedAt", now())
            .put("ok", true)
            .put("count", result.channels.size)
            .put("channels", channels)
            .put("guideUrls", JSONArray(result.guideUrls))
            .put("vodSkipped", result.vodSkipped)
            .put("truncated", result.channels.size >= PlaylistParser.MAX_CHANNELS)
        writeAtomic(playlistFile, json.toString())
        return result.channels.size
    }

    /* ---- Guide ------------------------------------------------------------ */

    internal fun importGuide(urls: List<String>): Int {
        val (wStart, wEnd) = window(now())
        val channels = HashMap<String, String>()
        val programmes = ArrayList<GuideParser.Programme>()
        var truncated = false
        var lastError: Exception? = null
        var parsedAny = false

        val budgetEnd = now() + GUIDE_BUDGET_MS
        for (url in urls) {
            if (cancelled) return 0
            // Never start another link past the budget: the page stops waiting at 7 min.
            if (parsedAny && now() > budgetEnd) { lastError = ImportError("The guide took too long."); break }
            try {
                val room = GuideParser.MAX_PROGRAMMES - programmes.size
                if (room <= 0) { truncated = true; break }
                val r = open.open(url, MAX_GUIDE_BYTES) { cancelled }.use { raw ->
                    GuideParser.parse(ContentGuard(maybeGunzip(raw), MAX_GUIDE_XML_BYTES, xml = true), wStart, wEnd, room) { cancelled }
                }
                parsedAny = true
                for ((id, name) in r.channels) channels.putIfAbsent(id, name)
                programmes.addAll(r.programmes)
                truncated = truncated || r.truncated
            } catch (e: GuideParser.NotAGuide) {
                lastError = ImportError("That link isn't an XMLTV guide.")
            } catch (e: org.xmlpull.v1.XmlPullParserException) {
                lastError = ImportError("The guide file is damaged or incomplete.")
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (cancelled) return 0
        if (!parsedAny) throw lastError ?: ImportError(GENERIC)

        // Keep only the channels a kept programme is on; a guide lists thousands.
        val used = programmes.mapTo(HashSet()) { it.channel }
        val chJson = JSONObject()
        for (id in used) chJson.put(id, JSONObject().put("name", channels[id] ?: id))
        val progJson = JSONArray()
        for (p in programmes.sortedBy { it.start }) {
            val o = JSONObject().put("c", p.channel).put("s", p.start).put("e", p.stop).put("t", p.title)
            if (p.subTitle.isNotEmpty()) o.put("st", p.subTitle)
            if (p.categories.isNotEmpty()) o.put("cat", JSONArray(p.categories))
            if (p.live) o.put("live", true)
            if (p.rerun) o.put("rerun", true)
            progJson.put(o)
        }
        val json = JSONObject()
            .put("v", 1)
            .put("fetchedAt", now())
            .put("windowStart", wStart)
            .put("windowEnd", wEnd)
            .put("truncated", truncated)
            .put("partial", lastError != null)
            .put("channels", chJson)
            .put("programmes", progJson)
        writeAtomic(guideFile, json.toString())
        return programmes.size
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw ImportError("Couldn't save it on this device.")
        }
    }

    companion object {
        const val MAX_GUIDE_URLS = 4
        const val MAX_PLAYLIST_BYTES = 64L * 1024 * 1024
        const val MAX_GUIDE_BYTES = 400L * 1024 * 1024
        /** Decompressed ceilings: a gzip bomb passes the download cap. */
        const val MAX_PLAYLIST_TEXT_BYTES = 256L * 1024 * 1024
        const val MAX_GUIDE_XML_BYTES = 1536L * 1024 * 1024
        private const val GUIDE_BUDGET_MS = 3 * 60_000L
        private const val WINDOW_BEFORE_MS = 4 * 3600_000L
        private const val GENERIC = "Couldn't read it. Check the link and try again."

        /** Same window as guideWindow() in guide.mjs: four hours ago to the end of tomorrow, local. */
        fun window(now: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Pair<Long, Long> {
            val cal = Calendar.getInstance(zone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, 2)
            }
            return (now - WINDOW_BEFORE_MS) to cal.timeInMillis
        }

        /** Guides are served gzipped as often as not, with or without saying so: go by the magic bytes. */
        fun maybeGunzip(raw: InputStream): InputStream {
            val buf = if (raw.markSupported()) raw else BufferedInputStream(raw, 64 * 1024)
            buf.mark(2)
            val b0 = buf.read()
            val b1 = buf.read()
            buf.reset()
            return if (b0 == 0x1f && b1 == 0x8b) BufferedInputStream(GZIPInputStream(buf, 64 * 1024), 64 * 1024) else buf
        }
    }
}

/** HTTP(S) with every redirect hop re-checked by SafeUrl, timeouts, a byte cap and an overall deadline. */
class HttpOpener(
    private val deadlineMs: Long = 150_000L,
) : Importer.Opener {
    override fun open(url: String, maxBytes: Long, cancelled: () -> Boolean): InputStream {
        val deadline = System.currentTimeMillis() + deadlineMs
        var current = url
        repeat(MAX_HOPS + 1) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Encoding", "gzip")
            }
            val code = try { conn.responseCode } catch (e: IOException) { conn.disconnect(); throw e }
            if (code in 300..399) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next == null) throw Importer.ImportError("The server sent a broken redirect.")
                val safe = SafeUrl.check(URL(URL(current), next).toString())
                if (!safe.ok) throw Importer.ImportError("The link redirects somewhere Core Line won't follow.")
                current = safe.url
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw Importer.ImportError(
                    when (code) {
                        401, 403 -> "The provider refused the request (HTTP $code). Check the account in the link."
                        404 -> "Nothing at that link (HTTP 404)."
                        else -> "The server answered HTTP $code."
                    },
                )
            }
            val len = conn.contentLengthLong
            if (len > maxBytes) { conn.disconnect(); throw Importer.ImportError("That file is too large.") }
            return Guarded(conn.inputStream, maxBytes, deadline, cancelled) { conn.disconnect() }
        }
        throw Importer.ImportError("The link redirects too many times.")
    }

    /** Fails the read once past the byte cap or the deadline, and stops on cancel. */
    private class Guarded(
        stream: InputStream,
        private val maxBytes: Long,
        private val deadline: Long,
        private val cancelled: () -> Boolean,
        private val onClose: () -> Unit,
    ) : FilterInputStream(stream) {
        private var total = 0L

        private fun check(n: Int): Int {
            if (n > 0) total += n
            if (total > maxBytes) throw Importer.ImportError("That file is too large.")
            if (System.currentTimeMillis() > deadline) throw Importer.ImportError("The download took too long.")
            if (cancelled()) throw Importer.ImportError("Cancelled.")
            return n
        }

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) check(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = check(super.read(b, off, len))

        override fun close() {
            try { super.close() } finally { onClose() }
        }
    }

    companion object {
        private const val MAX_HOPS = 5
        private const val USER_AGENT = "CoreLine/1.0 (+https://github.com/brevityA/CoreBuildsApps)"
    }
}
