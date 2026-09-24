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

    fun supported(): Boolean = PACKAGE.isNotEmpty()

    /** The art-style setting asks for banners and this build can deliver them. */
    fun wanted(context: Context): Boolean =
        supported() && Prefs.pickerPrefersBanners(context)

    /** Installed at this app's version or newer. */
    fun ready(context: Context): Boolean {
        val code = installedVersionCode(context) ?: return false
        return code >= BuildConfig.VERSION_CODE
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
     * Fetch and offer the companion, then remember that an apply is owed.
     *
     * The system installer is a separate screen the user may back out of, so
     * the apply cannot simply follow in the next line. [takePendingApply]
     * lets the screen that started this finish the job on resume, once the
     * package is really there.
     */
    fun ensure(activity: Activity) {
        if (!supported()) return
        if (!UpdateInstaller.canInstall(activity)) {
            toast(activity, activity.getString(R.string.banners_install_permission))
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
                    Prefs.set(activity, Prefs.KEY_BANNERS_PENDING_APPLY, true)
                    try {
                        UpdateInstaller.install(activity, event.file)
                    } catch (e: Exception) {
                        Prefs.set(activity, Prefs.KEY_BANNERS_PENDING_APPLY, false)
                        toast(activity, activity.getString(
                            R.string.banners_failed_fmt, e.message ?: e.javaClass.simpleName))
                    }
                }
                is UpdateInstaller.Event.Failed ->
                    toast(activity, activity.getString(R.string.banners_failed_fmt, event.reason))
            }
        }
    }

    /**
     * True once, when an install [ensure] started has landed. A declined
     * install leaves the apply owed; it fires on the first resume after the
     * companion does arrive, and never while it is absent.
     */
    fun takePendingApply(context: Context): Boolean {
        if (!Prefs.bannersPendingApply(context)) return false
        if (!ready(context)) return false
        Prefs.set(context, Prefs.KEY_BANNERS_PENDING_APPLY, false)
        return true
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
