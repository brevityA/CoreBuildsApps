package tv.corebuilds.iconpack.screens

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.corebuilds.iconpack.AboutActivity
import tv.corebuilds.iconpack.AuditorActivity
import tv.corebuilds.iconpack.FaqActivity
import tv.corebuilds.iconpack.InspectorActivity
import tv.corebuilds.iconpack.LauncherCompatActivity
import tv.corebuilds.iconpack.LauncherSetupActivity
import tv.corebuilds.iconpack.MainActivity
import tv.corebuilds.iconpack.Plus
import tv.corebuilds.iconpack.PlusStore
import tv.corebuilds.iconpack.R
import tv.corebuilds.iconpack.RequestsActivity
import tv.corebuilds.iconpack.SettingsActivity
import tv.corebuilds.iconpack.SuiteActivity
import tv.corebuilds.iconpack.SupportActivity
import tv.corebuilds.iconpack.WallpapersActivity
import tv.corebuilds.iconpack.WhatsNewActivity

/**
 * Every label on every restyled screen fits its box on a 1080p TV at the
 * default text size: no line ends in an ellipsis the layout did not mean, and
 * no text is taller than the view drawing it.
 *
 * The 2.6.0 restyle found two of each by eye in the renders - Home's
 * "No supported launcher found" cut to "No supported launcher f…", About's
 * update line measured at one line in a wrap_content column and cut - so this
 * holds what the renders showed. A label that may end in an ellipsis by design
 * (the update bar's one-line highlight, a row's runtime subtitle) is listed in
 * [MAY_ELLIPSIZE] with the reason beside it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextFitTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    /** Ids allowed to end in an ellipsis, each for a stated reason. */
    private val MAY_ELLIPSIZE = setOf(
        "update_highlights",   // one release bullet of eight; the release page has the rest
        "update_sub",          // a download error string of any length
    )

    @Before
    fun tv() {
        ScreenHarness.prepareTv()
        val pkg = "com.spocky.projengmenu"
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg }
        })
    }

    private fun problems(activity: Activity): List<String> {
        val out = mutableListOf<String>()
        fun walk(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view is TextView && view !is EditText && view.text.isNotEmpty()) {
                val layout = view.layout
                val id = if (view.id != View.NO_ID) activity.resources.getResourceEntryName(view.id) else "\"${view.text}\""
                if (layout != null && id !in MAY_ELLIPSIZE) {
                    val ellipsized = (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }
                    val textHeight = layout.height
                    val room = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
                    when {
                        ellipsized -> out += "$id is cut short: \"${view.text}\""
                        // 2px of slack for font metrics rounding at xhdpi.
                        room > 0 && textHeight > room + 2 -> out += "$id is taller ($textHeight px) than its box ($room px)"
                    }
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(activity.window.decorView)
        return out
    }

    private fun <A : Activity> check(type: Class<A>, extras: Intent.() -> Unit = {}) {
        ActivityScenario.launch<A>(Intent(app, type).apply(extras)).use { scenario ->
            scenario.onActivity { activity ->
                ScreenHarness.idle()
                ScreenHarness.render(activity, "fit-" + type.simpleName)
                val found = problems(activity)
                assertTrue("${type.simpleName}:\n  " + found.joinToString("\n  "), found.isEmpty())
            }
        }
    }

    @Test fun home() = check(MainActivity::class.java)
    @Test fun wallpapers() = check(WallpapersActivity::class.java)
    @Test fun support() = check(SupportActivity::class.java)
    @Test fun settings() = check(SettingsActivity::class.java)
    @Test fun about() = check(AboutActivity::class.java)
    @Test fun missingIcons() = check(AuditorActivity::class.java)
    @Test fun faq() = check(FaqActivity::class.java)
    @Test fun launcherCompat() = check(LauncherCompatActivity::class.java)
    @Test fun suite() = check(SuiteActivity::class.java)
    @Test fun whatsNew() = check(WhatsNewActivity::class.java)

    @Test
    fun inspector() = check(InspectorActivity::class.java) {
        putExtra(InspectorActivity.EXTRA_DRAWABLE, app.resources.getStringArray(R.array.icon_pack)[0])
        putExtra(InspectorActivity.EXTRA_NAME, app.resources.getStringArray(R.array.icon_names)[0])
        putExtra(InspectorActivity.EXTRA_CATEGORY, "VOD")
    }

    @Test
    fun launcherSetup() = check(LauncherSetupActivity::class.java) {
        putExtra(LauncherSetupActivity.EXTRA_LAUNCHER, "monet")
    }

    @Test
    fun myRequests() {
        assumeTrue("Google Play build only", PlusStore.AVAILABLE)
        Plus.startTrial(app)
        check(RequestsActivity::class.java)
    }
}
