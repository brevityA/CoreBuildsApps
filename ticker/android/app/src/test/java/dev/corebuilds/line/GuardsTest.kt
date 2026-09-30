package dev.corebuilds.line

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GuardsTest {
    private fun drain(s: InputStream) { val buf = ByteArray(8192); while (s.read(buf) >= 0) Unit }

    private fun rejects(bytes: ByteArray, xml: Boolean, max: Long = Long.MAX_VALUE, want: String) {
        try {
            drain(ContentGuard(ByteArrayInputStream(bytes), max, xml)); fail("accepted: $want")
        } catch (e: Importer.ImportError) {
            assertTrue(e.message, e.message!!.contains(want))
        }
    }

    @Test
    fun entityDeclarationsAreRefusedWhateverTheParserWouldDo() {
        val lol = """<?xml version="1.0"?><!DOCTYPE tv [<!ENTITY a "aaaa"><!ENTITY b "&a;&a;">]><tv>&b;</tv>"""
        rejects(lol.toByteArray(), xml = true, want = "entities")
    }

    @Test
    fun entityTextAfterTheRootIsOnlyText() {
        val ok = """<?xml version="1.0"?><tv date="x"><programme><title>&lt;!ENTITY is fine here</title></programme></tv>"""
        drain(ContentGuard(ByteArrayInputStream(ok.toByteArray()), Long.MAX_VALUE, xml = true))
    }

    @Test
    fun aTextNodeTheSizeOfTheFileFails() {
        val big = "<tv><desc>" + "x".repeat((1 shl 20) + 10) + "</desc></tv>"
        rejects(big.toByteArray(), xml = true, want = "damaged")
    }

    @Test
    fun decompressedSizeIsCapped() {
        rejects(ByteArray(10_000) { 'a'.code.toByte() }, xml = false, max = 9_999, want = "too large")
    }

    @Test
    fun linesAreBoundedAndSplitLikeJavaScript() {
        val r = BoundedLineReader(StringReader("a\r\nb\rc\n" + "z".repeat(50) + "\nlast"), maxLine = 10)
        assertEquals("a", r.readLine())
        assertEquals("b\rc", r.readLine()) // a lone \r stays, as with split(/\r?\n/)
        assertEquals("z".repeat(10), r.readLine())
        assertEquals("last", r.readLine())
        assertNull(r.readLine())
    }

    @Test
    fun febThirtyFirstIsNotADate() {
        assertNull(XmltvTime.parse("20260231120000 +0000"))
    }

    @Test
    fun overTheCapTheSoonestProgrammesAreKept() {
        val body = (0 until 10).joinToString("") { i ->
            // channel order: later channels list earlier times
            val h = "%02d".format(23 - i)
            """<programme start="20260929${h}0000 +0000" stop="20260929${h}3000 +0000" channel="c$i"><title>Live $i</title></programme>"""
        }
        val r = GuideParser.parse(ByteArrayInputStream("<tv>$body</tv>".toByteArray()), 0, Long.MAX_VALUE, maxProgrammes = 3)
        assertEquals(listOf("Live 9", "Live 8", "Live 7"), r.programmes.map { it.title })
        assertTrue(r.truncated)
    }

    @Test
    fun aRefusedStartNeverOverwritesARunningJob() {
        val gate = CountDownLatch(1)
        val dir = Files.createTempDirectory("imp").toFile()
        val importer = Importer(dir, { _, _, _ -> gate.await(5, TimeUnit.SECONDS); ByteArrayInputStream("#EXTM3U\nhttp://h/1.ts\n".toByteArray()) })
        assertTrue(importer.startPlaylist("https://example.com/a.m3u"))
        importer.startGuide(listOf("http://127.0.0.1/guide.xml")) // refused: private host
        assertTrue(importer.status(), importer.status().contains("\"running\""))
        gate.countDown()
        waitIdle(importer)
        assertTrue(importer.status(), importer.status().contains("\"done\""))
        importer.shutdown()
    }

    @Test
    fun clearingMidImportIsIdleNotAnError() {
        val gate = CountDownLatch(1)
        val dir = Files.createTempDirectory("imp").toFile()
        val importer = Importer(dir, { _, _, cancelled ->
            gate.await(5, TimeUnit.SECONDS)
            if (cancelled()) throw Importer.ImportError("Cancelled.")
            ByteArrayInputStream(ByteArray(0))
        })
        assertTrue(importer.startPlaylist("https://example.com/a.m3u"))
        importer.clear()
        gate.countDown()
        waitIdle(importer)
        assertTrue(importer.status(), importer.status().contains("\"idle\""))
        importer.shutdown()
    }

    private fun waitIdle(importer: Importer) {
        val end = System.currentTimeMillis() + 5_000
        while (importer.status().contains("\"running\"") && System.currentTimeMillis() < end) Thread.sleep(20)
    }
}
