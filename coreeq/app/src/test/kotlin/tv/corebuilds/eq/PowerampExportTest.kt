package tv.corebuilds.eq

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.dsp.PeakingFilter

class PowerampExportTest {
    @Test fun exportsPowerampImportPresetShapeWithIntegralFrequenciesAndPreamp() {
        val profile = Profile(
            id = "portable", name = "Living room", timestampMs = 1L,
            target = "flat", micType = "imported",
            filters = listOf(PeakingFilter(fc = 1234.8, gain = -2.5, q = 1.1))
        )
        val root = JSONArray(Formats.exportPowerampJson(profile))
        assertEquals(1, root.length())
        val preset = root.getJSONObject(0)
        assertEquals("Living room", preset.getString("name"))
        assertTrue(preset.getBoolean("parametric"))
        assertEquals(1, preset.getJSONArray("bands").length())
        val band = preset.getJSONArray("bands").getJSONObject(0)
        assertEquals(3, band.getInt("type"))
        assertEquals(0, band.getInt("channels"))
        assertEquals(1234, band.getInt("frequency"))
        assertEquals(-2.5, band.getDouble("gain"), 0.0)
    }
}
