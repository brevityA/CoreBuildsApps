package tv.corebuilds.eq.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import tv.corebuilds.eq.R
import java.lang.ref.WeakReference

/**
 * The root of every screen with a rail (1.12.0): the rail ([R.id.rail]) on the
 * left, the stage ([R.id.stage]) beside it. Android's own focus search treats the
 * screen as one plane, so Up at the top of the stage could land on a rail item
 * that happens to sit above-left, and Down at the bottom of the rail could
 * wander into the stage. This keeps the two apart and makes the moves between
 * them predictable:
 *
 * - in the rail, Up and Down stay in the rail and stop at its ends; Left stops;
 *   Right goes to the stage, to the control that last had focus there, or else
 *   to the screen's opening control;
 * - in the stage, every arrow searches the stage first; Left at its edge goes to
 *   the rail's current screen; the other edges hold focus where it is.
 *
 * A ScrollView in the stage still scrolls first: it handles the arrow before
 * focus search runs, and only lets the arrow reach here at its end.
 */
class RailStageLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var lastInStage: WeakReference<View>? = null
    private var lastInRail: WeakReference<View>? = null

    private val rail: ViewGroup? get() = findViewById(R.id.rail)
    private val stage: ViewGroup? get() = findViewById(R.id.stage)

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        val view = focused ?: return
        when {
            rail?.let { isInside(it, view) } == true -> lastInRail = WeakReference(view)
            stage?.let { isInside(it, view) } == true -> lastInStage = WeakReference(view)
        }
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        val rail = rail
        val stage = stage
        if (focused == null || rail == null || stage == null) return super.focusSearch(focused, direction)
        val finder = FocusFinder.getInstance()
        if (isInside(rail, focused)) {
            return when (direction) {
                View.FOCUS_RIGHT -> stageEntry(stage) ?: focused
                View.FOCUS_UP, View.FOCUS_DOWN -> finder.findNextFocus(rail, focused, direction) ?: focused
                View.FOCUS_LEFT -> focused
                else -> super.focusSearch(focused, direction)
            }
        }
        if (isInside(stage, focused)) {
            if (direction != View.FOCUS_LEFT && direction != View.FOCUS_RIGHT &&
                direction != View.FOCUS_UP && direction != View.FOCUS_DOWN
            ) return super.focusSearch(focused, direction)
            finder.findNextFocus(stage, focused, direction)?.let { return it }
            return if (direction == View.FOCUS_LEFT) railEntry(rail) ?: focused else focused
        }
        return super.focusSearch(focused, direction)
    }

    /**
     * Where focus goes when Android picks it: the first layout outside touch mode,
     * and the first key press that leaves touch mode, ask the root for its default
     * focus, and a LinearLayout offers its children in order. The rail comes
     * first, so a screen that named no opening control opened on the rail's first
     * item. The stage is offered first; a control marked focused-by-default (see
     * TvActivity.focusOnOpen) still wins, because Android looks for that before
     * it asks here.
     */
    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        val stage = stage
        if (stage != null && stage.requestFocus(direction, previouslyFocusedRect)) return true
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }

    private var settled = false

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (!settled) {
            settled = true
            post { settleOpeningFocus() }
        }
    }

    /**
     * Once, after the first layout: if the opening focus landed on a container
     * (a ScrollView takes focus itself when its rows are not laid out yet, and a
     * list has no rows before its first layout) or on the rail, move it to the
     * stage's first control. A container has no focus ring, so the screen looked
     * as if nothing had focus and the first arrow seemed to do nothing.
     */
    private fun settleOpeningFocus() {
        val stage = stage ?: return
        val rail = rail ?: return
        val focused = findFocus() ?: return
        val onContainer = focused is ViewGroup && isInside(stage, focused) && hasFocusableChild(focused)
        if (!onContainer && !isInside(rail, focused)) return
        FocusFinder.getInstance().findNextFocus(stage, null, View.FOCUS_DOWN)?.requestFocus()
    }

    private fun hasFocusableChild(group: ViewGroup): Boolean {
        val found = ArrayList<View>()
        group.addFocusables(found, View.FOCUS_DOWN)
        return found.any { it !== group }
    }

    /** Moves focus into the stage, as Right from the rail does. */
    fun enterStage(): Boolean = stage?.let { stageEntry(it) }?.requestFocus() == true

    /** Where Right from the rail lands: the stage's last focus, else its opening control, else its first. */
    private fun stageEntry(stage: ViewGroup): View? {
        lastInStage?.get()?.takeIf {
            usable(it) && isInside(stage, it) && !(it is ViewGroup && hasFocusableChild(it))
        }?.let { return it }
        firstWhere(stage) { it.isFocusedByDefault && usable(it) }?.let { return it }
        return FocusFinder.getInstance().findNextFocus(stage, null, View.FOCUS_DOWN)
    }

    /** Where Left from the stage's edge lands: the current screen's rail item, else the last one focused. */
    private fun railEntry(rail: ViewGroup): View? {
        firstWhere(rail) { it.isActivated && usable(it) }?.let { return it }
        lastInRail?.get()?.takeIf { usable(it) && isInside(rail, it) }?.let { return it }
        return FocusFinder.getInstance().findNextFocus(rail, null, View.FOCUS_DOWN)
    }

    private fun usable(view: View): Boolean =
        view.isAttachedToWindow && view.isShown && view.isFocusable && view.isEnabled

    private fun firstWhere(group: ViewGroup, test: (View) -> Boolean): View? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (test(child)) return child
            if (child is ViewGroup) firstWhere(child, test)?.let { return it }
        }
        return null
    }

    private fun isInside(group: ViewGroup, view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === group) return true
            current = current.parent as? View
        }
        return false
    }
}
