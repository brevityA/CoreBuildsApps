package tv.corebuilds.eq.apply

import android.app.Application
import android.app.Service
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.corebuilds.eq.export.ProfileStore

/** Real service entry points, without constructing any audio effects. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EqServiceLifecycleTest {
    @Test
    fun `a late start with the switch off goes foreground before it stops and applies nothing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = ProfileStore(app)
        store.correctionEnabled = false
        val controller = Robolectric.buildService(EqService::class.java).create()
        try {
            val service = controller.get()
            val shadow = shadowOf(service)
            assertFalse(EqService.running)
            // enable() uses startForegroundService(): Android crashes the app if the
            // service stops before startForeground(), so it must go foreground first.
            // stopForeground(REMOVE) clears the shadow's notification but keeps its id.
            assertNotEquals(0, shadow.lastForegroundNotificationId)
            assertNull(shadow.lastForegroundNotification)
            assertTrue(shadow.isForegroundStopped)
            assertTrue(shadow.notificationShouldRemoved)
            assertTrue(shadow.isStoppedBySelf)
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
