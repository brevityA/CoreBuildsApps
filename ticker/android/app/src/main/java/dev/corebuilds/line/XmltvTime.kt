package dev.corebuilds.line

import java.util.Calendar
import java.util.TimeZone

/**
 * XMLTV timestamps: `YYYYMMDDhhmmss +hhmm`, where everything after the date is
 * optional and a missing offset means UTC (the XMLTV DTD's rule). Returns UTC
 * milliseconds, or null for anything that is not one.
 */
object XmltvTime {
    private val PATTERN = Regex("""^(\d{4})(\d{2})(\d{2})(\d{2})?(\d{2})?(\d{2})?\s*(?:([+-])(\d{2}):?(\d{2}))?""")

    fun parse(raw: String?): Long? {
        val m = PATTERN.find(raw?.trim() ?: return null) ?: return null
        val g = m.groupValues
        val year = g[1].toInt()
        val month = g[2].toInt()
        val day = g[3].toInt()
        val hour = g[4].ifEmpty { "0" }.toInt()
        val minute = g[5].ifEmpty { "0" }.toInt()
        val second = g[6].ifEmpty { "0" }.toInt()
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            isLenient = false
            set(year, month - 1, day, hour, minute, second)
        }
        var ms = try { cal.timeInMillis } catch (_: IllegalArgumentException) { return null } // 31 February
        if (g[7].isNotEmpty()) {
            val offH = g[8].toInt()
            val offM = g[9].toInt()
            if (offH > 14 || offM > 59) return null
            val off = (offH * 60 + offM) * 60_000L
            ms -= if (g[7] == "+") off else -off
        }
        return ms
    }
}
