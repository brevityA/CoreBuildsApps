package tv.corebuilds.iconpack

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Horizontal category chips. Activated chip is the current filter.
 *
 * Selection is applied with targeted notifyItemChanged calls rather than
 * notifyDataSetChanged(). A full rebind here cancels the chip row's item
 * animations and drops focus from the chip the user just pressed — on a D-pad
 * that means the highlight vanishes on the press that was supposed to move it.
 */
class ChipAdapter(
    private val labels: List<String>,
    private val keys: List<String>,
    selected: String,
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<ChipAdapter.VH>() {

    private var selectedKey: String = selected

    class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = keys[position].hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chip, parent, false) as TextView
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val key = keys[position]
        holder.view.text = labels[position]
        holder.view.isActivated = key == selectedKey
        holder.view.setOnClickListener { select(key) }
    }

    override fun getItemCount() = keys.size

    /** Move the filter. Exposed so an empty state can offer "show all". */
    fun select(key: String) {
        if (key == selectedKey) return
        val previous = keys.indexOf(selectedKey)
        selectedKey = key
        val next = keys.indexOf(key)
        if (previous >= 0) notifyItemChanged(previous)
        if (next >= 0) notifyItemChanged(next)
        onPick(key)
    }
}
