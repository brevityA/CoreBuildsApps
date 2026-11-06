package tv.corebuilds.iconpack.screens

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.corebuilds.iconpack.Distribution
import tv.corebuilds.iconpack.MainActivity
import tv.corebuilds.iconpack.R
import tv.corebuilds.iconpack.WallpapersActivity

/**
 * The 2.6.0 D-pad routes, pressed the way a remote presses them.
 *
 * Home is two columns since the rail-and-stage restyle: the rail walks top to
 * bottom and stops at its ends, the stage walks update bar -> search -> chips
 * -> grid, and LEFT/RIGHT cross between them. The Wallpapers rail's series
 * used to take RIGHT as "next series"; RIGHT now reaches the grid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusChainTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun tv() {
        ScreenHarness.prepareTv()
        val pkg = "com.spocky.projengmenu"
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg }
        })
    }

    private fun <A : Activity> on(type: Class<A>, block: (A) -> Unit) {
        ActivityScenario.launch(type).use { scenario ->
            scenario.onActivity { activity ->
                ScreenHarness.idle()
                block(activity)
            }
        }
    }

    private fun Activity.press(key: Int) = ScreenHarness.press(this, key)

    private fun Activity.focusedId(): Int = ScreenHarness.focused(this)?.id ?: View.NO_ID

    private fun Activity.name(id: Int): String =
        if (id == View.NO_ID) "nothing" else resources.getResourceEntryName(id)

    private fun Activity.assertFocus(expected: Int, after: String) {
        val got = focusedId()
        assertEquals("after $after, focus is on ${name(got)}", name(expected), name(got))
    }

    /** True when the focused view is [id] or inside it (a tile in the grid, a chip in a row). */
    private fun Activity.focusInside(id: Int): Boolean {
        var node: Any? = ScreenHarness.focused(this)
        while (node is View) {
            if (node.id == id) return true
            node = node.parent
        }
        return false
    }

    @Test
    fun `home opens on Apply and the rail walks down to About, then stops`() = on(MainActivity::class.java) { a ->
        a.assertFocus(R.id.apply_button, "opening Home")
        val rail = buildList {
            add(R.id.style_entry)
            add(R.id.missing_entry)
            add(R.id.wallpapers_entry)
            if (!Distribution.PLAY) add(R.id.support_entry)
            add(R.id.settings_entry)
            add(R.id.about_entry)
        }
        for (id in rail) {
            a.press(KeyEvent.KEYCODE_DPAD_DOWN)
            a.assertFocus(id, "DOWN")
        }
        a.press(KeyEvent.KEYCODE_DPAD_DOWN)
        a.assertFocus(R.id.about_entry, "DOWN at the rail's foot")
        for (id in rail.reversed().drop(1)) {
            a.press(KeyEvent.KEYCODE_DPAD_UP)
            a.assertFocus(id, "UP")
        }
        a.press(KeyEvent.KEYCODE_DPAD_UP)
        a.assertFocus(R.id.apply_button, "UP to the top of the rail")
    }

    @Test
    fun `home crosses from the rail to the stage and walks it to the grid`() = on(MainActivity::class.java) { a ->
        // RIGHT crosses geometrically: from Apply, the stage control level with
        // it is the chip row, one UP under the search field.
        a.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue("RIGHT from Apply lands on the stage, got ${a.name(a.focusedId())}",
            a.focusInside(R.id.chip_row) || a.focusedId() == R.id.search)
        if (a.focusInside(R.id.chip_row)) {
            a.press(KeyEvent.KEYCODE_DPAD_UP)
            a.assertFocus(R.id.search, "UP from the chips")
        }
        a.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue("DOWN from search lands in the category chips", a.focusInside(R.id.chip_row))
        a.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue("DOWN from the chips lands in the grid", a.focusInside(R.id.grid))
        a.press(KeyEvent.KEYCODE_DPAD_UP)
        assertTrue("UP from the grid's top row returns to the chips", a.focusInside(R.id.chip_row))
        a.press(KeyEvent.KEYCODE_DPAD_UP)
        a.assertFocus(R.id.search, "UP from the chips")
        a.press(KeyEvent.KEYCODE_DPAD_UP)
        a.assertFocus(R.id.search, "UP from search with no update bar")
        a.press(KeyEvent.KEYCODE_DPAD_LEFT)
        val back = a.focusedId()
        assertTrue("LEFT from search returns to the rail, got ${a.name(back)}",
            back == R.id.apply_button || back == R.id.style_entry)
    }

    @Test
    fun `wallpapers RIGHT from a series reaches the grid`() = on(WallpapersActivity::class.java) { a ->
        val chips = a.findViewById<RecyclerView>(R.id.wp_chips)
        assertTrue("Wallpapers opens on the series list", a.focusInside(R.id.wp_chips))
        val first = ScreenHarness.focused(a)
        assertNotNull(first)
        a.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue("DOWN walks the series", a.focusInside(R.id.wp_chips))
        assertTrue("DOWN moved to another series", ScreenHarness.focused(a) !== first)
        a.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue("RIGHT from a series lands in the grid, not on the next series",
            a.focusInside(R.id.wp_grid))
        a.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue("LEFT from the grid's first column returns to the series",
            a.focusInside(R.id.wp_chips))
        assertTrue(chips.childCount > 1)
    }
}
