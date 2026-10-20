package tv.corebuilds.iconpack

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * Grid tile for a Core Builds wallpaper.
 *
 * Thumbs are ~5 KB JPEGs bundled in assets/wallpapers_thumbs, so the whole
 * 50-wall grid renders instantly offline. Decode runs on a shared 2-thread
 * pool (not one raw Thread per bind) with a tag guard against view recycling.
 * No image-loading dependency.
 *
 * Supports a selection mode for bulk export: long-press toggles a tile, and
 * [selectionMode] shows the cyan ring on chosen items.
 */
class WallpaperAdapter(
    private var items: List<Wallpaper>,
    private val onSelect: (Wallpaper) -> Unit,
    /**
     * A tile the user asked for that needs the purchase. Separate from
     * [onSelect] so the grid decides once what a locked press means (the
     * Premium screen) instead of every call site re-testing the rule, and so
     * selection can refuse a locked tile without opening a preview for it.
     */
    private val onLocked: (Wallpaper) -> Unit = {}
) : RecyclerView.Adapter<WallpaperAdapter.VH>() {

    private val selected = HashSet<String>()  // cacheNames
    var selectionMode = false
        private set

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.wp_image)
        val label: TextView = view.findViewById(R.id.wp_name)
        val ring: View = view.findViewById(R.id.wp_selected_ring)
        val badge: TextView = view.findViewById(R.id.wp_live_badge)
        val premiumBadge: TextView = view.findViewById(R.id.wp_premium_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wallpaper, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        bindingContext = holder.itemView.context
        holder.image.setImageBitmap(null)
        holder.image.tag = item.thumbAsset
        loadThumb(holder.image.context, item.thumbAsset) { bmp ->
            if (holder.image.tag == item.thumbAsset) holder.image.setImageBitmap(bmp)
        }
        holder.label.text = item.title
        holder.badge.visibility = if (item.isLive) View.VISIBLE else View.GONE
        // The lock is drawn from the same rule the press is gated on, so a
        // badge can never promise something the press then refuses (or the
        // reverse). On the GitHub build nothing is gated, so nothing is badged.
        val locked = Premium.needsPurchase(holder.itemView.context, item)
        holder.premiumBadge.visibility = if (locked) View.VISIBLE else View.GONE
        val context = holder.itemView.context
        holder.itemView.contentDescription = buildString {
            append(item.title)
            if (item.isLive) append(context.getString(R.string.wp_live_spoken))
            if (locked) append(context.getString(R.string.premium_locked_spoken))
        }
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true

        bindSelection(holder, position)

        holder.itemView.setOnClickListener {
            when {
                Premium.needsPurchase(holder.itemView.context, item) -> onLocked(item)
                selectionMode -> toggle(item)
                else -> onSelect(item)
            }
        }
        holder.itemView.setOnLongClickListener {
            // Loops select like stills: a bulk save sends them to
            // Movies/CoreBuilds, where Projectivy and Monet pick up video.
            if (!selectionMode) enterSelectionMode()
            toggle(item)
            true
        }

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

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            // Ring-only refresh: the tile ViewHolder stays attached, so the
            // D-pad focus ring and the long-press selection frame can show at
            // the same time. A full (payload-less) rebind detaches the
            // focused view and the highlight disappears mid-press.
            bindSelection(holder, position)
            return
        }
        onBindViewHolder(holder, position)
    }

    private fun bindSelection(holder: VH, position: Int) {
        holder.ring.visibility =
            if (selectionMode && selected.contains(items[position].cacheName)) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    override fun getItemCount() = items.size

    fun currentItems(): List<Wallpaper> = items

    fun submit(next: List<Wallpaper>) {
        items = next
        // Drop selections that are no longer visible after filtering.
        val visible = next.map { it.cacheName }.toSet()
        selected.retainAll(visible)
        // The data set itself changed; focus lives on the chip strip while a
        // filter is picked, so a full rebind of the grid is safe here.
        notifyDataSetChanged()
    }

    fun enterSelectionMode() {
        if (selectionMode) return
        selectionMode = true
        notifyItemRangeChanged(0, itemCount, SELECTION_PAYLOAD)
    }

    fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        selected.clear()
        notifyItemRangeChanged(0, itemCount, SELECTION_PAYLOAD)
    }

    fun toggle(item: Wallpaper) {
        // Bulk save must not carry a wall the account has not bought: the
        // selection is the export list, so the gate has to be here too.
        if (needsPurchase(item)) {
            onLocked(item)
            return
        }
        if (!selectionMode) enterSelectionMode()
        if (!selected.add(item.cacheName)) selected.remove(item.cacheName)
        notifyItemChanged(
            items.indexOfFirst { it.cacheName == item.cacheName },
            SELECTION_PAYLOAD
        )
    }

    fun selectAll() {
        // "Select all" means all of what this account can save.
        selected.addAll(items.filterNot { needsPurchase(it) }.map { it.cacheName })
        notifyItemRangeChanged(0, itemCount, SELECTION_PAYLOAD)
    }

    fun clearSelection() {
        selected.clear()
        notifyItemRangeChanged(0, itemCount, SELECTION_PAYLOAD)
    }

    fun selectedItems(): List<Wallpaper> =
        items.filter { selected.contains(it.cacheName) && !needsPurchase(it) }

    private fun needsPurchase(item: Wallpaper): Boolean {
        val context = bindingContext ?: return false
        return Premium.needsPurchase(context, item)
    }

    /** Held from the first bind so selection helpers can consult the gate. */
    private var bindingContext: Context? = null

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
        // Partial-bind payload for selection-mode/ring changes.
        private const val SELECTION_PAYLOAD = "selection"

        private val io = Executors.newFixedThreadPool(2)
        private val main = Handler(Looper.getMainLooper())
        private val cache = android.util.LruCache<String, android.graphics.Bitmap>(40)
    }
}
