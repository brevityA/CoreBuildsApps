package tv.corebuilds.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * A user-controlled layout backup. It contains launcher preferences only:
 * favourites, hidden apps, settings and local launch history. Pack artwork is
 * a portable resource reference; imported image files stay on the TV that owns
 * them. There is no account, cloud destination or automatic write.
 */
internal object LauncherBackup {
    const val FORMAT = "core-builds-launcher-layout"
    const val VERSION = 1
    const val MAX_BYTES = 256 * 1024
    const val FILE_NAME = "core-builds-launcher-layout.json"

    fun export(context: Context): String {
        val values = JSONObject()
        LauncherPreferences.rawPreferences(context).all.forEach { (key, value) ->
            when (value) {
                is String -> values.put(key, value)
                is Boolean -> values.put(key, value)
                is Int -> values.put(key, value)
                is Long -> values.put(key, value)
                is Float -> values.put(key, value.toDouble())
                is Set<*> -> values.put(key, JSONArray(value.filterIsInstance<String>()))
                JSONObject.NULL -> Unit
                else -> Unit
            }
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("values", values)
            .toString(2)
    }

    /** Parses a bounded, versioned backup and writes only launcher-owned keys. */
    fun restore(context: Context, text: String): Boolean {
        if (text.length > MAX_BYTES) return false
        return try {
            val root = JSONObject(text)
            if (root.optString("format") != FORMAT || root.optInt("version") != VERSION) return false
            val values = root.getJSONObject("values")
            val map = HashMap<String, Any?>()
            for (key in values.keys()) {
                val value = values.get(key)
                map[key] = when {
                    value === JSONObject.NULL -> null
                    value is JSONArray -> (0 until value.length()).mapNotNull { index ->
                        value.opt(index) as? String
                    }
                    value is JSONObject -> null
                    value is Double -> value.toFloat()
                    else -> value
                }
            }
            LauncherPreferences.replaceAll(context, map)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Reads at most [MAX_BYTES] bytes, so a huge picked file cannot exhaust memory. */
    fun readBounded(stream: InputStream): String? {
        return try {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var total = 0
            var tooLarge = false
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_BYTES) {
                    tooLarge = true
                    break
                }
                out.write(buffer, 0, count)
            }
            if (tooLarge) null else String(out.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
