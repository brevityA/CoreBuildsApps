package dev.corebuilds.line

import org.json.JSONObject

/**
 * Where and how the scoreboard bug is drawn. The web side builds it
 * (`scoreBugConfig` in lib/scorebug.mjs, which folds the panel's calibrated
 * overscan into [marginDp]); this is the native half, sanitised the same way
 * so a corrupt payload can only ever land on the defaults.
 */
data class ScoreBugConfig(
    val position: String = POSITION_TOP_CENTER,
    /** Alpha of the whole bug, as a percentage (40–100). */
    val opacity: Int = 92,
    /** Distance from the top edge (and the side, for a corner), dp. */
    val marginDp: Int = 16,
) {
    fun sanitized(): ScoreBugConfig = copy(
        position = normalizePosition(position),
        opacity = opacity.coerceIn(40, 100),
        marginDp = marginDp.coerceIn(0, 200),
    )

    fun toJson(): String = sanitized().let {
        JSONObject()
            .put("position", it.position)
            .put("opacity", it.opacity)
            .put("marginDp", it.marginDp)
            .toString()
    }

    companion object {
        const val POSITION_TOP_CENTER = "top_center"
        const val POSITION_TOP_LEFT = "top_left"
        const val POSITION_TOP_RIGHT = "top_right"

        val POSITIONS = listOf(POSITION_TOP_CENTER, POSITION_TOP_LEFT, POSITION_TOP_RIGHT)
        val DEFAULT = ScoreBugConfig()

        fun normalizePosition(value: String?): String =
            if (value != null && POSITIONS.contains(value)) value else POSITION_TOP_CENTER

        /** Null when the payload is not JSON at all; the caller keeps its current config. */
        fun fromJson(raw: String?): ScoreBugConfig? {
            if (raw.isNullOrBlank()) return null
            return try {
                val json = JSONObject(raw)
                ScoreBugConfig(
                    position = normalizePosition(json.optString("position", POSITION_TOP_CENTER)),
                    opacity = json.optInt("opacity", DEFAULT.opacity),
                    marginDp = json.optInt("marginDp", DEFAULT.marginDp),
                ).sanitized()
            } catch (err: Exception) {
                null
            }
        }
    }
}
