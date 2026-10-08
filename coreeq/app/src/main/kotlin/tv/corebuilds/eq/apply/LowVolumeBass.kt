package tv.corebuilds.eq.apply

import android.content.Context
import tv.corebuilds.eq.ui.EnhancedAudioPrefs

/**
 * Low-volume bass for the output in use: its reference volume, the volume
 * now, and the lift between them. Shared by [EqService] and the Extra effects
 * screen so the two can never disagree.
 *
 * Each output keeps its own reference. Android keeps a separate media volume
 * per output, each through its own curve, so a reference taken on the TV
 * speakers says nothing about a soundbar or headphones. The first volume
 * Android reports on an output becomes that output's reference, so the lift
 * starts at 0 everywhere and grows only as that output is turned down.
 */
object LowVolumeBass {

    data class Reading(val referenceDb: Double?, val nowDb: Double?, val liftDb: Double)

    /** What a reading resolves to; [capture] means [referenceDb] is new and must be stored. */
    data class Settled(val referenceDb: Double?, val capture: Boolean, val liftDb: Double)

    /** The pure half: the stored reference (if any) and the volume now (if Android reports one). */
    fun settle(storedDb: Double?, nowDb: Double?): Settled {
        val reference = storedDb ?: nowDb
        val lift = if (reference != null && nowDb != null) ToneLayers.lowVolumeBassDb(reference - nowDb) else 0.0
        return Settled(reference, capture = storedDb == null && nowDb != null, liftDb = lift)
    }

    fun read(context: Context, prefs: EnhancedAudioPrefs): Reading {
        val route = routeKey(context)
        val now = VolumeLevel.mediaDb(context)
        val settled = settle(prefs.lowVolumeReferenceDb(route), now)
        if (settled.capture) prefs.setLowVolumeReferenceDb(route, settled.referenceDb)
        return Reading(settled.referenceDb, now, settled.liftDb)
    }

    /** The output a reference belongs to: its kind and Android's name for it. */
    private fun routeKey(context: Context): String =
        OutputRoute.current(context)?.let { "${it.kind}|${it.name}" } ?: OutputRoute.UNKNOWN
}
