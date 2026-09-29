package tv.corebuilds.eq.export

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import tv.corebuilds.eq.dsp.PeakingFilter

/**
 * What the correction service last did, in words the Home screen can show.
 *
 * [playing] is the live half of the same report: true while audio the
 * correction is attached to is audible, which is what the Home screen's
 * indicator pulses on. It dies with the service — a stale flag can never light
 * the badge, because the screen only trusts it while the service is running.
 */
data class EqStatus(
    val message: String,
    val isError: Boolean,
    val updatedMs: Long,
    val playing: Boolean = false
)

/**
 * Saved profiles, the active one, whether correction is switched on, and the
 * service's last status. A fresh install has no profiles: nothing is shown as
 * a measurement until this room has been measured.
 */
class ProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Builds before this one seeded three demo profiles made from a
        // synthetic room. They were never measurements, so they go.
        if (!prefs.getBoolean(KEY_DEMO_PURGED, false)) {
            val kept = getAllProfiles().filterNot { it.id in DEMO_IDS }
            persistProfiles(kept)
            if (prefs.getString(KEY_ACTIVE_ID, null) in DEMO_IDS) {
                prefs.edit().remove(KEY_ACTIVE_ID).apply()
            }
            prefs.edit().putBoolean(KEY_DEMO_PURGED, true).apply()
        }
    }

    fun getAllProfiles(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val list = mutableListOf<Profile>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                try {
                    list.add(parseProfile(arr.getJSONObject(i)))
                } catch (e: JSONException) {
                    Log.e(TAG, "Skipping unreadable profile #$i", e)
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Saved profiles are not valid JSON", e)
        }
        return list.sortedByDescending { it.timestampMs }
    }

    /** The active profile, or null before anything has been measured. */
    fun getActiveProfile(): Profile? {
        val all = getAllProfiles()
        val activeId = prefs.getString(KEY_ACTIVE_ID, null)
        return all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
    }

    fun setActiveProfile(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun saveProfile(profile: Profile, setAsActive: Boolean = false) {
        val list = getAllProfiles().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) list[existingIndex] = profile else list.add(profile)
        persistProfiles(list)
        if (setAsActive) setActiveProfile(profile.id)
    }

    fun deleteProfile(id: String): Boolean {
        val list = getAllProfiles().toMutableList()
        val removed = list.removeAll { it.id == id }
        if (removed) {
            persistProfiles(list)
            if (prefs.getString(KEY_ACTIVE_ID, null) == id) {
                val next = list.firstOrNull()
                if (next != null) setActiveProfile(next.id) else prefs.edit().remove(KEY_ACTIVE_ID).apply()
            }
        }
        return removed
    }

    var correctionEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    fun status(): EqStatus? {
        val msg = prefs.getString(KEY_STATUS_MSG, null) ?: return null
        return EqStatus(
            msg,
            prefs.getBoolean(KEY_STATUS_ERR, false),
            prefs.getLong(KEY_STATUS_TIME, 0L),
            prefs.getBoolean(KEY_STATUS_PLAYING, false)
        )
    }

    fun setStatus(message: String, isError: Boolean, playing: Boolean = false) {
        prefs.edit()
            .putString(KEY_STATUS_MSG, message)
            .putBoolean(KEY_STATUS_ERR, isError)
            .putLong(KEY_STATUS_TIME, System.currentTimeMillis())
            .putBoolean(KEY_STATUS_PLAYING, playing)
            .apply()
    }

    /** Packages that have announced an audio session to Core EQ, most recent first. */
    fun sessionPackages(): List<String> =
        prefs.getString(KEY_SESSION_PKGS, "")!!.split(',').filter { it.isNotBlank() }

    fun noteSessionPackage(pkg: String) {
        if (pkg.isBlank()) return
        val list = (listOf(pkg) + sessionPackages().filter { it != pkg }).take(8)
        prefs.edit().putString(KEY_SESSION_PKGS, list.joinToString(",")).apply()
    }

    private fun persistProfiles(list: List<Profile>) {
        val arr = JSONArray()
        for (p in list) arr.put(JSONObject(Formats.exportProfileJson(p)))
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    private fun parseProfile(obj: JSONObject): Profile {
        val room = obj.optJSONObject("room")
        fun optNullable(o: JSONObject?, key: String): Double? =
            if (o != null && o.has(key) && !o.isNull(key)) o.optDouble(key).takeIf { !it.isNaN() } else null

        val nullsList = mutableListOf<Double>()
        obj.optJSONArray("nulls_untouched_hz")?.let { a -> for (i in 0 until a.length()) nullsList.add(a.getDouble(i)) }

        val filtersList = mutableListOf<PeakingFilter>()
        obj.optJSONArray("filters")?.let { a ->
            for (i in 0 until a.length()) {
                val f = a.getJSONObject(i)
                filtersList.add(PeakingFilter(f.getDouble("fc"), f.getDouble("q"), f.getDouble("gain")))
            }
        }

        val bandsList = mutableListOf<PlatformBand>()
        obj.optJSONArray("platform_bands")?.let { a ->
            for (i in 0 until a.length()) {
                val b = a.getJSONObject(i)
                bandsList.add(PlatformBand(b.getDouble("center_hz"), b.getInt("millibels")))
            }
        }

        val curveList = mutableListOf<CurvePoint>()
        obj.optJSONArray("curve")?.let { a ->
            for (i in 0 until a.length()) {
                val c = a.getJSONObject(i)
                curveList.add(CurvePoint(c.getDouble("hz"), c.getDouble("measured_db"), c.getDouble("correction_db")))
            }
        }

        val capMap = mutableMapOf<String, String>()
        obj.optJSONObject("capability")?.let { c -> for (k in c.keys()) capMap[k] = c.optString(k, "") }
        val measurementNotes = mutableListOf<String>()
        obj.optJSONArray("measurement_notes")?.let { a ->
            for (i in 0 until a.length()) {
                val note = a.optString(i, "")
                if (note.isNotBlank()) measurementNotes.add(note)
            }
        }

        return Profile(
            id = obj.getString("id"),
            name = obj.optString("name", "Unnamed room"),
            timestampMs = obj.optLong("timestamp_ms", 0L),
            target = obj.optString("target", "dialogue"),
            micType = obj.optString("microphone", "microphone"),
            deviceName = obj.optString("device", "Android TV"),
            stimulus = obj.optString("stimulus", "sweep_10s"),
            captureSeconds = obj.optDouble("capture_seconds", 10.0),
            volumeM3 = optNullable(room, "volume_m3"),
            rt60Seconds = optNullable(room, "rt60_s"),
            schroederHz = optNullable(obj, "schroeder_hz"),
            transitionHz = obj.optDouble("transition_hz", 300.0),
            rolloffHz = obj.optDouble("rolloff_hz", 40.0),
            snrDb = optNullable(obj, "snr_db"),
            nullsUntouchedHz = nullsList,
            preampDb = obj.optDouble("preamp_db", 0.0),
            filters = filtersList,
            platformBands = bandsList,
            curve = curveList,
            capabilityVerdict = capMap,
            measurementNotes = measurementNotes
        )
    }

    companion object {
        private const val TAG = "CoreEqProfiles"
        private const val PREFS_NAME = "core_eq_profiles"
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_ID = "active_profile_id"
        private const val KEY_ENABLED = "correction_enabled"
        private const val KEY_STATUS_MSG = "status_message"
        private const val KEY_STATUS_ERR = "status_is_error"
        private const val KEY_STATUS_TIME = "status_time"
        private const val KEY_STATUS_PLAYING = "status_playing"
        private const val KEY_SESSION_PKGS = "session_packages"
        private const val KEY_DEMO_PURGED = "demo_profiles_purged"
        private val DEMO_IDS = setOf("profile-living-room", "profile-bedroom", "profile-kitchen")
    }
}
