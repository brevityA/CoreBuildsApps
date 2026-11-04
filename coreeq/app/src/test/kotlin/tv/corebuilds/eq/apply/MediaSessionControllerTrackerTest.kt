package tv.corebuilds.eq.apply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSessionControllerTrackerTest {
    private class FakeController(val token: String) {
        var callback: (() -> Unit)? = null
        var unregisterCount = 0
    }

    @Test
    fun attachesOncePerTokenAndStateEventsNotifyTheObserver() {
        var changedCount = 0
        val first = FakeController("session-a")
        val tracker = tracker { changedCount++ }

        tracker.update(listOf(first))
        tracker.update(listOf(first))
        assertEquals(0, first.unregisterCount)

        first.callback?.invoke()
        assertEquals(1, changedCount)
    }

    @Test
    fun removesMissingControllersAndAttachesNewOnes() {
        var changedCount = 0
        val first = FakeController("session-a")
        val second = FakeController("session-b")
        val tracker = tracker { changedCount++ }

        tracker.update(listOf(first))
        tracker.update(listOf(second))

        assertEquals(1, first.unregisterCount)
        assertNull(first.callback)
        assertEquals(0, second.unregisterCount)
        second.callback?.invoke()
        assertEquals(1, changedCount)
    }

    @Test
    fun replacesControllerWhenTheSameTokenGetsANewControllerObject() {
        val oldController = FakeController("session-a")
        val newController = FakeController("session-a")
        val tracker = tracker {}

        tracker.update(listOf(oldController))
        tracker.update(listOf(newController))

        assertEquals(1, oldController.unregisterCount)
        assertNull(oldController.callback)
        assertEquals(0, newController.unregisterCount)
        newController.callback?.invoke()
        tracker.clear()
        assertEquals(1, newController.unregisterCount)
    }

    @Test
    fun clearUnregistersAllAndFailedRegistrationsCanRetry() {
        val one = FakeController("session-a")
        val two = FakeController("session-b")
        var shouldFail = true
        val tracker = MediaSessionControllerTracker<FakeController, String>(
            keyOf = FakeController::token,
            attach = { controller, changed ->
                if (controller === two && shouldFail) {
                    shouldFail = false
                    null
                } else {
                    controller.callback = changed
                    val unregister: () -> Unit = {
                        controller.unregisterCount++
                        controller.callback = null
                    }
                    unregister
                }
            },
            onChanged = {}
        )

        tracker.update(listOf(one, two))
        tracker.update(listOf(one, two))
        tracker.clear()

        assertEquals(1, one.unregisterCount)
        assertEquals(1, two.unregisterCount)
        assertNull(one.callback)
        assertNull(two.callback)
    }

    private fun tracker(onChanged: () -> Unit) =
        MediaSessionControllerTracker<FakeController, String>(
            keyOf = FakeController::token,
            attach = { controller, callback ->
                controller.callback = callback
                val unregister: () -> Unit = {
                    controller.unregisterCount++
                    controller.callback = null
                }
                unregister
            },
            onChanged = onChanged
        )
}
