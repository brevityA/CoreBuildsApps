package tv.corebuilds.iconpack

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper as ProjectivyWallpaper

/**
 * The Core Builds walls as a Projectivy wallpaper source: the contract Core
 * Motion (motion-plugin/) implements for its video loops, here for the stills.
 * Which walls it hands over is [ProjectivyWalls]' business.
 *
 * Projectivy binds it when the pack is chosen under its wallpaper settings and
 * asks for the list on its own rotation timer. Nothing here runs otherwise:
 * there is no alarm and no background work, and the service reads the bundled
 * catalogue and this TV's settings without going online.
 *
 * `adb logcat -s CoreBuilds/Walls` shows whether Projectivy bound at all,
 * which is the first question when a source "isn't listed".
 */
class WallsProviderService : Service() {

    override fun onBind(intent: Intent): IBinder {
        // Not Binder.getCallingUid(): onBind is not a binder transaction, so
        // it would name this app, not Projectivy.
        Log.i(TAG, "bound, action=${intent.action}")
        return binder
    }

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<ProjectivyWallpaper> {
            val walls = ProjectivyWalls.walls(this@WallsProviderService)
            Log.i(TAG, "getWallpapers(event=${event?.eventType}) -> ${walls.size} walls")
            return walls.map(ProjectivyWalls::toProjectivy)
        }

        override fun getPreferences(): String =
            ProjectivyWalls.exportPrefs(this@WallsProviderService)

        override fun setPreferences(params: String?) {
            ProjectivyWalls.importPrefs(this@WallsProviderService, params)
        }
    }

    private companion object {
        const val TAG = "CoreBuilds/Walls"
    }
}
