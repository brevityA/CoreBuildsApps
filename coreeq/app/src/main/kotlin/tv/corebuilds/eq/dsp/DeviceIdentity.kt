package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.apply.OutputRoute
import java.util.Locale

/**
 * Which brand made the TV Core EQ runs on, and which brand the sound output
 * appears to be, reported separately.
 *
 * - The TV's brand comes from `Build.MANUFACTURER`, which names the device the
 *   app runs on. It is reliable, and it covers the TV's own speakers.
 * - The output's brand comes from its product name. That name is a hint, not
 *   proof. Over HDMI ARC or eARC, Android may report the TV rather than the
 *   soundbar, so a name that matches the TV's own brand on those routes is
 *   flagged [Detection.ambiguous] and not claimed as a soundbar.
 *
 * Pure Kotlin, so the rules are unit-tested without Android.
 */
object DeviceIdentity {

    data class Brand(
        val id: String,
        val name: String,
        /** Lower-cased `Build.MANUFACTURER` values that mean this brand made the TV. */
        val manufacturers: Set<String>,
        /** Output product names that name this brand, as whole words. */
        val nameMatch: Regex
    )

    /** Whole-word match, so "Sonosphere" is not Sonos and "Lgtv" is not LG. */
    private fun word(vararg words: String) =
        Regex("(?<![a-z])(?:" + words.joinToString("|") + ")(?![a-z])", RegexOption.IGNORE_CASE)

    /** The brands Core EQ recognises. TV brands also appear as soundbar brands. */
    val BRANDS: List<Brand> = listOf(
        Brand("samsung", "Samsung", setOf("samsung"), word("samsung")),
        Brand("lg", "LG", setOf("lg", "lge", "lg electronics"), word("lg", "lg electronics")),
        Brand("sony", "Sony", setOf("sony"), word("sony")),
        Brand("tcl", "TCL", setOf("tcl"), word("tcl")),
        Brand("hisense", "Hisense", setOf("hisense"), word("hisense")),
        Brand("philips", "Philips", setOf("philips", "tp vision", "tpv"), word("philips")),
        Brand("jbl", "JBL", emptySet(), word("jbl")),
        Brand("sonos", "Sonos", emptySet(), word("sonos")),
        Brand("bose", "Bose", emptySet(), word("bose")),
        Brand("yamaha", "Yamaha", emptySet(), word("yamaha"))
    )

    /** The brand of a TV from `Build.MANUFACTURER`, or null when it is not recognised. */
    fun brandForManufacturer(manufacturer: String?): Brand? {
        val key = manufacturer?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        return BRANDS.firstOrNull { key in it.manufacturers }
    }

    /**
     * What the output and the TV are.
     *
     * - [tvBrand]: the brand of the TV Core EQ runs on, or null if unrecognised.
     * - [outputBrand]: the brand of the sound output, or null when no name identifies one.
     * - [hardware]: the shipped model the output matched, if any (see [HardwarePresets.detect]).
     * - [ambiguous]: the output name matches the TV's own brand on an HDMI or ARC
     *   route, so it may be the TV and not a soundbar. [outputBrand] is null then.
     */
    data class Detection(
        val tvBrand: Brand?,
        val outputBrand: Brand?,
        val hardware: HardwarePresets.Model?,
        val ambiguous: Boolean
    )

    /**
     * Detect from the TV's manufacturer and the current route.
     *
     * - [kind] is the output kind [OutputRoute] settled on. The TV's own speakers
     *   (and an unknown output) identify only the TV, never a soundbar.
     * - [routeName] and [connectedNames] are the names Android gave, as in
     *   [HardwarePresets.detect].
     */
    fun detect(
        tvManufacturer: String?,
        kind: String?,
        routeName: String?,
        connectedNames: Collection<String?>
    ): Detection {
        val tvBrand = brandForManufacturer(tvManufacturer)
        if (kind == null || kind == OutputRoute.SPEAKER || kind == OutputRoute.UNKNOWN) {
            return Detection(tvBrand, outputBrand = null, hardware = null, ambiguous = false)
        }

        val names = (listOf(routeName) + connectedNames).mapNotNull { name ->
            name?.trim()?.takeIf { it.isNotEmpty() }
        }
        val hardware = HardwarePresets.detect(kind, routeName, connectedNames)
        val nameBrand = BRANDS.firstOrNull { brand -> names.any { brand.nameMatch.containsMatchIn(it) } }
        val onHdmi = kind == OutputRoute.HDMI || kind == OutputRoute.HDMI_ARC

        // A model match is specific. A brand-only match on HDMI that equals the
        // TV's own brand is most likely the TV's name, so it is not claimed.
        val ambiguous = hardware == null && nameBrand != null && onHdmi &&
            tvBrand != null && nameBrand.id == tvBrand.id
        val outputBrand = when {
            ambiguous -> null
            nameBrand != null -> nameBrand
            hardware != null -> BRANDS.firstOrNull { brand -> hardware.name.contains(brand.name, ignoreCase = true) }
            else -> null
        }
        return Detection(tvBrand, outputBrand, hardware, ambiguous)
    }
}
