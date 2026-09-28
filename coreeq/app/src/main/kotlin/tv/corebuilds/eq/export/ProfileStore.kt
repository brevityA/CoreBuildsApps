package tv.corebuilds.eq.export

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import tv.corebuilds.eq.dsp.Correction
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.Peaking
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.dsp.SyntheticRoom
import tv.corebuilds.eq.dsp.Targets

/**
 * Manages persistent storage of Core EQ profiles.
 * Automatically seeds default calibrated profiles if the database is fresh.
 */
class ProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_PROFILES)) {
            seedDefaults()
        }
    }

    fun getAllProfiles(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val list = mutableListOf<Profile>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(parseProfile(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getActiveProfile(): Profile {
        val all = getAllProfiles()
        val activeId = prefs.getString(KEY_ACTIVE_ID, null)
        return all.firstOrNull { it.id == activeId } ?: all.firstOrNull() ?: createFallbackProfile()
    }

    fun setActiveProfile(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun saveProfile(profile: Profile, setAsActive: Boolean = false) {
        val list = getAllProfiles().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            list[existingIndex] = profile
        } else {
            list.add(profile)
        }
        persistProfiles(list)
        if (setAsActive) {
            setActiveProfile(profile.id)
        }
    }

    fun deleteProfile(id: String): Boolean {
        val list = getAllProfiles().toMutableList()
        val removed = list.removeAll { it.id == id }
        if (removed) {
            persistProfiles(list)
            val currentActive = prefs.getString(KEY_ACTIVE_ID, null)
            if (currentActive == id) {
                list.firstOrNull()?.let { setActiveProfile(it.id) }
            }
        }
        return removed
    }

    private fun persistProfiles(list: List<Profile>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(JSONObject(Formats.exportProfileJson(p)))
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    private fun parseProfile(obj: JSONObject): Profile {
        val id = obj.optString("id", "profile-1")
        val name = obj.optString("name", "Living room")
        val timestamp = obj.optLong("timestamp_ms", System.currentTimeMillis())
        val target = obj.optString("target", "dialogue")
        val mic = obj.optString("microphone", "remote_mic")
        val device = obj.optString("device", "Android TV")
        val stimulus = obj.optString("stimulus", "sweep_10s")
        val capSec = obj.optDouble("capture_seconds", 10.0)
        val transHz = obj.optDouble("transition_hz", 384.0)
        val schroeder = obj.optDouble("schroeder_hz", 192.0)
        val rolloff = obj.optDouble("rolloff_hz", 40.0)
        val preamp = obj.optDouble("preamp_db", -3.39)

        val nullsList = mutableListOf<Double>()
        obj.optJSONArray("nulls_untouched_hz")?.let { narr ->
            for (i in 0 until narr.length()) nullsList.add(narr.getDouble(i))
        }

        val filtersList = mutableListOf<PeakingFilter>()
        obj.optJSONArray("filters")?.let { farr ->
            for (i in 0 until farr.length()) {
                val fo = farr.getJSONObject(i)
                filtersList.add(PeakingFilter(fo.getDouble("fc"), fo.getDouble("q"), fo.getDouble("gain")))
            }
        }

        val bandsList = mutableListOf<PlatformBand>()
        obj.optJSONArray("platform_bands")?.let { barr ->
            for (i in 0 until barr.length()) {
                val bo = barr.getJSONObject(i)
                bandsList.add(PlatformBand(bo.getDouble("center_hz"), bo.getInt("millibels")))
            }
        }

        val curveList = mutableListOf<CurvePoint>()
        obj.optJSONArray("curve")?.let { carr ->
            for (i in 0 until carr.length()) {
                val co = carr.getJSONObject(i)
                curveList.add(CurvePoint(co.getDouble("hz"), co.getDouble("measured_db"), co.getDouble("correction_db")))
            }
        }

        val capMap = mutableMapOf<String, String>()
        obj.optJSONObject("capability")?.let { co ->
            for (k in co.keys()) {
                capMap[k] = co.optString(k, "")
            }
        }

        return Profile(
            id = id,
            name = name,
            timestampMs = timestamp,
            target = target,
            micType = mic,
            deviceName = device,
            stimulus = stimulus,
            captureSeconds = capSec,
            schroederHz = schroeder,
            transitionHz = transHz,
            rolloffHz = rolloff,
            nullsUntouchedHz = nullsList,
            preampDb = preamp,
            filters = filtersList,
            platformBands = bandsList,
            curve = curveList,
            capabilityVerdict = capMap
        )
    }

    private fun seedDefaults() {
        val centres = DspConstants.ISO_CENTRES_HZ
        val measured = SyntheticRoom.generateDb(centres, 11L)
        val nulls = Correction.detectNulls(centres, measured)
        val tHz = Correction.transitionHz(54.0, 0.5)

        // 1. Living room (Dialogue target, remote mic)
        val targetDialogue = Targets.targetCurve("dialogue", centres)
        val corrDialogue = Correction.calculateCorrectionCurve(centres, measured, targetDialogue, transitionHz = tHz, nullMask = nulls)
        val filtersDialogue = Peaking.fitPeakingFilters(centres, corrDialogue, 6)
        val preampDialogue = Peaking.preampDb(filtersDialogue)
        val platformBands5 = Correction.collapseToBands(
            doubleArrayOf(60.0, 230.0, 910.0, 3600.0, 14000.0),
            { hz -> interpolate(hz, centres, corrDialogue) },
            -1500, 1500
        ).map { PlatformBand(it.first, it.second) }

        val curveDialogue = centres.indices.map {
            CurvePoint(centres[it], measured[it], corrDialogue[it])
        }

        val livingRoom = Profile(
            id = "profile-living-room",
            name = "Living room",
            timestampMs = System.currentTimeMillis() - 86400000L * 4,
            target = "dialogue",
            micType = "remote mic",
            deviceName = "Android TV",
            stimulus = "sweep_10s",
            captureSeconds = 10.0,
            volumeM3 = 54.0,
            rt60Seconds = 0.5,
            schroederHz = 192.0,
            transitionHz = tHz,
            rolloffHz = 40.0,
            nullsUntouchedHz = listOf(84.0),
            preampDb = preampDialogue,
            filters = filtersDialogue,
            platformBands = platformBands5,
            curve = curveDialogue,
            capabilityVerdict = mapOf(
                "bands" to "5 bands",
                "session_broadcast" to "no",
                "global_mix" to "not supported"
            )
        )

        // 2. Bedroom TV (B&K target, remote mic)
        val targetBk = Targets.targetCurve("bk", centres)
        val corrBk = Correction.calculateCorrectionCurve(centres, measured, targetBk, transitionHz = 320.0, nullMask = nulls)
        val filtersBk = Peaking.fitPeakingFilters(centres, corrBk, 5)
        val preampBk = Peaking.preampDb(filtersBk)
        val curveBk = centres.indices.map {
            CurvePoint(centres[it], measured[it], corrBk[it])
        }
        val bedroom = Profile(
            id = "profile-bedroom",
            name = "Bedroom TV",
            timestampMs = System.currentTimeMillis() - 86400000L * 2,
            target = "bk",
            micType = "remote mic",
            volumeM3 = 30.0,
            rt60Seconds = 0.6,
            schroederHz = 282.0,
            transitionHz = 320.0,
            preampDb = preampBk,
            filters = filtersBk,
            platformBands = platformBands5,
            curve = curveBk
        )

        // 3. Kitchen (Flat target, USB mic)
        val targetFlat = Targets.targetCurve("flat", centres)
        val corrFlat = Correction.calculateCorrectionCurve(centres, measured, targetFlat, transitionHz = 400.0, nullMask = nulls)
        val filtersFlat = Peaking.fitPeakingFilters(centres, corrFlat, 8)
        val preampFlat = Peaking.preampDb(filtersFlat)
        val curveFlat = centres.indices.map {
            CurvePoint(centres[it], measured[it], corrFlat[it])
        }
        val kitchen = Profile(
            id = "profile-kitchen",
            name = "Kitchen",
            timestampMs = System.currentTimeMillis() - 86400000L * 7,
            target = "flat",
            micType = "USB mic",
            volumeM3 = 25.0,
            rt60Seconds = 0.4,
            schroederHz = 252.0,
            transitionHz = 400.0,
            preampDb = preampFlat,
            filters = filtersFlat,
            platformBands = platformBands5,
            curve = curveFlat
        )

        val list = listOf(livingRoom, bedroom, kitchen)
        persistProfiles(list)
        setActiveProfile(livingRoom.id)
    }

    private fun createFallbackProfile(): Profile {
        return Profile(
            id = "profile-default",
            name = "Default",
            timestampMs = System.currentTimeMillis(),
            target = "dialogue",
            micType = "remote mic"
        )
    }

    private fun interpolate(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (xs.isEmpty()) return 0.0
        if (x <= xs[0]) return ys[0]
        if (x >= xs[xs.size - 1]) return ys[ys.size - 1]
        for (i in 0 until xs.size - 1) {
            if (x in xs[i]..xs[i + 1]) {
                val frac = (x - xs[i]) / (xs[i + 1] - xs[i])
                return ys[i] + frac * (ys[i + 1] - ys[i])
            }
        }
        return ys[0]
    }

    companion object {
        private const val PREFS_NAME = "core_eq_profiles"
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_ACTIVE_ID = "active_profile_id"
    }
}
