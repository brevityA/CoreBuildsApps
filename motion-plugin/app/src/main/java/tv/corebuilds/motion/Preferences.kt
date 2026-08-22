package tv.corebuilds.motion

import android.content.Context
import org.json.JSONObject

/**
 * Plugin preferences (the feed URL). `getPreferences`/`setPreferences` are the
 * AIDL contract Projectivy uses to back up / restore plugin settings, so they
 * round-trip a small JSON object.
 */
object Preferences {

    private const val PREFS = "core_motion"
    private const val KEY_FEED_URL = "feed_url"

    fun feedUrl(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FEED_URL, MotionFeed.DEFAULT_FEED_URL)
            ?.ifBlank { MotionFeed.DEFAULT_FEED_URL }
            ?: MotionFeed.DEFAULT_FEED_URL
    }

    fun setFeedUrl(context: Context, url: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FEED_URL, url.ifBlank { MotionFeed.DEFAULT_FEED_URL }).apply()
    }

    fun export(context: Context): String =
        JSONObject().put("feed_url", feedUrl(context)).toString()

    fun import(context: Context, params: String) {
        val url = try {
            JSONObject(params).optString("feed_url", MotionFeed.DEFAULT_FEED_URL)
        } catch (_: Exception) {
            MotionFeed.DEFAULT_FEED_URL
        }
        setFeedUrl(context, url)
    }
}
