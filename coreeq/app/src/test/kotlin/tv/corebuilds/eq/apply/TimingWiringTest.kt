package tv.corebuilds.eq.apply

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.corebuilds.eq.CapabilityActivity
import tv.corebuilds.eq.MainActivity
import tv.corebuilds.eq.ProfilesActivity
import tv.corebuilds.eq.apply.RuntimeTimings.Op
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.ui.screens.ScreenHarness
import tv.corebuilds.eq.ui.screens.ScreenSeed
import java.io.PrintWriter
import java.io.StringWriter

/**
 * The timings are recorded where the code says they are: the real screens
 * and service under Robolectric. Robolectric's frames and threads are not a
 * TV's, so this proves the wiring, never a duration.
 *
 * Robolectric lays views out but never runs a draw pass, so a first-draw
 * listener would never fire on its own here. The tests call the framework's
 * own draw dispatch (`ViewTreeObserver.dispatchOnDraw`, hidden, by reflection),
 * which is what a real frame calls; that a TV's first frame reaches it is for
 * the device benchmark to show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class TimingWiringTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun recorded() = RuntimeTimings.snapshot().map { it.op }.toSet()

    private fun drawPass(activity: android.app.Activity) {
        android.view.ViewTreeObserver::class.java.getDeclaredMethod("dispatchOnDraw")
            .apply { isAccessible = true }
            .invoke(activity.window.decorView.viewTreeObserver)
    }

    @Before fun tv() {
        ScreenHarness.prepareTv()
        RuntimeTimings.reset()
    }

    @After fun clear() = RuntimeTimings.reset()

    @Test fun `Home records its first draw and its readiness, once each`() {
        ScreenSeed.measuredRoom()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ScreenHarness.idle()
            assertTrue("ready is recorded from content, before any frame", Op.HOME_READY in recorded())
            assertFalse(Op.HOME_FIRST_DRAW in recorded())
            scenario.onActivity { drawPass(it) }
            ScreenHarness.idle()
            scenario.onActivity { drawPass(it) }
            ScreenHarness.idle()
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            ScreenHarness.idle()
        }
        val stats = RuntimeTimings.snapshot().associateBy { it.op }
        assertTrue(recorded().toString(), Op.HOME_READY in stats)
        assertTrue(recorded().toString(), Op.HOME_FIRST_DRAW in stats)
        assertTrue("a second resume is not a second start", stats.getValue(Op.HOME_READY).count == 1L)
        assertTrue(stats.getValue(Op.HOME_FIRST_DRAW).count == 1L)
    }

    @Test fun `Profiles records its first draw and its readiness once the list is laid out`() {
        ScreenSeed.measuredRoom()
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            ScreenHarness.idle()
            assertTrue(recorded().toString(), Op.PROFILES_READY in recorded())
            scenario.onActivity { drawPass(it) }
            ScreenHarness.idle()
        }
        assertTrue(recorded().toString(), Op.PROFILES_FIRST_DRAW in recorded())
    }

    @Test fun `diagnostics carries the timings line`() {
        ScreenSeed.measuredRoom()
        ActivityScenario.launch(ProfilesActivity::class.java).use { ScreenHarness.idle() }
        ActivityScenario.launch(CapabilityActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val text = activity.diagnosticsText()
                val line = text.lines().single { it.startsWith("Timings since start: ") }
                assertTrue(line, line.contains("Profiles ready 1× median "))
            }
        }
    }

    @Test fun `the service dump has counters and timings and no names`() {
        ScreenSeed.measuredRoom()
        ProfileStore(app).saveProfile(ScreenSeed.livingRoom("p-named", "Sam's den").copy(outputKind = "bluetooth", outputName = "Sam's Sonos Roam"))
        RuntimeTimings.record(Op.APPLY, 3_000_000L, RuntimeTimings.Outcome.OK)
        val service = Robolectric.buildService(EqService::class.java).get()
        val out = StringWriter()
        // Service.dump is protected: call it the way dumpsys does, through the framework method.
        val dump = android.app.Service::class.java.getDeclaredMethod(
            "dump", java.io.FileDescriptor::class.java, PrintWriter::class.java, Array<String>::class.java
        ).apply { isAccessible = true }
        PrintWriter(out).use { dump.invoke(service, null, it, emptyArray<String>()) }
        val text = out.toString()
        assertTrue(text, text.contains("Background work: "))
        assertTrue(text, text.contains("  apply: count=1 last=3.0 ms"))
        for (name in listOf("Sam", "Sonos", "Living room")) assertFalse(text, text.contains(name))
    }

    @Test fun `an apply with correction on is timed with its outcome`() {
        ScreenSeed.measuredRoom()
        val store = ProfileStore(app)
        store.correctionEnabled = true
        store.bypassEnabled = false
        val controller = Robolectric.buildService(EqService::class.java).create()
        try {
            // The A/B switch applies at once, without waiting for a route probe.
            controller.get().onStartCommand(
                Intent(app, EqService::class.java).setAction(EqService.ACTION_SET_BYPASS).putExtra(EqService.EXTRA_BYPASS, true),
                0, 1
            )
            ScreenHarness.idle()
        } finally {
            controller.destroy()
        }
        val apply = RuntimeTimings.snapshot().firstOrNull { it.op == Op.APPLY }
        assertTrue("apply was not timed: ${RuntimeTimings.summary()}", apply != null && apply.count >= 1)
    }
}
