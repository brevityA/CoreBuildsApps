package tv.corebuilds.eq.export

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.ManualEqPreset
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.dsp.PeakingFilter
import java.util.UUID

/**
 * What the correction service last did, in words the Home screen can show.
 *
 * [playing] is a live playback hint for the output-mix path: true when that
 * effect is configured and Android reports active media playback. It does not
 * prove the reported stream traverses the effect or is audible at the output.
 * The flag dies with the service, and Home trusts it only while the service is
 * running.
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
 * a measurement until the room is measured. A separate manual-only profile can
 * exist without pretending that a measurement happened.
 */
class ProfileStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        if (prefs.contains(KEY_SESSION_PKGS)) prefs.edit().remove(KEY_SESSION_PKGS).apply()
        migrateLegacyManualFiltersToEveryday()
    }

    /** Move v1 manual trims into the Everyday overlay without losing profile-specific edits. */
    private fun migrateLegacyManualFiltersToEveryday() {
        if (prefs.getBoolean(KEY_MODE_EQ_MIGRATED, false)) return
        val modes = ContentModeStore(appContext)
        val profiles = getAllProfiles()
        val migrated = mutableListOf<Profile>()
        var changed = false
        for (profile in profiles) {
            if (profile.manualFilters.isEmpty()) {
                migrated += profile
                continue
            }
            if (!modes.importLegacyEveryday(profile.id, profile.manualFilters)) {
                Log.e(TAG, "Could not durably migrate manual EQ for profile ${profile.id}; legacy filters were kept")
                return
            }
            migrated += profile.copy(manualFilters = emptyList())
            changed = true
        }
        if (changed && !persistProfiles(migrated, synchronous = true)) {
            Log.e(TAG, "Could not durably save migrated profiles; legacy manual EQ was kept")
            return
        }
        if (!prefs.edit().putBoolean(KEY_MODE_EQ_MIGRATED, true).commit()) {
            Log.e(TAG, "Could not record manual EQ migration completion; migration will retry next launch")
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

    /** The active room or manual profile, or null until one is created. */
    fun getActiveProfile(): Profile? {
        val all = getAllProfiles()
        val activeId = prefs.getString(KEY_ACTIVE_ID, null)
        return all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
    }

    /** The id the viewer chose, which may not be the one applied on the current output. */
    fun chosenId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

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
            ContentModeStore(appContext).deleteProfileOverlays(id)
            if (prefs.getString(KEY_ACTIVE_ID, null) == id) {
                val next = list.firstOrNull()
                if (next != null) setActiveProfile(next.id) else prefs.edit().remove(KEY_ACTIVE_ID).apply()
            }
        }
        return removed
    }

    /** Built-in tone presets followed by any presets the viewer saved. */
    fun manualEqPresets(): List<ManualEqPreset> = ManualEq.BUILT_IN_PRESETS + storedManualEqPresets()

    /** Save (or replace by case-insensitive name) a reusable user preset. */
    fun saveManualEqPreset(name: String, filters: List<PeakingFilter>): ManualEqPreset {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Preset name cannot be empty" }
        require(cleanName.length <= 32) { "Preset names must be 32 characters or fewer" }
        require(ManualEq.BUILT_IN_PRESETS.none { it.name.equals(cleanName, ignoreCase = true) }) {
            "Choose a name other than a built-in preset"
        }
        val existing = storedManualEqPresets().firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        val preset = ManualEqPreset(
            id = existing?.id ?: "custom-${UUID.randomUUID()}",
            name = cleanName,
            filters = ManualEq.sanitize(filters)
        )
        val updated = storedManualEqPresets().filterNot { it.id == preset.id } + preset
        persistManualEqPresets(updated)
        return preset
    }

    fun deleteManualEqPreset(id: String): Boolean {
        if (ManualEq.BUILT_IN_PRESETS.any { it.id == id }) return false
        val presets = storedManualEqPresets()
        val updated = presets.filterNot { it.id == id }
        if (updated.size == presets.size) return false
        persistManualEqPresets(updated)
        return true
    }

    private fun storedManualEqPresets(): List<ManualEqPreset> {
        val raw = prefs.getString(KEY_MANUAL_PRESETS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val filters = mutableListOf<PeakingFilter>()
                    val savedFilters = item.optJSONArray("filters") ?: JSONArray()
                    for (filterIndex in 0 until savedFilters.length()) {
                        val filter = savedFilters.optJSONObject(filterIndex) ?: continue
                        filters += PeakingFilter(
                            filter.optDouble("fc", Double.NaN),
                            filter.optDouble("q", Double.NaN),
                            filter.optDouble("gain", Double.NaN)
                        )
                    }
                    val id = item.optString("id", "")
                    val name = item.optString("name", "").trim()
                    if (id.isNotBlank() && name.isNotBlank()) {
                        add(ManualEqPreset(id, name, ManualEq.sanitize(filters)))
                    }
                }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Ignoring unreadable manual EQ presets", e)
            emptyList()
        }
    }

    private fun persistManualEqPresets(presets: List<ManualEqPreset>) {
        val array = JSONArray()
        for (preset in presets) {
            val filters = JSONArray()
            for (filter in ManualEq.sanitize(preset.filters)) {
                filters.put(JSONObject()
                    .put("fc", filter.fc)
                    .put("q", filter.q)
                    .put("gain", filter.gain))
            }
            array.put(JSONObject()
                .put("id", preset.id)
                .put("name", preset.name)
                .put("filters", filters))
        }
        prefs.edit().putString(KEY_MANUAL_PRESETS, array.toString()).apply()
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

    /** The currently-applied engine layout; transient runtime state, not profile data. */
    fun runtimeBands(): List<PlatformBand> {
        val raw = prefs.getString(KEY_RUNTIME_BANDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val band = array.getJSONObject(index)
                PlatformBand(band.getDouble("center_hz"), band.getInt("millibels"))
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Ignoring unreadable runtime band layout", e)
            emptyList()
        }
    }

    fun setRuntimeBands(bands: List<PlatformBand>) {
        if (bands.isEmpty()) {
            clearRuntimeBands()
            return
        }
        val array = JSONArray()
        for (band in bands) {
            array.put(JSONObject()
                .put("center_hz", band.centerHz)
                .put("millibels", band.millibels))
        }
        prefs.edit().putString(KEY_RUNTIME_BANDS, array.toString()).apply()
    }

    fun clearRuntimeBands() {
        prefs.edit().remove(KEY_RUNTIME_BANDS).apply()
    }

    private fun persistProfiles(list: List<Profile>, synchronous: Boolean = false): Boolean {
        val arr = JSONArray()
        for (p in list) arr.put(JSONObject(Formats.exportProfileJson(p)))
        val editor = prefs.edit().putString(KEY_PROFILES, arr.toString())
        return if (synchronous) {
            editor.commit()
        } else {
            editor.apply()
            true
        }
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
        val manualFiltersList = mutableListOf<PeakingFilter>()
        obj.optJSONArray("manual_filters")?.let { a ->
            for (i in 0 until a.length()) {
                val f = a.optJSONObject(i) ?: continue
                manualFiltersList.add(
                    PeakingFilter(
                        f.optDouble("fc", Double.NaN),
                        f.optDouble("q", Double.NaN),
                        f.optDouble("gain", Double.NaN)
                    )
                )
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
            qualityScore = if (obj.has("quality_score") && !obj.isNull("quality_score"))
                obj.optInt("quality_score", -1).takeIf { it in 0..100 } else null,
            nullsUntouchedHz = nullsList,
            preampDb = obj.optDouble("room_preamp_db", obj.optDouble("preamp_db", 0.0)),
            filters = filtersList,
            platformBands = bandsList,
            curve = curveList,
            capabilityVerdict = capMap,
            measurementNotes = measurementNotes,
            outputKind = obj.optJSONObject("output")?.optString("kind", "")?.takeIf { it.isNotBlank() },
            outputName = obj.optJSONObject("output")?.let { o -> if (o.isNull("name")) null else o.optString("name") }
                ?.takeIf { it.isNotBlank() },
            manualFilters = ManualEq.sanitize(manualFiltersList),
            manualOnly = obj.optBoolean("manual_only", false)
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
        private const val KEY_RUNTIME_BANDS = "runtime_effect_bands"
        private const val KEY_MANUAL_PRESETS = "manual_eq_presets"
        private const val KEY_MODE_EQ_MIGRATED = "manual_eq_migrated_to_modes"
        private const val KEY_DEMO_PURGED = "demo_profiles_purged"
        private val DEMO_IDS = setOf("profile-living-room", "profile-bedroom", "profile-kitchen")
    }
}
