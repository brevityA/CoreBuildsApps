package dev.corebuilds.line

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Streams an XMLTV guide down to the part Core Line uses.
 *
 * A provider's guide is routinely 100–300 MB for a week of every channel. The
 * board needs two days of live sport, so this keeps a programme only when it
 * is in the window and looks like sport (a sport category, or marked live) —
 * a coarse cut. The precise judgement, and matching guide channels to the
 * viewer's playlist, stays in ticker/lib/guide.mjs, which reads the result.
 *
 * Nothing but the kept programmes is ever held in memory.
 */
object GuideParser {
    const val MAX_PROGRAMMES = 12_000
    private const val MAX_CHANNELS = 50_000
    private const val MAX_TEXT = 160
    private const val MAX_CATS = 4
    private const val MAX_ID = 128

    data class Programme(
        val channel: String,
        val start: Long,
        val stop: Long,
        val title: String,
        val subTitle: String,
        val categories: List<String>,
        val live: Boolean,
        val rerun: Boolean,
    )

    class Result(
        /** Every channel in the file: id → display name. Trimmed to the kept ones by [Guide]. */
        val channels: Map<String, String>,
        val programmes: List<Programme>,
        val truncated: Boolean,
        /** Programmes read, kept or not — a guide with none is not a guide. */
        val seen: Int,
    )

    class NotAGuide : Exception("not an XMLTV guide")

    // Same vocabulary as SPORT_CATEGORY in guide.mjs; that module re-checks.
    private val SPORT = Regex(
        """\b(?:sport|sports|football|soccer|rugby|cricket|tennis|golf|basketball|baseball|hockey|boxing|mma|ufc|wrestling|motorsport|formula|racing|cycling|athletics|darts|snooker|volleyball|lacrosse|nfl|nba|mlb|nhl|ncaa)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val LIVE_TITLE = Regex("""^\s*(?:live\b|\(live\))""", RegexOption.IGNORE_CASE)

    fun parse(
        input: InputStream,
        windowStart: Long,
        windowEnd: Long,
        maxProgrammes: Int = MAX_PROGRAMMES,
        cancelled: () -> Boolean = { false },
    ): Result {
        val xpp = XmlPullParserFactory.newInstance().newPullParser()
        xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        // null: honour the <?xml encoding?> declaration, UTF-8 otherwise.
        xpp.setInput(input, null)

        val channels = HashMap<String, String>()
        // Past the cap, keep the programmes that start soonest: a guide sorted
        // by channel would otherwise lose whole channels, not far-off slots.
        val programmes = java.util.PriorityQueue<Programme>(64, compareByDescending { it.start })
        var truncated = false
        var seen = 0
        var sawRoot = false

        var event = xpp.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (xpp.name) {
                    "tv" -> sawRoot = true
                    "channel" -> readChannel(xpp)?.let { (id, name) ->
                        if (id !in channels && channels.size < MAX_CHANNELS) channels[id] = name
                    }
                    "programme" -> {
                        seen += 1
                        if (seen % 512 == 0 && cancelled()) break
                        val p = readProgramme(xpp, windowStart, windowEnd)
                        if (p != null) {
                            if (programmes.size < maxProgrammes) {
                                programmes.add(p)
                            } else {
                                truncated = true
                                if (p.start < programmes.peek()!!.start) { programmes.poll(); programmes.add(p) }
                            }
                        }
                    }
                }
            }
            event = xpp.next()
        }
        if (!sawRoot) throw NotAGuide()
        return Result(channels, programmes.sortedBy { it.start }, truncated, seen)
    }

    /** `<channel id="…"><display-name>…</display-name>…</channel>`: the id and its first name. */
    private fun readChannel(xpp: XmlPullParser): Pair<String, String>? {
        val id = xpp.getAttributeValue(null, "id")?.trim()?.take(MAX_ID).orEmpty()
        var name = ""
        val depth = xpp.depth
        while (true) {
            val ev = xpp.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && xpp.depth == depth) break
            if (ev == XmlPullParser.START_TAG && xpp.name == "display-name" && name.isEmpty()) {
                name = clip(readText(xpp))
            }
        }
        return if (id.isEmpty()) null else id to name.ifEmpty { id }
    }

    /**
     * One `<programme>`, or null when it is outside the window or not sport.
     * The window test runs on the attributes, before any child is read into a
     * string, so the bulk of a large guide is skipped rather than parsed.
     */
    private fun readProgramme(xpp: XmlPullParser, windowStart: Long, windowEnd: Long): Programme? {
        val channel = xpp.getAttributeValue(null, "channel")?.trim()?.take(MAX_ID).orEmpty()
        val start = XmltvTime.parse(xpp.getAttributeValue(null, "start"))
        // A programme without a stop runs until the next one; two hours is a
        // fair game-length guess and guide.mjs only uses it for "on now".
        val stop = XmltvTime.parse(xpp.getAttributeValue(null, "stop")) ?: start?.plus(2 * 3600_000L)
        val inWindow = channel.isNotEmpty() && start != null && stop != null &&
            stop > start && stop > windowStart && start < windowEnd
        if (!inWindow) {
            skip(xpp)
            return null
        }

        var title = ""
        var subTitle = ""
        val cats = ArrayList<String>(2)
        var live = false
        var rerun = false
        val depth = xpp.depth
        while (true) {
            val ev = xpp.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && xpp.depth == depth) break
            if (ev != XmlPullParser.START_TAG || xpp.depth != depth + 1) continue
            when (xpp.name) {
                "title" -> if (title.isEmpty()) title = clip(readText(xpp)) else skip(xpp)
                "sub-title" -> if (subTitle.isEmpty()) subTitle = clip(readText(xpp)) else skip(xpp)
                "category" -> {
                    val c = clip(readText(xpp))
                    if (c.isNotEmpty() && cats.size < MAX_CATS && c !in cats) cats.add(c)
                }
                "live" -> { live = true; skip(xpp) }
                "previously-shown" -> { rerun = true; skip(xpp) }
                else -> skip(xpp)
            }
        }
        if (title.isEmpty()) return null
        if (!live && LIVE_TITLE.containsMatchIn(title)) live = true
        val sporty = live || cats.any { SPORT.containsMatchIn(it) }
        if (!sporty) return null
        return Programme(channel, start!!, stop!!, title, subTitle, cats, live, rerun)
    }

    /** Text content of the current element, children flattened; leaves the parser on its end tag. */
    private fun readText(xpp: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = xpp.depth
        while (true) {
            val ev = xpp.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && xpp.depth == depth) break
            if ((ev == XmlPullParser.TEXT || ev == XmlPullParser.ENTITY_REF) && sb.length < MAX_TEXT * 2) {
                sb.append(xpp.text ?: "")
            }
        }
        return sb.toString()
    }

    /** Skip the current element and everything in it; leaves the parser on its end tag. */
    private fun skip(xpp: XmlPullParser) {
        if (xpp.eventType != XmlPullParser.START_TAG) return
        var level = 1
        while (level > 0) {
            when (xpp.next()) {
                XmlPullParser.START_TAG -> level += 1
                XmlPullParser.END_TAG -> level -= 1
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private val SPACES = Regex("\\s+")
    private fun clip(s: String): String = s.replace(SPACES, " ").trim().take(MAX_TEXT)
}
