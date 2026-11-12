package tv.corebuilds.eq.export

import org.json.JSONObject

/**
 * Where a profile's correction came from, recorded once when the profile is
 * created (`docs/CORE_EQ_SOUND_EDITOR_ROADMAP.md`, milestone C).
 *
 * The fields keep apart what different builds did, and leave out what is not
 * known:
 *
 * | [kind]              | [createdBy]                  | [capturedBy]                         | [analysedBy]          |
 * |---------------------|------------------------------|--------------------------------------|-----------------------|
 * | `tv_measurement`    | TV build that saved it       | the same TV build (it played and recorded the sweep) | the same TV build |
 * | `phone_measurement` | TV build that saved it       | the phone's build, only if the phone reports it (it does not yet) | the TV build |
 * | `rew_import`        | TV build that read the file  | absent: measured outside Core EQ     | absent                |
 * | `manual`            | TV build that created it     | absent: nothing was measured         | absent                |
 *
 * [createdBy] is the build that created the *profile*, which is not always
 * one that measured anything: a REW import's [createdBy] is the build that
 * read the file, and the software and version that measured it are not
 * recorded. A profile saved
 * before this existed has no origin, and nothing fills one in later.
 *
 * Stored inside the profile JSON as `provenance.origin`:
 * `{"kind": "...", "created_by": {...}, "captured_by": {...}, "analysed_by": {...}}`,
 * each build as [AppBuild.toJson]. A [kind] this build does not know (written
 * by a newer one) is kept as it is and shown as recorded.
 *
 * Every origin is a record, not a proof. One written on this TV says what this
 * TV's builds did; one that came in with an imported file says what the file
 * says, and nothing checks it. Details labels it as not verified.
 */
data class ProfileOrigin(
    val kind: String,
    val createdBy: AppBuild,
    val capturedBy: AppBuild? = null,
    val analysedBy: AppBuild? = null
) {

    val isMeasurement: Boolean get() = kind == TV_MEASUREMENT || kind == PHONE_MEASUREMENT

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind)
        put("created_by", createdBy.toJson())
        capturedBy?.let { put("captured_by", it.toJson()) }
        analysedBy?.let { put("analysed_by", it.toJson()) }
    }

    /** One clause for the diagnostics profile line, with no names in it. */
    fun summary(): String = when (kind) {
        TV_MEASUREMENT -> "TV measurement, " + if (capturedBy == createdBy && analysedBy == createdBy) {
            "recorded and analysed by ${createdBy.label()}"
        } else {
            "saved by ${createdBy.label()} · recorded by ${capturedBy?.label() ?: "not recorded"}" +
                " · analysed by ${analysedBy?.label() ?: "not recorded"}"
        }
        PHONE_MEASUREMENT -> "phone measurement, analysed by ${analysedBy?.label() ?: "not recorded"}" +
            " · phone build ${capturedBy?.label() ?: "not reported"}"
        REW_IMPORT -> "measurement file imported by ${createdBy.label()}, measured outside Core EQ"
        MANUAL -> "manual profile created by ${createdBy.label()}"
        // The kind itself is not repeated: it came from a file and could be anything
        // that fits the pattern, a name included. Details shows it on the TV.
        else -> "origin of a kind this build does not know, recorded by ${createdBy.label()}"
    }

    companion object {
        const val TV_MEASUREMENT = "tv_measurement"
        const val PHONE_MEASUREMENT = "phone_measurement"
        const val REW_IMPORT = "rew_import"
        const val MANUAL = "manual"

        private val KIND = Regex("[a-z][a-z0-9_]{0,39}")

        /** The TV played, recorded and analysed the sweep: all one build. */
        fun tvMeasurement(build: AppBuild) = ProfileOrigin(TV_MEASUREMENT, build, capturedBy = build, analysedBy = build)

        /** The phone recorded; this TV build analysed and saved. The phone's build only when it reported one. */
        fun phoneMeasurement(tvBuild: AppBuild, phoneBuild: AppBuild?) =
            ProfileOrigin(PHONE_MEASUREMENT, tvBuild, capturedBy = phoneBuild, analysedBy = tvBuild)

        /** A measurement made elsewhere and imported here; nothing about how it was measured is claimed. */
        fun rewImport(build: AppBuild) = ProfileOrigin(REW_IMPORT, build)

        fun manual(build: AppBuild) = ProfileOrigin(MANUAL, build)

        /** Null, meaning "not recorded", when the kind or the creating build is missing or malformed. */
        fun fromJson(obj: JSONObject?): ProfileOrigin? {
            if (obj == null) return null
            val kind = (obj.opt("kind") as? String)?.takeIf { KIND.matches(it) } ?: return null
            val created = AppBuild.fromJson(obj.optJSONObject("created_by")) ?: return null
            return ProfileOrigin(
                kind = kind,
                createdBy = created,
                capturedBy = AppBuild.fromJson(obj.optJSONObject("captured_by")),
                analysedBy = AppBuild.fromJson(obj.optJSONObject("analysed_by"))
            )
        }
    }
}
