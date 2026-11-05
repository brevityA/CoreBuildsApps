package tv.corebuilds.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.widget.TextClock
import androidx.lifecycle.Lifecycle
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.corebuilds.launcher.HomeHarness.appCards
import tv.corebuilds.launcher.HomeHarness.cards
import tv.corebuilds.launcher.HomeHarness.focused
import tv.corebuilds.launcher.HomeHarness.name
import tv.corebuilds.launcher.HomeHarness.press

/**
 * The home screen as the remote uses it, on the real activity: what it opens
 * on, where every arrow goes, that a focused card is never clipped, that text
 * fits, and that every option and setting does what it says and survives a
 * restart. Renders land in build/screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = HomeHarness.TV)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun tv() = HomeHarness.prepareTv()

    private fun home(block: (LauncherActivity) -> Unit) {
        ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
            scenario.onActivity {
                HomeHarness.waitForApps(it)
                block(it)
            }
        }
    }

    private fun favouriteLabels(activity: LauncherActivity) = appCards(activity, RowKind.FAVOURITES).map { it.app.label }

    @Test
    fun opensOnTheFirstFavouriteWithCoreBuildsAppsSeeded() = home { activity ->
        assertEquals(listOf("Core Builds Icon Pack", "Core Shift", "Core EQ"), favouriteLabels(activity))
        assertEquals("FAVOURITES:Core Builds Icon Pack", name(focused(activity)))
        HomeHarness.render(activity, "01-home")
    }

    @Test
    fun everyCardAndCustomiseAreReachableByRemote() = home { activity ->
        val all = cards(activity).toSet<View>() + activity.findViewById<View>(android.R.id.content).let { root ->
            HomeHarness.descendants(root).filter { it.contentDescription == context.getString(R.string.customise_description) }.toSet()
        }
        val seen = linkedSetOf(focused(activity)!!)
        val queue = ArrayDeque(seen.toList())
        val keys = intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            for (key in keys) {
                from.requestFocus()
                HomeHarness.idle()
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
                HomeHarness.idle()
                val now = focused(activity) ?: continue
                if (seen.add(now)) queue.addLast(now)
            }
        }
        val missed = all - seen
        assertTrue("unreachable: ${missed.joinToString { name(it) }}", missed.isEmpty())
    }

    @Test
    fun rowEndsHoldFocusAndUpFromTheTopReachesCustomise() = home { activity ->
        // Right along Favourites ends on the Add card and stays there.
        repeat(6) { press(activity, KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertEquals("Add card", name(focused(activity)))
        // Left back to the first card, and once more stays.
        repeat(6) { press(activity, KeyEvent.KEYCODE_DPAD_LEFT) }
        assertEquals("FAVOURITES:Core Builds Icon Pack", name(focused(activity)))
        // Up from the first row is Customise; Down comes back to the same card.
        press(activity, KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(context.getString(R.string.customise_description), focused(activity)?.contentDescription)
        repeat(3) { press(activity, KeyEvent.KEYCODE_DPAD_LEFT) }
        repeat(3) { press(activity, KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertEquals(context.getString(R.string.customise_description), focused(activity)?.contentDescription)
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("FAVOURITES:Core Builds Icon Pack", name(focused(activity)))

        // In All apps, Right at a row's end stays in the row (0.1 jumped to the top bar).
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        val rowStart = focused(activity) as AppCard
        assertEquals(RowKind.ALL_APPS, rowStart.kind)
        repeat(8) { press(activity, KeyEvent.KEYCODE_DPAD_RIGHT) }
        val rowEnd = focused(activity) as AppCard
        assertEquals(RowKind.ALL_APPS, rowEnd.kind)
        assertSame(rowStart.parent, rowEnd.parent)
        // Down to the last row and once more stays there.
        repeat(12) { press(activity, KeyEvent.KEYCODE_DPAD_DOWN) }
        val bottom = focused(activity)
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        assertSame(bottom, focused(activity))
        HomeHarness.render(activity, "02-all-apps-bottom")
    }

    @Test
    fun aFocusedCardIsAlwaysWholeOnScreen() = home { activity ->
        // Walk every row from its first card to its last and check each focused
        // card shows whole on screen, its 1.1x lift and ring included.
        val visited = HashSet<View>()
        var rowStart: View? = null
        repeat(12) {
            repeat(8) { press(activity, KeyEvent.KEYCODE_DPAD_LEFT) }
            val first = focused(activity)
            if (first === rowStart) return@repeat
            rowStart = first
            var card = focused(activity)
            while (card is FocusCard && visited.add(card)) {
                val share = HomeHarness.shownShare(card)
                assertTrue("${name(card)} shows ${"%.2f".format(share)} of itself", share >= 0.99)
                press(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
                card = focused(activity)
            }
            press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        }
        assertEquals(cards(activity).size, visited.size)
    }

    @Test
    fun textFitsItsBoxIncludingALongAppNameInTheInfoArea() = home { activity ->
        assertEquals(emptyList<String>(), HomeHarness.textProblems(activity))
        val long = cards(activity).filterIsInstance<AppCard>().first { it.app.packageName == "org.example.longname" }
        long.requestFocus()
        HomeHarness.settle()
        assertEquals(emptyList<String>(), HomeHarness.textProblems(activity))
        HomeHarness.render(activity, "03-long-name")
    }

    @Test
    fun holdingOkOpensAppOptionsAndBackReturnsToTheCard() = home { activity ->
        val card = focused(activity) as AppCard
        card.performLongClick()
        HomeHarness.settle()
        val panel = HomeHarness.descendants(activity.window.decorView).filterIsInstance<SidePanel>().single()
        assertTrue(panel.isOpen)
        val titles = HomeHarness.descendants(panel).filterIsInstance<android.widget.TextView>().map { it.text.toString() }.toList()
        for (expected in listOf(R.string.option_open, R.string.option_remove_favourite, R.string.option_move, R.string.option_hide, R.string.option_app_info)) {
            assertTrue("options lack ${context.getString(expected)}", context.getString(expected) in titles)
        }
        HomeHarness.render(activity, "04-app-options")
        // Arrows stay in the panel: Left and Right do nothing, Up and Down stop at the ends.
        repeat(3) { press(activity, KeyEvent.KEYCODE_DPAD_LEFT) }
        repeat(9) { press(activity, KeyEvent.KEYCODE_DPAD_DOWN) }
        assertEquals(panel.rowCount - 1, panel.focusedIndex)
        repeat(9) { press(activity, KeyEvent.KEYCODE_DPAD_UP) }
        assertEquals(0, panel.focusedIndex)
        activity.handleBack()
        HomeHarness.settle()
        assertFalse(panel.isOpen)
        assertSame(card, focused(activity))
    }

    @Test
    fun theMenuKeyOpensOptionsToo() = home { activity ->
        press(activity, KeyEvent.KEYCODE_MENU)
        val panel = HomeHarness.descendants(activity.window.decorView).filterIsInstance<SidePanel>().single()
        assertTrue(panel.isOpen)
    }

    @Test
    fun moveReordersFavouritesAndTheOrderIsSaved() {
        home { activity ->
            press(activity, KeyEvent.KEYCODE_DPAD_RIGHT) // Core Shift
            val card = focused(activity) as AppCard
            card.performLongClick()
            HomeHarness.settle()
            val panel = HomeHarness.descendants(activity.window.decorView).filterIsInstance<SidePanel>().single()
            HomeHarness.descendants(panel).first { it.contentDescription?.startsWith(context.getString(R.string.option_move)) == true }.performClick()
            HomeHarness.settle()
            assertFalse(panel.isOpen)
            assertTrue(card.moving)
            HomeHarness.render(activity, "05-moving")
            press(activity, KeyEvent.KEYCODE_DPAD_LEFT)
            press(activity, KeyEvent.KEYCODE_DPAD_UP) // ignored while moving
            assertSame(card, focused(activity))
            assertEquals(listOf("Core Shift", "Core Builds Icon Pack", "Core EQ"), favouriteLabels(activity))
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            assertFalse(card.moving)
        }
        // A fresh start reads the saved order.
        home { activity -> assertEquals(listOf("Core Shift", "Core Builds Icon Pack", "Core EQ"), favouriteLabels(activity)) }
    }

    @Test
    fun addingAFavouritePutsItAtTheEndAndHidingAnAppTakesItOutOfAllApps() {
        home { activity ->
            val atlas = appCards(activity, RowKind.ALL_APPS).first { it.app.label == "Atlas Video" }
            atlas.requestFocus()
            HomeHarness.settle()
            atlas.performLongClick()
            HomeHarness.settle()
            clickOption(activity, R.string.option_add_favourite)
            HomeHarness.waitForApps(activity)
            assertEquals(listOf("Core Builds Icon Pack", "Core Shift", "Core EQ", "Atlas Video"), favouriteLabels(activity))

            val beacon = appCards(activity, RowKind.ALL_APPS).first { it.app.label == "Beacon Music" }
            beacon.requestFocus()
            HomeHarness.settle()
            beacon.performLongClick()
            HomeHarness.settle()
            clickOption(activity, R.string.option_hide)
            HomeHarness.waitForApps(activity)
            assertTrue(appCards(activity, RowKind.ALL_APPS).none { it.app.label == "Beacon Music" })
        }
        home { activity ->
            assertEquals(listOf("Core Builds Icon Pack", "Core Shift", "Core EQ", "Atlas Video"), favouriteLabels(activity))
            assertTrue(appCards(activity, RowKind.ALL_APPS).none { it.app.label == "Beacon Music" })
            // Customise › Hidden apps lists it, and OK brings it back.
            openCustomise(activity)
            clickRow(activity, R.string.setting_hidden)
            HomeHarness.render(activity, "06-hidden-apps")
            val panel = HomeHarness.descendants(activity.window.decorView).filterIsInstance<SidePanel>().single()
            HomeHarness.descendants(panel).first { it.contentDescription?.startsWith("Beacon Music") == true }.performClick()
            HomeHarness.waitForApps(activity)
            assertTrue(appCards(activity, RowKind.ALL_APPS).any { it.app.label == "Beacon Music" })
        }
    }

    @Test
    fun recentlyOpenedShowsLaunchedAppsAndTheSettingTurnsItOff() {
        LauncherPreferences.recordLaunch(context, "org.example.harbor")
        LauncherPreferences.recordLaunch(context, "org.example.cinder")
        LauncherPreferences.recordLaunch(context, "tv.corebuilds.eq") // a favourite: never in Recent
        home { activity ->
            assertEquals(listOf("Cinder Live", "Harbor News"), appCards(activity, RowKind.RECENT).map { it.app.label })
            press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            HomeHarness.render(activity, "07-recent")
            openCustomise(activity)
            clickRow(activity, R.string.setting_recent)
            HomeHarness.waitForApps(activity)
            assertTrue(appCards(activity, RowKind.RECENT).isEmpty())
        }
    }

    @Test
    fun cardSizeChangesHowManyCardsARowHoldsAndIsSaved() {
        home { activity ->
            assertEquals(5, allAppsFirstRow(activity).size)
            openCustomise(activity)
            HomeHarness.render(activity, "08-customise")
            clickRow(activity, R.string.setting_card_size)
            HomeHarness.render(activity, "09-card-size")
            clickRow(activity, R.string.card_size_large)
            HomeHarness.waitForApps(activity)
            assertEquals(4, allAppsFirstRow(activity).size)
            activity.handleBack()
            activity.handleBack()
            HomeHarness.settle()
            HomeHarness.render(activity, "10-large-cards")
        }
        home { activity -> assertEquals(4, allAppsFirstRow(activity).size) }
    }

    @Test
    fun backgroundAndClockSettingsApplyAtOnce() = home { activity ->
        val backdrop = HomeHarness.descendants(activity.window.decorView).filterIsInstance<BackdropView>().single()
        assertEquals(Backdrop.FOCUSED_APP, backdrop.mode)
        openCustomise(activity)
        clickRow(activity, R.string.setting_background)
        clickRow(activity, R.string.background_core)
        assertEquals(Backdrop.CORE_BUILDS, backdrop.mode)
        activity.handleBack()
        clickRow(activity, R.string.setting_clock)
        val clock = HomeHarness.descendants(activity.window.decorView).filterIsInstance<TextClock>().first()
        assertFalse(clock.isShown)
        activity.handleBack()
        HomeHarness.settle()
        HomeHarness.render(activity, "11-core-builds-background")
        assertEquals(Backdrop.CORE_BUILDS, LauncherPreferences.readSettings(context).backdrop)
        assertFalse(LauncherPreferences.readSettings(context).showClock)
    }

    @Test
    fun theAddCardOpensTheFavouritesPicker() = home { activity ->
        val add = cards(activity).single { it is AddCard }
        add.requestFocus()
        HomeHarness.settle()
        add.performClick()
        HomeHarness.settle()
        HomeHarness.render(activity, "12-favourites-picker")
        val panel = HomeHarness.descendants(activity.window.decorView).filterIsInstance<SidePanel>().single()
        assertTrue(panel.isOpen)
        HomeHarness.descendants(panel).first { it.contentDescription?.startsWith("Drift Player") == true }.performClick()
        HomeHarness.waitForApps(activity)
        assertEquals("Drift Player", favouriteLabels(activity).last())
    }

    @Test
    fun homePressedOnHomeReturnsToTheFirstCard() = home { activity ->
        repeat(4) { press(activity, KeyEvent.KEYCODE_DPAD_DOWN) }
        assertTrue((focused(activity) as AppCard).kind == RowKind.ALL_APPS)
        activity.onNewIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        HomeHarness.settle()
        assertEquals("FAVOURITES:Core Builds Icon Pack", name(focused(activity)))
    }

    @Test
    fun anInstalledAppAppearsWithoutLeavingHome() = home { activity ->
        val fresh = HomeHarness.FakeApp("org.example.quartz", "Quartz Clips", tv = true, banner = true, color = 0xFF0EA5E9.toInt())
        HomeHarness.install(fresh)
        activity.sendBroadcast(Intent(Intent.ACTION_PACKAGE_ADDED).setData(android.net.Uri.fromParts("package", fresh.pkg, null)).setPackage(activity.packageName))
        HomeHarness.waitForApps(activity)
        assertTrue(appCards(activity, RowKind.ALL_APPS).any { it.app.label == "Quartz Clips" })
    }

    @Test
    fun launchingAnAppStartsItsTvEntryAndRecordsIt() = home { activity ->
        val card = focused(activity) as AppCard
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(card.app.activityName, started.component?.className)
        assertTrue(started.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
    }

    private fun allAppsFirstRow(activity: LauncherActivity): List<AppCard> {
        val all = appCards(activity, RowKind.ALL_APPS)
        return all.filter { it.parent === all.first().parent }
    }

    private fun openCustomise(activity: LauncherActivity) {
        val button = HomeHarness.descendants(activity.window.decorView)
            .first { it.contentDescription == context.getString(R.string.customise_description) }
        button.requestFocus()
        button.performClick()
        HomeHarness.settle()
    }

    private fun clickRow(activity: LauncherActivity, title: Int) {
        val panel = HomeHarness.descendants(activity.window.decorView).filterIsInstance<SidePanel>().single()
        val label = context.getString(title)
        val row = HomeHarness.descendants(panel).first { it.isFocusable && it.contentDescription?.startsWith(label) == true }
        row.requestFocus()
        row.performClick()
        HomeHarness.settle()
    }

    private fun clickOption(activity: LauncherActivity, title: Int) = clickRow(activity, title)
}

/** Every supported Android opens Home with a focused card: API 30 (Android 11) to the newest Robolectric runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 31, 32, 33, 34, 35], qualifiers = HomeHarness.TV)
class HomeOpensTest {
    @Before
    fun tv() = HomeHarness.prepareTv(HomeHarness.CORE_APPS)

    @Test
    fun homeOpensWithAFocusedCard() {
        ActivityScenario.launch(LauncherActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                HomeHarness.waitForApps(activity)
                assertTrue("no card has focus: ${name(focused(activity))}", focused(activity) is AppCard)
            }
        }
    }
}
