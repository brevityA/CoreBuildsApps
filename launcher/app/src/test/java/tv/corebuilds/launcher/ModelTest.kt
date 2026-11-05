package tv.corebuilds.launcher

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

/** The saved layout and the icon-pack contract, without a screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = context.getSharedPreferences("launcher_private_preferences", Context.MODE_PRIVATE)

    @Test
    fun firstRunSeedsTheInstalledCoreBuildsApps() {
        val state = LauncherPreferences.snapshot(context, listOf("org.example.a", "tv.corebuilds.eq", "tv.corebuilds.iconpack"))
        assertEquals(listOf("tv.corebuilds.iconpack", "tv.corebuilds.eq"), state.favourites)
    }

    @Test
    fun a01FavouriteSetMigratesInTheOrder01ShowedItAndKeepsUninstalledOnes() {
        prefs.edit().putStringSet("pinned_packages", setOf("z.app", "b.app", "gone.app")).commit()
        val installed = listOf("b.app", "m.app", "z.app") // A-Z by label
        assertEquals(listOf("b.app", "z.app"), LauncherPreferences.snapshot(context, installed).favourites)
        assertFalse(prefs.contains("pinned_packages"))
        // The uninstalled favourite is remembered and comes back when reinstalled.
        assertEquals(listOf("b.app", "z.app", "gone.app"), LauncherPreferences.snapshot(context, installed + "gone.app").favourites)
    }

    @Test
    fun reorderingKeepsAnUninstalledFavouritesSlot() {
        val installed = listOf("a", "b", "c")
        LauncherPreferences.setFavourite(context, "a", true, installed)
        LauncherPreferences.setFavourite(context, "gone", true, installed + "gone")
        LauncherPreferences.setFavourite(context, "b", true, installed)
        LauncherPreferences.setFavourite(context, "c", true, installed)
        LauncherPreferences.reorderFavourites(context, listOf("c", "a", "b"), installed)
        assertEquals(listOf("c", "a", "b"), LauncherPreferences.snapshot(context, installed).favourites)
        assertEquals(listOf("c", "gone", "a", "b"), LauncherPreferences.snapshot(context, installed + "gone").favourites)
    }

    @Test
    fun recentsAreFiveLaunchableAppsNewestFirstWithoutFavouritesOrHiddenApps() {
        val installed = (1..8).map { "app$it" }
        installed.forEach { LauncherPreferences.recordLaunch(context, it) }
        LauncherPreferences.setFavourite(context, "app8", true, installed)
        LauncherPreferences.setHidden(context, "app7", true)
        assertEquals(listOf("app6", "app5", "app4"), LauncherPreferences.snapshot(context, installed).recents)
        LauncherPreferences.recordLaunch(context, "app5")
        assertEquals(listOf("app5", "app6", "app4"), LauncherPreferences.snapshot(context, installed).recents)
    }

    @Test
    fun settingsRoundTripAndResetGoesBackToFirstRun() {
        val changed = HomeSettings(ArtworkChoice.IconPack("pack.x"), Backdrop.VOID, Dim.STRONG, CardSize.COMPACT, showRecents = false, showClock = false)
        LauncherPreferences.saveSettings(context, changed)
        assertEquals(changed, LauncherPreferences.readSettings(context))
        LauncherPreferences.reset(context)
        assertEquals(HomeSettings(), LauncherPreferences.readSettings(context))
    }

    @Test
    fun appfilterMapsComponentsAndFallsBackByPackage() {
        val xml = """
            <resources>
              <iconback img1="back"/>
              <item component="ComponentInfo{org.example.atlas/.TvMain}" drawable="atlas_banner"/>
              <item component="ComponentInfo{org.example.atlas/org.example.atlas.Main}" drawable="atlas_phone"/>
              <item component="ComponentInfo{org.example.beacon/org.example.beacon.Main}" drawable="beacon_banner"/>
              <item component=":LAUNCHER_ACTION_APP_DRAWER" drawable="drawer"/>
              <item component="ComponentInfo{broken}" drawable="nothing"/>
            </resources>
        """.trimIndent()
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        val (byComponent, byPackage) = IconPacks.parse(parser)
        assertEquals("atlas_banner", byComponent["org.example.atlas/org.example.atlas.TvMain"])
        assertEquals(3, byComponent.size)
        assertEquals("beacon_banner", byPackage["org.example.beacon"])
        val loaded = mutableListOf<String>()
        val pack = IconPack("pack", byComponent, byPackage) { name -> loaded += name; ColorDrawable(Color.RED) }
        assertNotNull(pack.drawableFor("org.example.atlas", "org.example.atlas.TvMain"))
        // A TV entry the pack does not list falls back to the package's first mapping.
        assertNotNull(pack.drawableFor("org.example.beacon", "org.example.beacon.TvMain"))
        assertNull(pack.drawableFor("org.example.unknown", "org.example.unknown.Main"))
        assertEquals(listOf("atlas_banner", "beacon_banner"), loaded)
    }

    @Test
    fun wideArtFillsTheCardAndSquareArtSitsOnATintedPlate() {
        val spec = ArtSpec(320, 180, 76, "test")
        val wide = BitmapDrawable(context.resources, Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) })
        val square = BitmapDrawable(context.resources, Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) })
        val fromPackBanner = CardArtFactory.create(spec, wide, { null }, { square })
        assertNotNull(fromPackBanner.banner)
        assertEquals(320, fromPackBanner.banner!!.width)
        assertNotNull(fromPackBanner.backdrop)
        assertTrue(fromPackBanner.fromPack)
        val fromPackGlyph = CardArtFactory.create(spec, square, { wide }, { square })
        assertNull(fromPackGlyph.banner)
        assertEquals(76, fromPackGlyph.icon!!.width)
        assertEquals(Color.GREEN, fromPackGlyph.tint)
        val ownBanner = CardArtFactory.create(spec, null, { wide }, { square })
        assertFalse(ownBanner.fromPack)
        assertNotNull(ownBanner.banner)
    }
}
