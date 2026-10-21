package tv.corebuilds.iconpack

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * How far one icon request has got, for My requests.
 *
 * Two sources, the TV first: if the installed pack already maps the
 * requested component, the request is done here whatever GitHub says. Only
 * otherwise is the issue read from GitHub's public API, unauthenticated
 * (60 reads an hour per address), one GET per issue, by number only.
 */
object RequestStatus {

    enum class State { IN_PACK, DONE, PRIORITY, OPEN, CLOSED, UNKNOWN }

    /** The fields of a GitHub issue that decide the state. */
    data class Issue(val open: Boolean, val completed: Boolean, val priority: Boolean)

    private const val API = "https://api.github.com/repos/brevityA/CoreBuildsApps/issues/"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    /** The broker's label for a Plus owner's request (tools/icon_request_broker). */
    const val PRIORITY_LABEL = "priority"

    fun parse(json: String): Issue {
        val o = JSONObject(json)
        val labels = o.optJSONArray("labels")
        val priority = labels != null && (0 until labels.length()).any {
            labels.optJSONObject(it)?.optString("name") == PRIORITY_LABEL
        }
        return Issue(
            open = o.optString("state") == "open",
            completed = o.optString("state_reason") == "completed",
            priority = priority,
        )
    }

    fun state(issue: Issue?, inPack: Boolean): State = when {
        inPack -> State.IN_PACK
        issue == null -> State.UNKNOWN
        issue.open && issue.priority -> State.PRIORITY
        issue.open -> State.OPEN
        issue.completed -> State.DONE
        else -> State.CLOSED
    }

    /** Whether the installed pack's appfilter names [component] (`pkg/activity`). */
    fun inPack(context: Context, component: String): Boolean {
        val pkg = component.substringBefore('/', "")
        val activity = component.substringAfter('/', "")
        if (pkg.isEmpty() || activity.isEmpty()) return false
        val mapped = runCatching { AppAudit.mappedByPackage(context.assets) }.getOrNull() ?: return false
        return AppAudit.normalize(pkg, activity) in mapped[pkg].orEmpty()
    }

    /** Worker thread only. The issue, or null on any failure (offline, rate limit, gone). */
    fun fetch(number: Int): Issue? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(API + number).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CoreBuilds-IconPack")
            }
            if (conn.responseCode != 200) return null
            parse(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
