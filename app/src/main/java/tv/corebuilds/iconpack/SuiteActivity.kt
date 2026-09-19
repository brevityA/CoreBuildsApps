package tv.corebuilds.iconpack

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

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
 * Installed-state and version come from getPackageInfo, which sees these
 * packages at all because the manifest names every companion in `<queries>`
 * - six targeted entries, the same grammar as the launcher block, and the
 * reason this screen does not need QUERY_ALL_PACKAGES either. Rows inflate
 * straight into the scroll column rather than through a RecyclerView: six
 * static rows is below the threshold where view recycling pays for itself,
 * and a column of plain rows keeps the focus chain exactly as long as it
 * looks.
 */
class SuiteActivity : AppCompatActivity() {

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

        for (i in names.indices) {
            val pkg = pkgs.getOrElse(i) { "" }
            val code = codes.getOrElse(i) { "" }
            val row = inflater.inflate(R.layout.item_suite, column, false)
            row.findViewById<TextView>(R.id.suite_item_name).text = names[i]
            val installed = with(ApplyIconPack) { isInstalled(pkg) }
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
            row.findViewById<TextView>(R.id.suite_item_code).text =
                if (code.isNotEmpty()) getString(R.string.suite_code_fmt, code)
                else getString(R.string.suite_no_code)
            row.contentDescription = "${names[i]}, " + status.text
            row.setOnClickListener {
                if (!openApp(pkg)) {
                    toast(getString(R.string.suite_open_failed_fmt, names[i]))
                }
            }
            column.addView(row)
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
