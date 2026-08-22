package dev.corebuilds.shift

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.net.URL
import java.util.concurrent.Executors

class MotionAdapter(
    private val context: Context,
    private val entries: List<MotionEntry>
) : RecyclerView.Adapter<MotionAdapter.ViewHolder>() {

    private val thumbLoader = Executors.newFixedThreadPool(2)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val title: TextView = view.findViewById(R.id.title)
        val resolution: TextView = view.findViewById(R.id.resolution)
        val btnDownload: Button = view.findViewById(R.id.btn_download)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_motion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.title.text = entry.name
        holder.resolution.text = "${entry.resolution} · ${entry.duration}s loop"

        val filename = entry.url1080p.substringAfterLast('/')
        val alreadySaved = MotionDownloader.isDownloaded(context, filename)

        if (alreadySaved) {
            holder.btnDownload.setText(R.string.downloaded)
            holder.btnDownload.isEnabled = false
        } else {
            holder.btnDownload.setText(R.string.download)
            holder.btnDownload.isEnabled = true
        }

        holder.btnDownload.setOnClickListener {
            holder.btnDownload.setText(R.string.downloading)
            holder.btnDownload.isEnabled = false

            MotionDownloader.download(context, entry.url1080p, filename) { result ->
                when (result) {
                    is MotionDownloader.Result.Success -> {
                        holder.btnDownload.setText(R.string.downloaded)
                    }
                    is MotionDownloader.Result.Failed -> {
                        holder.btnDownload.setText(R.string.download_failed)
                        holder.btnDownload.isEnabled = true
                        Log.w("CoreShiftAdapter", "download failed: ${result.reason}")
                    }
                }
            }
        }

        loadThumbnail(holder.thumb, entry.thumb)
    }

    override fun getItemCount() = entries.size

    private fun loadThumbnail(imageView: ImageView, url: String) {
        if (url.isBlank()) return
        thumbLoader.execute {
            try {
                val conn = URL(url).openConnection().apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "CoreShift-Thumbs")
                }
                val bitmap = BitmapFactory.decodeStream(conn.getInputStream())
                if (bitmap != null) {
                    imageView.post { imageView.setImageBitmap(bitmap) }
                }
            } catch (e: Exception) {
                Log.w("CoreShiftAdapter", "thumb load failed: ${e.message}")
            }
        }
    }
}
