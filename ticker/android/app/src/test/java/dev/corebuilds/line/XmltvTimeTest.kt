package dev.corebuilds.line

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class XmltvTimeTest {
    private val t = 1_790_000_000_000L // any fixed instant

    @Test
    fun offsetsAreAppliedToUtc() {
        // 2026-09-29 19:30 in London (BST, +0100) is 18:30 UTC.
        assertEquals(XmltvTime.parse("20260929183000 +0000"), XmltvTime.parse("20260929193000 +0100"))
        assertEquals(XmltvTime.parse("20260929183000 +0000"), XmltvTime.parse("20260929143000 -0400"))
        assertEquals(XmltvTime.parse("20260929183000 +0000"), XmltvTime.parse("20260930000000 +0530"))
        assertEquals(XmltvTime.parse("20260929183000 +0000"), XmltvTime.parse("20260929193000+01:00"))
    }

    @Test
    fun missingOffsetIsUtcAndShortFormsWork() {
        assertEquals(XmltvTime.parse("20260929183000 +0000"), XmltvTime.parse("20260929183000"))
        assertEquals(XmltvTime.parse("20260929180000 +0000"), XmltvTime.parse("202609291800"))
        assertEquals(XmltvTime.parse("20260929000000 +0000"), XmltvTime.parse("20260929"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(XmltvTime.parse(null))
        assertNull(XmltvTime.parse(""))
        assertNull(XmltvTime.parse("tomorrow"))
        assertNull(XmltvTime.parse("20261329183000 +0000"))
        assertNull(XmltvTime.parse("20260929253000 +0000"))
    }

    @Test
    fun windowEndsAtLocalMidnightAfterTomorrow() {
        val zones = listOf("UTC", "Europe/London", "America/Toronto", "Asia/Kolkata", "Pacific/Auckland")
        for (id in zones) {
            val zone = TimeZone.getTimeZone(id)
            val (start, end) = Importer.window(t, zone)
            assertEquals(t - 4 * 3600_000L, start)
            val cal = java.util.Calendar.getInstance(zone).apply { timeInMillis = end }
            assertEquals(id, 0, cal.get(java.util.Calendar.HOUR_OF_DAY))
            assertEquals(id, 0, cal.get(java.util.Calendar.MINUTE))
            val hours = (end - t) / 3600_000.0
            assertEquals(id, true, hours > 24.0 && hours <= 49.0)
        }
    }
}
