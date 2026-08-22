package dev.corebuilds.shift

import android.content.Intent
import android.graphics.BitmapFactory
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
 */
class LiveAdapter(
    private val items: List<LiveEntry>,
    private val onDownload: (LiveEntry, Int) -> Unit,
) : RecyclerView.Adapter<LiveAdapter.Holder>() {

    private val statuses = HashMap<Int, String>()
    private val saved = HashSet<Int>()
    private val busy = HashSet<Int>()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val title: TextView = view.findViewById(R.id.title)
        val spec: TextView = view.findViewById(R.id.spec)
        val status: TextView = view.findViewById(R.id.status)
        val btnPreview: Button = view.findViewById(R.id.btn_preview)
        val btnDownload: Button = view.findViewById(R.id.btn_download)
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
        item.thumbAsset?.let { asset ->
            try {
                ctx.assets.open(asset).use { stream ->
                    holder.thumb.setImageBitmap(BitmapFactory.decodeStream(stream))
                }
            } catch (_: Exception) {
                // Leave the placeholder; the title still identifies the row.
            }
        }

        holder.btnPreview.setOnClickListener {
            val intent = Intent(ctx, PreviewActivity::class.java)
                .putExtra(PreviewActivity.EXTRA_URL, item.url1080p)
                .putExtra(PreviewActivity.EXTRA_TITLE, item.title)
            ctx.startActivity(intent)
        }

        val isSaved = saved.contains(position)
        val isBusy = busy.contains(position)
        holder.btnDownload.isEnabled = !isSaved && !isBusy
        holder.btnDownload.text = ctx.getString(if (isSaved) R.string.saved else R.string.download)
        holder.btnDownload.setOnClickListener { onDownload(item, position) }
    }

    override fun getItemCount(): Int = items.size

    fun setStatus(position: Int, text: String) {
        statuses[position] = text
        notifyItemChanged(position)
    }

    fun markBusy(position: Int) {
        busy.add(position)
        statuses[position] = ""
        notifyItemChanged(position)
    }

    fun markSaved(position: Int, text: String) {
        saved.add(position)
        busy.remove(position)
        statuses[position] = text
        notifyItemChanged(position)
    }

    fun markFailed(position: Int, text: String) {
        busy.remove(position)
        statuses[position] = text
        notifyItemChanged(position)
    }
}
