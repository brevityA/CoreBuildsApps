package dev.corebuilds.line

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class GuideParserTest {
    // 2026-09-29 18:00 UTC
    private val now = XmltvTime.parse("20260929180000 +0000")!!
    private val wStart = now - 4 * 3600_000L
    private val wEnd = XmltvTime.parse("20261001000000 +0000")!!

    private fun xml(body: String) = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tv SYSTEM "xmltv.dtd">
<tv generator-info-name="test">
  <channel id="sky.main"><display-name lang="en">Sky Sports Main Event</display-name><display-name>SSME</display-name><icon src="x"/></channel>
  <channel id="bbc1"><display-name>BBC One</display-name></channel>
  $body
</tv>"""

    private fun parse(s: String, max: Int = GuideParser.MAX_PROGRAMMES) =
        GuideParser.parse(ByteArrayInputStream(s.toByteArray()), wStart, wEnd, max)

    @Test
    fun keepsSportInTheWindowAndDropsTheRest() {
        val r = parse(
            xml(
                """
  <programme start="20260929190000 +0100" stop="20260929210000 +0100" channel="sky.main">
    <title lang="en">Live Premier League: Arsenal v Chelsea</title>
    <sub-title>Kick-off 7.30pm</sub-title>
    <desc>Long description &amp; more</desc>
    <category>Sport</category><category>Football</category><category>Sport</category>
    <credits><presenter>Someone</presenter></credits>
  </programme>
  <programme start="20260929190000 +0100" stop="20260929200000 +0100" channel="bbc1">
    <title>EastEnders</title><category>Soap</category>
  </programme>
  <programme start="20260920190000 +0000" stop="20260920210000 +0000" channel="sky.main">
    <title>Old: A v B</title><category>Sport</category>
  </programme>
  <programme start="20261003190000 +0000" stop="20261003210000 +0000" channel="sky.main">
    <title>Future: A v B</title><category>Sport</category>
  </programme>
  <programme start="20260929200000 +0000" stop="20260929220000 +0000" channel="bbc1">
    <title>Boxing Night</title><live/>
  </programme>
  <programme start="20260929220000 +0000" stop="20260929230000 +0000" channel="sky.main">
    <title>Classic Match: Leeds v Derby</title><category>Sport</category><previously-shown start="19750101"/>
  </programme>
""",
            ),
        )
        assertEquals(listOf("Live Premier League: Arsenal v Chelsea", "Boxing Night", "Classic Match: Leeds v Derby"), r.programmes.map { it.title })
        val p = r.programmes[0]
        assertEquals("sky.main", p.channel)
        assertEquals(XmltvTime.parse("20260929180000 +0000"), p.start)
        assertEquals("Kick-off 7.30pm", p.subTitle)
        assertEquals(listOf("Sport", "Football"), p.categories)
        assertTrue(p.live) // "Live …" title
        assertTrue(r.programmes[1].live) // <live/>
        assertTrue(r.programmes[2].rerun)
        assertEquals("Sky Sports Main Event", r.channels["sky.main"])
        assertEquals(6, r.seen)
        assertFalse(r.truncated)
    }

    @Test
    fun capsProgrammesAndSaysSo() {
        val body = (0 until 20).joinToString("\n") {
            """<programme start="20260929${"%02d".format(18 + it / 10)}${"%02d".format(it % 10 * 5)}00 +0000" stop="20260929235900 +0000" channel="bbc1"><title>Match $it</title><category>Sports</category></programme>"""
        }
        val r = parse(xml(body), max = 5)
        assertEquals(5, r.programmes.size)
        assertTrue(r.truncated)
    }

    @Test
    fun missingStopMeansTwoHours() {
        val r = parse(xml("""<programme start="20260929180000 +0000" channel="bbc1"><title>Rugby</title><category>Rugby Union</category></programme>"""))
        assertEquals(2 * 3600_000L, r.programmes.single().stop - r.programmes.single().start)
    }

    @Test
    fun notAGuideAndDamagedFilesFail() {
        try {
            parse("<html><body>Login</body></html>"); fail("html accepted")
        } catch (_: GuideParser.NotAGuide) {
        }
        try {
            parse(xml("<programme start=\"2026").dropLast(20)); fail("truncated accepted")
        } catch (_: org.xmlpull.v1.XmlPullParserException) {
        }
    }

    @Test
    fun honoursTheDeclaredEncoding() {
        val s = """<?xml version="1.0" encoding="ISO-8859-1"?><tv><channel id="c"><display-name>Télé Sport</display-name></channel>
<programme start="20260929180000 +0000" stop="20260929200000 +0000" channel="c"><title>Coupe de France : Nîmes v Sète</title><category>Sport</category></programme></tv>"""
        val r = GuideParser.parse(ByteArrayInputStream(s.toByteArray(Charsets.ISO_8859_1)), wStart, wEnd)
        assertEquals("Télé Sport", r.channels["c"])
        assertEquals("Coupe de France : Nîmes v Sète", r.programmes.single().title)
    }

    @Test
    fun importerWritesTheCompactGuideFromGzip() {
        val dir = Files.createTempDirectory("guide").toFile()
        val gz = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use {
                it.write(xml("""<programme start="20260929180000 +0000" stop="20260929200000 +0000" channel="sky.main"><title>NFL: Chiefs @ Bills</title><category>American Football</category><category>Sports</category></programme>""").toByteArray())
            }
        }.toByteArray()
        val importer = Importer(dir, { _, _, _ -> ByteArrayInputStream(gz) as InputStream }, { now })
        assertEquals(1, importer.importGuide(listOf("https://example.com/guide.xml.gz")))
        val json = JSONObject(File(dir, "guide.json").readText())
        assertEquals(1, json.getInt("v"))
        assertEquals(setOf("sky.main"), json.getJSONObject("channels").keySet())
        val p = json.getJSONArray("programmes").getJSONObject(0)
        assertEquals("sky.main", p.getString("c"))
        assertEquals("NFL: Chiefs @ Bills", p.getString("t"))
        assertEquals(now, p.getLong("s"))
        assertFalse(p.has("live"))
    }

    @Test
    fun importerKeepsWhatParsedWhenOneOfTwoGuidesFails() {
        val dir = Files.createTempDirectory("guide").toFile()
        val good = xml("""<programme start="20260929180000 +0000" stop="20260929200000 +0000" channel="bbc1"><title>Live Golf</title></programme>""")
        val importer = Importer(dir, { url, _, _ ->
            if (url.contains("bad")) throw Importer.ImportError("The server answered HTTP 500.")
            ByteArrayInputStream(good.toByteArray())
        }, { now })
        assertEquals(1, importer.importGuide(listOf("https://bad.example.com/a", "https://good.example.com/b")))
        assertTrue(JSONObject(File(dir, "guide.json").readText()).getBoolean("partial"))
    }

    @Test
    fun importerErrorsNeverCarryTheLink() {
        val dir = Files.createTempDirectory("guide").toFile()
        val secret = "https://prov.example.com/xmltv.php?username=alice&password=hunter2"
        val importer = Importer(dir, { _, _, _ -> ByteArrayInputStream("<html>nope</html>".toByteArray()) }, { now })
        try {
            importer.importGuide(listOf(secret)); fail("html accepted")
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            assertFalse(msg, msg.contains("hunter2") || msg.contains("alice") || msg.contains("prov.example.com"))
        }
        assertFalse(File(dir, "guide.json").exists())
    }

    /**
     * A provider-sized guide, streamed from disk through gzip the way a
     * download arrives. Size in MB from -Dcoreline.guideMb (build.gradle.kts
     * passes 120 unless told otherwise; 300 is the stress case).
     */
    @Test
    fun largeGuideStreamsInBoundedMemoryAndTime() {
        val targetMb = (System.getProperty("coreline.guideMb") ?: "120").toInt()
        val file = File.createTempFile("big-guide", ".xml.gz").apply { deleteOnExit() }
        var bytes = 0L
        var programmes = 0
        GZIPOutputStream(file.outputStream().buffered(1 shl 16)).bufferedWriter().use { w ->
            val head = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv>\n"
            w.write(head); bytes += head.length
            for (c in 0 until 3000) {
                val line = "<channel id=\"ch$c\"><display-name>Channel $c</display-name></channel>\n"
                w.write(line); bytes += line.length
            }
            val desc = "A long description of the programme that providers pad every entry with. ".repeat(4)
            var i = 0
            while (bytes < targetMb * 1_000_000L) {
                val c = i % 3000
                val day = 25 + (i / 3000) % 12 // spread across 12 days, two in the window
                val hour = (i / 7) % 24
                val sport = i % 11 == 0
                val start = "202609${"%02d".format(day)}${"%02d".format(hour)}0000 +0000"
                val stop = "202609${"%02d".format(day)}${"%02d".format(hour)}5900 +0000"
                val line = "<programme start=\"$start\" stop=\"$stop\" channel=\"ch$c\"><title>" +
                    (if (sport) "Team $i v Team ${i + 1}" else "Show $i") +
                    "</title><desc>$desc</desc><category>${if (sport) "Sports" else "Drama"}</category></programme>\n"
                w.write(line); bytes += line.length; i += 1
            }
            programmes = i
            w.write("</tv>\n")
        }
        Runtime.getRuntime().gc()
        val rt = Runtime.getRuntime()
        val before = rt.totalMemory() - rt.freeMemory()
        val t0 = System.nanoTime()
        val r = Importer.maybeGunzip(file.inputStream()).use { GuideParser.parse(it, wStart, wEnd) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val used = (rt.totalMemory() - rt.freeMemory() - before) / 1_000_000
        println("guide bench: ${bytes / 1_000_000} MB, $programmes programmes, ${file.length() / 1_000_000} MB gz → kept ${r.programmes.size} (truncated=${r.truncated}) in $ms ms, heap delta ~$used MB")
        assertEquals(programmes, r.seen)
        assertTrue(r.programmes.isNotEmpty())
        assertTrue(r.programmes.all { it.stop > wStart && it.start < wEnd && it.categories.contains("Sports") })
        assertTrue("took $ms ms", ms < 120_000)
    }
}
