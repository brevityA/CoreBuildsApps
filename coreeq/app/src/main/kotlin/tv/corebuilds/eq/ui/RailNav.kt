package tv.corebuilds.eq.ui

import android.app.Activity
import android.content.Intent
import android.view.View
import tv.corebuilds.eq.CapabilityActivity
import tv.corebuilds.eq.ContentTypeActivity
import tv.corebuilds.eq.EnhancedAudioSettingsActivity
import tv.corebuilds.eq.MainActivity
import tv.corebuilds.eq.ManualEqActivity
import tv.corebuilds.eq.ProfilesActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.SetupActivity
import tv.corebuilds.eq.companion.CompanionActivity
import tv.corebuilds.eq.display.DisplayCalibrationActivity

/**
 * The rail's clicks (1.12.0), the same on every screen that has one.
 *
 * Each item opens its screen; the item for the screen you are on is marked
 * (activated) and moves focus into the stage instead. Moving sideways from one
 * screen to another closes the one you leave, so Back always returns Home and
 * never walks through every screen you passed on the way. The Home item brings
 * the existing Home forward rather than opening a second one.
 */
object RailNav {

    /** Rail item id to the screen it opens, in rail order. */
    val TARGETS: List<Pair<Int, Class<out Activity>>> = listOf(
        R.id.rail_home to MainActivity::class.java,
        R.id.btn_nav_manual_eq to ManualEqActivity::class.java,
        R.id.btn_nav_extras to EnhancedAudioSettingsActivity::class.java,
        R.id.btn_nav_content_type to ContentTypeActivity::class.java,
        R.id.btn_nav_profiles to ProfilesActivity::class.java,
        R.id.btn_nav_capability to CapabilityActivity::class.java,
        R.id.btn_nav_setup to SetupActivity::class.java,
        R.id.btn_nav_companion to CompanionActivity::class.java,
        R.id.btn_nav_display_calibration to DisplayCalibrationActivity::class.java
    )

    fun attach(activity: Activity) {
        for ((id, target) in TARGETS) {
            val item = activity.findViewById<View>(id) ?: continue
            val current = target == activity.javaClass
            item.isActivated = current
            item.setOnClickListener {
                if (current) enterStage(item) else open(activity, target)
            }
        }
    }

    fun open(from: Activity, target: Class<out Activity>) {
        val intent = Intent(from, target)
        if (target == MainActivity::class.java) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        from.startActivity(intent)
        if (from !is MainActivity) from.finish()
    }

    private fun enterStage(item: View) {
        var parent = item.parent
        while (parent != null && parent !is RailStageLayout) parent = parent.parent
        (parent as? RailStageLayout)?.enterStage()
    }
}
