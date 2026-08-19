package tv.corebuilds.iconpack

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/**
 * The launcher-facing "pick one icon" contract.
 *
 * When a launcher lets a user override a single app's icon, it does not read
 * our appfilter — it starts this app with a pick action and waits for an
 * activity result. Projectivy's per-app "choose icon" browser is exactly this
 * path, so without it the pack can only be applied wholesale.
 *
 * The contract is the ADW icon-pack one that every launcher still implements
 * (see docs/PICKER.md). The EXTRA_SHORTCUT_* constants are deprecated as of
 * API 26 but remain what launchers actually send and expect back, so they are
 * used deliberately here.
 */
object IconPicker {

    private val PICK_ACTIONS = setOf(
        "org.adw.launcher.icons.ACTION_PICK_ICON",
        "com.spocky.projengmenu.icons.ACTION_PICK_ICON",
        "ch.deletescape.lawnchair.PICK_ICON",
        "net.oneplus.launcher.icons.ACTION_PICK_ICON",
        "com.phonemetra.turbo.launcher.icons.ACTION_PICK_ICON",
    )

    private const val EXTRA_RESOURCE_MODE =
        "org.adw.launcher.icons.ACTION_PICK_ICON_RESOURCE"

    fun isPickRequest(intent: Intent?): Boolean =
        intent?.action != null && intent.action in PICK_ACTIONS

    fun deliver(activity: Activity, drawableName: String): Boolean {
        val resId = activity.resources.getIdentifier(
            drawableName, "drawable", activity.packageName)
        if (resId == 0) return false

        val result = Intent()
        // Projectivy and ADW both accept EXTRA_SHORTCUT_ICON_RESOURCE.
        // Some older pickers only read the bitmap extra. Send both so a
        // launcher that ignores one still gets a usable icon.
        result.putExtra(
            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
            Intent.ShortcutIconResource.fromContext(activity, resId))
        val bitmap = rasterise(activity, resId)
        if (bitmap != null) {
            result.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
            result.putExtra("icon", bitmap)
        }

        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
        return true
    }

    fun cancel(activity: Activity) =
        activity.setResult(Activity.RESULT_CANCELED)

    private fun rasterise(activity: Activity, resId: Int): Bitmap? {
        val d: Drawable = ContextCompat.getDrawable(activity, resId)
            ?: return null
        (d as? BitmapDrawable)?.bitmap?.let { return it }

        val w = d.intrinsicWidth.takeIf { it > 0 } ?: FALLBACK_PX
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: FALLBACK_PX
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, canvas.width, canvas.height)
        d.draw(canvas)
        return bmp
    }

    private const val FALLBACK_PX = 192
}
