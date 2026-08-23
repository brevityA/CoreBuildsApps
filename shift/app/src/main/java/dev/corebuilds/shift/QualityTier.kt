package dev.corebuilds.shift

/** User-selectable delivery tier for local TV playback/downloads. */
enum class QualityTier(val label: String) {
    HD_1080("1080p"),
    UHD_4K("4K"),
}

fun LiveEntry.urlFor(tier: QualityTier): String? = when (tier) {
    QualityTier.HD_1080 -> url1080p.takeIf { it.isNotBlank() }
    QualityTier.UHD_4K -> url4k?.takeIf { it.isNotBlank() }
}

fun LiveEntry.hasTier(tier: QualityTier): Boolean = urlFor(tier) != null
