package tv.corebuilds.eq.apply

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings

/**
 * The TV's "Surround sound" setting (Settings → Display & Sound → Advanced
 * sound settings), which decides whether Dolby and DTS leave the box as a
 * bitstream or are decoded and mixed to PCM first.
 *
 * It matters twice to Core EQ. A bitstream never passes through Android's
 * mixer, so no on-device equaliser reaches it ([OutputRoute.mayPassThrough]).
 * And on stereo speakers (1.10.0, Stereo speakers in Extra effects) a
 * bitstream is folded to two channels by the soundbar instead of by Android,
 * where Core EQ cannot restore the centre channel the fold buries.
 *
 * Android 12 (API 31) made the setting public: [AudioManager.getEncodedSurroundMode]
 * reads it and [AudioManager.setEncodedSurroundMode] changes it, for an app the
 * user has allowed to "Modify system settings" (WRITE_SETTINGS). On Android 11
 * it is the hidden global `encoded_surround_output`, which apps may read but
 * not write; there the only route is the TV's own menu or ADB.
 *
 * The mode values are AudioManager's ENCODED_SURROUND_OUTPUT_* constants and
 * the global setting's stored values, which are the same numbers.
 */
object SurroundOutput {

    const val AUTO = 0
    const val NEVER = 1
    const val ALWAYS = 2
    const val MANUAL = 3

    /** Android 12 (API 31): the first version with a public switch. */
    const val PUBLIC_API_SDK = 31

    /** The hidden global Android 11 stores the mode in. */
    const val GLOBAL_KEY = "encoded_surround_output"

    /** What a person types when Core EQ cannot change it: works on Android 11 and later. */
    const val ADB_NEVER_COMMAND = "adb shell settings put global $GLOBAL_KEY $NEVER"

    /**
     * Allows Core EQ to change it on Android 12+ without the TV's permission
     * screen. Takes the running package: the debug build is `tv.corebuilds.eq.debug`.
     */
    fun adbAllowCommand(packageName: String): String = "adb shell appops set $packageName WRITE_SETTINGS allow"

    /**
     * True when [mode] lets Dolby or DTS leave the box undecoded. Null (the
     * setting could not be read) is false: Core EQ only warns about what it
     * read. Every mode but Never can pass a bitstream: Manual does for each
     * format left switched on.
     */
    fun mayBypass(mode: Int?): Boolean = mode != null && mode != NEVER

    /** The setting's name as the TV's menu shows it. */
    fun label(mode: Int?): String = when (mode) {
        AUTO -> "Auto"
        NEVER -> "Never"
        ALWAYS -> "Always"
        MANUAL -> "Manual"
        else -> "unknown"
    }

    /** The current mode, or null where Android does not say. */
    fun read(context: Context): Int? {
        if (Build.VERSION.SDK_INT >= PUBLIC_API_SDK) {
            val am = context.getSystemService(AudioManager::class.java) ?: return null
            return am.encodedSurroundMode.takeIf { it in AUTO..MANUAL }
        }
        // Android 11: readable, but absent on builds without the setting, in
        // which case nothing is claimed either way.
        return try {
            Settings.Global.getInt(context.contentResolver, GLOBAL_KEY, -1).takeIf { it in AUTO..MANUAL }
        } catch (_: Exception) {
            null
        }
    }

    /** True when Core EQ may change the setting itself: Android 12+ with "Modify system settings" allowed. */
    fun canChange(context: Context): Boolean =
        Build.VERSION.SDK_INT >= PUBLIC_API_SDK && Settings.System.canWrite(context)

    /** Sets [mode]; false when Android refused or this version has no public switch. */
    fun write(context: Context, mode: Int): Boolean {
        if (Build.VERSION.SDK_INT < PUBLIC_API_SDK || !Settings.System.canWrite(context)) return false
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return try {
            am.setEncodedSurroundMode(mode)
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * What switching Stereo speakers on should do with the setting, given what
     * was read. Pure, so the decisions are unit-tested without Android.
     */
    enum class OnAction {
        /** This output only takes PCM: Android folds 5.1 itself whatever the setting says. */
        PCM_ONLY,
        /** Already Never: Android decodes and folds 5.1 itself; nothing to change. */
        NOTHING,
        /** Change it to Never, remembering [mode] to put back later. */
        SET_NEVER,
        /** It may pass a bitstream and Core EQ may not change it on Android 12+: ask for the permission. */
        ASK_PERMISSION,
        /** Android 11, or unreadable: say how to change it by hand. */
        EXPLAIN
    }

    fun onAction(mode: Int?, sdk: Int, canWrite: Boolean, bitstreamOutput: Boolean): OnAction = when {
        // A system setting is only changed where it changes something.
        !bitstreamOutput -> OnAction.PCM_ONLY
        mode == NEVER -> OnAction.NOTHING
        sdk < PUBLIC_API_SDK || mode == null -> OnAction.EXPLAIN
        canWrite -> OnAction.SET_NEVER
        else -> OnAction.ASK_PERMISSION
    }

    /**
     * Puts the saved mode back when Stereo speakers is switched off, and returns what is still
     * owed: null when the mode is back or is no longer Core EQ's to restore, otherwise the saved
     * mode again, so the caller keeps it and retries.
     *
     * The rule is narrow on purpose. Core EQ restores only what it replaced, and only while the
     * setting is still Never: if somebody changed it since, that was their choice and it stays.
     * A write Android refuses — the permission was withdrawn — must not look like a restore, or
     * the saved mode is lost and the user's own setting is gone with it.
     *
     * [write] is the effect, passed in rather than called here, so this whole rule is unit-tested
     * without a device; the failure path in particular is the one that regressed before.
     */
    fun restore(saved: Int?, current: Int?, write: (Int) -> Boolean): Int? {
        val mode = saved?.takeIf { current == NEVER && it in AUTO..MANUAL && it != NEVER } ?: return null
        return if (write(mode)) null else saved
    }
}
