package tv.corebuilds.eq.ui.screens

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import tv.corebuilds.eq.CapabilityActivity
import tv.corebuilds.eq.ContentTypeActivity
import tv.corebuilds.eq.EnhancedAudioSettingsActivity
import tv.corebuilds.eq.FirstRunActivity
import tv.corebuilds.eq.MainActivity
import tv.corebuilds.eq.ManualEqActivity
import tv.corebuilds.eq.MeasureActivity
import tv.corebuilds.eq.ModeActivity
import tv.corebuilds.eq.ProfilesActivity
import tv.corebuilds.eq.SetupActivity
import tv.corebuilds.eq.SupportActivity
import tv.corebuilds.eq.companion.CompanionActivity
import tv.corebuilds.eq.display.DisplayCalibrationActivity

/**
 * The contract every TV screen keeps through any restyle, run on the real layouts,
 * Android's own FocusFinder and the real click handlers:
 *
 * 1. it opens on a 1080p TV and renders (build/screens/<tag>-<screen>.png);
 * 2. every control on it can be reached with the four arrows from where focus opens;
 * 3. every button does something you can see: opens a screen or a dialog, closes
 *    this one, starts the service, shows a toast, or changes what is on screen;
 * 4. the buttons that navigate still open the screen they open today.
 *
 * Buttons in [Screen.wiredOnly] start a sweep, a pairing server or another long job
 * the JVM cannot run; for those the test checks that a handler is attached, not
 * what it does.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenContractTest(private val screen: Screen) {

    data class Screen(
        val name: String,
        val activity: Class<out Activity>,
        /** Control name (id, or class(text) for unnamed views) to the screen it must open. */
        val opens: Map<String, Class<out Activity>> = emptyMap(),
        /** Controls that must close this screen. */
        val finishes: Set<String> = emptySet(),
        /** Controls whose job cannot run on the JVM: they must be wired, nothing more. */
        val wiredOnly: Set<String> = emptySet()
    ) {
        override fun toString() = name
    }

    companion object {
        private val tag = System.getProperty("coreeq.screens.tag") ?: "now"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            Screen(
                "home", MainActivity::class.java,
                opens = mapOf(
                    "btn_remeasure" to MeasureActivity::class.java,
                    "btn_nav_manual_eq" to ManualEqActivity::class.java,
                    "btn_nav_modes" to ModeActivity::class.java,
                    "btn_nav_profiles" to ProfilesActivity::class.java,
                    "btn_nav_capability" to CapabilityActivity::class.java,
                    "btn_nav_setup" to SetupActivity::class.java,
                    "btn_nav_companion" to CompanionActivity::class.java,
                    "btn_nav_display_calibration" to DisplayCalibrationActivity::class.java,
                    "btn_nav_content_type" to ContentTypeActivity::class.java,
                    "btn_nav_extras" to EnhancedAudioSettingsActivity::class.java
                )
            ),
            Screen("manual-eq", ManualEqActivity::class.java, finishes = setOf("btn_manual_eq_done")),
            Screen("extras", EnhancedAudioSettingsActivity::class.java),
            Screen("content-type", ContentTypeActivity::class.java, finishes = setOf("btn_content_done")),
            Screen("mode", ModeActivity::class.java, finishes = setOf("btn_mode_done")),
            Screen("profiles", ProfilesActivity::class.java),
            Screen(
                "capability", CapabilityActivity::class.java,
                opens = mapOf("btn_open_support" to SupportActivity::class.java)
            ),
            Screen("setup", SetupActivity::class.java, finishes = setOf("btn_setup_done")),
            Screen(
                "companion", CompanionActivity::class.java,
                wiredOnly = setOf("btn_companion_start", "Button(Start pairing)")
            ),
            Screen("support", SupportActivity::class.java, finishes = setOf("btn_support_done")),
            Screen("display-calibration", DisplayCalibrationActivity::class.java),
            Screen(
                "measure", MeasureActivity::class.java,
                wiredOnly = setOf("btn_measure_start", "btn_measure_repeat", "btn_measure_add_seat", "btn_measure_check")
            ),
            Screen(
                "first-run", FirstRunActivity::class.java,
                opens = mapOf(
                    "btn_first_run_measure" to MeasureActivity::class.java,
                    "btn_first_run_setup" to SetupActivity::class.java
                ),
                finishes = setOf("btn_first_run_later")
            )
        ).map { arrayOf<Any>(it) }
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

    @Test
    fun opensAndRenders() {
        withScreen { activity ->
            val file = ScreenHarness.render(activity, "$tag-${screen.name}")
            assertTrue("no render for ${screen.name}", file.length() > 0)
            assertTrue("${screen.name} has nothing the remote can focus", ScreenHarness.focusables(activity).isNotEmpty())
        }
    }

    @Test
    fun everyControlIsReachableWithTheDpad() {
        withScreen { activity ->
            val all = ScreenHarness.focusables(activity)
            val reached = ScreenHarness.reachable(activity)
            val missing = all.filterNot { it in reached }.map { ScreenHarness.name(it) }
            assertTrue("${screen.name}: the D-pad cannot reach $missing (reached ${reached.map { ScreenHarness.name(it) }})", missing.isEmpty())
        }
    }

    @Test
    fun everyButtonDoesSomethingYouCanSee() {
        val controls = withScreen { activity -> buttons(activity).map { ScreenHarness.name(it) } }
        assertTrue("${screen.name} has no buttons", controls.isNotEmpty())
        val silent = mutableListOf<String>()
        for (control in controls) {
            val outcome = withScreen { activity -> press(activity, control) }
            if (outcome == null) silent += control
            screen.opens[control]?.let { expected ->
                assertEquals("${screen.name}: $control opens the wrong screen", "opens ${expected.name}", outcome)
            }
            if (control in screen.finishes) {
                assertEquals("${screen.name}: $control must close the screen", "finishes", outcome)
            }
        }
        assertTrue("${screen.name}: these buttons do nothing you can see: $silent", silent.isEmpty())
    }

    /** Visible, enabled controls with a click handler: what a person can press. */
    private fun buttons(activity: Activity): List<View> =
        ScreenHarness.focusables(activity).filter { it.hasOnClickListeners() }

    /** Presses [control] and names what happened, or null when nothing did. */
    private fun press(activity: Activity, control: String): String? {
        val view = buttons(activity).firstOrNull { ScreenHarness.name(it) == control }
            ?: return "gone before it could be pressed"
        if (control in screen.wiredOnly) return if (view.hasOnClickListeners()) "wired" else null
        // A choice that is already the chosen one may rightly change nothing.
        val alreadyChosen = view.isActivated || view.isSelected || (view as? Checkable)?.isChecked == true

        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        while (shadowApp.nextStartedActivity != null) Unit
        while (shadowApp.nextStartedService != null) Unit
        ShadowToast.reset()
        val dialogBefore = ShadowDialog.getLatestDialog()
        val before = snapshot(activity)

        view.performClick()
        ScreenHarness.idle()

        shadowOf(activity).nextStartedActivity?.let { return "opens ${it.component?.className ?: it.action}" }
        shadowApp.nextStartedActivity?.let { return "opens ${it.component?.className ?: it.action}" }
        if (activity.isFinishing) return "finishes"
        val dialog = ShadowDialog.getLatestDialog()
        if (dialog != null && dialog !== dialogBefore && dialog.isShowing) return "dialog"
        shadowApp.nextStartedService?.let { return "service ${it.component?.className ?: it.action}" }
        if (ShadowToast.getLatestToast() != null) return "toast"
        return when {
            snapshot(activity) != before -> "changes the screen"
            alreadyChosen -> "already chosen"
            else -> null
        }
    }

    /** What a person can see of the screen: every view's text and state, in order. */
    private fun snapshot(activity: Activity): List<String> {
        val out = ArrayList<String>()
        fun walk(view: View) {
            val text = (view as? TextView)?.text?.toString().orEmpty()
            out += "${view.javaClass.simpleName}|$text|${view.visibility}|${view.isActivated}|${view.isSelected}|" +
                "${view.isEnabled}|${(view as? Checkable)?.isChecked}|${view.contentDescription}"
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(activity.window.decorView)
        return out
    }
}
