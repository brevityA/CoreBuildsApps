package tv.corebuilds.iconpack

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * The post-update narrative. The update bar can only afford one line ("what
 * Download does" or the first highlight), so once the APK installs the user
 * knows *that* something changed but never what. This screen is where the
 * rest of the sentence lives: the highlights of the build now running, read
 * from `assets/version.json`, which the release stamper keeps byte-identical
 * to `Latestrelease/version.json` (tools/prepare_release.py, and validate.py
 * keeps the pair honest).
 *
 * Shown once per upgrade by MainActivity's gate (Prefs.whatsNewSeen advances
 * when Done or back closes the screen — relaunch refused even across process
 * deaths mid-smash), and any time from Settings > What's new. Because the
 * source is the APK's own asset, the sheet answers "what changed?" in full
 * for the exact build being run, online or not, UpdateChecker on or off.
 */
class WhatsNewActivity : TvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whats_new)

        val title = findViewById<TextView>(R.id.whats_new_title)
        val count = findViewById<TextView>(R.id.whats_new_count)
        val list = findViewById<LinearLayout>(R.id.whats_new_list)

        val manifest = try {
            JSONObject(assets.open("version.json").bufferedReader().readText())
        } catch (e: Exception) {
            null
        }
        if (manifest == null) {
            title.setText(R.string.whats_new_title_plain)
            list.addView(bodyRow(getString(R.string.whats_new_missing)))
        } else {
            title.text = getString(
                R.string.whats_new_title_fmt, manifest.optString("versionName")
            )
            count.text = getString(
                R.string.whats_new_count_fmt,
                manifest.optInt("iconCount"), manifest.optInt("componentCount")
            )
            val highlights = manifest.optJSONArray("highlights")
            if (highlights == null || highlights.length() == 0) {
                list.addView(bodyRow(getString(R.string.whats_new_no_highlights)))
            } else {
                for (n in 0 until highlights.length()) {
                    list.addView(
                        bodyRow(getString(R.string.whats_new_item_fmt, highlights.getString(n)))
                    )
                }
                count.visibility = View.VISIBLE
            }
        }

        findViewById<LinearLayout>(R.id.whats_new_done).setOnClickListener { finish() }
    }

    /**
     * A body row in Settings' voice: slate, data size, stacked rhythm —
     * written in code because the rows are runtime content off the manifest.
     */
    private fun bodyRow(text: String): TextView {
        val row = TextView(this)
        row.text = text
        row.setTextColor(ContextCompat.getColor(this, R.color.cb_slate))
        row.setTextSize(
            TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.cb_text_data)
        )
        row.setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.cb_space_sm))
        return row
    }

    override fun finish() {
        // Closed is seen, whichever way it closed: a process death between
        // show and dismiss must not re-narrate the same release. The once-
        // per-upgrade guarantee lives here rather than at the show site so
        // the Settings row can still re-open the sheet on purpose.
        Prefs.setWhatsNewSeen(this, BuildConfig.VERSION_CODE)
        super.finish()
    }
}
