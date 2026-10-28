package tv.corebuilds.iconpack

import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The night-hours arithmetic: no Android behind it, so plain JUnit. */
class DayNightTest {

    private lateinit var saved: TimeZone

    @Before fun pinZone() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(saved)

    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 22, hour, minute)
    }.timeInMillis

    @Test fun nightCrossesMidnight() {
        val window = 20 to 7
        assertTrue(DayNight.isNight(20, window))
        assertTrue(DayNight.isNight(23, window))
        assertTrue(DayNight.isNight(0, window))
        assertTrue(DayNight.isNight(6, window))
        assertFalse(DayNight.isNight(7, window))
        assertFalse(DayNight.isNight(12, window))
        assertFalse(DayNight.isNight(19, window))
    }

    @Test fun nightWithinOneDay() {
        val window = 1 to 5
        assertTrue(DayNight.isNight(1, window))
        assertTrue(DayNight.isNight(4, window))
        assertFalse(DayNight.isNight(5, window))
        assertFalse(DayNight.isNight(0, window))
    }

    @Test fun everyWindowIsANightOfSensibleLength() {
        for (window in DayNight.WINDOWS) {
            val hours = (0..23).count { DayNight.isNight(it, window) }
            assertTrue("$window is $hours h", hours in 8..12)
        }
    }

    @Test fun switchIsTheNearestBoundaryAhead() {
        val window = 20 to 7
        assertEquals(30 * 60_000L, DayNight.msUntilSwitch(at(19, 30), window))
        assertEquals(60 * 60_000L, DayNight.msUntilSwitch(at(6, 0), window))
        // Exactly on a boundary, the next one is the other end of the night.
        assertEquals(11 * 3_600_000L, DayNight.msUntilSwitch(at(20, 0), window))
        // After the night starts, the next boundary is tomorrow morning.
        assertEquals(8 * 3_600_000L, DayNight.msUntilSwitch(at(23, 0), window))
    }

    @Test fun nightLoopCyclesThroughOffAndBackToOff() {
        val onTv = listOf("a", "b")
        assertEquals("a", DayNight.nextNightLoop(null, onTv))
        assertEquals("b", DayNight.nextNightLoop("a", onTv))
        assertNull(DayNight.nextNightLoop("b", onTv))
        // A loop no longer on the TV restarts the cycle at off.
        assertNull(DayNight.nextNightLoop("gone", onTv))
    }
}
