package tv.corebuilds.eq.ui.screens

import android.app.Application
import android.content.Context
import android.view.KeyEvent
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.corebuilds.eq.EnhancedAudioSettingsActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.dsp.NightMode
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.ui.EnhancedAudioPrefs

/**
 * Night mode and Clarity as stored settings and as remote-controlled rows (1.14.0).
 * Robolectric drives the real screen; the audio these settings produce is not
 * checked here (see NightModeTest / ListeningLayersTest for the configured values).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class ListeningSettingsTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun raw() = app.getSharedPreferences(EnhancedAudioPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun tv() {
        ScreenHarness.prepareTv()
        ScreenSeed.measuredRoom()
    }

    @Test fun `an old night switch that was on keeps its exact sound as Strong`() {
        raw().edit().putBoolean("night_mode_v121", true).commit()
        val prefs = EnhancedAudioPrefs(app)
        assertEquals(NightMode.Level.STRONG, prefs.nightLevel)
        assertEquals(EnhancedAudioPrefs.limiterFor(nightMode = true), prefs.limiter())
    }

    @Test fun `an old dialogue switch that was on is Clarity plus two`() {
        raw().edit().putBoolean("dialogue_boost_v130", true).commit()
        assertEquals(2.0, EnhancedAudioPrefs(app).clarityDb, 0.0)
    }

    @Test fun `nothing stored means off, and the new keys win over the old ones`() {
        assertEquals(NightMode.Level.OFF, EnhancedAudioPrefs(app).nightLevel)
        assertEquals(0.0, EnhancedAudioPrefs(app).clarityDb, 0.0)
        raw().edit().putBoolean("night_mode_v121", true).putBoolean("dialogue_boost_v130", true).commit()
        val prefs = EnhancedAudioPrefs(app)
        prefs.nightLevel = NightMode.Level.LIGHT
        prefs.clarityDb = 0.0
        val reread = EnhancedAudioPrefs(app)
        assertEquals(NightMode.Level.LIGHT, reread.nightLevel)
        assertEquals(0.0, reread.clarityDb, 0.0)
    }

    @Test fun `listening reset leaves profiles and the correction switch alone`() {
        val store = ProfileStore(app)
        val before = store.getAllProfiles()
        val enabled = store.correctionEnabled
        val prefs = EnhancedAudioPrefs(app)
        prefs.nightLevel = NightMode.Level.STRONG
        prefs.clarityDb = 3.0
        prefs.resetListening()
        assertEquals(NightMode.Level.OFF, prefs.nightLevel)
        assertEquals(0.0, prefs.clarityDb, 0.0)
        assertEquals(before, ProfileStore(app).getAllProfiles())
        assertEquals(enabled, ProfileStore(app).correctionEnabled)
    }

    @Test fun `left and right step night mode, and the ends let focus move on`() {
        ActivityScenario.launch(EnhancedAudioSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val night = activity.findViewById<Button>(R.id.btn_extras_night)
                night.requestFocus()
                ScreenHarness.idle()
                ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
                assertEquals(NightMode.Level.LIGHT, EnhancedAudioPrefs(activity).nightLevel)
                assertSame("stepping must keep focus on the row", night, ScreenHarness.focused(activity))
                assertEquals("Night mode: Light", night.text.toString())
                assertTrue(night.contentDescription.toString().contains("Light"))
                ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
                assertEquals(NightMode.Level.STRONG, EnhancedAudioPrefs(activity).nightLevel)
                ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_LEFT)
                ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_LEFT)
                assertEquals(NightMode.Level.OFF, EnhancedAudioPrefs(activity).nightLevel)
                assertSame(night, ScreenHarness.focused(activity))
                // At Off, Left is not consumed: focus leaves for the rail instead of being trapped.
                ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_LEFT)
                assertNotSame("Left at Off must let focus move on", night, ScreenHarness.focused(activity))
                assertEquals(NightMode.Level.OFF, EnhancedAudioPrefs(activity).nightLevel)
            }
        }
    }

    @Test fun `clarity steps one decibel at a time and ok wraps to off`() {
        ActivityScenario.launch(EnhancedAudioSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val clarity = activity.findViewById<Button>(R.id.btn_extras_dialogue)
                clarity.requestFocus()
                ScreenHarness.idle()
                repeat(5) { ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_RIGHT) }
                // Three steps reach the top; the extra presses are not consumed and do not overshoot.
                assertEquals(3.0, EnhancedAudioPrefs(activity).clarityDb, 0.0)
                clarity.requestFocus()
                ScreenHarness.idle()
                assertEquals("Clarity: +3 dB", clarity.text.toString())
                clarity.performClick()
                ScreenHarness.idle()
                assertEquals(0.0, EnhancedAudioPrefs(activity).clarityDb, 0.0)
                assertEquals("Clarity: Off", clarity.text.toString())
                assertFalse(clarity.isActivated)
            }
        }
    }

    @Test fun `reset turns every extra off and says so on screen`() {
        val prefs = EnhancedAudioPrefs(app)
        prefs.nightLevel = NightMode.Level.STRONG
        prefs.clarityDb = 2.0
        prefs.bassBoostEnabled = true
        prefs.loudnessEnhancerEnabled = true
        prefs.autoLoudnessEnabled = true
        ActivityScenario.launch(EnhancedAudioSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.btn_extras_reset).performClick()
                ScreenHarness.idle()
                val after = EnhancedAudioPrefs(activity)
                assertEquals(NightMode.Level.OFF, after.nightLevel)
                assertEquals(0.0, after.clarityDb, 0.0)
                assertFalse(after.bassBoostEnabled || after.loudnessEnhancerEnabled || after.autoLoudnessEnabled || after.lowVolumeBassEnabled)
                val status = activity.findViewById<android.widget.TextView>(R.id.text_extras_detail_status)
                assertEquals(activity.getString(R.string.extras_reset_done), status.text.toString())
            }
        }
    }
}
