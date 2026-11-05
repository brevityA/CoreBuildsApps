package tv.corebuilds.eq.apply

import android.app.Application
import android.app.Service
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.corebuilds.eq.export.ProfileStore

/** Real service entry points, without constructing any audio effects or a foreground notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EqServiceLifecycleTest {
    @Test
    fun `a late start with the switch off stops without going foreground`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = ProfileStore(app)
        store.correctionEnabled = false
        val controller = Robolectric.buildService(EqService::class.java).create()
        try {
            val service = controller.get()
            assertFalse(EqService.running)
            assertTrue(shadowOf(service).isStoppedBySelf)
            assertNull(shadowOf(service).lastForegroundNotification)
            for (action in listOf(null, EqService.ACTION_START, EqService.ACTION_REAPPLY, EqService.ACTION_RESUME, EqService.ACTION_STOP)) {
                val intent = action?.let { Intent(app, EqService::class.java).setAction(it) }
                assertEquals(Service.START_NOT_STICKY, service.onStartCommand(intent, 0, 1))
                assertFalse(store.correctionEnabled)
            }
        } finally {
            controller.destroy()
        }
    }
}
