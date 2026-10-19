package tv.corebuilds.eq.mode

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.export.Profile

/** Persists mode overlays, app rules and the user's manual/automatic mode policy. */
class ContentModeStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun selectedMode(): ContentMode = ContentMode.fromKey(prefs.getString(KEY_SELECTED_MODE, null))

    fun automaticSwitching(): Boolean = prefs.getBoolean(KEY_AUTOMATIC, false)

    fun stickyManualOverride(): Boolean = prefs.getBoolean(KEY_STICKY_OVERRIDE, false)

    fun setStickyManualOverride(sticky: Boolean) {
        prefs.edit().putBoolean(KEY_STICKY_OVERRIDE, sticky).apply()
        prefs.getString(KEY_OVERRIDE_MODE, null)?.let {
            prefs.edit().putBoolean(KEY_OVERRIDE_STICKY, sticky).apply()
        }
    }

    fun setAutomaticSwitching(
        enabled: Boolean,
        activePackages: Set<String> = lastActivePackages(),
        hasUnidentifiedPlayer: Boolean = lastActivePlayerUnknown()
    ) {
        val editor = prefs.edit().putBoolean(KEY_AUTOMATIC, enabled)
        if (!enabled) editor.putString(KEY_SELECTED_MODE, currentDecision().mode.key)
        editor.apply()
        clearOverride()
        resolve(activePackages, hasUnidentifiedPlayer)
    }

    /** Select a mode manually; in automatic mode this becomes a sticky or temporary override. */
    fun selectMode(
        mode: ContentMode,
        activePackages: Set<String> = lastActivePackages(),
        hasUnidentifiedPlayer: Boolean = lastActivePlayerUnknown()
    ) {
        if (!automaticSwitching()) {
            prefs.edit().putString(KEY_SELECTED_MODE, mode.key).apply()
            clearOverride()
            resolve(activePackages, hasUnidentifiedPlayer)
            return
        }
        val playbackStarted = activePackages.isNotEmpty() || hasUnidentifiedPlayer
        prefs.edit()
            .putString(KEY_OVERRIDE_MODE, mode.key)
            .putBoolean(KEY_OVERRIDE_STICKY, stickyManualOverride())
            .putString(KEY_OVERRIDE_PACKAGES, encodePackages(activePackages))
            .putBoolean(KEY_OVERRIDE_STARTED, playbackStarted)
            .putBoolean(KEY_OVERRIDE_UNKNOWN_PLAYER, hasUnidentifiedPlayer)
            .apply()
        resolve(activePackages, hasUnidentifiedPlayer)
    }

    fun setAppRule(packageName: String, mode: ContentMode?) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        val rules = readRules().toMutableMap()
        if (mode == null) rules.remove(pkg) else rules[pkg] = mode
        val json = JSONObject()
        rules.toSortedMap().forEach { (name, value) -> json.put(name, value.key) }
        prefs.edit().putString(KEY_APP_RULES, json.toString()).apply()
        resolve(lastActivePackages(), lastActivePlayerUnknown())
    }

    fun appRules(): Map<String, ContentMode> = readRules()

    fun modeFilters(profileId: String, mode: ContentMode): List<PeakingFilter> {
        if (profileId.isBlank()) return emptyList()
        val byMode = readModeRoot().optJSONObject(profileId) ?: return emptyList()
        val filters = byMode.optJSONArray(mode.key) ?: return emptyList()
        return parseFilters(filters)
    }

    fun allModeFilters(profileId: String): Map<ContentMode, List<PeakingFilter>> {
        if (profileId.isBlank()) return emptyMap()
        val byMode = readModeRoot().optJSONObject(profileId) ?: return emptyMap()
        return ContentMode.entries.mapNotNull { mode ->
            val array = byMode.optJSONArray(mode.key) ?: return@mapNotNull null
            mode to parseFilters(array)
        }.toMap()
    }

    fun saveModeFilters(profileId: String, mode: ContentMode, filters: List<PeakingFilter>) {
        writeModeFilters(profileId, mode, filters, synchronous = false)
    }

    fun deleteProfileOverlays(profileId: String) {
        if (profileId.isBlank()) return
        val root = readModeRoot()
        if (!root.has(profileId)) return
        root.remove(profileId)
        prefs.edit().putString(KEY_MODE_EQ, root.toString()).apply()
    }

    /** Merge legacy trims into Everyday without overwriting newer per-mode edits. */
    fun importLegacyEveryday(profileId: String, filters: List<PeakingFilter>): Boolean {
        if (filters.isEmpty()) return true
        val existing = modeFilters(profileId, ContentMode.EVERYDAY)
        // ManualEq.sanitize keeps the later filter at a duplicated band centre,
        // so a previously edited Everyday band wins while untouched legacy
        // bands are preserved. Re-running after a failed profile commit is safe.
        val merged = ManualEq.mergeLegacyWithOverlay(filters, existing)
        return writeModeFilters(profileId, ContentMode.EVERYDAY, merged, synchronous = true)
    }

    fun effectiveProfile(profile: Profile, mode: ContentMode): Profile = profile.copy(
        manualFilters = ManualEq.sanitize(profile.manualFilters + modeFilters(profile.id, mode))
    )

    /** Current app set and last resolver result are persisted for visible UI and override lifetime. */
    fun lastActivePackages(): Set<String> = decodePackages(prefs.getString(KEY_ACTIVE_PACKAGES, null))

    fun lastActivePlayerUnknown(): Boolean = prefs.getBoolean(KEY_UNKNOWN_ACTIVE_PLAYER, false)

    /** Whether the persisted player set came from a complete, recognized scan. */
    fun lastActiveSnapshotComplete(): Boolean = prefs.getBoolean(KEY_ACTIVE_SNAPSHOT_COMPLETE, false)

    fun currentDecision(): ModeDecision = ModeDecision(
        mode = ContentMode.fromKey(prefs.getString(KEY_EFFECTIVE_MODE, null) ?: selectedMode().key),
        reason = prefs.getString(KEY_EFFECTIVE_REASON, null) ?: "Manual selection",
        activePackages = lastActivePackages(),
        conflictingPackages = decodePackages(prefs.getString(KEY_CONFLICTING_PACKAGES, null)),
        hasUnidentifiedPlayer = lastActivePlayerUnknown()
    )

    fun resolve(
        activePackages: Set<String>,
        hasUnidentifiedPlayer: Boolean = false,
        advanceTemporaryOverride: Boolean = lastActiveSnapshotComplete()
    ): ModeDecision {
        val cleanPackages = activePackages.filter { it.isNotBlank() }.toSortedSet()
        val savedOverride = readOverride()
        val nextOverride = ContentModeResolver.reconcileTemporaryOverride(
            override = savedOverride,
            activePackages = cleanPackages,
            hasUnidentifiedPlayer = hasUnidentifiedPlayer,
            snapshotComplete = advanceTemporaryOverride
        )
        persistOverride(nextOverride)
        val decision = ContentModeResolver.resolve(
            activePackages = cleanPackages,
            rules = readRules(),
            automatic = automaticSwitching(),
            selectedMode = selectedMode(),
            override = nextOverride,
            hasUnidentifiedPlayer = hasUnidentifiedPlayer
        )
        prefs.edit()
            .putString(KEY_ACTIVE_PACKAGES, encodePackages(cleanPackages))
            .putBoolean(KEY_UNKNOWN_ACTIVE_PLAYER, hasUnidentifiedPlayer)
            .putBoolean(KEY_ACTIVE_SNAPSHOT_COMPLETE, advanceTemporaryOverride)
            .putString(KEY_EFFECTIVE_MODE, decision.mode.key)
            .putString(KEY_EFFECTIVE_REASON, decision.reason)
            .putString(KEY_CONFLICTING_PACKAGES, encodePackages(decision.conflictingPackages))
            .apply()
        return decision
    }

    private fun readOverride(): ModeOverride? {
        val modeKey = prefs.getString(KEY_OVERRIDE_MODE, null) ?: return null
        return ModeOverride(
            mode = ContentMode.fromKey(modeKey),
            sticky = prefs.getBoolean(KEY_OVERRIDE_STICKY, false),
            anchorPackages = decodePackages(prefs.getString(KEY_OVERRIDE_PACKAGES, null)),
            playbackStarted = prefs.getBoolean(KEY_OVERRIDE_STARTED, false),
            anchorHasUnidentifiedPlayer = prefs.getBoolean(KEY_OVERRIDE_UNKNOWN_PLAYER, false)
        )
    }

    private fun persistOverride(override: ModeOverride?) {
        val editor = prefs.edit()
        if (override == null) {
            editor.remove(KEY_OVERRIDE_MODE)
                .remove(KEY_OVERRIDE_PACKAGES)
                .remove(KEY_OVERRIDE_STARTED)
                .remove(KEY_OVERRIDE_STICKY)
                .remove(KEY_OVERRIDE_UNKNOWN_PLAYER)
        } else {
            editor.putString(KEY_OVERRIDE_MODE, override.mode.key)
                .putString(KEY_OVERRIDE_PACKAGES, encodePackages(override.anchorPackages))
                .putBoolean(KEY_OVERRIDE_STARTED, override.playbackStarted)
                .putBoolean(KEY_OVERRIDE_STICKY, override.sticky)
                .putBoolean(KEY_OVERRIDE_UNKNOWN_PLAYER, override.anchorHasUnidentifiedPlayer)
        }
        editor.apply()
    }

    private fun clearOverride() {
        prefs.edit()
            .remove(KEY_OVERRIDE_MODE)
            .remove(KEY_OVERRIDE_PACKAGES)
            .remove(KEY_OVERRIDE_STARTED)
            .remove(KEY_OVERRIDE_STICKY)
            .remove(KEY_OVERRIDE_UNKNOWN_PLAYER)
            .apply()
    }

    private fun readRules(): Map<String, ContentMode> {
        val source = prefs.getString(KEY_APP_RULES, null)
        if (source.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(source)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val packageName = keys.next()
                    val mode = ContentMode.fromKey(json.optString(packageName, null))
                    put(packageName, mode)
                }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Ignoring unreadable app-to-mode rules", e)
            emptyMap()
        }
    }

    private fun readModeRoot(): JSONObject = try {
        JSONObject(prefs.getString(KEY_MODE_EQ, "{}").orEmpty())
    } catch (e: JSONException) {
        Log.w(TAG, "Ignoring unreadable mode EQ overlays", e)
        JSONObject()
    }

    private fun writeModeFilters(
        profileId: String,
        mode: ContentMode,
        filters: List<PeakingFilter>,
        synchronous: Boolean
    ): Boolean {
        if (profileId.isBlank()) return false
        val root = readModeRoot()
        val byMode = root.optJSONObject(profileId) ?: JSONObject()
        val clean = ManualEq.sanitize(filters)
        if (clean.isEmpty()) {
            byMode.remove(mode.key)
        } else {
            val array = JSONArray()
            clean.forEach { filter ->
                array.put(JSONObject()
                    .put("fc", filter.fc)
                    .put("q", filter.q)
                    .put("gain", filter.gain))
            }
            byMode.put(mode.key, array)
        }
        if (byMode.length() == 0) root.remove(profileId) else root.put(profileId, byMode)
        val editor = prefs.edit().putString(KEY_MODE_EQ, root.toString())
        return if (synchronous) {
            editor.commit()
        } else {
            editor.apply()
            true
        }
    }

    private fun parseFilters(array: JSONArray): List<PeakingFilter> {
        val filters = mutableListOf<PeakingFilter>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            filters += PeakingFilter(
                item.optDouble("fc", Double.NaN),
                item.optDouble("q", Double.NaN),
                item.optDouble("gain", Double.NaN)
            )
        }
        return ManualEq.sanitize(filters)
    }

    private fun encodePackages(packages: Set<String>): String = JSONArray().apply {
        packages.filter { it.isNotBlank() }.sorted().forEach { put(it) }
    }.toString()

    private fun decodePackages(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return try {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Ignoring unreadable active app list", e)
            emptySet()
        }
    }

    companion object {
        private const val TAG = "CoreEqModes"
        private const val PREFS_NAME = "core_eq_modes"
        private const val KEY_MODE_EQ = "mode_eq_json"
        private const val KEY_SELECTED_MODE = "selected_mode"
        private const val KEY_AUTOMATIC = "automatic_switching"
        private const val KEY_STICKY_OVERRIDE = "sticky_manual_override"
        private const val KEY_OVERRIDE_MODE = "manual_override_mode"
        private const val KEY_OVERRIDE_STICKY = "manual_override_sticky"
        private const val KEY_OVERRIDE_PACKAGES = "manual_override_packages"
        private const val KEY_OVERRIDE_STARTED = "manual_override_started"
        private const val KEY_OVERRIDE_UNKNOWN_PLAYER = "manual_override_unknown_player"
        private const val KEY_APP_RULES = "app_mode_rules"
        private const val KEY_ACTIVE_PACKAGES = "active_packages"
        private const val KEY_UNKNOWN_ACTIVE_PLAYER = "unknown_active_player"
        private const val KEY_ACTIVE_SNAPSHOT_COMPLETE = "active_snapshot_complete"
        private const val KEY_EFFECTIVE_MODE = "effective_mode"
        private const val KEY_EFFECTIVE_REASON = "effective_reason"
        private const val KEY_CONFLICTING_PACKAGES = "conflicting_packages"
    }
}
