package tv.corebuilds.iconpack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Core Builds Icon Pack — front door.
 *
 * Brand Guide §05: every interactive element states what it will do BEFORE
 * it does it, and the receipts voice reports exactly what happened.
 */
class MainActivity : AppCompatActivity() {

    private var target: ApplyIconPack.Launcher? = null
    private var updateChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val icons = resources.getStringArray(R.array.icon_pack)

        findViewById<TextView>(R.id.count).text =
            getString(R.string.icon_count_fmt, icons.size)

        findViewById<RecyclerView>(R.id.grid).apply {
            layoutManager = GridLayoutManager(this@MainActivity, spanForScreen())
            adapter = IconAdapter(icons)
            setHasFixedSize(true)
        }

        bindApplyButton()
    }

    override fun onResume() {
        super.onResume()
        // The user may have installed a launcher while we were backgrounded.
        bindApplyButton()
        if (!updateChecked) {
            updateChecked = true
            checkForUpdate()
        }
    }

    /**
     * Ask the repo whether a newer build exists.
     *
     * Reports and stops there — no silent download, no self-install. Doing
     * that needs REQUEST_INSTALL_PACKAGES, a sensitive permission, which is
     * a poor trade for a pack the user sideloaded deliberately.
     */
    private fun checkForUpdate() {
        UpdateChecker.check(this) { result ->
            when (result) {
                is UpdateChecker.Result.Available -> {
                    toast(getString(R.string.update_available_fmt,
                        result.versionName, result.iconCount))
                    findViewById<TextView>(R.id.count).apply {
                        text = getString(R.string.update_available_fmt,
                            result.versionName, result.iconCount)
                        setOnClickListener { openReleases(result.apkUrl) }
                    }
                }
                is UpdateChecker.Result.UpToDate -> {
                    // Say nothing on success; a toast on every launch is noise.
                }
                is UpdateChecker.Result.Failed -> {
                    // Name the failure rather than swallowing it, but keep it
                    // to the log — a failed check is not the user's problem.
                    android.util.Log.w("CoreBuilds",
                        getString(R.string.update_failed_fmt, result.reason))
                }
            }
        }
    }

    private fun openReleases(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast(getString(R.string.update_failed_fmt, "no browser installed"))
        }
    }

    /**
     * The button names the launcher it will hand off to, before it's pressed —
     * no "Apply" that leaves you guessing where it went.
     */
    private fun bindApplyButton() {
        val button = findViewById<TextView>(R.id.apply_button)
        val sub = findViewById<TextView>(R.id.apply_sub)

        val detected = ApplyIconPack.detectInstalled(this)
        target = detected

        if (detected == null) {
            button.text = getString(R.string.cta_no_launcher)
            sub.text = getString(R.string.cta_sub_no_launcher)
            button.setOnClickListener {
                toast(getString(R.string.projectivy_missing))
            }
            return
        }

        button.text = getString(R.string.cta_apply_to_fmt, detected.displayName)
        sub.text = getString(R.string.cta_sub_apply_fmt, detected.displayName)
        button.setOnClickListener { applyTo(detected) }
    }

    private fun applyTo(launcher: ApplyIconPack.Launcher) {
        when (val result = ApplyIconPack.apply(this, launcher)) {
            is ApplyIconPack.Result.Applied ->
                toast(getString(R.string.apply_handed_off_fmt, result.launcherName))

            is ApplyIconPack.Result.NotInstalled ->
                toast(getString(R.string.apply_not_installed_fmt, result.launcherName))

            is ApplyIconPack.Result.Manual -> {
                // Direct apply bounced. Name the launcher, name the menu path,
                // and open it so the user isn't hunting.
                toast(
                    getString(
                        R.string.apply_manual_fmt,
                        result.launcherName,
                        result.instructions
                    )
                )
                ApplyIconPack.openLauncher(this, launcher)
            }
        }
    }

    private fun spanForScreen(): Int {
        val dp = resources.configuration.screenWidthDp
        return (dp / 140).coerceIn(3, 10)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
