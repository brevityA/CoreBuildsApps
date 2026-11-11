package tv.corebuilds.eq.export

import org.json.JSONObject
import tv.corebuilds.eq.BuildConfig

/**
 * The Core EQ build that did something to a profile: version name and code.
 *
 * Profiles carry two of these (`docs/CORE_EQ_SOUND_EDITOR_ROADMAP.md`, milestone
 * C): [Profile.createdBy], the build that produced the profile's correction
 * (a measurement, a REW import or a manual profile), written once; and
 * [Profile.lastSavedBy], the build that last saved that profile. Profiles saved
 * before provenance existed have neither, and nothing ever fills one in after
 * the fact: an unknown origin stays unknown.
 *
 * Stored inside a profile's JSON as
 * `"provenance": {"created_by": {...}, "last_saved_by": {...}}`, each
 * `{"app": "Core EQ", "version": "<name>", "code": <int>}`. The object is
 * omitted when both are unknown, so an older profile serializes exactly as
 * it did before.
 */
data class AppBuild(val versionName: String, val versionCode: Int) {

    /** "Core EQ 1.13.2 (29)". */
    fun label(): String = "$APP $versionName ($versionCode)"

    fun toJson(): JSONObject = JSONObject()
        .put("app", APP)
        .put("version", versionName)
        .put("code", versionCode)

    companion object {
        const val APP = "Core EQ"
        private const val MAX_VERSION_CHARS = 40

        /** This build. */
        fun current(): AppBuild = AppBuild(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        /**
         * A build read from JSON, or null when anything about it is missing or
         * implausible. A malformed record is treated as not recorded rather than
         * repaired: provenance is never guessed.
         */
        fun fromJson(obj: JSONObject?): AppBuild? {
            if (obj == null) return null
            if (obj.has("app") && obj.optString("app") != APP) return null
            val version = obj.opt("version") as? String ?: return null
            val name = version.trim().takeIf { it.isNotEmpty() && it.length <= MAX_VERSION_CHARS } ?: return null
            val code = when (val raw = obj.opt("code")) {
                is Int -> raw
                is Long -> raw.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
                else -> null
            } ?: return null
            if (code <= 0) return null
            return AppBuild(name, code)
        }
    }
}
