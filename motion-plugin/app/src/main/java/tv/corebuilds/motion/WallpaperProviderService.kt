package tv.corebuilds.motion

import android.app.Service
import android.content.Intent
import android.os.IBinder
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
 */
class WallpaperProviderService : Service() {

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            // A static feed doesn't care which event fired; return the set on
            // every request and let Projectivy's cache + interval do the pacing.
            // Bundled Lottie vector loops come first, then the video feed.
            return BundledAnimations.list(this@WallpaperProviderService) +
                MotionFeed.load(Preferences.feedUrl(this@WallpaperProviderService))
        }

        override fun getPreferences(): String =
            Preferences.export(this@WallpaperProviderService)

        override fun setPreferences(params: String) {
            Preferences.import(this@WallpaperProviderService, params)
        }
    }
}
