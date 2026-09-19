package tv.corebuilds.iconpack

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

/**
 * Base class for every screen, and the app's whole answer to "which panel is
 * this".
 *
 * Android TV sets do not agree on the dp box they report: a 1080p panel says
 * 960x540dp, a 4K panel commonly says 1280x720dp or 1920x1080dp depending on
 * the density bucket its OEM picked. A layout written in dp then renders at a
 * different *physical* size on each - on the doubled box every dp is half the
 * millimetres it is on the reference set, so a 16sp label becomes an 8sp label
 * at the same three metres and the 48dp focus floor stops being a floor in any
 * physical sense.
 *
 * Resource qualifiers cannot fix that: they are steps, and between steps they
 * are wrong by the step ratio (a 1280x720dp panel matches sw720dp and would get
 * the 1920dp panel's metrics, 1.5x too big). The exact fix is to normalise the
 * box itself. The whole UI - layouts, dimens, the mockup frames - is designed
 * against one canvas, 960x540dp, and the density that yields exactly that box
 * on any panel is `widthPixels * 160 / 960`. Overriding
 * `configuration.densityDpi` to it in [attachBaseContext] makes every dp, sp,
 * minHeight and margin render at its intended physical fraction on every
 * panel, continuously, with no error band and nothing to regenerate.
 *
 * What follows from the override, and what deliberately does not:
 *  - `sp` scales with it (scaledDensity derives from the overridden density),
 *    and the user's accessibility font-scale multiplier is preserved - it is
 *    applied on top of the density we set, not replaced by it.
 *  - `drawable-nodpi` art is decoded at raw pixels and laid out into dp-sized
 *    ImageView boxes, so glyphs and thumbs scale with everything else.
 *  - The grid's column counts, derived in MainActivity and WallpapersActivity
 *    as pane width over tile pitch, become panel-invariant for free: both
 *    halves are measured in the normalised box.
 *  - The override is per-app. System UI, other launchers and other apps keep
 *    their own density; only this process's resources see it.
 *
 * Panels already reporting the design box (within 5%) are left untouched, so a
 * reference set never pays for the override in bitmap-cache churn.
 */
abstract class TvActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(normalizeDpBox(newBase))
    }

    companion object {
        /** The canvas every layout, dimen and mockup frame is drawn against. */
        const val DESIGN_WIDTH_DP = 960

        /** The densityDpi that makes [widthPixels] come out as [DESIGN_WIDTH_DP] dp. */
        fun targetDensityDpi(widthPixels: Int): Int =
            (widthPixels * 160 / DESIGN_WIDTH_DP).coerceIn(160, 640)

        fun normalizeDpBox(context: Context): Context {
            val metrics = context.resources.displayMetrics
            val target = targetDensityDpi(metrics.widthPixels)
            val current = metrics.densityDpi
            if (abs(target - current) * 20 <= current) return context
            val config = Configuration(context.resources.configuration)
            config.densityDpi = target
            return context.createConfigurationContext(config)
        }
    }
}
