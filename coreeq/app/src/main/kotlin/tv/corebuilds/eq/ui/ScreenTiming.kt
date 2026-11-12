package tv.corebuilds.eq.ui

import android.view.View
import android.view.ViewTreeObserver
import tv.corebuilds.eq.apply.RuntimeTimings

/**
 * One screen's two start-up timings, kept apart on purpose: when its first
 * draw pass began, and when it was ready, meaning its content was filled from
 * real data. A screen can draw an empty frame before it is ready, or be
 * filled before anything is drawn; the two numbers say which.
 *
 * Both count from when this object is created, which is when the activity is
 * constructed (a field initializer), so they include onCreate. A cold start's
 * process launch comes before that and is measured from outside, by
 * `am start -W` in `tools/core_eq_device_bench.sh`.
 *
 * Each is recorded at most once per activity instance.
 */
class ScreenTiming(private val firstDraw: RuntimeTimings.Op, private val ready: RuntimeTimings.Op) {

    private val startedNs = RuntimeTimings.start()
    private var drawRecorded = false
    private var readyRecorded = false

    /** Record [firstDraw] at the first draw pass of [root]'s window. */
    fun watchFirstDraw(root: View) {
        val listener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (drawRecorded) return
                drawRecorded = true
                RuntimeTimings.finish(firstDraw, startedNs)
                // A draw listener cannot remove itself while it is being called.
                root.post { if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnDrawListener(this) }
            }
        }
        root.viewTreeObserver.addOnDrawListener(listener)
    }

    /** Record [ready] now. */
    fun ready() {
        if (readyRecorded) return
        readyRecorded = true
        RuntimeTimings.finish(ready, startedNs)
    }

    /** Record [ready] at the first layout of [view] after which [isReady] holds. */
    fun readyAfterLayout(view: View, isReady: () -> Boolean) {
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (!isReady()) return
                ready()
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }
}
