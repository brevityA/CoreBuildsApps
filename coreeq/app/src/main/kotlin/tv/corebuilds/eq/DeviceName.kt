package tv.corebuilds.eq

import android.os.Build

/**
 * A device's name for people to read: maker and model, without saying the maker twice.
 *
 * Joining `Build.MANUFACTURER` and `Build.MODEL` reads well for most devices ("Google Pixel 9
 * Pro XL", "samsung SM-S921U"), but some models already start with the maker: an onn box
 * reports "onn" and "onn 4K Pro Streaming Device", which the phone showed as "Connected to
 * onn onn 4K Pro Streaming Device". The maker is dropped only when the model starts with it as
 * a whole word, so "LG" and "LGTV" stay "LG LGTV". A combining mark after the maker belongs
 * to the word it follows: "A" and "A\u0301 Model" stay "A A\u0301 Model".
 *
 * For display and records only. Brand detection reads `Build.MANUFACTURER` itself
 * ([tv.corebuilds.eq.dsp.DeviceIdentity]).
 */
internal object DeviceName {

    fun of(manufacturer: String?, model: String?): String {
        val maker = manufacturer.orEmpty().trim()
        val name = model.orEmpty().trim()
        return when {
            name.isEmpty() -> maker
            maker.isEmpty() -> name
            startsWithWord(name, maker) -> name
            else -> "$maker $name"
        }
    }

    /** This device's name. */
    fun current(): String = of(Build.MANUFACTURER, Build.MODEL)

    private fun startsWithWord(name: String, word: String): Boolean {
        if (!name.regionMatches(0, word, 0, word.length, ignoreCase = true)) return false
        if (name.length == word.length) return true
        val next = name[word.length]
        return !next.isLetterOrDigit() && Character.getType(next) !in COMBINING_MARKS
    }

    private val COMBINING_MARKS = setOf(
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt()
    )
}
