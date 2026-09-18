package tv.corebuilds.iconpack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Build facts, and the complete list of what this app sends anywhere.
 *
 * The second half is the point. Icon packs conventionally use this screen to
 * ask for consent to collect the installed-app list; this one states the
 * opposite, and states it in a form a reader can check against the source:
 * exactly two requests leave the device, both user-initiated, neither
 * carrying anything about the device. If that list ever grows, this screen is
 * the thing that has to change first — it is deliberately written as a
 * complete enumeration rather than a reassuring summary, so it cannot quietly
 * stay true while the behaviour drifts.
 *
 * Counts are read from generated resources and the bundled catalog, never
 * typed in: a hand-maintained "940 icons" goes stale on the next tranche and
 * nothing in the build would catch it.
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.about_version).text = BuildConfig.VERSION_NAME
        findViewById<TextView>(R.id.about_build).text = BuildConfig.VERSION_CODE.toString()

        // icon_count is written into icon_pack.xml by tools/build_icons.py, so
        // it cannot disagree with what actually shipped. The wallpaper figure
        // is counted from the bundled catalog for the same reason. The design
        // also showed a mapped-component count; there is no generated resource
        // for it and inventing one here would be a number maintained by hand,
        // so it is omitted rather than guessed.
        findViewById<TextView>(R.id.about_icons).text =
            resources.getInteger(R.integer.icon_count).toString()
        findViewById<TextView>(R.id.about_wallpapers).text =
            WallpaperCatalog.load(this).size.toString()

        findViewById<View>(R.id.about_source).setOnClickListener {
            open(getString(R.string.about_source_url))
        }
        findViewById<View>(R.id.about_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.about_back).setOnClickListener { finish() }
    }

    /**
     * Hand a URL to whatever can show it.
     *
     * Plenty of Android TV boxes ship without a browser, and an unhandled
     * ACTION_VIEW crashes rather than doing nothing. Catching it and saying so
     * is the difference between "this box has no browser" and "the icon pack
     * crashed".
     */
    private fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.about_no_browser, url), Toast.LENGTH_LONG)
                .show()
        }
    }
}
