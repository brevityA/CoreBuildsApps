package tv.corebuilds.eq.ui.screens

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import tv.corebuilds.eq.FirstRunActivity
import java.io.File
import java.io.FileOutputStream

/**
 * What every screen test needs: a TV that says it is one, a first-run guide that
 * has been seen, and ways to press the remote, list what can take focus, and
 * render the screen.
 *
 * The qualifiers make Robolectric's display a 1080p TV: 960 x 540 dp at xhdpi
 * is 1920 x 1080 pixels, the box [tv.corebuilds.eq.TvActivity] designs for.
 */
object ScreenHarness {

    const val TV = "en-rUS-w960dp-h540dp-land-television-xhdpi"

    /**
     * Runs before each launch. Home sends a device without leanback to the phone
     * companion, and a TV starts out of touch mode: without that, requestFocus()
     * on a plain Button does nothing and no screen would open with focus, which is
     * what a phone does and a TV must not.
     */
    fun prepareTv() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        app.getSharedPreferences(FirstRunActivity.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(FirstRunActivity.KEY_COMPLETED, true).commit()
    }

    fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * The view with focus, read from the window. Robolectric's ShadowActivity keeps a
     * separate currentFocus field that only setCurrentFocus() writes, so
     * Activity.getCurrentFocus() reads null while the window has a focused view.
     */
    fun focused(activity: Activity): View? = activity.window.currentFocus

    /**
     * One press of a remote key, routed the way ViewRootImpl routes it on a device:
     * the view hierarchy gets the key first (a ScrollView scrolling, the band editor
     * adjusting a band), and only an arrow nobody handled moves focus, by the
     * focused view's own focusSearch() with the same focused-rect hand-off that
     * ViewRootImpl.performFocusNavigation() does, then dispatchUnhandledMove().
     * Activity.dispatchKeyEvent() alone never moves focus, which is why a harness
     * built on it sees every screen as a dead end.
     */
    fun press(activity: Activity, keyCode: Int) {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val handled = activity.dispatchKeyEvent(down)
        if (!handled) navigate(activity, keyCode)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        idle()
    }

    private fun navigate(activity: Activity, keyCode: Int) {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            else -> return
        }
        val root = activity.window.decorView as ViewGroup
        val focused = root.findFocus()
        if (focused == null) {
            root.restoreDefaultFocus()
            return
        }
        val next = focused.focusSearch(direction)
        if (next != null && next !== focused) {
            val rect = android.graphics.Rect()
            focused.getFocusedRect(rect)
            root.offsetDescendantRectToMyCoords(focused, rect)
            root.offsetRectIntoDescendantCoords(next, rect)
            if (next.requestFocus(direction, rect)) return
        }
        root.dispatchUnhandledMove(focused, direction)
    }

    /** Every view on screen that can take focus from the remote, in layout order. */
    fun focusables(activity: Activity): List<View> {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val out = ArrayList<View>()
        content.addFocusables(out, View.FOCUS_DOWN, View.FOCUSABLES_ALL)
        return out.filter { it.isShown && it.isEnabled && it.width > 0 && it.height > 0 }
    }

    /** A name for a view in failure messages: its id, else its class and text. */
    fun name(view: View?): String {
        if (view == null) return "nothing"
        val id = if (view.id != View.NO_ID) runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() else null
        val text = (view as? android.widget.TextView)?.text?.toString()?.take(40)
        return id ?: "${view.javaClass.simpleName}(${text ?: ""})"
    }

    /** Presses one arrow at most this many times while focus stays put, as a held key would. */
    const val HOLD_PRESSES = 12

    /**
     * Every focusable view the remote can reach from where focus starts. From each
     * view reached, each arrow is held: pressed again while focus stays on that view,
     * up to [HOLD_PRESSES] times, because a view may use the arrow first (a ScrollView
     * scrolling towards its next control, the band editor moving to its next band)
     * and only let focus go at its edge. Focus is put back on each view before each
     * arrow, so one dead end does not hide the rest of the screen.
     */
    fun reachable(activity: Activity): Set<View> {
        idle()
        val start = focused(activity) ?: focusables(activity).firstOrNull() ?: return emptySet()
        val seen = linkedSetOf(start)
        val queue = ArrayDeque(listOf(start))
        val keys = intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            for (key in keys) {
                if (!from.isShown || !from.requestFocus()) break
                idle()
                var now: View? = from
                var presses = 0
                while (now === from && presses < HOLD_PRESSES) {
                    press(activity, key)
                    now = focused(activity)
                    presses++
                }
                if (now != null && seen.add(now)) queue.addLast(now)
            }
        }
        return seen
    }

    /** Draws the whole window at its real size into build/screens/<name>.png. */
    fun render(activity: Activity, name: String): File {
        idle()
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File(System.getProperty("coreeq.screens.dir") ?: "build/screens").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
