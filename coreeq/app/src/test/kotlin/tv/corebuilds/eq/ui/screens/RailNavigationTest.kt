package tv.corebuilds.eq.ui.screens

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.corebuilds.eq.CapabilityActivity
import tv.corebuilds.eq.ContentTypeActivity
import tv.corebuilds.eq.EnhancedAudioSettingsActivity
import tv.corebuilds.eq.MainActivity
import tv.corebuilds.eq.ManualEqActivity
import tv.corebuilds.eq.ModeActivity
import tv.corebuilds.eq.ProfilesActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.SetupActivity
import tv.corebuilds.eq.SupportActivity
import tv.corebuilds.eq.companion.CompanionActivity
import tv.corebuilds.eq.ui.RailNav

/**
 * The rail (1.12.0) on every screen that has one, on the real layouts and the
 * real focus search:
 *
 * - every item opens its screen, the same one Home's menu opened; moving
 *   sideways closes the screen you leave, so Back returns Home; the item for
 *   the screen you are on is marked and moves focus into the stage instead;
 * - Up and Down walk the rail in order and stop at its ends; Left stops;
 * - Left from where focus opens reaches the rail, on the current screen's item,
 *   and Right goes back to the control that had focus.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RailNavigationTest(private val screen: Screen) {

    data class Screen(val name: String, val activity: Class<out Activity>) {
        override fun toString() = name
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            Screen("home", MainActivity::class.java),
            Screen("manual-eq", ManualEqActivity::class.java),
            Screen("extras", EnhancedAudioSettingsActivity::class.java),
            Screen("content-type", ContentTypeActivity::class.java),
            Screen("mode", ModeActivity::class.java),
            Screen("profiles", ProfilesActivity::class.java),
            Screen("capability", CapabilityActivity::class.java),
            Screen("setup", SetupActivity::class.java),
            Screen("companion", CompanionActivity::class.java),
            Screen("support", SupportActivity::class.java)
        ).map { arrayOf<Any>(it) }

        /** Presses one arrow at most this many times looking for the rail. */
        const val MAX_LEFT_PRESSES = 40
    }

    @Before
    fun tv() {
        ScreenHarness.prepareTv()
        ScreenSeed.measuredRoom()
    }

    private fun <T> withScreen(block: (Activity) -> T): T {
        ActivityScenario.launch(screen.activity).use { scenario ->
            var out: T? = null
            scenario.onActivity { out = block(it) }
            @Suppress("UNCHECKED_CAST")
            return out as T
        }
    }

    private fun rail(activity: Activity): ViewGroup = activity.findViewById(R.id.rail)

    private fun inRail(activity: Activity, view: View?): Boolean {
        var current: View? = view
        val rail = rail(activity)
        while (current != null) {
            if (current === rail) return true
            current = current.parent as? View
        }
        return false
    }

    @Test
    fun everyRailItemOpensItsScreen() {
        for ((id, target) in RailNav.TARGETS) {
            withScreen { activity ->
                val item = activity.findViewById<View>(id)
                assertNotNull("${screen.name}: the rail has no ${target.simpleName} item", item)
                val app = ApplicationProvider.getApplicationContext<Application>()
                while (shadowOf(app).nextStartedActivity != null) Unit
                val current = target == screen.activity
                assertEquals("${screen.name}: ${target.simpleName} marked current", current, item.isActivated)
                item.performClick()
                ScreenHarness.idle()
                val started = shadowOf(activity).nextStartedActivity
                if (current) {
                    assertEquals("${screen.name}: the current item opened a screen", null, started)
                    assertFalse("${screen.name}: the current item moved focus nowhere", inRail(activity, ScreenHarness.focused(activity)))
                } else {
                    assertNotNull("${screen.name}: ${target.simpleName} opened nothing", started)
                    assertEquals(target.name, started!!.component?.className)
                    if (target == MainActivity::class.java) {
                        assertTrue("Home must come forward, not open again",
                            started.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
                    }
                    assertEquals("${screen.name}: moving sideways must close the screen you leave",
                        screen.activity != MainActivity::class.java, activity.isFinishing)
                }
            }
        }
    }

    @Test
    fun upAndDownWalkTheRailAndStopAtItsEnds() {
        withScreen { activity ->
            val order = RailNav.TARGETS.map { (id, _) -> activity.findViewById<View>(id) }
            val first = order.first()
            assertTrue(first.requestFocus())
            ScreenHarness.idle()
            ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_UP)
            assertSame("${screen.name}: Up at the top of the rail left it", first, ScreenHarness.focused(activity))
            ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_LEFT)
            assertSame("${screen.name}: Left in the rail left it", first, ScreenHarness.focused(activity))
            for (next in order.drop(1)) {
                ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
                assertEquals("${screen.name}: Down in the rail", ScreenHarness.name(next), ScreenHarness.name(ScreenHarness.focused(activity)))
            }
            ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            assertSame("${screen.name}: Down at the bottom of the rail left it", order.last(), ScreenHarness.focused(activity))
        }
    }

    @Test
    fun leftReachesTheCurrentItemAndRightComesBack() {
        withScreen { activity ->
            ScreenHarness.idle()
            var before = ScreenHarness.focused(activity)
            assertNotNull("${screen.name} opens with nothing focused", before)
            assertFalse("${screen.name} opens in the rail", inRail(activity, before))
            var presses = 0
            while (!inRail(activity, ScreenHarness.focused(activity)) && presses < MAX_LEFT_PRESSES) {
                before = ScreenHarness.focused(activity)
                ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_LEFT)
                presses++
            }
            val landed = ScreenHarness.focused(activity)
            assertTrue("${screen.name}: Left never reached the rail (stuck on ${ScreenHarness.name(landed)})", inRail(activity, landed))
            val expected = RailNav.TARGETS.firstOrNull { (_, target) -> target == screen.activity }?.first ?: R.id.rail_home
            assertEquals("${screen.name}: Left landed on the wrong rail item",
                ScreenHarness.name(activity.findViewById(expected)), ScreenHarness.name(landed))
            ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
            assertSame("${screen.name}: Right from the rail did not return to ${ScreenHarness.name(before)}",
                before, ScreenHarness.focused(activity))
        }
    }

    @Test
    fun stageEdgesHoldFocus() {
        // Up from the stage's top control and Down from its bottom one must not
        // fall into the rail, which sits beside the whole stage.
        withScreen { activity ->
            val stage = activity.findViewById<ViewGroup>(R.id.stage)
            val controls = ScreenHarness.focusables(activity).filter { !inRail(activity, it) && isIn(stage, it) }
            assertTrue("${screen.name}: nothing on the stage takes focus", controls.isNotEmpty())
            for ((view, key) in listOf(controls.first() to KeyEvent.KEYCODE_DPAD_UP, controls.last() to KeyEvent.KEYCODE_DPAD_DOWN)) {
                assertTrue(view.requestFocus())
                ScreenHarness.idle()
                repeat(ScreenHarness.HOLD_PRESSES) { ScreenHarness.press(activity, key) }
                assertFalse("${screen.name}: ${KeyEvent.keyCodeToString(key)} from ${ScreenHarness.name(view)} fell into the rail",
                    inRail(activity, ScreenHarness.focused(activity)))
            }
        }
    }

    private fun isIn(group: ViewGroup, view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === group) return true
            current = current.parent as? View
        }
        return false
    }
}
