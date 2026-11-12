package tv.corebuilds.eq.export

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.corebuilds.eq.ui.screens.ScreenSeed
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

/**
 * Profiles are written from more than one thread: activities and the service
 * on the main thread, and the companion server's worker when a phone
 * measurement is saved. Each write reads the whole list, changes it and
 * writes it back, so two writers that interleave lose one another's change
 * unless the read-modify-write is serialised. Each test races separate
 * ProfileStore instances, as the app's screens and server hold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileStoreConcurrencyTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun race(threads: Int, each: Int, work: (thread: Int, i: Int, store: ProfileStore) -> Unit) {
        val start = CyclicBarrier(threads)
        val done = CountDownLatch(threads)
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        repeat(threads) { t ->
            Thread {
                try {
                    val store = ProfileStore(app)
                    start.await()
                    for (i in 0 until each) work(t, i, store)
                } catch (e: Throwable) {
                    errors += e
                } finally {
                    done.countDown()
                }
            }.start()
        }
        assertTrue("writers did not finish", done.await(60, TimeUnit.SECONDS))
        assertTrue(errors.toString(), errors.isEmpty())
    }

    @Test fun `concurrent saves from separate stores keep every profile`() {
        race(threads = 4, each = 40) { t, i, store ->
            store.saveProfile(ScreenSeed.livingRoom("t$t-$i", "T$t #$i"))
        }
        val ids = ProfileStore(app).getAllProfiles().map { it.id }.toSet()
        assertEquals(160, ids.size)
    }

    @Test fun `a rename racing saves is neither lost nor undone`() {
        val seed = ProfileStore(app)
        seed.saveProfile(ScreenSeed.livingRoom("keep", "Original"))
        race(threads = 3, each = 40) { t, i, store ->
            if (t == 0) store.renameProfile("keep", "Renamed $i")
            else store.saveProfile(ScreenSeed.livingRoom("t$t-$i", "T$t #$i"))
        }
        val all = ProfileStore(app).getAllProfiles()
        assertEquals(81, all.size)
        assertEquals("Renamed 39", all.single { it.id == "keep" }.name)
    }

    @Test fun `deletes racing saves remove exactly what was deleted`() {
        val seed = ProfileStore(app)
        for (i in 0 until 40) seed.saveProfile(ScreenSeed.livingRoom("old-$i", "Old $i"))
        race(threads = 2, each = 40) { t, i, store ->
            if (t == 0) store.deleteProfile("old-$i")
            else store.saveProfile(ScreenSeed.livingRoom("new-$i", "New $i"))
        }
        val ids = ProfileStore(app).getAllProfiles().map { it.id }
        assertEquals((0 until 40).map { "new-$it" }.toSet(), ids.toSet())
    }
}
