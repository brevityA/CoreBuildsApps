package tv.corebuilds.eq.export

import org.json.JSONObject
import tv.corebuilds.eq.BuildConfig

/**
 * The Core EQ build that did something to a profile: version name and code.
 *
 * A profile's provenance (`docs/CORE_EQ_SOUND_EDITOR_ROADMAP.md`, milestone C)
 * is built from these: [Profile.origin] names the builds that created the
 * profile, recorded its sweep and analysed it ([ProfileOrigin]), written once;
 * [Profile.lastSavedBy] is the build that last wrote it into this TV's profile
 * list. Profiles saved before provenance existed have neither, and nothing
 * ever fills one in after the fact: an unknown origin stays unknown.
 *
 * Stored inside a profile's JSON as
 * `"provenance": {"origin": {...}, "last_saved_by": {...}}`, each build as
 * `{"app": "Core EQ", "version": "<name>", "code": <int>}`. The object is
 * omitted when neither is known, so an older profile is written with the same
 * keys as before.
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
