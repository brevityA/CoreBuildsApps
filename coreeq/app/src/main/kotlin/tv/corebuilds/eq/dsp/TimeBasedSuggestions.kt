package tv.corebuilds.eq.dsp

/**
 * Time-based intelligence (1.11.0): suggests night mode during evening and
 * late-night hours so loud passages don't startle the household.
 *
 * Pure Kotlin, unit-tested without Android. The clock is injected so tests
 * can run at any hour.
 */
object TimeBasedSuggestions {

    /** The result of a time-based check. */
    data class Suggestion(
        /** EVENING (relaxed listening) or NIGHT (aggressive compression). */
        val kind: Kind,
        /** A short user-facing message, e.g. "It's 22:30 — try night mode?" */
        val message: String,
        /** The hour this period started (0-23). */
        val startHour: Int,
        /** The hour this period ends (0-23). */
        val endHour: Int
    ) {
        enum class Kind { EVENING, NIGHT }
    }

    /** Default evening window: 19:00–22:00 local time. */
    private const val DEFAULT_EVENING_START = 19
    private const val DEFAULT_EVENING_END = 22
    /** Default night window: 22:00–07:00 local time. */
    private const val DEFAULT_NIGHT_START = 22
    private const val DEFAULT_NIGHT_END = 7

    /**
     * Returns a [Suggestion] if [nowMs] falls inside the evening or night
     * window, otherwise null. [clockMillis] defaults to the system clock.
     */
    fun suggestionAt(
        nowMs: Long = System.currentTimeMillis(),
        eveningStart: Int = DEFAULT_EVENING_START,
        eveningEnd: Int = DEFAULT_EVENING_END,
        nightStart: Int = DEFAULT_NIGHT_START,
        nightEnd: Int = DEFAULT_NIGHT_END
    ): Suggestion? {
        val hour = hourOf(nowMs)

        // Night window can wrap past midnight (e.g. 22:00–07:00).
        val inNight = if (nightStart < nightEnd) {
            hour in nightStart until nightEnd
        } else {
            hour >= nightStart || hour < nightEnd
        }
        if (inNight) {
            return Suggestion(
                kind = Suggestion.Kind.NIGHT,
                message = "It's ${formatHour(hour)} — night mode compresses loud passages for late viewing.",
                startHour = nightStart,
                endHour = nightEnd
            )
        }

        val inEvening = hour in eveningStart until eveningEnd
        if (inEvening) {
            return Suggestion(
                kind = Suggestion.Kind.EVENING,
                message = "It's ${formatHour(hour)} — evening listening: try night mode for quieter dynamics.",
                startHour = eveningStart,
                endHour = eveningEnd
            )
        }

        return null
    }

    private fun hourOf(nowMs: Long): Int {
        // Local time via the system default time zone; offset in ms from UTC.
        val offsetMs = java.util.TimeZone.default.rawOffset.toLong() +
            (if (java.util.Calendar.getInstance(java.util.TimeZone.default)
                .inDaylightTime(java.util.Date(nowMs))) java.util.TimeZone.default.dstSavings else 0L)
        val localMs = nowMs + offsetMs
        val localSec = localMs / 1000L
        val hour = ((localSec % 86400L + 86400L) % 86400L) / 3600L
        return hour.toInt()
    }

    private fun formatHour(hour: Int): String =
        "%02d:00".format(java.util.Locale.US, hour)
}
