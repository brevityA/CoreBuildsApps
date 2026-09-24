package tv.corebuilds.iconpack

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast

/**
 * The Glyphs/Banners toggle, as far as launchers are concerned.
 *
 * A launcher auto-applies whatever the selected pack's appfilter maps, and it
 * reads that appfilter out of the pack's APK: this app cannot change its own
 * at runtime. So the toggle changes *which package* the launcher is told to
 * apply. Glyphs is this app, as it always was. Banners is Core Builds
 * Banners ([PACKAGE], built from banners/), a resource-only twin whose
 * appfilter maps the same components to the same apps' 16:9 art.
 *
 * The companion ships in the same release as this app and must be exactly
 * this app's version: a pack a version behind would silently miss every icon
 * added since. When it is missing or stale, [ensure] downloads the matching
 * release asset through [UpdateInstaller] (GitHub-only, size-bounded, and
 * signature-checked against this app's own certificate) and hands it to the
 * system installer, which always asks the user to confirm.
 *
 * Builds without a companion (:pop, the candidate test build) carry an empty
 * [PACKAGE]; for them [wanted] is always false and nothing here runs.
 */
object BannersCompanion {

    val PACKAGE: String = BuildConfig.BANNERS_PACKAGE

    private const val RELEASES =
        "https://github.com/brevityA/CoreBuildsApps/releases/download"
    const val ASSET = "iconpack-banners-release.apk"

    /** The companion's display name, as launchers list it. */
    const val LABEL = "Core Builds Banners"

    /**
     * Set by the companion's BannersActivity on the icon-pick requests it
     * forwards here. A launcher that opened *Core Builds Banners* gets 16:9
     * art back; one that opened this pack gets glyphs, whatever the toggle
     * says. The literal is repeated in BannersActivity.java (it cannot see
     * this class); tests/test_banners_pack.py holds the two equal.
     */
    const val EXTRA_PICK_BANNERS = "tv.corebuilds.iconpack.extra.PICK_BANNERS"

    fun supported(): Boolean = PACKAGE.isNotEmpty()

    /** The art-style setting asks for banners and this build can deliver them. */
    fun wanted(context: Context): Boolean =
        supported() && Prefs.pickerPrefersBanners(context)

    /**
     * Installed at this app's version or newer, and signed by this app's own
     * certificate. The download path already refuses anything else; this
     * also refuses a same-named package that arrived some other way, so the
     * launcher is never pointed at art this project did not sign.
     */
    fun ready(context: Context): Boolean {
        val code = installedVersionCode(context) ?: return false
        if (code < BuildConfig.VERSION_CODE) return false
        return context.packageManager.checkSignatures(context.packageName, PACKAGE) ==
            PackageManager.SIGNATURE_MATCH
    }

    /** The package a launcher should be told to apply right now. */
    fun applyTarget(context: Context): String =
        if (wanted(context) && ready(context)) PACKAGE else context.packageName

    /** The release asset built alongside this exact version. */
    fun apkUrl(): String = "$RELEASES/v${BuildConfig.VERSION_NAME}/$ASSET"

    private fun installedVersionCode(context: Context): Int? = try {
        val info = context.packageManager.getPackageInfo(PACKAGE, 0)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * Fetch and offer the companion, then remember that an apply is owed to
     * [launcherKey] (an [ApplyIconPack.Launcher.key]).
     *
     * The system installer is a separate screen the user may back out of, and
     * Android may recreate the screen behind it, so the apply cannot simply
     * follow in the next line and the target cannot live in a field.
     * [takePendingApply] hands the key back on resume, once the package is
     * really there, so the apply lands on the launcher that was chosen.
     *
     * When the companion cannot be had - no install permission yet, a failed
     * download, a refused hand-off - the art style goes back to Glyphs and
     * [onUnavailable] runs, so the switch never claims banners the launcher
     * is not showing.
     */
    fun ensure(activity: Activity, launcherKey: String, onUnavailable: () -> Unit = {}) {
        if (!supported()) return
        if (!UpdateInstaller.canInstall(activity)) {
            toast(activity, activity.getString(R.string.banners_install_permission))
            revertToGlyphs(activity)
            onUnavailable()
            UpdateInstaller.requestInstallPermission(activity)
            return
        }
        toast(activity, activity.getString(R.string.banners_downloading))
        UpdateInstaller.downloadCompanion(
            activity, apkUrl(), PACKAGE, BuildConfig.VERSION_CODE
        ) { event ->
            when (event) {
                is UpdateInstaller.Event.Progress -> Unit
                is UpdateInstaller.Event.Ready -> {
                    Prefs.setBannersPendingApply(activity, launcherKey)
                    try {
                        UpdateInstaller.install(activity, event.file)
                    } catch (e: Exception) {
                        Prefs.setBannersPendingApply(activity, null)
                        toast(activity, activity.getString(
                            R.string.banners_failed_fmt, e.message ?: e.javaClass.simpleName))
                        revertToGlyphs(activity)
                        onUnavailable()
                    }
                }
                is UpdateInstaller.Event.Failed -> {
                    toast(activity, activity.getString(R.string.banners_failed_fmt, event.reason))
                    revertToGlyphs(activity)
                    onUnavailable()
                }
            }
        }
    }

    /** The art style the launcher is actually showing when banners fall through. */
    private fun revertToGlyphs(context: Context) {
        Prefs.set(context, Prefs.KEY_PICK_BANNERS, false)
    }

    /** What an install [ensure] started came to, as seen on the next resume. */
    sealed class Pending {
        /** The companion is in place: apply to this launcher key now. */
        data class Ready(val launcherKey: String) : Pending()

        /** The installer closed without installing it; the style is Glyphs again. */
        object Declined : Pending()
    }

    /**
     * The outcome of the install [ensure] handed to the system installer,
     * returned once, on the first resume after it: that resume is the
     * installer closing. [Pending.Ready] carries the launcher to apply to.
     * [Pending.Declined] means the package still is not there (the user
     * backed out, or the install failed), so the art style has gone back to
     * Glyphs rather than showing a Banners switch over a glyph home screen.
     */
    fun takePendingApply(context: Context): Pending? {
        val key = Prefs.bannersPendingApply(context) ?: return null
        Prefs.setBannersPendingApply(context, null)
        if (ready(context)) return Pending.Ready(key)
        revertToGlyphs(context)
        return Pending.Declined
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
