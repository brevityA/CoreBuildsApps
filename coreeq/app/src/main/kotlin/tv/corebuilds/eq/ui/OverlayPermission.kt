package tv.corebuilds.eq.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * "Display over other apps", which the on-screen card needs (1.3.2), in one
 * place for Home's bar and Profiles.
 *
 * Until 1.3.2 only Profiles asked for it, so a TV that never visited Profiles
 * showed a short text toast where the card should be: the reported "the card
 * isn't showing". Home now asks too, because Home is where correction is
 * switched on.
 */
object OverlayPermission {

    fun allowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Whether Home shows its bar: only while the notice is on and Android will
     * not let Core EQ draw it, and never again once Later was pressed (Profiles
     * still offers the same button).
     */
    fun homePromptVisible(noticeOn: Boolean, allowed: Boolean, dismissed: Boolean): Boolean =
        noticeOn && !allowed && !dismissed

    /**
     * Android's own "Display over other apps" screen for Core EQ. False when
     * this TV ships no such screen; the caller then shows the ADB line
     * (`profiles_card_no_settings`).
     */
    fun open(activity: Activity): Boolean = try {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${activity.packageName}".toUri())
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
