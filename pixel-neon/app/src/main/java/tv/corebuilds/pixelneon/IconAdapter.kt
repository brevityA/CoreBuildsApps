package tv.corebuilds.pixelneon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** One labeled tile in the in-app browser. */
class IconAdapter(
    private var items: List<IconItem>,
    private val onActivate: (IconItem) -> Unit
) : RecyclerView.Adapter<IconAdapter.VH>() {

    data class IconItem(
        val drawable: String,
        val name: String,
        val category: String,
        /** True when the glyph is a drawn brandmark, not a letter tile. */
        val bespoke: Boolean = false
    )

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.icon_image)
        val label: TextView = view.findViewById(R.id.icon_name)
        val brandmark: View = view.findViewById(R.id.icon_brandmark)
    }

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
        // Set on every bind: a recycled view carries the previous row's dot.
        holder.brandmark.visibility = if (item.bespoke) View.VISIBLE else View.GONE
        holder.itemView.contentDescription =
            "${item.name}, ${if (item.bespoke) "brandmark" else "monogram"}"
        holder.itemView.setOnClickListener { onActivate(item) }
    }

    override fun getItemCount() = items.size

    fun submit(next: List<IconItem>) {
        items = next
        notifyDataSetChanged()
    }
}
