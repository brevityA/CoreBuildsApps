package tv.corebuilds.iconpack

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Horizontal category chips. Activated chip is the current filter. */
class ChipAdapter(
    private val labels: List<String>,
    private val keys: List<String>,
    private var selected: String,
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<ChipAdapter.VH>() {

    class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chip, parent, false) as TextView
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val key = keys[position]
        holder.view.text = labels[position]
        holder.view.isActivated = key == selected
        holder.view.setOnClickListener {
            selected = key
            notifyDataSetChanged()
            onPick(key)
        }
    }

    override fun getItemCount() = keys.size
}
