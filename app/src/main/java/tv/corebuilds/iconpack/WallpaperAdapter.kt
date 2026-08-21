package tv.corebuilds.iconpack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService

class WallpaperAdapter(
    private val cacheDir: File,
    private val io: ExecutorService,
    private val main: Handler,
    private val onActivate: (WallpaperActivity.WallpaperItem) -> Unit
) : RecyclerView.Adapter<WallpaperAdapter.VH>() {

    private var items = listOf<WallpaperActivity.WallpaperItem>()
    private val bitmapCache = HashMap<String, Bitmap>()
    private val placeholder: Bitmap by lazy {
        Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.wallpaper_image)
        val label: TextView = view.findViewById(R.id.wallpaper_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wallpaper, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.label.text = item.name
        holder.itemView.contentDescription = item.name
        holder.itemView.setOnClickListener { onActivate(item) }

        val cached = bitmapCache[item.thumbUrl]
        if (cached != null) {
            holder.image.setImageBitmap(cached)
            return
        }

        holder.image.setImageBitmap(placeholder)

        val thumbUrl = item.thumbUrl
        io.execute {
            try {
                val bitmap = loadThumb(thumbUrl)
                bitmapCache[thumbUrl] = bitmap
                main.post {
                    val pos = holder.bindingAdapterPosition
                    if (pos in items.indices && items[pos].thumbUrl == thumbUrl) {
                        holder.image.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override fun getItemCount() = items.size

    fun submit(next: List<WallpaperActivity.WallpaperItem>) {
        items = next
        notifyDataSetChanged()
    }

    private fun loadThumb(url: String): Bitmap {
        val filename = url.substringAfterLast("/")
        val file = File(cacheDir, filename)

        if (file.exists()) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) return bmp
        }

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            return BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IllegalStateException("decode failed")
        } finally {
            conn.disconnect()
        }
    }
}
