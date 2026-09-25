package tv.corebuilds.iconpack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast

/**
 * The walk through a launcher that has no inbound apply.
 *
 * Monet is the reason this screen exists. It discovers icon packs through the
 * standard actions we already declare, but it offers nothing an app can call to
 * select one - its settings activity is `exported=false`, so there is no deep
 * link either (docs/MONET_LAUNCHER.md, probe in docs/research/monet-probe/).
 * The sheet's CTA therefore has exactly two honest options: do nothing, or show
 * the user the walk and get out of the way. The old answer was a toast saying
 * "didn't accept direct apply. Set it here: ... then pick Core Builds Banners"
 * - which named two packs, named one that 1.9.5 retired, and expired the moment
 * HOME was pressed to go and follow it.
 *
 * So: the walk as numbered steps that end on ONE pack, the honest reason it
 * cannot be done for the user, the one action that does exist (open the
 * launcher), and the pack-missing hint only when the pack really is not
 * answering the launcher's discovery actions. Nothing here changes anything by
 * itself; the launcher's own settings are where the choice is made.
 *
 * The launcher arrives by key and its steps are built by
 * [ApplyIconPack.handoff] on the spot, so this screen cannot drift from what
 * [ApplyIconPack.apply] would have done with the same launcher.
 */
class LauncherSetupActivity : TvActivity() {

    companion object {
        const val EXTRA_LAUNCHER = "tv.corebuilds.iconpack.extra.LAUNCHER"

        fun intent(context: Context, launcherKey: String): Intent =
            Intent(context, LauncherSetupActivity::class.java)
                .putExtra(EXTRA_LAUNCHER, launcherKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher_setup)

        val key = intent.getStringExtra(EXTRA_LAUNCHER).orEmpty()
        // An installed launcher first: the screen is about what is on this
        // device. The static entry is the fallback for the moment between a
        // launcher being uninstalled and this screen being opened.
        val launcher = ApplyIconPack.installed(this).firstOrNull { it.key == key }
            ?: ApplyIconPack.ALL.firstOrNull { it.key == key }
        if (launcher == null || launcher.inboundApply) {
            // Nothing to walk through. The sheet it came from is one Back press
            // away and still correct, so leave rather than show an empty screen.
            finish()
            return
        }

        val handoff = ApplyIconPack.handoff(this, launcher)
        findViewById<TextView>(R.id.setup_title).text =
            getString(R.string.setup_title_fmt, handoff.launcherName)
        findViewById<TextView>(R.id.setup_why).text =
            getString(R.string.setup_why_fmt, handoff.launcherName)
        // One line per step, in order: the numbering is the launcher's own
        // settings tree, so it is the one thing on this screen that must not be
        // reordered or summarised.
        findViewById<TextView>(R.id.setup_steps).text = handoff.steps.joinToString("\n")

        val hint = findViewById<TextView>(R.id.setup_hint)
        if (handoff.listed) {
            hint.visibility = View.GONE
        } else {
            hint.visibility = View.VISIBLE
            hint.text = getString(
                R.string.setup_not_listed_fmt, handoff.packLabel, handoff.launcherName
            )
        }

        val openButton = findViewById<TextView>(R.id.setup_open)
        openButton.text = getString(R.string.setup_open_fmt, handoff.launcherName)
        openButton.setOnClickListener {
            if (!ApplyIconPack.openLauncher(this, launcher)) {
                toast(getString(R.string.setup_open_failed_fmt, handoff.launcherName))
            }
        }
        findViewById<View>(R.id.setup_back).setOnClickListener { finish() }
        openButton.requestFocus()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
