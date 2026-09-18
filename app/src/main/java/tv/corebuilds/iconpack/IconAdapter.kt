package tv.corebuilds.iconpack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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

    // Resource lookup is relatively expensive on lower-memory TV sticks. The
    // grid rebinds frequently while filtering, so resolve each drawable once.
    private val resourceIds = HashMap<String, Int>()

    data class IconItem(
        val drawable: String,
        val name: String,
        val category: String,
        /**
         * True when this icon's glyph is a drawn brandmark rather than a
         * letter in a container. Generated into R.array.icon_bespoke from
         * glyphs.MONOGRAM_GLYPHS, so the grid and the generators cannot
         * disagree about what counts.
         */
        val bespoke: Boolean = false
    )

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.icon_image)
        val label: TextView = view.findViewById(R.id.icon_name)
        val brandmark: View = view.findViewById(R.id.icon_brandmark)
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
        val id = resourceIds.getOrPut(item.drawable) {
            ctx.resources.getIdentifier(item.drawable, "drawable", ctx.packageName)
        }
        if (id != 0) holder.image.setImageResource(id)
        holder.label.text = item.name
        // Recycled views carry the previous row's dot, so this is set on every
        // bind rather than only when true.
        holder.brandmark.visibility = if (item.bespoke) View.VISIBLE else View.GONE
        // The dot is decorative in the tree (importantForAccessibility=no), so
        // the distinction it draws is spoken here instead.
        val kind = if (item.bespoke) "brandmark" else "monogram"
        holder.itemView.contentDescription =
            "${item.name}, ${item.category.lowercase()} $kind"
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener { onActivate(item) }

        // A TV cursor should feel attached to the tile, not blink between
        // positions. Reset recycled views first, then use a short scale and
        // elevation lift that is easy to track with peripheral vision.
        holder.itemView.animate().cancel()
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
        holder.itemView.elevation = 0f
        holder.itemView.setOnFocusChangeListener { view, focused ->
            view.animate().cancel()
            view.animate()
                .scaleX(if (focused) 1.035f else 1f)
                .scaleY(if (focused) 1.035f else 1f)
                .setDuration(160L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            view.animate()
                .translationZ(if (focused) 8f else 0f)
                .setDuration(160L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun getItemCount() = items.size

    /** Used by the activity to restore the focused icon after filtering. */
    fun itemAt(position: Int): IconItem? = items.getOrNull(position)

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
