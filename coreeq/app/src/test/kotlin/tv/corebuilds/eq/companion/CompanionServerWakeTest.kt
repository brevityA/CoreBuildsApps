package tv.corebuilds.eq.companion

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.corebuilds.eq.export.ProfileStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompanionServerWakeTest {
    @Test
    fun `legacy unauthenticated wake acknowledges without opening pairing or starting TV UI`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = ProfileStore(app)
        store.correctionEnabled = false
        CompanionServer(app).use { server ->
            server.start()
            CompanionServer::class.java.getDeclaredMethod("wake").apply { isAccessible = true }.invoke(server)
            assertFalse(server.isPairing())
            assertFalse(store.correctionEnabled)
            assertNull(shadowOf(app).nextStartedActivity)
            assertNull(shadowOf(app).nextStartedService)
        }
    }
}
