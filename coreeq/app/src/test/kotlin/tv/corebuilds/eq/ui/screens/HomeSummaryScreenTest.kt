package tv.corebuilds.eq.ui.screens

import android.app.Application
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.corebuilds.eq.MainActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.dsp.NightMode
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.ui.EnhancedAudioPrefs

/** Home's summary on the real screen; the service itself is not running here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class HomeSummaryScreenTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun tv() {
        ScreenHarness.prepareTv()
        ScreenSeed.measuredRoom()
    }

    @Test fun `home says what is applied and what is only requested`() {
        EnhancedAudioPrefs(app).nightLevel = NightMode.Level.LIGHT
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ScreenHarness.idle()
                val text = activity.findViewById<TextView>(R.id.text_home_summary).text.toString()
                val lines = text.split("\n")
                assertEquals(2, lines.size)
                assertTrue("applied line: ${lines[0]}", lines[0].isNotBlank() && !lines[0].startsWith("Active"))
                assertEquals("Listening: night light.", lines[1])
            }
        }
    }

    @Test fun `the service facts round-trip and clear`() {
        val store = ProfileStore(app)
        store.measurementPaused = true
        store.appliedEngine = "DynamicsProcessing"
        assertTrue(ProfileStore(app).measurementPaused)
        assertEquals("DynamicsProcessing", ProfileStore(app).appliedEngine)
        store.appliedEngine = null
        store.measurementPaused = false
        assertNull(ProfileStore(app).appliedEngine)
        assertEquals(false, ProfileStore(app).measurementPaused)
    }
}
