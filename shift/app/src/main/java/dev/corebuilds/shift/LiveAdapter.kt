package dev.corebuilds.shift

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Rows of the live-wallpaper browser: bundled poster thumb, title, spec, and
 * [Preview] + [Download] buttons. Per-position state so RecyclerView reuse
 * never mislabels an item.
 *
 * Focus motion lives in [RowMotion]. Note that the *row* is never focusable —
 * the two buttons are — so the card's focused appearance is driven from the
 * children's focus listeners rather than from a state selector, which would
 * never see `state_focused` on the parent.
 */
class LiveAdapter(
    private var items: List<LiveEntry>,
    private val onDownload: (LiveEntry, Int) -> Unit,
    private val onPreview: (LiveEntry) -> Unit,
) : RecyclerView.Adapter<LiveAdapter.Holder>() {

    private val statuses = HashMap<Int, String>()
    private val saved = HashSet<Int>()
    private val busy = HashSet<Int>()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val spine: View = view.findViewById(R.id.spine)
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val title: TextView = view.findViewById(R.id.title)
        val spec: TextView = view.findViewById(R.id.spec)
        val status: TextView = view.findViewById(R.id.status)
        val btnPreview: Button = view.findViewById(R.id.btn_preview)
        val btnDownload: Button = view.findViewById(R.id.btn_download)

        val motion = RowMotion(card, spine, thumb)

        init {
            // Either button holding focus means "this row is the active one".
            // Posting defers the read until focus has actually settled, so
            // moving between the two buttons in a row doesn't flicker the card.
            val listener = View.OnFocusChangeListener { _, _ ->
                view.post {
                    motion.setFocused(btnPreview.hasFocus() || btnDownload.hasFocus())
                }
            }
            btnPreview.onFocusChangeListener = listener
            btnDownload.onFocusChangeListener = listener
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_live, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        holder.title.text = item.title
        holder.spec.text = item.specLabel
        holder.status.text = statuses[position] ?: ""

        holder.thumb.setImageDrawable(null)
        holder.thumb.setBackgroundColor(0xFF1B2634.toInt())
        holder.thumb.tag = item.thumbAsset ?: item.thumbUrl
        item.thumbAsset?.let { asset ->
            try {
                ctx.assets.open(asset).use { stream ->
                    holder.thumb.setImageBitmap(BitmapFactory.decodeStream(stream))
                }
                holder.thumb.setBackgroundColor(Color.TRANSPARENT)
            } catch (_: Exception) {
                // Leave the placeholder; the title still identifies the row.
            }
        } ?: item.thumbUrl?.let { url ->
            RemoteThumbLoader.load(ctx, url) { loadedUrl, bitmap ->
                if (holder.thumb.tag == loadedUrl && bitmap != null) {
                    holder.thumb.setBackgroundColor(Color.TRANSPARENT)
                    holder.thumb.setImageBitmap(bitmap)
                }
            }
        }

        // Snap, don't animate: a recycled row must not replay a focus enter.
        holder.motion.setFocused(
            holder.btnPreview.hasFocus() || holder.btnDownload.hasFocus(),
            animate = false,
        )

        holder.btnPreview.setOnClickListener { onPreview(item) }

        val isSaved = saved.contains(position)
        val isBusy = busy.contains(position)
        holder.btnDownload.isEnabled = item.mediaAvailable && !isSaved && !isBusy
        holder.btnDownload.text = when {
            !item.mediaAvailable -> ctx.getString(R.string.preview_only)
            isSaved -> ctx.getString(R.string.saved)
            else -> ctx.getString(R.string.download)
        }
        holder.btnDownload.setOnClickListener { onDownload(item, position) }
    }

    override fun onViewRecycled(holder: Holder) {
        // Animators hold hard references to their targets; a recycled row that
        // is still sweeping will keep painting into whatever it gets rebound to.
        holder.motion.release()
        super.onViewRecycled(holder)
    }

    /** Replace the visible catalog when a remote content feed arrives. */
    fun submit(next: List<LiveEntry>) {
        items = next
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    fun setStatus(position: Int, text: String) {
        statuses[position] = text
        notifyItemChanged(position, PAYLOAD_STATUS)
    }

    fun markBusy(position: Int) {
        busy.add(position)
        statuses[position] = ""
        notifyItemChanged(position, PAYLOAD_STATUS)
    }

    fun markSaved(position: Int, text: String) {
        saved.add(position)
        busy.remove(position)
        statuses[position] = text
        notifyItemChanged(position, PAYLOAD_STATUS)
    }

    fun markFailed(position: Int, text: String) {
        busy.remove(position)
        statuses[position] = text
        notifyItemChanged(position, PAYLOAD_STATUS)
    }

    /**
     * Partial rebind for status changes.
     *
     * Without this, a full [onBindViewHolder] during a download tears down and
     * rebuilds the row — which drops focus and kills the focus animation
     * mid-flight, exactly when the user is watching it. Payload binds touch
     * only the text.
     */
    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_STATUS)) {
            val ctx = holder.itemView.context
            holder.status.text = statuses[position] ?: ""
            val item = items[position]
            val isSaved = saved.contains(position)
            val isBusy = busy.contains(position)
            holder.btnDownload.isEnabled = item.mediaAvailable && !isSaved && !isBusy
            holder.btnDownload.text = when {
                !item.mediaAvailable -> ctx.getString(R.string.preview_only)
                isSaved -> ctx.getString(R.string.saved)
                else -> ctx.getString(R.string.download)
            }

            // Fade the status line in rather than popping it.
            holder.status.alpha = 0f
            holder.status.animate().alpha(1f).setDuration(180L).start()
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private companion object {
        const val PAYLOAD_STATUS = "status"
    }
}
