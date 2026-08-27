package tv.corebuilds.iconpack

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * Grid tile for a Core Builds wallpaper.
 *
 * Thumbs are ~5 KB JPEGs bundled in assets/wallpapers_thumbs, so the whole
 * 70-wall grid renders instantly offline. Decode runs on a shared 2-thread
 * pool (not one raw Thread per bind) with a tag guard against view recycling.
 * No image-loading dependency.
 *
 * Supports a selection mode for bulk export: long-press toggles a tile, and
 * [selectionMode] shows the cyan ring on chosen items.
 */
class WallpaperAdapter(
    private var items: List<Wallpaper>,
    private val onSelect: (Wallpaper) -> Unit
) : RecyclerView.Adapter<WallpaperAdapter.VH>() {

    private val selected = HashSet<String>()  // cacheNames
    var selectionMode = false
        private set

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.wp_image)
        val label: TextView = view.findViewById(R.id.wp_name)
        val ring: View = view.findViewById(R.id.wp_selected_ring)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wallpaper, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.image.setImageBitmap(null)
        holder.image.tag = item.thumbAsset
        loadThumb(holder.image.context, item.thumbAsset) { bmp ->
            if (holder.image.tag == item.thumbAsset) holder.image.setImageBitmap(bmp)
        }
        holder.label.text = item.title
        holder.itemView.contentDescription = item.title

        val isSelected = selected.contains(item.cacheName)
        holder.ring.visibility = if (selectionMode && isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (selectionMode) toggle(item) else onSelect(item)
        }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) enterSelectionMode()
            toggle(item)
            true
        }
    }

    override fun getItemCount() = items.size

    fun submit(next: List<Wallpaper>) {
        items = next
        // Drop selections that are no longer visible after filtering.
        val visible = next.map { it.cacheName }.toSet()
        selected.retainAll(visible)
        notifyDataSetChanged()
    }

    fun enterSelectionMode() {
        if (selectionMode) return
        selectionMode = true
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        selected.clear()
        notifyDataSetChanged()
    }

    fun toggle(item: Wallpaper) {
        if (!selectionMode) enterSelectionMode()
        if (!selected.add(item.cacheName)) selected.remove(item.cacheName)
        notifyItemChanged(items.indexOfFirst { it.cacheName == item.cacheName })
    }

    fun selectAll() {
        selected.addAll(items.map { it.cacheName })
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
    }

    fun selectedItems(): List<Wallpaper> =
        items.filter { selected.contains(it.cacheName) }

    fun selectedCount(): Int = selected.size

    private fun loadThumb(context: Context, asset: String, onReady: (android.graphics.Bitmap?) -> Unit) {
        val cached = cache.get(asset)
        if (cached != null) {
            onReady(cached)
            return
        }
        io.execute {
            val bmp = try {
                context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) cache.put(asset, bmp)
            main.post { onReady(bmp) }
        }
    }

    companion object {
        private val io = Executors.newFixedThreadPool(2)
        private val main = Handler(Looper.getMainLooper())
        private val cache = android.util.LruCache<String, android.graphics.Bitmap>(40)
    }
}
