package dev.corebuilds.line

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

class PlaylistParserTest {
    private fun fixture(name: String) = File("../../tests/fixtures/$name")

    /** The same file and the same answer as the parity test in tests/playlist.test.mjs. */
    @Test
    fun matchesTheJavaScriptParserOnTheParityFixture() {
        val got = fixture("playlist-parity.m3u").bufferedReader(Charsets.UTF_8).use { PlaylistParser.parse(it) }
        val want = JSONObject(fixture("playlist-parity.expected.json").readText())
        val chans = want.getJSONArray("channels")
        assertEquals(chans.length(), got.channels.size)
        for (i in 0 until chans.length()) {
            val w = chans.getJSONObject(i)
            val g = got.channels[i]
            assertEquals("name[$i]", w.getString("name"), g.name)
            assertEquals("url[$i]", w.getString("url"), g.url)
            assertEquals("group[$i]", w.getString("group"), g.group)
            assertEquals("tvgId[$i]", w.getString("tvgId"), g.tvgId)
            assertEquals("tvgName[$i]", w.getString("tvgName"), g.tvgName)
        }
        val urls = want.getJSONArray("guideUrls")
        assertEquals((0 until urls.length()).map { urls.getString(it) }, got.guideUrls)
        assertEquals(want.getInt("vodSkipped"), got.vodSkipped)
    }

    @Test
    fun stopsAtTheCapAndCountsVodBeforeIt() {
        val text = buildString {
            append("#EXTM3U\n#EXTINF:-1,Film\nhttp://h/movie/u/p/1.mkv\n")
            for (i in 1..10) append("#EXTINF:-1,C$i\nhttp://h/live/$i.ts\n")
        }
        val r = PlaylistParser.parse(BufferedReader(StringReader(text)), maxChannels = 3)
        assertEquals(listOf("C1", "C2", "C3"), r.channels.map { it.name })
        assertEquals(1, r.vodSkipped)
    }

    @Test
    fun cancelStopsReading() {
        val text = (1..100).joinToString("\n") { "http://h/live/$it.ts" }
        var n = 0
        val r = PlaylistParser.parse(BufferedReader(StringReader(text))) { ++n > 5 }
        assertTrue(r.channels.size <= 5)
    }

    @Test
    fun vodUrls() {
        assertTrue(PlaylistParser.isVodUrl("http://h/movie/u/p/1.ts"))
        assertTrue(PlaylistParser.isVodUrl("http://h/x/film.MKV?x=1"))
        assertFalse(PlaylistParser.isVodUrl("http://h/live/u/p/1.ts"))
        assertFalse(PlaylistParser.isVodUrl("http://h/live/moviechannel.m3u8"))
        assertFalse(PlaylistParser.isVodUrl("not a url"))
    }
}
