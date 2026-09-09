package tv.corebuilds.iconpack.ui

import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

/**
 * Smooth focus + enter motion for TV, in one place.
 *
 * The drawables already draw a cyan focus ring (see `bg_card.xml`); this adds
 * the motion half: a small scale-up while focused and a staggered fade/slide
 * when a screen or a list first appears. Everything here is dependency-free
 * `ViewPropertyAnimator` work, so it runs on minSdk 21 with nothing new on the
 * classpath and compiles unchanged into both the classic pack and Pop.
 *
 * Numbers: 160 ms gain / 120 ms loss at 1.06x. Fast enough that rapid D-pad
 * movement never queues a backlog (each change cancels the in-flight
 * animation first), slow enough to read as motion rather than a flicker.
 */
object TvFocus {

    /** Focused scale. 1.06 reads at 3 m without pushing neighbours around. */
    const val FOCUS_SCALE = 1.06f

    /** Item zoom inside grids. Smaller than [FOCUS_SCALE]: tiles sit edge to
     *  edge, so a full pop would overlap its neighbours. */
    const val ITEM_SCALE = 1.04f

    /** Gain is slightly slower than loss — arrival should feel soft, departure
     *  snappy, or fast D-pad travel smears. */
    const val FOCUS_GAIN_MS = 160L
    const val FOCUS_LOSS_MS = 120L

    /** Screen-enter: per-view duration and stagger between siblings. */
    const val ENTER_MS = 220L
    const val ENTER_STAGGER_MS = 28L
    const val ENTER_TRAVEL_DP = 18f

    /** Elevation lift while focused. minSdk is 21 so no version guard needed. */
    const val FOCUS_LIFT_DP = 8f

    private val interp = DecelerateInterpolator(1.5f)

    /**
     * Attach smooth focus zoom to a view.
     *
     * This installs an `OnFocusChangeListener`, which replaces any listener
     * already on the view — pass follow-up work as [onFocus] instead of
     * setting a second listener afterwards, or the zoom silently stops.
     *
     * @return the same view, so calls chain: `TvFocus.attach(button).apply {}`
     */
    @JvmOverloads
    fun attach(
        view: View,
        scale: Float = FOCUS_SCALE,
        gainMs: Long = FOCUS_GAIN_MS,
        lossMs: Long = FOCUS_LOSS_MS,
        onFocus: ((hasFocus: Boolean) -> Unit)? = null
    ): View {
        // Pivot to the centre once laid out. Scaling around the default
        // top-left pivot would drift the tile down-right on every focus.
        view.post {
            if (view.width > 0 && view.height > 0) {
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
            }
        }
        val liftPx = FOCUS_LIFT_DP * view.resources.displayMetrics.density
        view.setOnFocusChangeListener { v, has ->
            // Cancel first: without this, holding a D-pad direction queues a
            // backlog of animations and focus visibly lags the input.
            v.animate().cancel()
            v.animate()
                .scaleX(if (has) scale else 1f)
                .scaleY(if (has) scale else 1f)
                .translationZ(if (has) liftPx else 0f)
                .setDuration(if (has) gainMs else lossMs)
                .setInterpolator(interp)
                .start()
            onFocus?.invoke(has)
        }
        return view
    }

    /**
     * Attach [attach]-style zoom to every item of a grid/list, whatever
     * adapter it runs.
     *
     * Shared adapters (`IconAdapter`, `ChipAdapter`, `WallpaperAdapter`) are
     * deliberately untouched — this hooks attach/detach instead, so generated
     * screens get item motion without forking code both packs compile.
     */
    @JvmOverloads
    fun zoomItems(recycler: RecyclerView, scale: Float = ITEM_SCALE) {
        recycler.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    attach(view, scale = scale)
                }

                override fun onChildViewDetachedFromWindow(view: View) {
                    view.animate().cancel()
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.translationZ = 0f
                    // Only ours was ever installed (adapters in this app set
                    // click listeners, not focus listeners), so clearing is safe.
                    view.onFocusChangeListener = null
                }
            }
        )
    }

    /**
     * Staggered fade-and-rise for a screen's top-level children. Call once
     * from `onCreate` after `setContentView`, after requesting initial focus —
     * alpha/translation never steal focus, so order is safe either way.
     *
     * GONE children (update bars, empty states) are skipped; when they appear
     * later they animate with [reveal] instead.
     */
    @JvmOverloads
    fun playEnter(
        root: ViewGroup,
        staggerMs: Long = ENTER_STAGGER_MS,
        travelDp: Float = ENTER_TRAVEL_DP,
        durationMs: Long = ENTER_MS
    ) {
        val travelPx = travelDp * root.resources.displayMetrics.density
        var index = 0
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            child.alpha = 0f
            child.translationY = travelPx
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * staggerMs)
                .setDuration(durationMs)
                .setInterpolator(interp)
                .start()
            index++
        }
    }

    /**
     * One-shot enter for the items currently laid out in a list. Unlike
     * per-bind adapter animations this cannot fight `DiffUtil`: it runs once,
     * on whatever is attached, and never re-triggers on filter updates.
     */
    @JvmOverloads
    fun playListEnter(recycler: RecyclerView, max: Int = 12, staggerMs: Long = 24L) {
        recycler.post {
            val travelPx = ENTER_TRAVEL_DP * recycler.resources.displayMetrics.density
            val count = minOf(recycler.childCount, max)
            for (i in 0 until count) {
                val child = recycler.getChildAt(i) ?: continue
                child.alpha = 0f
                child.translationY = travelPx
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * staggerMs)
                    .setDuration(ENTER_MS)
                    .setInterpolator(interp)
                    .start()
            }
        }
    }

    /**
     * Reveal a contextual bar (update, selection, empty state) with motion
     * instead of a visibility pop. Pairs with `DpadNav.chainVertical` to
     * rewire focus around the newly visible bar.
     */
    fun reveal(bar: View, durationMs: Long = ENTER_MS) {
        val travelPx = 12f * bar.resources.displayMetrics.density
        bar.alpha = 0f
        bar.translationY = -travelPx
        bar.visibility = View.VISIBLE
        bar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(interp)
            .start()
    }
}
