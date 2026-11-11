package tv.corebuilds.eq.ui.screens

import android.app.Application
import android.content.ClipboardManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import tv.corebuilds.eq.BuildConfig
import tv.corebuilds.eq.CapabilityActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.export.ProfileStore

/**
 * The diagnostics summary on Capability, under Robolectric: it opens
 * a preview, what is copied is exactly what was shown, and the seeded profile
 * names never appear. A real TV's clipboard and a phone scanning the QR code
 * are not exercised here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class DiagnosticsScreenTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun tv() {
        ScreenHarness.prepareTv()
        ScreenSeed.measuredRoom()
        ProfileStore(app).recordMeasurement("failed", "primary", "No sweep was heard.")
    }

    private fun open(activity: CapabilityActivity): AlertDialog {
        ScreenHarness.idle()
        activity.findViewById<Button>(R.id.btn_diagnostics).performClick()
        ScreenHarness.idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    @Test fun `diagnostics preview first, copy exactly what was shown`() {
        ActivityScenario.launch(CapabilityActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = open(activity)
                assertTrue(dialog.isShowing)
                val shown = mutableListOf<String>()
                fun walk(v: android.view.View) {
                    if (v is android.widget.TextView && v !is Button) shown += v.text.toString()
                    if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
                }
                walk(dialog.window!!.decorView)
                val body = shown.first { it.startsWith("Core EQ diagnostics") }
                assertTrue("the scroller takes the remote first", dialog.currentFocus is ScrollView)
                assertTrue(body, body.contains("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})"))
                assertTrue(body, body.contains("Last measurement: failed (primary)"))
                assertFalse("profile names stay out: $body", body.contains("Living room") || body.contains("Bedroom"))
                // Nothing is copied until Copy is pressed.
                val clipboard = app.getSystemService(ClipboardManager::class.java)
                assertFalse(clipboard.hasPrimaryClip())
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
                ScreenHarness.idle()
                assertEquals(body, clipboard.primaryClip!!.getItemAt(0).text.toString())
                assertTrue(dialog.isShowing)
            }
        }
    }

    @Test fun `the QR code replaces the text in the same dialog`() {
        ActivityScenario.launch(CapabilityActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = open(activity)
                val qrButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                qrButton.performClick()
                ScreenHarness.idle()
                val qr = dialog.window!!.decorView.findViewWithTag<ImageView>("diagnostics_qr")
                assertNotNull("the QR code is shown", qr)
                assertNotNull(qr.drawable)
                assertFalse("pressing it again would stack codes", qrButton.isEnabled)
            }
        }
    }

    @Test fun `a second press behind the dialog opens nothing more`() {
        ActivityScenario.launch(CapabilityActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val first = open(activity)
                activity.findViewById<Button>(R.id.btn_diagnostics).performClick()
                ScreenHarness.idle()
                assertTrue(first === ShadowDialog.getLatestDialog())
                first.dismiss()
                ScreenHarness.idle()
                assertTrue("focus returns to the button", activity.findViewById<Button>(R.id.btn_diagnostics).isFocused)
            }
        }
    }
}
