package tv.corebuilds.eq

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

abstract class TvActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(normalizeDpBox(newBase))
    }

    companion object {
        const val DESIGN_WIDTH_DP = 960
        const val DESIGN_HEIGHT_DP = 540

        /**
         * The density that fits the 960 x 540dp design box inside the window,
         * set by whichever side runs out first (1.3.2). Until then only the
         * width counted, which assumes a 16:9 window. A projector or box
         * running a phone-style Android build keeps a status or navigation bar
         * on screen; at 1920 x 1032 the width gave 320dpi, the window was
         * 516dp tall, and the bottom of every screen designed for 540dp was
         * cut off. [heightPixels] defaults to a 16:9 window.
         */
        fun targetDensityDpi(
            widthPixels: Int,
            heightPixels: Int = widthPixels * DESIGN_HEIGHT_DP / DESIGN_WIDTH_DP
        ): Int = minOf(
            widthPixels * 160 / DESIGN_WIDTH_DP,
            heightPixels * 160 / DESIGN_HEIGHT_DP
        ).coerceIn(160, 640)

        /**
         * Whether [currentDpi] can stay. A density a little under the target
         * (within 5%) only leaves a margin, so it stays; any density over the
         * target makes the box bigger than the window and cuts an edge off, so
         * it never stays. The old symmetric 5% let 320dpi stand against a
         * 305dpi target, which is exactly the 1920 x 1032 case.
         */
        fun keepsCurrentDensity(currentDpi: Int, targetDpi: Int): Boolean =
            currentDpi <= targetDpi && (targetDpi - currentDpi) * 20 <= currentDpi

        fun normalizeDpBox(context: Context): Context {
            val metrics = context.resources.displayMetrics
            val target = targetDensityDpi(metrics.widthPixels, metrics.heightPixels)
            if (keepsCurrentDensity(metrics.densityDpi, target)) return context
            val config = Configuration(context.resources.configuration)
            config.densityDpi = target
            return context.createConfigurationContext(config)
        }
    }
}
