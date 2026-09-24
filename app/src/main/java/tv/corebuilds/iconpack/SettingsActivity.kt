package tv.corebuilds.iconpack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import java.io.File
import java.util.Locale

/**
 * The stored settings that change what the app does, two launcher tools, and
 * the door to the FAQ.
 *
 * Every row here is wired to a reader: update checks gate [UpdateChecker],
 * reduce motion gates the chip animation in [ChipAdapter], AMOLED chrome
 * repaints the window, and the cache row deletes the directory
 * [WallpaperDownloader.cacheDir] actually writes to. Rows that could only ever
 * store a preference nobody consults were left out of the design rather than
 * shipped inert. The LAUNCHER and HELP rows are actions rather than stored
 * settings, and they earn their place the same way: refresh re-fires the
 * detected launcher's apply contract through [ApplyIconPack], app info opens
 * the system details page where a force stop clears a bitmap cache re-applying
 * cannot reach, and the help row opens [FaqActivity]. All three read their
 * target from detection rather than assumption, and each says plainly when it
 * found nothing to act on.
 *
 * TV focus model: the whole row is the focusable target, not the switch inside
 * it. A SwitchCompat that takes focus separately means two D-pad stops per
 * setting and a thumb that moves without the row looking selected, so the
 * switches here are display-only (`focusable=false`, `clickable=false`) and
 * the row's click drives them.
 *
 * Changing the chrome setting calls [recreate] — the window background is set
 * during onCreate and there is no sane way to repaint a live window's system
 * bars without it.
 *
 * That used to carry the line "the recreate is why this screen keeps no scroll
 * state worth preserving", which stopped being true when the rows moved into a
 * ScrollView. Scroll position and focus survive the recreate anyway, without
 * anything here: [recreate] runs the normal save path, `set_scroll` and every
 * row have ids, ScrollView saves its own scroll offset, and the window restores
 * the focused view by id. Nothing was added to re-implement what the platform
 * already does; the claim was simply wrong and is removed. What the recreate
 * does still cost is a visible rebuild of the window, which is not worth the
 * fragility of repainting a live one.
 */
class SettingsActivity : TvActivity() {

    private lateinit var updateSwitch: SwitchCompat
    private lateinit var motionSwitch: SwitchCompat
    private lateinit var amoledSwitch: SwitchCompat
    private lateinit var bannerSwitch: SwitchCompat
    private lateinit var cacheSize: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        updateSwitch = findViewById(R.id.set_updates_switch)
        motionSwitch = findViewById(R.id.set_motion_switch)
        amoledSwitch = findViewById(R.id.set_amoled_switch)
        bannerSwitch = findViewById(R.id.set_banner_switch)
        cacheSize = findViewById(R.id.set_cache_size)

        updateSwitch.isChecked = Prefs.updateChecks(this)
        motionSwitch.isChecked = Prefs.reduceMotion(this)
        amoledSwitch.isChecked = Prefs.amoled(this)
        bannerSwitch.isChecked = Prefs.pickerPrefersBanners(this)

        row(R.id.set_updates_row) {
            val next = !updateSwitch.isChecked
            updateSwitch.isChecked = next
            Prefs.set(this, Prefs.KEY_UPDATE_CHECKS, next)
        }

        row(R.id.set_motion_row) {
            val next = !motionSwitch.isChecked
            motionSwitch.isChecked = next
            Prefs.set(this, Prefs.KEY_REDUCE_MOTION, next)
        }

        row(R.id.set_amoled_row) {
            val next = !amoledSwitch.isChecked
            amoledSwitch.isChecked = next
            Prefs.set(this, Prefs.KEY_AMOLED, next)
            recreate()
        }

        row(R.id.set_banner_row) {
            val next = !bannerSwitch.isChecked
            bannerSwitch.isChecked = next
            Prefs.set(this, Prefs.KEY_PICK_BANNERS, next)
            // Where there is a companion pack, the art style is also the
            // launcher's: re-apply now so the home screen follows the switch
            // (installing Core Builds Banners first if it is not there yet).
            if (BannersCompanion.supported()) refreshLauncher()
        }

        row(R.id.set_refresh_row) { refreshLauncher() }

        row(R.id.set_appinfo_row) { openLauncherInfo() }

        row(R.id.set_suite_row) {
            startActivity(Intent(this, SuiteActivity::class.java))
        }

        row(R.id.set_whatsnew_row) {
            startActivity(Intent(this, WhatsNewActivity::class.java))
        }

        row(R.id.set_audit_row) {
            startActivity(Intent(this, AuditorActivity::class.java))
        }

        row(R.id.set_faq_row) {
            startActivity(Intent(this, FaqActivity::class.java))
        }

        findViewById<View>(R.id.set_cache_clear).setOnClickListener {
            clearCache()
            showCacheSize()
        }

        findViewById<View>(R.id.set_back).setOnClickListener { finish() }

        showCacheSize()
    }

    override fun onResume() {
        super.onResume()
        // A companion install whose result never reached us: apply once the
        // package is really there (see MainActivity.onResume). Declines are
        // reported by the installer's result, in onActivityResult.
        onCompanionOutcome(BannersCompanion.takePendingApply(this))
        bannerSwitch.isChecked = Prefs.pickerPrefersBanners(this)
    }

    @Deprecated("Deprecated in AndroidX; the installer result still arrives here")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BannersCompanion.INSTALL_REQUEST) {
            onCompanionOutcome(BannersCompanion.onInstallResult(this))
            bannerSwitch.isChecked = Prefs.pickerPrefersBanners(this)
        }
    }

    /** Finish the apply the switch asked for, or say it was declined. */
    private fun onCompanionOutcome(pending: BannersCompanion.Pending?) {
        when (pending) {
            is BannersCompanion.Pending.Ready -> refreshLauncher(pending.launcherKey)
            BannersCompanion.Pending.Declined -> toast(getString(R.string.banners_declined))
            null -> Unit
        }
    }

    private fun row(id: Int, onSelect: () -> Unit) {
        findViewById<LinearLayout>(id).setOnClickListener { onSelect() }
    }

    /**
     * Total bytes under the wallpaper cache directory.
     *
     * Walked rather than cached: the directory is also written by
     * [WallpaperDownloader] while this screen is not in the foreground, so a
     * remembered figure would be stale exactly when someone came here to check
     * it. The tree is tens of files, so walking it on the main thread is
     * cheaper than the machinery to avoid doing so.
     */
    private fun cacheBytes(): Long {
        val dir = WallpaperDownloader.cacheDir(this)
        if (!dir.isDirectory) return 0L
        return dir.walkTopDown().filter(File::isFile).map(File::length).sum()
    }

    /**
     * Re-fire the detected launcher's apply contract.
     *
     * Launchers cache rendered cards, so a pack update can leave yesterday's
     * bitmaps on the home screen; re-applying is the documented nudge and this
     * row is that nudge, reachable from the sofa. Every outcome is named -
     * applied, manual path, or nothing detected - because a silent no-op is
     * exactly the confusion that ends in rebooting the TV.
     */
    private fun refreshLauncher(launcherKey: String? = null) {
        val launcher = launcherKey
            ?.let { key -> ApplyIconPack.installed(this).firstOrNull { it.key == key } }
            ?: ApplyIconPack.detectInstalled(this)
        if (launcher == null) {
            toast(getString(R.string.refresh_no_launcher))
            return
        }
        when (val result = ApplyIconPack.apply(this, launcher)) {
            is ApplyIconPack.Result.Applied ->
                toast(getString(R.string.refresh_applied_fmt, result.launcherName))
            is ApplyIconPack.Result.Manual ->
                toast(getString(R.string.refresh_manual_fmt, result.launcherName, result.instructions))
            is ApplyIconPack.Result.NotInstalled ->
                toast(getString(R.string.refresh_no_launcher))
            ApplyIconPack.Result.NeedsCompanion ->
                BannersCompanion.ensure(this, launcher.key) {
                    bannerSwitch.isChecked = Prefs.pickerPrefersBanners(this)
                }
        }
    }

    /**
     * The system app-info page for the detected launcher, the one place a
     * force stop lives. A force stop clears the launcher's in-memory card
     * cache, which re-applying cannot reach; opening the page from here saves
     * a D-pad hunt through the TV's own settings tree.
     */
    private fun openLauncherInfo() {
        val launcher = ApplyIconPack.detectInstalled(this)
        val pkg = launcher?.let { l ->
            with(ApplyIconPack) { l.packages.firstOrNull { isInstalled(it) } }
        }
        if (pkg == null) {
            toast(getString(R.string.appinfo_missing))
            return
        }
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$pkg")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast(getString(R.string.appinfo_missing))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun clearCache() {
        val dir = WallpaperDownloader.cacheDir(this)
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun showCacheSize() {
        val bytes = cacheBytes()
        cacheSize.text = when {
            bytes <= 0L -> getString(R.string.settings_cache_empty)
            bytes < 1_000_000L ->
                String.format(Locale.US, "%.0f kB", bytes / 1_000.0)
            bytes < 1_000_000_000L ->
                String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
            else ->
                String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        }
    }
}
