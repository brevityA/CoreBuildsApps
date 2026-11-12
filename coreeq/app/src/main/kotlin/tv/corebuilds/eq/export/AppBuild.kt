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
 *
 * A build read from an imported file is the file's claim, not a verified
 * identity: anyone can write any version into a JSON file. [fromJson] only makes
 * sure the claim is a bounded, single-line version number before it is shown.
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

        /**
         * A version name as Core EQ writes one: up to three dotted numbers and an
         * optional short suffix ("1.14.0-rc1"). Anything else (spaces, newlines,
         * control or direction characters, a fourth number, which is the shape
         * of an IPv4 address, or a colon, the shape of a MAC or IPv6 address)
         * is not a version, so the record reads as not recorded.
         */
        private val VERSION = Regex("""\d{1,4}(?:\.\d{1,4}){0,2}(?:[-+][0-9A-Za-z](?:[0-9A-Za-z.-]{0,18}[0-9A-Za-z])?)?""")

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
            // Not trimmed: a version with a trailing newline or space was not written by Core EQ.
            val name = version.takeIf { it.length <= MAX_VERSION_CHARS && VERSION.matches(it) } ?: return null
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
