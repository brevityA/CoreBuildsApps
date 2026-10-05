package tv.corebuilds.eq.apply

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Result of one discovery attempt. Incomplete results are positive-only evidence. */
internal data class DiscoverySnapshot(
    val sessions: List<DiscoveredSession>,
    /** A media/game-like session could not be attributed; arbitration may use Everyday. */
    val hasUnidentifiedPlayer: Boolean,
    /** Both service dumps completed and matched a supported parser shape. */
    val complete: Boolean
)

/**
 * Ladder rung 2: DUMP-assisted session discovery.
 *
 * If the device grants `android.permission.DUMP`, Core EQ can try to read the
 * system's audio dumps and find candidate sessions, including some players
 * that never announce a session. Android's API reference says DUMP is not for
 * third-party apps; an ADB `pm grant` may be refused on stock/release builds,
 * so the Capability screen shows it only as an optional device check. Not all
 * sessions are visible: Wavelet's DUMP-based detection is documented as not
 * working with YouTube, but that is a product/setup-specific report, not an
 * Android guarantee. Which apps a given TV exposes remains unverified until
 * device runs say so. Without an effective grant, nothing here runs.
 *
 * All parsing lives in [DumpsysSessions] (pure, fixture-tested); this object
 * is only the exec and the UID question. [discover] blocks — call it from a
 * background thread. Empty or unrecognized output is not treated as a trusted
 * empty player set: the last known snapshot is retained instead.
 */
object DumpsysDiscovery {

    const val DUMP_PERMISSION = "android.permission.DUMP"
    private const val TAG = "CoreEqDiscovery"
    private const val DUMP_TIMEOUT_MS = 10_000L
    private val EXPLICIT_FAILURE = Regex(
        """(?im)^\s*(?:permission denial\b|can't find service\b|error:|unknown service\b|dumpsys:.*(?:not found|permission denied))"""
    )

    fun hasGrant(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, DUMP_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Sessions the correction may attach to right now, plus snapshot confidence. */
    internal fun discover(ourUid: Int): DiscoverySnapshot {
        val flinger = runDumpsys("media.audio_flinger")
        val audio = runDumpsys("audio")
        val parsedFlinger = DumpsysSessions.parseWithStatus(flinger.text)
        val parsedAudio = DumpsysSessions.parseWithStatus(audio.text)
        val all = DumpsysSessions.merge(parsedFlinger.sessions, parsedAudio.sessions)
        val complete = flinger.usableWith(parsedFlinger) && audio.usableWith(parsedAudio)
        if (!complete) {
            Log.w(TAG, "At least one audio dump failed or had an unrecognized shape; preserving the last known player snapshot")
        }
        return DiscoverySnapshot(
            sessions = DumpsysSessions.attachable(all, ourUid),
            hasUnidentifiedPlayer = DumpsysSessions.hasUnidentifiedCandidate(all),
            complete = complete
        )
    }

    private data class DumpRead(val text: String, val successful: Boolean)

    private fun DumpRead.usableWith(parsed: DumpsysParseResult): Boolean =
        successful && parsed.recognized && !EXPLICIT_FAILURE.containsMatchIn(text)

    /**
     * Reads one dump to EOF (the pipe can fill and deadlock a dumpsys killed
     * early) with a watchdog that destroys a hung process. Exit status and a
     * recognized service/parser shape are both required before absence is
     * trusted by the caller.
     */
    private fun runDumpsys(what: String): DumpRead {
        var process: Process? = null
        var watchdog: Thread? = null
        val timedOut = AtomicBoolean(false)
        return try {
            val started = ProcessBuilder("dumpsys", what).redirectErrorStream(true).start()
            process = started
            watchdog = Thread {
                try {
                    Thread.sleep(DUMP_TIMEOUT_MS)
                    if (started.isAlive) {
                        timedOut.set(true)
                        Log.w(TAG, "dumpsys $what exceeded ${DUMP_TIMEOUT_MS}ms; destroying")
                        started.destroy()
                    }
                } catch (_: InterruptedException) {
                    // The read finished in time: the watchdog is no longer needed.
                }
            }.apply {
                isDaemon = true
                start()
            }
            val text = started.inputStream.bufferedReader().use { it.readText() }
            watchdog?.interrupt()
            val exited = started.waitFor(1, TimeUnit.SECONDS)
            if (!exited) {
                started.destroy()
                started.waitFor(1, TimeUnit.SECONDS)
            }
            val exitCode = if (started.isAlive) null else started.exitValue()
            val successful = exitCode == 0 && !timedOut.get()
            if (!successful) Log.w(TAG, "dumpsys $what did not complete successfully (exit=$exitCode)")
            DumpRead(text, successful)
        } catch (e: Exception) {
            watchdog?.interrupt()
            process?.destroy()
            Log.w(TAG, "dumpsys $what failed", e)
            DumpRead("", successful = false)
        }
    }
}
