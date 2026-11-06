package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceNameTest {

    @Test
    fun `a model that starts with the maker is not given the maker twice`() {
        // The first supporter test: the phone read "Connected to onn onn 4K Pro Streaming Device".
        assertEquals("onn 4K Pro Streaming Device", DeviceName.of("onn", "onn 4K Pro Streaming Device"))
        assertEquals("Xiaomi 13T", DeviceName.of("Xiaomi", "Xiaomi 13T"))
        assertEquals("TCL-Smart-TV", DeviceName.of("TCL", "TCL-Smart-TV"))
        assertEquals("onn 4K Pro", DeviceName.of("ONN", "onn 4K Pro"))
        assertEquals("onn", DeviceName.of("onn", "onn"))
    }

    @Test
    fun `other makers and models are joined as before`() {
        assertEquals("Google Pixel 9 Pro XL", DeviceName.of("Google", "Pixel 9 Pro XL"))
        assertEquals("samsung SM-S921U", DeviceName.of("samsung", "SM-S921U"))
        assertEquals("NVIDIA SHIELD Android TV", DeviceName.of("NVIDIA", "SHIELD Android TV"))
        assertEquals("Amazon AFTKA", DeviceName.of("Amazon", "AFTKA"))
    }

    @Test
    fun `the maker is dropped only when it is a whole word at the start`() {
        assertEquals("LG LGTV", DeviceName.of("LG", "LGTV"))
        assertEquals("Sony Sonyx 9", DeviceName.of("Sony", "Sonyx 9"))
    }

    @Test
    fun `blank parts and stray spaces leave no gaps`() {
        assertEquals("SM-S921U", DeviceName.of("", "SM-S921U"))
        assertEquals("onn", DeviceName.of("onn", "  "))
        assertEquals("", DeviceName.of(null, null))
        assertEquals("Google Pixel 9", DeviceName.of(" Google ", " Pixel 9 "))
    }
}
