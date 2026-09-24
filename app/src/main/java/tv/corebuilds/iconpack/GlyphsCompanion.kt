package tv.corebuilds.iconpack

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast

/**
 * The Banners/Glyphs toggle, as far as launchers are concerned.
 *
 * A launcher auto-applies whatever the selected pack's appfilter maps, and it
 * reads that appfilter out of the pack's APK: this app cannot change its own
 * at runtime. So the toggle changes *which package* the launcher is told to
 * apply. Banners - the default - is this app, whose appfilter maps every app
 * to its 16:9 art. Glyphs is Core Builds Glyphs ([PACKAGE], built from
 * glyphs/), a resource-only twin whose appfilter maps the same components to
 * the same apps' square glyphs.
 *
 * The companion ships in the same release as this app and must be exactly
 * this app's version: a pack a version behind would silently miss every icon
 * added since. When it is missing or stale, [ensure] downloads the matching
 * release asset through [UpdateInstaller] (GitHub-only, size-bounded, and
 * signature-checked against this app's own certificate) and hands it to the
 * system installer, which always asks the user to confirm.
 *
 * Builds without a companion (the candidate test build) carry an empty
 * [PACKAGE]; for them [wanted] is always false and nothing here runs.
 */
object GlyphsCompanion {

    val PACKAGE: String = BuildConfig.GLYPHS_PACKAGE

    private const val RELEASES =
        "https://github.com/brevityA/CoreBuildsApps/releases/download"
    const val ASSET = "iconpack-glyphs-release.apk"

    /** The companion's display name, as launchers list it. */
    const val LABEL = "Core Builds Glyphs"

    /**
     * Set by the companion's GlyphsActivity on the icon-pick requests it
     * forwards here. A launcher that opened *Core Builds Glyphs* gets square
     * art back; one that opened this pack gets banners, whatever the toggle
     * says. The literal is repeated in GlyphsActivity.java (it cannot see
     * this class); tests/test_glyphs_pack.py holds the two equal.
     */
    const val EXTRA_PICK_GLYPHS = "tv.corebuilds.iconpack.extra.PICK_GLYPHS"

    /** onActivityResult request code for the companion install. */
    const val INSTALL_REQUEST = 0xB4

    /**
     * The newest [ensure] call. Downloads are serialised but not cancelled,
     * so an older request can still report after a newer one started; its
     * callback must not revert the style or open a second installer.
     */
    private var ensureGeneration = 0L

    fun supported(): Boolean = PACKAGE.isNotEmpty()

    /** The art-style setting asks for glyphs and this build can deliver them. */
    fun wanted(context: Context): Boolean =
        supported() && !Prefs.pickerPrefersBanners(context)

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
     * download, a refused hand-off - the art style goes back to Banners and
     * [onUnavailable] runs, so the switch never claims glyphs the launcher
     * is not showing.
     */
    fun ensure(activity: Activity, launcherKey: String, onUnavailable: () -> Unit = {}) {
        if (!supported()) return
        val generation = ++ensureGeneration
        if (!UpdateInstaller.canInstall(activity)) {
            toast(activity, activity.getString(R.string.glyphs_install_permission))
            revertToBanners(activity)
            onUnavailable()
            UpdateInstaller.requestInstallPermission(activity)
            return
        }
        toast(activity, activity.getString(R.string.glyphs_downloading))
        UpdateInstaller.downloadCompanion(
            activity, apkUrl(), PACKAGE, BuildConfig.VERSION_CODE
        ) { event ->
            if (generation != ensureGeneration) return@downloadCompanion
            when (event) {
                is UpdateInstaller.Event.Progress -> Unit
                is UpdateInstaller.Event.Ready -> {
                    Prefs.setCompanionPendingApply(activity, launcherKey)
                    try {
                        UpdateInstaller.installForResult(activity, event.file, INSTALL_REQUEST)
                    } catch (e: Exception) {
                        Prefs.setCompanionPendingApply(activity, null)
                        toast(activity, activity.getString(
                            R.string.glyphs_failed_fmt, e.message ?: e.javaClass.simpleName))
                        revertToBanners(activity)
                        onUnavailable()
                    }
                }
                is UpdateInstaller.Event.Failed -> {
                    toast(activity, activity.getString(R.string.glyphs_failed_fmt, event.reason))
                    revertToBanners(activity)
                    onUnavailable()
                }
            }
        }
    }

    /** The art style the launcher is actually showing when glyphs fall through. */
    private fun revertToBanners(context: Context) {
        Prefs.set(context, Prefs.KEY_PICK_BANNERS, true)
    }

    /** What an install [ensure] started came to, as seen on the next resume. */
    sealed class Pending {
        /** The companion is in place: apply to this launcher key now. */
        data class Ready(val launcherKey: String) : Pending()

        /** The installer closed without installing it; the style is Banners again. */
        object Declined : Pending()
    }

    /**
     * The system installer's answer to the install [ensure] started, from
     * onActivityResult ([INSTALL_REQUEST]). Returned once: [Pending.Ready]
     * with the launcher to apply to when the companion is really there,
     * otherwise [Pending.Declined] - the user backed out or the install
     * failed - and the art style is Banners again. Null when no install was
     * owed (a stale result).
     */
    fun onInstallResult(context: Context): Pending? {
        val key = Prefs.companionPendingApply(context) ?: return null
        Prefs.setCompanionPendingApply(context, null)
        // The package, not the result code, decides: some installers report
        // RESULT_CANCELED after "Done" on a successful install.
        if (ready(context)) return Pending.Ready(key)
        revertToBanners(context)
        return Pending.Declined
    }

    /**
     * On resume: the launcher an install is owed to, once the companion is
     * installed and verified - covering a result that never arrived, such as
     * the activity dying while the installer was up. Until then it returns
     * null and changes nothing. A resume is not an installer answer: the
     * installer can still be open in its own task.
     */
    fun takePendingApply(context: Context): Pending? {
        val key = Prefs.companionPendingApply(context) ?: return null
        if (!ready(context)) return null
        Prefs.setCompanionPendingApply(context, null)
        return Pending.Ready(key)
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
