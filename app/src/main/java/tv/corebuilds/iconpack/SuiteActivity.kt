package tv.corebuilds.iconpack

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * The living-room suite, one screen: what else from Core Builds is on this
 * TV, which version, and the Downloader code for what is not.
 *
 * The data is the generated `suite_hub.xml` resource - names, package ids and
 * Downloader codes stamped from suite.json by tools/check_suite_truth.py, so
 * the hub cannot disagree with the release registry, and a companion whose
 * code the registry still holds as `[USER TO SUPPLY]` arrives here as an
 * empty item and renders as "not on Downloader yet" rather than as a code
 * that would waste someone's evening.
 *
 * The Play build drops the second half of that: Downloader codes and "grab the
 * APK from the GitHub release" are ways to get an app that Play did not
 * install, and Play reads steering users to those as circumvention. So on
 * Play the screen lists only the companions actually present on this TV -
 * there it answers "what of ours is on this box", which is still the question
 * a living-room user has. Everything else is unchanged, and the sideload
 * builds keep the full registry because that is how their users install.
 *
 * Installed-state and version come from getPackageInfo, which sees these
 * packages at all because the manifest names every companion in `<queries>`
 * - six targeted entries, the same grammar as the launcher block, and the
 * reason this screen does not need QUERY_ALL_PACKAGES either. Rows inflate
 * straight into the scroll column rather than through a RecyclerView: six
 * static rows is below the threshold where view recycling pays for itself,
 * and a column of plain rows keeps the focus chain exactly as long as it
 * looks.
 */
class SuiteActivity : TvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_suite)

        findViewById<View>(R.id.suite_back).setOnClickListener { finish() }

        val names = resources.getStringArray(R.array.suite_hub_names)
        val pkgs = resources.getStringArray(R.array.suite_hub_pkgs)
        val codes = resources.getStringArray(R.array.suite_hub_codes)
        val column = findViewById<LinearLayout>(R.id.suite_rows)
        val inflater = LayoutInflater.from(this)

        val onPlay = Distribution.PLAY
        var rows = 0
        for (i in names.indices) {
            val pkg = pkgs.getOrElse(i) { "" }
            val code = codes.getOrElse(i) { "" }
            val installed = with(ApplyIconPack) { isInstalled(pkg) }
            // Play: a row for an app this TV does not have would be a dead end
            // whose only other information is how to install it off Play.
            if (onPlay && !installed) continue
            val row = inflater.inflate(R.layout.item_suite, column, false)
            row.findViewById<TextView>(R.id.suite_item_name).text = names[i]
            val version = if (installed) {
                runCatching { packageManager.getPackageInfo(pkg, 0).versionName }
                    .getOrNull()
            } else null
            // One lookup per id: lint reads a second findViewById of the same
            // id in one method as a copy-paste bug, and here it would also be
            // a second walk of the row's children for nothing.
            val status = row.findViewById<TextView>(R.id.suite_item_status)
            status.text = when {
                installed && version != null ->
                    getString(R.string.suite_installed_fmt, version)
                installed -> getString(R.string.suite_installed_plain)
                else -> getString(R.string.suite_not_installed)
            }
            val codeLine = row.findViewById<TextView>(R.id.suite_item_code)
            if (onPlay) {
                codeLine.visibility = View.GONE
            } else {
                codeLine.text = if (code.isNotEmpty()) getString(R.string.suite_code_fmt, code)
                else getString(R.string.suite_no_code)
            }
            row.contentDescription = "${names[i]}, " + status.text
            row.setOnClickListener {
                if (!openApp(pkg)) {
                    toast(getString(R.string.suite_open_failed_fmt, names[i]))
                }
            }
            column.addView(row)
            rows++
        }
        // On Play an empty section is a real state (a TV with only this app on
        // it). Say so rather than leaving a blank column that looks broken.
        if (onPlay && rows == 0) {
            val empty = TextView(this)
            empty.text = getString(R.string.suite_none_play)
            // ContextCompat, not getColor(): the latter is API 23 and this app
            // ships minSdk 21 for Fire TV.
            empty.setTextColor(ContextCompat.getColor(this, R.color.cb_slate))
            empty.textSize = 16f
            column.addView(empty)
        }
    }

    /** Launch a companion by package; false when absent or unlaunchable. */
    private fun openApp(pkg: String): Boolean {
        if (!with(ApplyIconPack) { isInstalled(pkg) }) return false
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
