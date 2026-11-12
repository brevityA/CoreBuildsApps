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
        assertEquals("A A1", DeviceName.of("A", "A1"))
        // Only a prefix is a duplicate. A maker later in the model still belongs there.
        assertEquals("onn Streaming onn device", DeviceName.of("onn", "Streaming onn device"))
        // Equivalent accents are not assumed to be the same raw prefix. Keeping both
        // names is conservative; dropping the maker would change the model's identity.
        assertEquals("A Á Model", DeviceName.of("A", "Á Model"))
    }

    @Test
    fun `every kind of combining mark continues the word it follows`() {
        // Kotlin's isLetterOrDigit() is false for marks. All three Unicode mark
        // categories must be excluded from the word-break decision, not just Mn.
        for (mark in listOf(
            "\u0301", // Mn: combining acute accent
            "\u0903", // Mc: Devanagari sign visarga
            "\u20dd", // Me: combining enclosing circle
            "\u0301\u0327" // a sequence of marks is still part of the word
        )) {
            val model = "A${mark} Model"
            assertEquals("A $model", DeviceName.of("A", model))
        }
    }

    @Test
    fun `blank parts and stray spaces leave no gaps`() {
        assertEquals("SM-S921U", DeviceName.of("", "SM-S921U"))
        assertEquals("SM-S921U", DeviceName.of(null, "SM-S921U"))
        assertEquals("onn", DeviceName.of("onn", "  "))
        assertEquals("onn", DeviceName.of("onn", null))
        assertEquals("", DeviceName.of(null, null))
        assertEquals("", DeviceName.of(" \t ", "\n"))
        assertEquals("Google Pixel 9", DeviceName.of(" Google ", " Pixel 9 "))
    }
}
