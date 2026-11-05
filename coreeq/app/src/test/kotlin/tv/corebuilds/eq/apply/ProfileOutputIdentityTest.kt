package tv.corebuilds.eq.apply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileOutputIdentityTest {
    @Test
    fun `a specific Bluetooth name is normalized without changing its identity`() {
        assertEquals("my headphones", ProfileOutputIdentity.specificName("Bluetooth", "  My Headphones  "))
        assertEquals("my headphones", ProfileOutputIdentity.specificName("Bluetooth", "MY HEADPHONES"))
    }

    @Test
    fun `blank or generic Bluetooth labels do not identify a device`() {
        for (name in listOf(null, "", "  ", "Bluetooth", "  BLUETOOTH  ")) {
            assertNull(ProfileOutputIdentity.specificName("Bluetooth", name))
            assertTrue(ProfileOutputIdentity.needsDeviceName("Bluetooth", name, listOf("My Headphones")))
        }
    }

    @Test
    fun `even a single named profile cannot identify a generically reported Bluetooth device`() {
        assertTrue(ProfileOutputIdentity.needsDeviceName("Bluetooth", "Bluetooth", listOf("My Headphones")))
        assertTrue(ProfileOutputIdentity.needsDeviceName("Bluetooth", null, listOf("Headphones A", "Headphones B")))
    }

    @Test
    fun `a named but unmeasured Bluetooth device is not confused with missing identity`() {
        assertFalse(ProfileOutputIdentity.needsDeviceName("Bluetooth", "New Headphones", listOf("Old Headphones")))
    }

    @Test
    fun `an existing generic Bluetooth profile keeps its documented matching behavior`() {
        for (generic in listOf(null, "", "Bluetooth", "  bluetooth  ")) {
            assertFalse(ProfileOutputIdentity.needsDeviceName("Bluetooth", "Bluetooth", listOf("My Headphones", generic)))
        }
    }

    @Test
    fun `having no same-kind profile is a missing profile rather than missing device identity`() {
        assertFalse(ProfileOutputIdentity.needsDeviceName("Bluetooth", "Bluetooth", emptyList()))
    }
}
