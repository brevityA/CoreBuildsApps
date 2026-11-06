package tv.corebuilds.iconpack.screens

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream

/**
 * What every icon pack screen test needs: a TV that says it is one, ways to press
 * the remote, list what can take focus, and render the screen. The same harness
 * Core EQ's screen tests use (coreeq/app/src/test/.../ui/screens/ScreenHarness.kt).
 *
 * [TV] makes Robolectric's display a 1080p TV: 960 x 540 dp at xhdpi is
 * 1920 x 1080 pixels.
 */
object ScreenHarness {

    const val TV = "en-rUS-w960dp-h540dp-land-television-xhdpi"

    /** A TV starts out of touch mode: without that, requestFocus() on a Button does nothing. */
    fun prepareTv() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    }

    fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The view with focus, read from the window (ShadowActivity's own field is never written). */
    fun focused(activity: Activity): View? = activity.window.currentFocus

    /**
     * One press of a remote key, routed the way ViewRootImpl routes it: the view
     * hierarchy gets the key first, and only an arrow nobody handled moves focus.
     */
    fun press(activity: Activity, keyCode: Int) {
        val handled = activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
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
        val focused = root.findFocus() ?: run { root.restoreDefaultFocus(); return }
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

    /** Draws the whole window at its real size into build/screens/<name>.png. */
    fun render(activity: Activity, name: String): File {
        // Let a focus lift or fade finish, so the render shows the settled state.
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
        idle()
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File(System.getProperty("iconpack.screens.dir") ?: "build/screens").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
