package tv.corebuilds.iconpack

import android.content.Context
import java.util.Calendar

/**
 * Day and night loops, a Core Builds Plus extra: during night hours the live
 * wallpaper plays a second loop, chosen in Settings, instead of the one picked
 * in the preview, and goes back to it when the night ends.
 *
 * Like [LoopRotation], there is no alarm and no background work.
 * [LiveWallpaperService] asks [loopId] whenever its surface comes back, and
 * while the home screen stays on it asks again at the next boundary
 * ([msUntilSwitch]), so a TV left on the home screen at 20:00 still changes
 * over. The night loop has to be on the TV already: the engine never
 * downloads, so a night loop that is not playable falls back to the day
 * choice, never to a still frame.
 */
object DayNight {

    /**
     * The night hours the Settings row cycles through, local time: the hour
     * night starts, then the hour it ends. The first is the default.
     */
    val WINDOWS = listOf(20 to 7, 21 to 7, 22 to 6, 19 to 7)

    fun window(context: Context): Pair<Int, Int> = WINDOWS[Prefs.nightWindow(context)]

    /** True when [hour] (0-23) falls inside [window], which may cross midnight. */
    fun isNight(hour: Int, window: Pair<Int, Int>): Boolean {
        val (from, to) = window
        return if (from > to) hour >= from || hour < to else hour in from until to
    }

    /** True when the schedule can change what plays: Plus owned and a night loop chosen. */
    fun active(context: Context): Boolean =
        Plus.owned(context) && Prefs.nightLoopId(context) != null

    /**
     * The loop the engine should play at [now]: the night loop during night
     * hours when the schedule is [active] and that loop is on this TV,
     * otherwise the loop picked in the preview (which [LoopRotation] may move).
     */
    fun loopId(context: Context, now: Long = System.currentTimeMillis()): String {
        val day = Prefs.liveLoopId(context)
        if (!active(context) || !isNight(hourOf(now), window(context))) return day
        val night = LiveLoop.LOOPS.firstOrNull { it.id == Prefs.nightLoopId(context) } ?: return day
        return if (LiveLoopDownloader.playable(context, night) != null) night.id else day
    }

    /** Milliseconds from [now] to the next start or end of night hours, local time. */
    fun msUntilSwitch(now: Long, window: Pair<Int, Int>): Long =
        listOf(window.first, window.second).minOf { hour ->
            val at = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }
            at.timeInMillis - now
        }

    /**
     * The Settings row's next night loop after [current]: off (null), then
     * each loop in [available] in catalogue order, then off again. A
     * [current] that is no longer available starts the cycle over at off.
     */
    fun nextNightLoop(current: String?, available: List<String>): String? {
        val options = listOf<String?>(null) + available
        return options[(options.indexOf(current) + 1) % options.size]
    }

    private fun hourOf(now: Long): Int =
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
}
