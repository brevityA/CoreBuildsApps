package tv.corebuilds.iconpack

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Category chips for the wallpaper browser. Same look as [ChipAdapter] but
 * keyed by a nullable String (null = "All") so it can't be confused with the
 * icon-pack adapter's non-null contract.
 *
 * Selection is applied with targeted notifyItemChanged calls rather than
 * notifyDataSetChanged(). A full rebind here cancels the chip row's item
 * animations and drops focus from the chip the user just pressed — on a D-pad
 * that means the highlight vanishes on the press that was supposed to move it.
 */
class WallpaperChipAdapter(
    private val labels: List<String>,
    private val keys: List<String?>,
    selected: String?,
    private val onPick: (String?) -> Unit
) : RecyclerView.Adapter<WallpaperChipAdapter.VH>() {

    private var selectedKey: String? = selected

    class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        (keys[position] ?: "\u0000").hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chip, parent, false) as TextView
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            bindSelection(holder, position)
            return
        }
        onBindViewHolder(holder, position)
    }

    private fun bindSelection(holder: VH, position: Int) {
        val selected = keys[position] == selectedKey
        holder.view.isActivated = selected
        holder.view.isSelected = selected
        holder.view.contentDescription =
            "${labels[position]}, filter${if (selected) ", selected" else ""}"
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val key = keys[position]
        holder.view.text = labels[position]
        bindSelection(holder, position)
        // item_chip.xml is shared with the icon grid and points nextFocus at
        // apply_button / grid, which do not exist on this screen. Override so
        // Up collapses through the (often GONE) selection bar to Back, and
        // Down lands on the wallpaper grid.
        holder.view.nextFocusUpId = R.id.wp_selection_bar
        holder.view.nextFocusDownId = R.id.wp_grid
        holder.view.setOnClickListener { select(key) }
        holder.view.setOnKeyListener { view, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val direction = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                else -> 0
            }
            if (direction == 0) return@setOnKeyListener false
            val positionNow = holder.bindingAdapterPosition
            if (positionNow == RecyclerView.NO_POSITION) return@setOnKeyListener false
            val target = (view.parent as? RecyclerView)
                ?.layoutManager
                ?.findViewByPosition(positionNow + direction)
            if (target != null) {
                target.requestFocus()
                true
            } else {
                false
            }
        }
        holder.view.animate().cancel()
        holder.view.scaleX = 1f
        holder.view.scaleY = 1f
        holder.view.setOnFocusChangeListener { view, focused ->
            view.animate().cancel()
            view.animate()
                .scaleX(if (focused) 1.04f else 1f)
                .scaleY(if (focused) 1.04f else 1f)
                .setDuration(140L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun getItemCount() = keys.size

    fun select(key: String?) {
        if (key == selectedKey) return
        val previous = keys.indexOf(selectedKey)
        selectedKey = key
        val next = keys.indexOf(key)
        if (previous >= 0) notifyItemChanged(previous, SELECTION_PAYLOAD)
        if (next >= 0) notifyItemChanged(next, SELECTION_PAYLOAD)
        onPick(key)
    }

    companion object {
        // Partial-bind payload: refreshes only activated/selected state so the
        // chip ViewHolder (and its D-pad focus/highlight) is reused in place
        // instead of being replaced by the RecyclerView change animation.
        private const val SELECTION_PAYLOAD = "selection"
    }
}
