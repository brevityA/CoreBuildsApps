package tv.corebuilds.iconpack

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Grid tile for a Core Builds wallpaper.
 *
 * Thumbs are ~5 KB JPEGs bundled in assets/wallpapers_thumbs, so the whole
 * 70-wall grid renders instantly offline. We decode off the main thread and
 * guard against view recycling with a tag check. No image-loading dependency.
 */
class WallpaperAdapter(
    private var items: List<Wallpaper>,
    private val onSelect: (Wallpaper) -> Unit
) : RecyclerView.Adapter<WallpaperAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.wp_image)
        val label: TextView = view.findViewById(R.id.wp_name)
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
        holder.itemView.setOnClickListener { onSelect(item) }
    }

    override fun getItemCount() = items.size

    fun submit(next: List<Wallpaper>) {
        items = next
        notifyDataSetChanged()
    }

    private fun loadThumb(context: Context, asset: String, onReady: (android.graphics.Bitmap?) -> Unit) {
        Thread {
            val bmp = try {
                context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onReady(bmp) }
        }.start()
    }
}
