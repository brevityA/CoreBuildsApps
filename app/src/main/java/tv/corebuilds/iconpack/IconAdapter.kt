package tv.corebuilds.iconpack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

/** Renders the pack's own drawables into the browser grid. */
class IconAdapter(private val names: Array<String>) :
    RecyclerView.Adapter<IconAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.icon_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val name = names[position]
        val ctx = holder.image.context
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (id != 0) holder.image.setImageResource(id)
        holder.image.contentDescription = name
    }

    override fun getItemCount() = names.size
}
