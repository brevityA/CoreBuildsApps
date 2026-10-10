package tv.corebuilds.eq.ui

import io.nayuki.qrcodegen.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Support screen's two codes, through the encoder that draws them.
 *
 * A code is only as readable as its modules are big. At 220dp, a version 3
 * code (29 modules, 37 with the quiet zone) gets about 6dp a module, which a
 * phone reads from the sofa. A longer support URL would quietly move to
 * version 4 or 5 and shrink every module; this fails first. The URLs are read
 * from strings.xml, the file the screen reads, so the test cannot pass on a
 * copy of them.
 */
class SupportQrTest {

    // Gradle runs unit tests with the module directory as the working directory.
    private val strings = File("src/main/res/values/strings.xml").readText()

    private fun url(name: String): String {
        val match = Regex("<string name=\"$name\">([^<]+)</string>").find(strings)
        assertTrue("strings.xml has no $name", match != null)
        return match!!.groupValues[1]
    }

    @Test
    fun bothCodesAreVersionThree() {
        for (name in listOf("support_sponsors_url", "support_kofi_url")) {
            val url = url(name)
            assertTrue("$name must be https: $url", url.startsWith("https://"))
            val qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM)
            assertEquals("$name ($url) encodes at version ${qr.version}", 29, qr.size)
        }
    }
}
