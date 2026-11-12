package tv.corebuilds.eq.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.export.ProfileStore

/**
 * The evidence Home and diagnostics read (`docs/CORE_EQ_HOME_STATUS.md`): what
 * a stopped service left behind is not trusted, and routes Android could not
 * pin down are not counted as identified outputs. The service is not running
 * under Robolectric, which is exactly the stale case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusFactsTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `a stopped service's stored engine, playing flag and measuring flag are not trusted`() {
        assertFalse(EqService.running)
        val store = ProfileStore(app)
        store.correctionEnabled = true
        store.appliedEngine = "DynamicsProcessing"
        store.measurementPaused = true
        store.setStatus("Applied to the output mix.", isError = false, playing = true)
        val facts = StatusFacts.facts(app, store, store.status(), true, true, true, "TV speakers")
        assertNull("engine", facts.appliedEngine)
        assertFalse("playing", facts.playing)
        assertEquals(HomeSummary.State.NOT_RUNNING, HomeSummary.stateOf(facts))
        assertTrue(HomeSummary.of(facts).applied.startsWith("Unknown:"))
    }

    @Test fun `a stopped service's last error is not shown as a live fault`() {
        val store = ProfileStore(app)
        store.correctionEnabled = true
        store.setStatus("DynamicsProcessing refused", isError = true)
        val facts = StatusFacts.facts(app, store, store.status(), true, true, true, "TV speakers")
        assertEquals("DynamicsProcessing refused", facts.faultMessage)
        assertEquals(HomeSummary.State.NOT_RUNNING, HomeSummary.stateOf(facts))
    }

    @Test fun `ambiguous and unconfirmed routes are not identified outputs, an unnamed device is`() {
        assertFalse(OutputRoute.isIdentified(null))
        assertFalse(OutputRoute.isIdentified(OutputRoute.AMBIGUOUS))
        assertFalse(OutputRoute.isIdentified(OutputRoute.UNCONFIRMED))
        assertTrue(OutputRoute.isIdentified(OutputRoute.UNKNOWN))
        assertTrue(OutputRoute.isIdentified(OutputRoute.SPEAKER))
        assertTrue(OutputRoute.isIdentified(OutputRoute.BLUETOOTH))
    }
}
