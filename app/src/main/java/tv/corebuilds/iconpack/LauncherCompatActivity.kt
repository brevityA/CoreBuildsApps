package tv.corebuilds.iconpack

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager

/**
 * The launcher compatibility summary.
 *
 * Different launchers resolve app components differently, and they consume
 * the pack differently - a TV launcher takes the 16:9 banners, a phone
 * launcher takes the square glyphs from Core Builds Glyphs, one launcher
 * (Leanback on Fire) takes no pack at all. A user switching launchers hits
 * exactly these differences and has had nowhere in the app to look.
 *
 * This screen is the answer, and it is deliberately a projection of
 * [ApplyIconPack.COMPATIBILITY]: the same data the Apply button, the handoff
 * walk and the Banners/Glyphs toggle already act on. Nothing here can claim
 * a launcher does something the apply flow would not do, and a launcher
 * added to [ApplyIconPack] appears here with its art, mode and notes in the
 * same commit.
 *
 * The top line says which launcher THIS TV is on ([ApplyIconPack.detectDefault]);
 * when the HOME launcher is not in the known list it says so plainly instead
 * of guessing.
 */
class LauncherCompatActivity : TvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher_compat)

        findViewById<View>(R.id.launcher_compat_back).setOnClickListener { finish() }

        val here = findViewById<TextView>(R.id.launcher_compat_here)
        val home = ApplyIconPack.detectDefault(this)
        here.text = if (home != null) {
            getString(R.string.launcher_compat_here_fmt, home.displayName,
                      ApplyIconPack.artText(this, home.art))
        } else {
            getString(R.string.launcher_compat_here_unknown)
        }
        here.visibility = View.VISIBLE

        findViewById<TextView>(R.id.launcher_compat_sub).text =
            getString(R.string.launcher_compat_sub_fmt,
                      ApplyIconPack.COMPATIBILITY.size)

        val list = findViewById<androidx.recyclerview.widget.RecyclerView>(
            R.id.launcher_compat_list)
        list.layoutManager = LinearLayoutManager(this)
        // A list that animates its rows on first layout drops the focus the
        // list just received; there is nothing here worth animating.
        list.itemAnimator = null
        list.adapter = LauncherCompatAdapter(
            ApplyIconPack.COMPATIBILITY,
            homeKey = home?.key,
            installedKeys = ApplyIconPack.COMPATIBILITY
                .filter { ApplyIconPack.isInstalled(this, it) }
                .map { it.key }
                .toSet(),
            context = this
        )
        list.requestFocus()
    }
}
