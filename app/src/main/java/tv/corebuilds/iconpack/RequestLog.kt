package tv.corebuilds.iconpack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The icon requests this TV filed through the broker, newest first: what My
 * requests (a Core Builds Plus extra) lists.
 *
 * Kept on the TV only, and only in the Google Play build, which is the only
 * one with My requests; a GitHub build records nothing. Each entry is what
 * the broker already made public in the issue (app name, component) plus the
 * issue number it answered with.
 */
object RequestLog {

    data class Entry(
        val issue: Int,
        val appName: String,
        val component: String,
        val mapped: Boolean,
        val at: Long,
    )

    private const val PREFS = "requests"
    private const val KEY = "log"
    private const val MAX = 200

    fun record(
        context: Context,
        issue: Int,
        appName: String,
        component: String,
        mapped: Boolean,
        at: Long = System.currentTimeMillis(),
    ) {
        if (!PlusStore.AVAILABLE || issue <= 0) return
        // A second press on the same app is a +1 on the same issue: one row.
        val kept = all(context).filterNot { it.issue == issue && it.component == component }
        val next = (listOf(Entry(issue, appName, component, mapped, at)) + kept).take(MAX)
        val array = JSONArray()
        for (e in next) {
            array.put(JSONObject()
                .put("issue", e.issue).put("app", e.appName).put("component", e.component)
                .put("mapped", e.mapped).put("at", e.at))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString()).apply()
    }

    fun all(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Entry(o.getInt("issue"), o.getString("app"), o.getString("component"),
                      o.optBoolean("mapped"), o.optLong("at"))
            }
        }.getOrDefault(emptyList())
    }
}
