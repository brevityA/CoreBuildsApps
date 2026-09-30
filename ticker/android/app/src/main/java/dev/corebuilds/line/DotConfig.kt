package dev.corebuilds.line

import org.json.JSONObject

/**
 * How the VPN dot is drawn. Persisted natively, not only in the WebView's
 * localStorage, because the boot receiver has to be able to put the dot back
 * before any WebView exists.
 */
data class DotConfig(
    val corner: String = CORNER_BOTTOM_RIGHT,
    /** Alpha of the healthy dot, as a percentage (15–100). */
    val opacity: Int = 55,
    val hideWhenOk: Boolean = false,
    val blink: Boolean = true,
    /** Distance from the screen edge, dp. The web side adds the panel's calibrated overscan. */
    val marginDp: Int = 32,
) {
    fun sanitized(): DotConfig = copy(
        corner = normalizeCorner(corner),
        opacity = opacity.coerceIn(15, 100),
        marginDp = marginDp.coerceIn(0, 200),
    )

    fun toJson(): String = JSONObject()
        .put("corner", normalizeCorner(corner))
        .put("opacity", opacity.coerceIn(15, 100))
        .put("hideWhenOk", hideWhenOk)
        .put("blink", blink)
        .put("marginDp", marginDp.coerceIn(0, 200))
        .toString()

    companion object {
        const val CORNER_BOTTOM_RIGHT = "bottom_right"
        const val CORNER_BOTTOM_LEFT = "bottom_left"
        const val CORNER_TOP_RIGHT = "top_right"
        const val CORNER_TOP_LEFT = "top_left"

        val CORNERS = listOf(CORNER_BOTTOM_RIGHT, CORNER_BOTTOM_LEFT, CORNER_TOP_RIGHT, CORNER_TOP_LEFT)
        val DEFAULT = DotConfig()

        fun normalizeCorner(value: String?): String =
            if (value != null && CORNERS.contains(value)) value else CORNER_BOTTOM_RIGHT

        /** Null when the payload is not JSON at all — the caller keeps its current config. */
        fun fromJson(raw: String?): DotConfig? {
            if (raw.isNullOrBlank()) return null
            return try {
                val json = JSONObject(raw)
                DotConfig(
                    corner = normalizeCorner(json.optString("corner", CORNER_BOTTOM_RIGHT)),
                    opacity = json.optInt("opacity", DEFAULT.opacity),
                    hideWhenOk = json.optBoolean("hideWhenOk", false),
                    blink = json.optBoolean("blink", true),
                    marginDp = json.optInt("marginDp", DEFAULT.marginDp),
                ).sanitized()
            } catch (err: Exception) {
                null
            }
        }
    }
}
