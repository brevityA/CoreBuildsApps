package tv.corebuilds.eq.apply

/**
 * One audio session read out of a system audio dump.
 *
 * The fields are only what the dump states: [sessionId] and [uid] identify the
 * stream, [usage] is the `AudioAttributes.USAGE_*` value when the dump names
 * one, [active] is what the dump says about playback (null when it says
 * nothing). No package names are held here — uid → package is the Android
 * layer's job — and no dump text is ever stored.
 */
data class DiscoveredSession(
    val sessionId: Int,
    val uid: Int? = null,
    val pid: Int? = null,
    val usage: Int? = null,
    val active: Boolean? = null
) {
    /** Field-wise merge: [other]'s non-null facts win, this one's survive the gaps. */
    internal fun filledFrom(other: DiscoveredSession) = DiscoveredSession(
        sessionId = sessionId,
        uid = other.uid ?: uid,
        pid = other.pid ?: pid,
        usage = other.usage ?: usage,
        active = other.active ?: active
    )
}

/**
 * Parses `dumpsys media.audio_flinger` and `dumpsys audio` into sessions.
 *
 * Firmware variance is the parser's problem, not the user's, so two shape
 * families are read and merged:
 *
 * 1. **Key-value rows** — the classic `Clients:` / PlayerBase lines:
 *    `pid: 24798, uid: 10234, session: 392, type: 3, state: 2, usage: 1`,
 *    `state: started`, `usage: media`, `sessionId`/`session_id` spellings.
 * 2. **The fixed-width Tracks table** of modern AudioFlinger dumps —
 *    `Type Id Active Client(pid/uid) Session Port Id S Flags … ST Usg CT …`
 *    — parsed by anchoring each row on its `pid/uid` token and shifting the
 *    column indices by the row's own alignment, because the `Type` value is
 *    not always printed and columns would drift by one otherwise.
 *
 * A line the parser cannot read is skipped; it never guesses. The rules that
 * decide what may be attached live in [attachable].
 *
 * Pure JVM on purpose: [DumpsysSessionsTest] pins every rule and shape, and
 * the fixtures there are representative shapes compiled from public AOSP dump
 * documentation until real captures replace them (plan M6a/M8 device runs).
 */
object DumpsysSessions {

    // AudioAttributes.USAGE_*, spelled out so this file stays pure JVM and the
    // numbers cannot drift from the platform. Pinned to the platform values in
    // DumpsysSessionsTest.
    const val USAGE_UNKNOWN = 0
    const val USAGE_MEDIA = 1
    const val USAGE_VOICE_COMMUNICATION = 2
    const val USAGE_ALARM = 4
    const val USAGE_NOTIFICATION = 5
    const val USAGE_SONIFICATION = 13
    const val USAGE_GAME = 14
    const val USAGE_VIRTUAL_SOURCE = 15
    const val USAGE_ASSISTANT = 16

    /** A key whose value follows it: `session: 392`, `uid=10234`. */
    private val KEY = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*[:=]""")

    /** The `pid/uid` column of a Tracks table row, sometimes printed `pid/ uid`. */
    private val CLIENT_TOKEN = Regex("""^\d+/\d+$""")
    private val CLIENT_SPLIT = Regex("""(\d+)/\s+(\d+)""")

    private val MULTIWORD_COLUMNS = listOf(
        "Port Id" to "PortId", "Chn mask" to "ChnMask",
        "G db" to "Gdb", "L dB" to "LdB", "R dB" to "RdB",
        "VS dB" to "VSdB", "PortVol dB" to "PortVoldB"
    )

    /** Every session the dumps mention, merged across sources, deduplicated by session id. */
    fun parse(text: String): List<DiscoveredSession> {
        val byId = LinkedHashMap<Int, DiscoveredSession>()
        var columns: Map<String, Int>? = null
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            val header = parseHeader(line)
            if (header != null) {
                columns = header
                continue
            }
            val row = parseTableRow(line, columns) ?: parseKeyValueLine(line)
            if (row != null) {
                byId[row.sessionId] = byId[row.sessionId]?.filledFrom(row) ?: row
            }
        }
        return byId.values.toList()
    }

    /** Merges per-dump lists into one table, one row per session id. */
    fun merge(vararg lists: List<DiscoveredSession>): List<DiscoveredSession> {
        val byId = LinkedHashMap<Int, DiscoveredSession>()
        for (list in lists) for (s in list) {
            byId[s.sessionId] = byId[s.sessionId]?.filledFrom(s) ?: s
        }
        return byId.values.toList()
    }

    /**
     * Sessions the correction may attach to: a real session, not our own uid
     * (the measurement sweep must never correct itself), and named as media or
     * game — or nameless.
     *
     * The nameless case is deliberate and narrow: a dump that prints no usage
     * column at all (old firmware) cannot be *shown* to be non-media, and
     * excluding it would make the mode dead on exactly the sets that need it.
     * A dump that *names* a non-media usage (voice, notifications, alarms) is
     * believed and excluded. An unrecognised usage word counts as named.
     */
    fun attachable(sessions: List<DiscoveredSession>, ourUid: Int): List<DiscoveredSession> =
        sessions.filter { s ->
            s.sessionId > 0 &&
                s.uid != null &&
                s.uid != ourUid &&
                attachableUsage(s.usage)
        }

    private fun attachableUsage(usage: Int?): Boolean =
        usage == null ||
            usage == USAGE_UNKNOWN ||
            usage == USAGE_MEDIA ||
            usage == USAGE_GAME

    // ------------------------------------------------------------------
    // Shape 2: the fixed-width Tracks table.

    /** Column index map when [line] is a Tracks header, else null. */
    private fun parseHeader(line: String): Map<String, Int>? {
        // Key-value rows never match: their tokens carry punctuation
        // ("session:") and have no pid/uid column, so this cannot swallow them.
        var normalized = line
        for ((multi, one) in MULTIWORD_COLUMNS) normalized = normalized.replace(multi, one)
        val tokens = normalized.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val session = tokens.indexOfFirst { it.equals("Session", ignoreCase = true) }
        val client = tokens.indexOfFirst {
            it.contains("pid/uid", ignoreCase = true) || it.equals("Client", ignoreCase = true)
        }
        if (session < 0 || client < 0) return null
        val map = mutableMapOf("session" to session, "client" to client)
        tokens.indexOfFirst { it.equals("Usg", ignoreCase = true) || it.equals("Usage", ignoreCase = true) }
            .takeIf { it >= 0 }?.let { map["usage"] = it }
        tokens.indexOfFirst { it.equals("Active", ignoreCase = true) }
            .takeIf { it >= 0 }?.let { map["active"] = it }
        return map
    }

    private fun parseTableRow(line: String, columns: Map<String, Int>?): DiscoveredSession? {
        if (columns == null || line.isBlank()) return null
        val tokens = CLIENT_SPLIT.replace(line.trim(), "$1/$2").split(Regex("""\s+"""))
            .filter { it.isNotEmpty() }
        val anchor = tokens.indexOfFirst { CLIENT_TOKEN.matches(it) }
        if (anchor < 0) return null
        val shift = anchor - columns.getValue("client")

        fun at(name: String): String? = columns[name]?.let { tokens.getOrNull(it + shift) }

        val session = at("session")?.toIntOrNull() ?: return null
        val client = at("client") ?: tokens[anchor]
        val active = when (at("active")?.lowercase()) {
            "yes", "true" -> true
            "no", "false" -> false
            else -> null
        }
        return DiscoveredSession(
            sessionId = session,
            uid = client.substringAfter('/').toIntOrNull(),
            pid = client.substringBefore('/').toIntOrNull(),
            usage = usageValue(at("usage")),
            active = active
        )
    }

    // ------------------------------------------------------------------
    // Shape 1: key-value rows.

    private fun parseKeyValueLine(line: String): DiscoveredSession? {
        // Split at key positions so a value that looks like the next key
        // ("Clients: session: 42") can never swallow it.
        val keys = KEY.findAll(line).toList()
        if (keys.isEmpty()) return null
        val map = mutableMapOf<String, String>()
        for ((i, match) in keys.withIndex()) {
            val valueStart = match.range.last + 1
            val valueEnd = keys.getOrNull(i + 1)?.range?.first ?: line.length
            val value = line.substring(valueStart, valueEnd).trim().trimEnd(',', ';').trim()
            if (value.isNotEmpty()) map[match.groupValues[1].lowercase()] = value
        }

        fun key(vararg names: String): String? = names.firstNotNullOfOrNull { map[it] }

        val session = (key("session", "sessionid", "session_id") ?: return null)
            .trim().toIntOrNull() ?: return null
        val uid = key("uid")?.toIntOrNull()
        val pid = key("pid")?.toIntOrNull()
        if (uid == null && pid == null) return null
        val active = when (key("state", "active")?.lowercase()) {
            "started", "playing", "active", "yes", "true" -> true
            "paused", "idle", "stopped", "released", "no", "false" -> false
            else -> null
        }
        return DiscoveredSession(
            sessionId = session,
            uid = uid,
            pid = pid,
            usage = usageValue(key("usage", "usg")),
            active = active
        )
    }

    /**
     * A usage field is either a number or a word. A word that names a usage we
     * do not recognise is [USAGE_EXCLUDED]: present, but not proven media —
     * silence (null) is the only thing treated as unproven-but-attachable.
     */
    private fun usageValue(raw: String?): Int? {
        if (raw == null) return null
        raw.trim().toIntOrNull()?.let { return it }
        return when (raw.trim().lowercase()
            .removePrefix("usage_")
            .removePrefix("android.media.audioattributes.usage_")) {
            "media" -> USAGE_MEDIA
            "game" -> USAGE_GAME
            "unknown" -> USAGE_UNKNOWN
            "voice_communication", "voicecommunication" -> USAGE_VOICE_COMMUNICATION
            "alarm" -> USAGE_ALARM
            "notification" -> USAGE_NOTIFICATION
            "sonification" -> USAGE_SONIFICATION
            "virtual_source", "virtualsource" -> USAGE_VIRTUAL_SOURCE
            "assistant" -> USAGE_ASSISTANT
            else -> USAGE_EXCLUDED
        }
    }

    /** A usage the dump named but the parser does not recognise: never attachable. */
    internal const val USAGE_EXCLUDED = -1
}
