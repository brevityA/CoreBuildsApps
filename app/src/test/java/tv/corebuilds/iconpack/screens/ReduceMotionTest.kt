package tv.corebuilds.iconpack.screens

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.corebuilds.iconpack.Prefs
import tv.corebuilds.iconpack.R
import tv.corebuilds.iconpack.SettingsActivity
import tv.corebuilds.iconpack.SuiteActivity

/**
 * Reduce motion reaches every focus lift on screen, not only the ones the
 * layout inflated: the screen it is switched on in, and rows a screen builds
 * after it loads. Before this, turning it on left the open Settings screen
 * lifting under focus, and the suite, request and auditor rows always lifted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReduceMotionTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun tv() = ScreenHarness.prepareTv()

    @Test
    fun `switching Reduce motion in Settings applies to the open screen both ways`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                ScreenHarness.idle()
                val back = a.findViewById<View>(R.id.set_back)
                val row = a.findViewById<View>(R.id.set_motion_row)
                assertNotNull("Back lifts under focus by default", back.stateListAnimator)

                row.performClick()
                ScreenHarness.idle()
                assertNull("Reduce motion on: Back no longer lifts", back.stateListAnimator)
                assertNull("nor does the row that was pressed", row.stateListAnimator)
                assertEquals("and the pressed row is not left raised", 0f, row.translationZ)

                row.performClick()
                ScreenHarness.idle()
                assertNotNull("Reduce motion off: Back lifts again", back.stateListAnimator)
            }
        }
    }

    @Test
    fun `suite rows built after the screen loads carry no lift with Reduce motion on`() {
        // Installed, so the Play build lists them too (it leaves out the apps
        // a TV does not have).
        for (pkg in app.resources.getStringArray(R.array.suite_hub_pkgs).filter { it.isNotEmpty() }) {
            shadowOf(app.packageManager).installPackage(PackageInfo().apply {
                packageName = pkg
                applicationInfo = ApplicationInfo().apply { packageName = pkg }
            })
        }
        Prefs.set(app, Prefs.KEY_REDUCE_MOTION, true)
        ActivityScenario.launch(SuiteActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                ScreenHarness.idle()
                val rows = a.findViewById<ViewGroup>(R.id.suite_rows)
                assertTrue("the suite lists at least one app", rows.childCount > 0)
                for (i in 0 until rows.childCount) {
                    assertNull("suite row $i still lifts", rows.getChildAt(i).stateListAnimator)
                }
            }
        }
    }
}
