package tv.corebuilds.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.widget.TextView

/**
 * Hides the system bars. Safe to call before setContentView(): on Android 11+
 * Window.getInsetsController() reads the window's decor view without creating
 * it, so calling it first in onCreate threw a NullPointerException and the
 * launcher closed the moment it opened (launcher-v0.1.0). The decor view's own
 * controller is asked instead; reading window.decorView creates the decor.
 */
internal fun Activity.enterImmersiveMode() {
    window.setDecorFitsSystemWindows(false)
    window.decorView.windowInsetsController?.apply {
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsets.Type.systemBars())
    }
}

internal fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
internal fun Context.dp(value: Int): Int = dp(value.toFloat())

// PackageManager's *Flags overloads are API 33; the floor is API 30.

@Suppress("DEPRECATION")
internal fun PackageManager.queryActivities(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        queryIntentActivities(intent, 0)
    }

@Suppress("DEPRECATION")
internal fun PackageManager.packageInfoOrNull(packageName: String): PackageInfo? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
    } else {
        getPackageInfo(packageName, 0)
    }
} catch (_: PackageManager.NameNotFoundException) {
    null
}

@Suppress("DEPRECATION")
internal fun PackageManager.applicationInfoOrNull(packageName: String): ApplicationInfo? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
    } else {
        getApplicationInfo(packageName, 0)
    }
} catch (_: PackageManager.NameNotFoundException) {
    null
}

/** Outfit, the Core Builds brand face, at a fixed weight (400-700). */
internal object Type {
    private val cache = HashMap<Int, Typeface>()

    fun outfit(context: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        val family = try {
            context.resources.getFont(R.font.outfit)
        } catch (_: RuntimeException) {
            Typeface.SANS_SERIF
        }
        Typeface.create(family, weight, false)
    }
}

/** A TextView in the launcher's type: Outfit at [weight], [sizeSp], one colour. */
internal fun Context.text(
    value: CharSequence = "",
    sizeSp: Float,
    color: Int,
    weight: Int = 400,
    maxLines: Int = 1,
    tracking: Float = 0f,
): TextView = TextView(this).apply {
    text = value
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    typeface = Type.outfit(context, weight)
    includeFontPadding = false
    letterSpacing = tracking
    this.maxLines = maxLines
    ellipsize = TextUtils.TruncateAt.END
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

/** The focus lift every focusable shares: [scale] up over 150 ms, back down the same way. */
internal fun View.animateFocus(focused: Boolean, scale: Float) {
    animate().cancel()
    animate()
        .scaleX(if (focused) scale else 1f)
        .scaleY(if (focused) scale else 1f)
        .setDuration(150)
        .setInterpolator(DecelerateInterpolator())
        .start()
}
