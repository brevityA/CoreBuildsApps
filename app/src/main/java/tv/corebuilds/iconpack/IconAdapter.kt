package tv.corebuilds.iconpack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * One labeled tile in the in-app browser.
 *
 * Updates go through DiffUtil. The grid holds 925 items and every search
 * keystroke used to rebind all of them, which also dropped focus from any tile
 * inside the grid and cancelled in-flight item animations.
 */
class IconAdapter(
    private var items: List<IconItem>,
    private val onActivate: (IconItem) -> Unit
) : RecyclerView.Adapter<IconAdapter.VH>() {

    data class IconItem(
        val drawable: String,
        val name: String,
        val category: String
    )

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.icon_image)
        val label: TextView = view.findViewById(R.id.icon_name)
    }

    init {
        // Stable ids let RecyclerView keep the focused view across a diff,
        // which is what stops the D-pad highlight jumping on filter.
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].drawable.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.image.context
        val id = ctx.resources.getIdentifier(item.drawable, "drawable", ctx.packageName)
        if (id != 0) holder.image.setImageResource(id)
        holder.label.text = item.name
        holder.itemView.contentDescription = item.name
        holder.itemView.setOnClickListener { onActivate(item) }
    }

    override fun getItemCount() = items.size

    fun submit(next: List<IconItem>) {
        val previous = items
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(old: Int, new: Int) =
                previous[old].drawable == next[new].drawable
            override fun areContentsTheSame(old: Int, new: Int) =
                previous[old] == next[new]
        })
        items = next
        result.dispatchUpdatesTo(this)
    }
}
