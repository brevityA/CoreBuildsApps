package tv.corebuilds.eq.export

import org.json.JSONException
import org.json.JSONObject
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.mode.ContentMode
import java.util.UUID

/** Strict reader for Core EQ's versioned, user-facing profile exchange document. */
object ProfileExchange {
    const val FORMAT = "corebuilds.core-eq/1"
    const val MAX_CHARS = 256 * 1024

    data class Imported(val profile: Profile, val overlays: Map<ContentMode, List<PeakingFilter>>)

    fun parse(text: String): Imported {
        require(text.length <= MAX_CHARS) { "The Core EQ profile file is too large." }
        val root = try { JSONObject(text.removePrefix("\uFEFF")) }
        catch (e: JSONException) { throw IllegalArgumentException("This is not a valid Core EQ profile JSON file.", e) }
        require(root.optString("format") == FORMAT) {
            "Unsupported Core EQ profile format. Expected $FORMAT."
        }
        val profile = try { ProfileStore.parseProfile(root) }
        catch (e: Exception) { throw IllegalArgumentException("The Core EQ profile is missing required or valid data.", e) }
        require(profile.name.isNotBlank() && profile.name.length <= 120) { "The profile name is invalid." }
        val overlays = linkedMapOf<ContentMode, List<PeakingFilter>>()
        val overlayJson = root.optJSONObject("mode_overlays")
        ContentMode.entries.forEach { mode ->
            val array = overlayJson?.optJSONArray(mode.key) ?: return@forEach
            val filters = mutableListOf<PeakingFilter>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val fc = item.optDouble("fc", Double.NaN)
                val q = item.optDouble("q", Double.NaN)
                val gain = item.optDouble("gain", Double.NaN)
                if (fc.isFinite() && q.isFinite() && gain.isFinite()) filters += PeakingFilter(fc, q, gain)
            }
            overlays[mode] = ManualEq.sanitize(filters)
        }
        return Imported(
            profile = profile.copy(
                id = UUID.randomUUID().toString(),
                name = "Imported: ${profile.name}".take(120),
                timestampMs = System.currentTimeMillis()
            ),
            overlays = overlays
        )
    }
}
