package tv.corebuilds.eq

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

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
