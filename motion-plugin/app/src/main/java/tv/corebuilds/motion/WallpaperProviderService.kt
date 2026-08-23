package tv.corebuilds.motion

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper

/**
 * The Core Motion Projectivy wallpaper provider.
 *
 * On Projectivy's rotation timer (TIME_ELAPSED) it returns the live-wallpaper
 * feed as a list of [Wallpaper] video objects; Projectivy caches them and
 * cycles through on the user's interval. This is the same contract Overflight
 * and every other wallpaper provider implements.
 *
 * The logging here is deliberate: when a plugin "isn't detected", the first
 * question is whether Projectivy ever bound to it at all. `adb logcat -s
 * CoreMotion` now answers that in one line.
 */
class WallpaperProviderService : Service() {

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "bound by ${callingPackageLabel()} action=${intent.action}")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "unbound")
        return super.onUnbind(intent)
    }

    private fun callingPackageLabel(): String =
        packageManager.getNameForUid(android.os.Binder.getCallingUid()) ?: "unknown"

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            // A static feed doesn't care which event fired; return the set on
            // every request and let Projectivy's cache + interval do the pacing.
            // Bundled Lottie vector loops come first, then the video feed.
            val bundled = BundledAnimations.list(this@WallpaperProviderService)
            val remote = MotionFeed.load(Preferences.feedUrl(this@WallpaperProviderService))
            Log.i(
                TAG,
                "getWallpapers(event=${event?.eventType}) -> " +
                    "${bundled.size} bundled + ${remote.size} feed",
            )
            return bundled + remote
        }

        override fun getPreferences(): String =
            Preferences.export(this@WallpaperProviderService)

        override fun setPreferences(params: String) {
            Preferences.import(this@WallpaperProviderService, params)
        }
    }

    companion object {
        private const val TAG = "CoreMotion"
    }
}
