package tv.corebuilds.eq.apply

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/**
 * Ladder rung 2: DUMP-assisted session discovery.
 *
 * With the one-time `android.permission.DUMP` grant (a development permission,
 * `pm grant`-able; the Capability screen shows the exact command), Core EQ can
 * read the system's own audio dumps and find the sessions players are playing
 * on — including players like Netflix that never announce a session. Without
 * the grant nothing here runs and the ladder behaves exactly as v1.0.
 *
 * All parsing lives in [DumpsysSessions] (pure, fixture-tested); this object
 * is only the exec and the uid question. [discover] blocks — call it from a
 * background thread. Every outcome is named; nothing is guessed.
 */
object DumpsysDiscovery {

    const val DUMP_PERMISSION = "android.permission.DUMP"
    private const val TAG = "CoreEqDiscovery"
    private const val DUMP_TIMEOUT_MS = 10_000L

    fun hasGrant(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, DUMP_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Sessions the correction may attach to right now. Blocks while it dumps. */
    fun discover(ourUid: Int): List<DiscoveredSession> {
        val all = DumpsysSessions.merge(
            DumpsysSessions.parse(runDumpsys("media.audio_flinger")),
            DumpsysSessions.parse(runDumpsys("audio"))
        )
        return DumpsysSessions.attachable(all, ourUid)
    }

    /**
     * Reads one dump to EOF (the pipe fills and deadlocks a dumpsys that is
     * killed early, so the read happens first) with a watchdog that destroys a
     * hung process. An empty string is a named failure upstream: the parser
     * finds nothing and nothing is attached.
     */
    private fun runDumpsys(what: String): String = try {
        val process = Runtime.getRuntime().exec(arrayOf("dumpsys", what))
        val watchdog = Thread {
            try {
                Thread.sleep(DUMP_TIMEOUT_MS)
                Log.w(TAG, "dumpsys $what exceeded ${DUMP_TIMEOUT_MS}ms; destroying")
                process.destroy()
            } catch (_: InterruptedException) {
                // The read finished in time: the watchdog is no longer needed.
            }
        }
        watchdog.isDaemon = true
        watchdog.start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        watchdog.interrupt()
        try {
            process.waitFor(1, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.w(TAG, "interrupted while reaping dumpsys $what", e)
        }
        text
    } catch (e: Exception) {
        Log.w(TAG, "dumpsys $what failed", e)
        ""
    }
}
