package tv.corebuilds.iconpack.screens

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.corebuilds.iconpack.AboutActivity
import tv.corebuilds.iconpack.AuditorActivity
import tv.corebuilds.iconpack.ExportProgressActivity
import tv.corebuilds.iconpack.FaqActivity
import tv.corebuilds.iconpack.InspectorActivity
import tv.corebuilds.iconpack.LauncherCompatActivity
import tv.corebuilds.iconpack.LauncherSetupActivity
import tv.corebuilds.iconpack.Plus
import tv.corebuilds.iconpack.PlusStore
import tv.corebuilds.iconpack.R
import tv.corebuilds.iconpack.RequestsActivity
import tv.corebuilds.iconpack.SettingsActivity
import tv.corebuilds.iconpack.SuiteActivity
import tv.corebuilds.iconpack.WallpaperCatalog
import tv.corebuilds.iconpack.WallpaperPreviewActivity
import tv.corebuilds.iconpack.WhatsNewActivity

/**
 * The other twelve screens (2.6.0 restyle), rendered on a 1080p TV for review:
 * build/screens/<screen>-<play|sideload>.png. PreviewRenderTest has Home,
 * Wallpapers and Core Premium. Each screen opens the way the app opens it,
 * with the extras its caller passes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AllScreensRenderTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val variant = if (PlusStore.AVAILABLE) "play" else "sideload"

    @Before
    fun tv() = ScreenHarness.prepareTv()

    private fun <A : Activity> shoot(type: Class<A>, name: String, extras: Intent.() -> Unit = {}) {
        val intent = Intent(app, type).apply(extras)
        ActivityScenario.launch<A>(intent).use { scenario ->
            scenario.onActivity { activity ->
                ScreenHarness.idle()
                assertTrue(ScreenHarness.render(activity, "$name-$variant").length() > 0)
            }
        }
    }

    @Test fun settings() = shoot(SettingsActivity::class.java, "settings")
    @Test fun about() = shoot(AboutActivity::class.java, "about")
    @Test fun missingIcons() = shoot(AuditorActivity::class.java, "missing-icons")
    @Test fun faq() = shoot(FaqActivity::class.java, "faq")
    @Test fun launcherCompat() = shoot(LauncherCompatActivity::class.java, "launcher-compat")
    @Test fun suite() = shoot(SuiteActivity::class.java, "suite")
    @Test fun whatsNew() = shoot(WhatsNewActivity::class.java, "whats-new")

    @Test
    fun inspector() {
        val drawables = app.resources.getStringArray(R.array.icon_pack)
        val names = app.resources.getStringArray(R.array.icon_names)
        shoot(InspectorActivity::class.java, "inspector") {
            putExtra(InspectorActivity.EXTRA_DRAWABLE, drawables[0])
            putExtra(InspectorActivity.EXTRA_NAME, names[0])
            putExtra(InspectorActivity.EXTRA_CATEGORY, "VOD")
        }
    }

    @Test
    fun launcherSetup() = shoot(LauncherSetupActivity::class.java, "launcher-setup") {
        putExtra(LauncherSetupActivity.EXTRA_LAUNCHER, "monet")
    }

    @Test
    fun wallpaperPreview() {
        val walls = ArrayList(WallpaperCatalog.load(app))
        shoot(WallpaperPreviewActivity::class.java, "wallpaper-preview") {
            putParcelableArrayListExtra(WallpaperPreviewActivity.EXTRA_WALLPAPERS, walls)
            putExtra(WallpaperPreviewActivity.EXTRA_INDEX, 0)
        }
    }

    @Test
    fun exportProgress() {
        val walls = ArrayList(WallpaperCatalog.load(app).take(3))
        shoot(ExportProgressActivity::class.java, "export-progress") {
            putParcelableArrayListExtra(ExportProgressActivity.EXTRA_WALLPAPERS, walls)
        }
    }

    /** My requests exists only where Premium is, and opens only while it is active. */
    @Test
    fun myRequests() {
        if (!PlusStore.AVAILABLE) return
        Plus.startTrial(app)
        shoot(RequestsActivity::class.java, "my-requests")
    }
}
