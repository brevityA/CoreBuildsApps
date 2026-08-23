package dev.corebuilds.shift

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: LiveAdapter
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var pending: Pair<LiveEntry, Int>? = null
    private var pendingUpdate: UpdateChecker.Result.Available? = null
    private var installOffered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list: RecyclerView = findViewById(R.id.live_list)
        list.layoutManager = LinearLayoutManager(this)

        val entries = LiveCatalog.load(this)
        adapter = LiveAdapter(entries) { entry, pos -> download(entry, pos) }
        list.adapter = adapter

        if (entries.isEmpty()) {
            findViewById<TextView>(R.id.empty).visibility = View.VISIBLE
        }

        checkForUpdate()
    }

    private fun checkForUpdate() {
        UpdateChecker.check(this) { result ->
            when (result) {
                is UpdateChecker.Result.Available -> showUpdateAvailable(result)
                is UpdateChecker.Result.UpToDate -> { }
                is UpdateChecker.Result.Failed -> { }
            }
        }
    }

    private fun showUpdateAvailable(update: UpdateChecker.Result.Available) {
        pendingUpdate = update
        val banner: LinearLayout = findViewById(R.id.update_banner)
        val text: TextView = findViewById(R.id.update_text)
        val btn: Button = findViewById(R.id.update_btn)

        text.text = getString(R.string.update_available, update.versionName)
        banner.visibility = View.VISIBLE

        btn.setOnClickListener { startUpdateDownload(update) }
    }

    private fun startUpdateDownload(update: UpdateChecker.Result.Available) {
        val text: TextView = findViewById(R.id.update_text)
        val btn: Button = findViewById(R.id.update_btn)
        btn.isEnabled = false
        text.text = getString(R.string.update_downloading)

        UpdateInstaller.download(this, update.apkUrl) { event ->
            when (event) {
                is UpdateInstaller.Event.Progress -> {
                    if (event.total > 0) {
                        val pct = (event.received * 100 / event.total).toInt()
                        text.text = "$pct%"
                    }
                }
                is UpdateInstaller.Event.Ready -> {
                    text.text = getString(R.string.update_installing)
                    promptInstall(event.file)
                }
                is UpdateInstaller.Event.Failed -> {
                    text.text = getString(R.string.update_failed_fmt, event.reason)
                    btn.isEnabled = true
                    btn.text = getString(R.string.update_btn)
                }
            }
        }
    }

    private fun promptInstall(file: File) {
        if (!UpdateInstaller.canInstall(this)) {
            val text: TextView = findViewById(R.id.update_text)
            text.text = getString(R.string.update_permission)
            if (!UpdateInstaller.requestInstallPermission(this)) {
                Toast.makeText(this, getString(R.string.update_permission), Toast.LENGTH_LONG).show()
            }
            return
        }
        installOffered = true
        UpdateInstaller.install(this, file)
    }

    override fun onResume() {
        super.onResume()
        val updateFile = File(cacheDir, "updates/coreshift-update.apk")
        if (updateFile.exists() && UpdateInstaller.canInstall(this) && !installOffered) {
            installOffered = true
            UpdateInstaller.install(this, updateFile)
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
