package dev.corebuilds.shift

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * Core Shift — the Core Builds live-wallpaper browser.
 *
 * Lists the Core Motion loops, plays each full-screen ([PreviewActivity]), and
 * downloads them to `Movies/CoreBuilds` for Monet. The Projectivy-native route
 * is the Core Motion plugin; this app covers preview + Monet folder delivery.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: LiveAdapter
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var pending: Pair<LiveEntry, Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list: RecyclerView = findViewById(R.id.live_list)
        list.layoutManager = LinearLayoutManager(this)

        val entries = LiveCatalog.load(this)
        adapter = LiveAdapter(entries) { entry, pos -> download(entry, pos) }
        list.adapter = adapter

        if (entries.isEmpty()) {
            findViewById<TextView>(R.id.empty).visibility = android.view.View.VISIBLE
        }
    }

    private fun download(entry: LiveEntry, pos: Int) {
        val perm = LiveDownloader.storagePermission()
        if (perm != null && !LiveDownloader.hasStoragePermission(this)) {
            pending = entry to pos
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_WRITE)
            return
        }
        startDownload(entry, pos)
    }

    private fun startDownload(entry: LiveEntry, pos: Int) {
        adapter.markBusy(pos)
        io.execute {
            val result = LiveDownloader.download(this, entry)
            main.post {
                if (isFinishing || isDestroyed) return@post
                when (result) {
                    is LiveDownloader.Result.Saved ->
                        adapter.markSaved(pos, getString(R.string.saved_hint))
                    is LiveDownloader.Result.NeedsPermission ->
                        adapter.markFailed(pos, getString(R.string.permission_needed))
                    is LiveDownloader.Result.Failed -> {
                        adapter.markFailed(pos, getString(R.string.download_failed_fmt, result.reason))
                        Toast.makeText(
                            this, getString(R.string.download_failed_fmt, result.reason),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_WRITE) return
        val pending = pending ?: return
        this.pending = null
        if (grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startDownload(pending.first, pending.second)
        } else {
            adapter.markFailed(pending.second, getString(R.string.permission_needed))
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_WRITE = 102
    }
}
