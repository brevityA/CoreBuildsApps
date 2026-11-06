package tv.corebuilds.iconpack.screens

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import tv.corebuilds.iconpack.MainActivity
import tv.corebuilds.iconpack.PlusState
import tv.corebuilds.iconpack.PlusStore
import tv.corebuilds.iconpack.SupportActivity
import tv.corebuilds.iconpack.WallpapersActivity

/**
 * Renders the three screens the UI polish was previewed on: Home, Wallpapers and
 * Support with the Core Premium card. Every build type renders Home and
 * Wallpapers; the Premium card exists only where [PlusStore.AVAILABLE] (the play
 * build type), so `testBannersPlayUnitTest` also renders it for sale and owned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewRenderTest {

    @Before
    fun tv() = ScreenHarness.prepareTv()

    /** The TV most people have: Projectivy installed, so Apply reads "Apply". */
    private fun withProjectivy() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pkg = "com.spocky.projengmenu"
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg }
        })
    }

    private fun <A : Activity> shoot(type: Class<A>, name: String, setUp: (A) -> Unit = {}) {
        ActivityScenario.launch(type).use { scenario ->
            scenario.onActivity { activity ->
                ScreenHarness.idle()
                setUp(activity)
                ScreenHarness.idle()
                assertTrue(ScreenHarness.render(activity, name).length() > 0)
            }
        }
    }

    private val variant = if (PlusStore.AVAILABLE) "play" else "sideload"

    @Test
    fun home() {
        withProjectivy()
        shoot(MainActivity::class.java, "home-$variant")
    }

    /** No supported launcher: the longest Apply state the rail has to hold. */
    @Test
    fun homeNoLauncher() = shoot(MainActivity::class.java, "home-nolauncher-$variant")

    @Test
    fun wallpapers() = shoot(WallpapersActivity::class.java, "wallpapers-$variant")

    @Test
    fun support() = shoot(SupportActivity::class.java, "support-$variant") { activity ->
        // The Premium card's state comes from Google Play, which a JVM test has no
        // connection to: show it as on sale, the state most buyers meet first.
        if (PlusStore.AVAILABLE) renderPlus(activity, PlusState.ForSale("$4.99"))
    }

    @Test
    fun supportOwned() {
        assumeTrue("Google Play build only", PlusStore.AVAILABLE)
        shoot(SupportActivity::class.java, "support-play-owned") { renderPlus(it, PlusState.Owned) }
    }

    private fun renderPlus(activity: SupportActivity, state: PlusState) {
        val render = SupportActivity::class.java.getDeclaredMethod("render", PlusState::class.java)
        render.isAccessible = true
        render.invoke(activity, state)
    }

    @Suppress("unused")
    private fun focusFirst(activity: Activity) = ScreenHarness.press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
}
