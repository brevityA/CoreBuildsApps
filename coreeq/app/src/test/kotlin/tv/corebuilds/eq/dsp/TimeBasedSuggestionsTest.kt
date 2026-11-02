package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeBasedSuggestionsTest {

    private val oneHourMs = 3600_000L

    // Fixed reference: 2026-01-15 00:00:00 UTC. Add hour offsets to test any time.
    private fun msAtUtcHour(hour: Int): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(2026, 0, 15, hour, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `evening window returns an evening suggestion`() {
        val s = requireNotNull(TimeBasedSuggestions.suggestionAt(msAtUtcHour(20), 19, 22, 22, 7))
        assertEquals(TimeBasedSuggestions.Suggestion.Kind.EVENING, s.kind)
        assertEquals(19, s.startHour)
        assertEquals(22, s.endHour)
    }

    @Test
    fun `night window returns a night suggestion`() {
        val s = requireNotNull(TimeBasedSuggestions.suggestionAt(msAtUtcHour(23), 19, 22, 22, 7))
        assertEquals(TimeBasedSuggestions.Suggestion.Kind.NIGHT, s.kind)
        assertEquals(22, s.startHour)
        assertEquals(7, s.endHour)
    }

    @Test
    fun `night window wraps past midnight`() {
        val s = requireNotNull(TimeBasedSuggestions.suggestionAt(msAtUtcHour(3), 19, 22, 22, 7))
        assertEquals(TimeBasedSuggestions.Suggestion.Kind.NIGHT, s.kind)
    }

    @Test
    fun `noon returns no suggestion`() {
        assertNull(TimeBasedSuggestions.suggestionAt(msAtUtcHour(12), 19, 22, 22, 7))
    }

    @Test
    fun `boundary hours are exclusive`() {
        // 19:00 is evening start (included)
        assertNotNull(TimeBasedSuggestions.suggestionAt(msAtUtcHour(19), 19, 22, 22, 7))
        // 22:00 is evening end (excluded) but night start (included)
        val at22 = requireNotNull(TimeBasedSuggestions.suggestionAt(msAtUtcHour(22), 19, 22, 22, 7))
        assertEquals(TimeBasedSuggestions.Suggestion.Kind.NIGHT, at22.kind)
        // 7:00 is night end (excluded)
        assertNull(TimeBasedSuggestions.suggestionAt(msAtUtcHour(7), 19, 22, 22, 7))
    }

    @Test
    fun `custom windows work`() {
        // 17:00-20:00 evening, 20:00-06:00 night
        assertEquals(
            TimeBasedSuggestions.Suggestion.Kind.EVENING,
            TimeBasedSuggestions.suggestionAt(msAtUtcHour(18), 17, 20, 20, 6)?.kind
        )
        assertEquals(
            TimeBasedSuggestions.Suggestion.Kind.NIGHT,
            TimeBasedSuggestions.suggestionAt(msAtUtcHour(21), 17, 20, 20, 6)?.kind
        )
        assertNull(TimeBasedSuggestions.suggestionAt(msAtUtcHour(10), 17, 20, 20, 6))
    }

    @Test
    fun `message mentions the hour`() {
        val s = TimeBasedSuggestions.suggestionAt(msAtUtcHour(20), 19, 22, 22, 7)!!
        assertTrue(s.message.contains("20:00"))
    }
}
