package tv.corebuilds.pixelneon

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

/**
 * Pixel Neon's copy of the Core Builds dp-box normaliser - see
 * tv.corebuilds.iconpack.TvActivity for the reasoning. The fork ships to the
 * same walls, so it normalises the same way: every screen renders the 960x540dp
 * design canvas at its intended physical size on any panel, 1080p or 4K.
 */
abstract class TvActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(normalizeDpBox(newBase))
    }

    companion object {
        const val DESIGN_WIDTH_DP = 960

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
