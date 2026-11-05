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
    fun theGooglePlayIconPackIsCoreBuildsBannersToo() {
        // An owner's Google TV (Android 11) had only the Play build, tv.corebuilds.iconpack.play,
        // and its glyphs companion: 0.2 seeded no icon pack and showed app art by default.
        val installed = listOf("tv.corebuilds.iconpack.play", "tv.corebuilds.iconpack.glyphs", "tv.corebuilds.eq")
        assertEquals(listOf("tv.corebuilds.iconpack.play", "tv.corebuilds.eq"), LauncherPreferences.snapshot(context, installed).favourites)
        val packs = listOf(
            IconPackInfo("org.example.pack", "A pack"),
            IconPackInfo("tv.corebuilds.iconpack.glyphs", "Core Builds Glyphs"),
            IconPackInfo("tv.corebuilds.iconpack.play", "Core Builds Icon Pack"),
        )
        assertEquals("tv.corebuilds.iconpack.play", IconPacks.resolve(ArtworkChoice.Automatic, packs)?.packageName)
        // With both builds installed, the sideload one is the default, and a TV gets one seeded card.
        val both = packs + IconPackInfo("tv.corebuilds.iconpack", "Core Builds Icon Pack")
        assertEquals("tv.corebuilds.iconpack", IconPacks.resolve(ArtworkChoice.Automatic, both)?.packageName)
        LauncherPreferences.reset(context)
        assertEquals(listOf("tv.corebuilds.iconpack"),
            LauncherPreferences.snapshot(context, listOf("tv.corebuilds.iconpack.play", "tv.corebuilds.iconpack")).favourites)
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
        // This test pins the legacy row; the default 0.3 Suggested row ranks events.
        LauncherPreferences.saveSettings(context, HomeSettings(smartSuggestions = false))
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
        val changed = HomeSettings(
            ArtworkChoice.IconPack("pack.x"), Backdrop.VOID, Dim.STRONG, CardSize.COMPACT,
            showRecents = false, showClock = false,
            motion = MotionMode.CALM, smartSuggestions = false, appSort = AppSort.TV_FIRST,
            wallpaperUrl = "https://example.invalid/wallpaper.jpg", launchOnBoot = true,
        )
        LauncherPreferences.saveSettings(context, changed)
        assertEquals(changed, LauncherPreferences.readSettings(context))
        LauncherPreferences.reset(context)
        assertEquals(HomeSettings(), LauncherPreferences.readSettings(context))
    }

    @Test
    fun aVersionedLayoutBackupRestoresOnlyKnownLauncherKeys() {
        LauncherPreferences.saveSettings(context, HomeSettings(
            motion = MotionMode.OFF,
            appSort = AppSort.MOST_USED,
            wallpaperUrl = "https://example.invalid/home.jpg",
            launchOnBoot = true,
        ))
        LauncherPreferences.setFavourite(context, "org.example.atlas", true, listOf("org.example.atlas"))
        val backup = LauncherBackup.export(context)
        prefs.edit().putString("unknown_key", "must not be restored").commit()
        val backupWithUnknownKey = LauncherBackup.export(context)
        assertTrue(backupWithUnknownKey.contains("unknown_key"))
        LauncherPreferences.reset(context)
        // A foreign key already on this device must also be cleared by a restore.
        prefs.edit().putString("unknown_key", "must not survive").commit()
        assertTrue(LauncherBackup.restore(context, backupWithUnknownKey))
        assertEquals(MotionMode.OFF, LauncherPreferences.readSettings(context).motion)
        assertEquals(AppSort.MOST_USED, LauncherPreferences.readSettings(context).appSort)
        assertEquals("https://example.invalid/home.jpg", LauncherPreferences.readSettings(context).wallpaperUrl)
        assertTrue(LauncherPreferences.readSettings(context).launchOnBoot)
        assertEquals(listOf("org.example.atlas"), LauncherPreferences.snapshot(context, listOf("org.example.atlas")).favourites)
        assertFalse(prefs.contains("unknown_key"))
        assertFalse(LauncherBackup.restore(context, "{}"))
        assertFalse(LauncherBackup.restore(context, "x".repeat(LauncherBackup.MAX_BYTES + 1)))
    }

    @Test
    fun localAppSearchRanksExactPrefixWordAndInitialMatches() {
        val apps = listOf(
            fakeApp("Core Builds Icon Pack", "tv.corebuilds.iconpack"),
            fakeApp("Core EQ", "tv.corebuilds.eq"),
            fakeApp("Harbor News", "org.example.harbor"),
            fakeApp("Atlas Video", "org.example.atlas"),
        )
        assertEquals("Harbor News", AppSearch.rank(apps, "harbor").first().label)
        assertEquals("Core EQ", AppSearch.rank(apps, "eq").first().label)
        assertEquals("Core Builds Icon Pack", AppSearch.rank(apps, "cbip").first().label)
        assertEquals("Atlas Video", AppSearch.rank(apps, "atlas").first().label)
        assertTrue(AppSearch.rank(apps, "not-installed").isEmpty())
        val preferred = setOf("org.example.atlas")
        assertEquals("Atlas Video", AppSearch.rank(apps, "", preferred).first().label)
    }

    @Test
    fun connectedTvSettingsRoundTripAndBackupKeepOwnerEnteredChoices() {
        val changed = HomeSettings(
            wallpaperUrl = "https://example.invalid/tv.jpg",
            launchOnBoot = true,
            smartSuggestions = false,
            appSort = AppSort.MOST_USED,
        )
        LauncherPreferences.saveSettings(context, changed)
        val backup = LauncherBackup.export(context)
        LauncherPreferences.reset(context)
        assertTrue(LauncherBackup.restore(context, backup))
        assertEquals(changed, LauncherPreferences.readSettings(context))
    }

    @Test
    fun networkWallpaperAcceptsOnlyHttpUrlsAndRejectsUnsafeSchemes() {
        assertTrue(NetworkWallpaper.isAllowedUrl("https://example.invalid/wall.jpg"))
        assertTrue(NetworkWallpaper.isAllowedUrl("http://example.invalid/wall.jpg"))
        assertFalse(NetworkWallpaper.isAllowedUrl("file:///sdcard/wall.jpg"))
        assertFalse(NetworkWallpaper.isAllowedUrl("content://media/wall.jpg"))
        assertFalse(NetworkWallpaper.isAllowedUrl("not a url"))
        assertFalse(NetworkWallpaper.isAllowedUrl(""))
    }

    private fun fakeApp(label: String, packageName: String): LaunchableApp = LaunchableApp(
        packageName,
        ".Main",
        label,
        tvOptimized = false,
        lastUpdateTime = 0L,
        art = CardArt(null, null, Color.RED, null, ArtSource.APP),
    )

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
    fun customArtRoundTripsAndAnIconBrowserResultIsUnderstood() {
        assertEquals(CustomArt.PackResource("tv.corebuilds.iconpack", "atlas_banner"), CustomArt.parse("res|tv.corebuilds.iconpack|atlas_banner"))
        assertEquals(CustomArt.Image("a.png"), CustomArt.parse(CustomArt.Image("a.png").stored()))
        assertNull(CustomArt.parse("file|../escape.png"))
        assertNull(CustomArt.parse("nonsense"))

        assertNull(LauncherPreferences.setCustomArt(context, "org.example.atlas", CustomArt.PackResource("p", "x")))
        assertEquals(CustomArt.PackResource("p", "x"), LauncherPreferences.setCustomArt(context, "org.example.atlas", CustomArt.Image("a.png")))
        assertEquals(mapOf("org.example.atlas" to CustomArt.Image("a.png")), LauncherPreferences.snapshot(context, listOf("org.example.atlas")).customArt)

        // The Core Builds pack answers ACTION_PICK_ICON with a resource reference...
        val reference = android.content.Intent().putExtra(
            android.content.Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
            android.content.Intent.ShortcutIconResource().apply {
                packageName = "tv.corebuilds.iconpack"
                resourceName = "tv.corebuilds.iconpack:drawable/atlas_banner"
            },
        )
        assertEquals(CustomArt.PackResource("tv.corebuilds.iconpack", "atlas_banner"),
            CustomArtStore.fromPickResult(context, reference, "org.example.atlas"))
        // ...and a pack that only sends a bitmap gets it saved here.
        val bitmapOnly = android.content.Intent().putExtra("icon", Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
        val saved = CustomArtStore.fromPickResult(context, bitmapOnly, "org.example.atlas") as CustomArt.Image
        assertNotNull(CustomArtStore.load(context, saved))
        CustomArtStore.discard(context, saved)
        assertNull(CustomArtStore.load(context, saved))
        assertNull(CustomArtStore.fromPickResult(context, android.content.Intent(), "org.example.atlas"))
    }

    @Test
    fun appfilterListsEveryDrawableAPackMapsToAnApp() {
        val xml = """
            <resources>
              <item component="ComponentInfo{org.example.atlas/.TvMain}" drawable="atlas_banner"/>
              <item component="ComponentInfo{org.example.atlas/.Main}" drawable="atlas_glyph"/>
              <item component="ComponentInfo{org.example.atlas/.Other}" drawable="atlas_banner"/>
            </resources>
        """.trimIndent()
        val filter = IconPacks.parse(Xml.newPullParser().apply { setInput(StringReader(xml)) })
        assertEquals(listOf("atlas_banner", "atlas_glyph"), filter.allByPackage["org.example.atlas"])
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
        assertEquals(ArtSource.PACK, fromPackBanner.source)
        val fromPackGlyph = CardArtFactory.create(spec, square, { wide }, { square })
        assertNull(fromPackGlyph.banner)
        assertEquals(76, fromPackGlyph.icon!!.width)
        assertEquals(Color.GREEN, fromPackGlyph.tint)
        val ownBanner = CardArtFactory.create(spec, null, { wide }, { square })
        assertEquals(ArtSource.APP, ownBanner.source)
        assertNotNull(ownBanner.banner)
    }
}
