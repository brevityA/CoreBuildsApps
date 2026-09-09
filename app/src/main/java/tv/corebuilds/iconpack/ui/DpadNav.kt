package tv.corebuilds.iconpack.ui

import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView

/**
 * D-pad navigation helpers for TV screens.
 *
 * Layouts carry the static wiring (`nextFocusUp/Down/Left/Right`, emitted by
 * `tools/build_ui.py` and audited by `tools/validate_ui.py`). This file holds
 * the half that must happen at runtime: chaining around bars that appear and
 * disappear, escaping the top row of a grid, and span math shared by every
 * grid screen.
 */
object DpadNav {

    /**
     * Columns for a grid of ~[cellDp]dp tiles. Same formula every grid in the
     * app uses, so tiles line up across screens on any panel.
     */
    @JvmOverloads
    fun spanForWidth(context: Context, cellDp: Int = 148, min: Int = 3, max: Int = 8): Int {
        val dp = context.resources.configuration.screenWidthDp
        return (dp / cellDp).coerceIn(min, max)
    }

    /**
     * Wire a vertical chain: each view's DOWN is the next view, each view's UP
     * is the previous one. Views without ids are skipped (there is nothing to
     * point at).
     *
     * Use it when a contextual bar toggles visibility — a static
     * `nextFocusDown` that points past a GONE bar is correct while the bar is
     * hidden and wrong the moment it appears, so re-chain on every toggle:
     *
     * ```kotlin
     * TvFocus.reveal(updateBar)
     * DpadNav.chainVertical(applyButton, updateButton, chipRow)
     * updateButton.focusFirst()
     * ```
     */
    fun chainVertical(vararg views: View) {
        for (i in 0 until views.size - 1) {
            val a = views[i]
            val b = views[i + 1]
            if (a.id != View.NO_ID && b.id != View.NO_ID) {
                a.nextFocusDownId = b.id
                b.nextFocusUpId = a.id
            }
        }
    }

    /** Horizontal twin of [chainVertical]: LEFT/RIGHT through a button row. */
    fun chainHorizontal(vararg views: View) {
        for (i in 0 until views.size - 1) {
            val a = views[i]
            val b = views[i + 1]
            if (a.id != View.NO_ID && b.id != View.NO_ID) {
                a.nextFocusRightId = b.id
                b.nextFocusLeftId = a.id
            }
        }
    }

    /**
     * First-row UP escape for grids.
     *
     * `nextFocusUp` on the `RecyclerView` itself only applies when the
     * *container* holds focus — once a tile is focused, UP is resolved from
     * the tile, and the framework's spatial search usually (not always) finds
     * the row above. This makes the first row deterministic: UP from any tile
     * in row 0 moves to [topId], whatever the panel geometry.
     *
     * Works with any adapter — no adapter changes needed — because it hooks
     * key events on attached children rather than binding. [span] is a lambda
     * so rotation-safe spans (`spanForWidth`) are read live, not frozen.
     */
    fun installTopEdge(recycler: RecyclerView, span: () -> Int, @IdRes topId: Int) {
        recycler.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    view.setOnKeyListener { v, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                            event.action == KeyEvent.ACTION_DOWN
                        ) {
                            val pos = recycler.getChildAdapterPosition(v)
                            if (pos in 0 until span()) {
                                val root: View = recycler.rootView
                                val target = root.findViewById<View>(topId)
                                if (target != null && target.isFocusable &&
                                    target.visibility == View.VISIBLE
                                ) {
                                    target.requestFocus()
                                    return@setOnKeyListener true
                                }
                            }
                        }
                        false
                    }
                }

                override fun onChildViewDetachedFromWindow(view: View) {
                    view.setOnKeyListener(null)
                }
            }
        )
    }
}

/**
 * Request focus after the next layout pass. Plain `requestFocus()` in
 * `onCreate` runs before the view is attached and silently does nothing —
 * this is the #1 reason generated screens focus correctly first try.
 */
fun View.focusFirst() {
    post { requestFocus() }
}
