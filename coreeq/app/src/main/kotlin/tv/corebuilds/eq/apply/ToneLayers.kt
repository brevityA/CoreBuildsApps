package tv.corebuilds.eq.apply

import tv.corebuilds.eq.dsp.EqualLoudness
import tv.corebuilds.eq.dsp.Targets
import tv.corebuilds.eq.export.Profile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The runtime tone layers on top of room correction (1.3.0): Dialogue boost
 * and Low-volume bass, both off until switched on in Extra effects.
 *
 * They are summed into the requested response before [BandMapping] takes its
 * headroom, so a layer's boost lowers everything else rather than clipping,
 * on DynamicsProcessing and on the platform Equalizer alike. They are never
 * saved into a profile, a backup or an export: those carry the measured room
 * and the user's own tone bands only.
 *
 * Kept free of Android types so the curves are unit-tested (`ToneLayersTest`).
 */
data class ToneLayers(
    val dialogue: Boolean = false,
    /** Low-volume bass shelf height in dB, 0..[MAX_LOW_VOLUME_BASS_DB]. */
    val lowVolumeBassDb: Double = 0.0
) {
    val isEmpty: Boolean get() = !dialogue && lowVolumeBassDb <= 0.0

    /**
     * Dialogue and House targets already carry the same +2 dB presence box
     * ([Targets.presenceDb]); the boost would double it, so it stands aside on
     * a measured profile with either target.
     */
    fun dialogueApplies(profile: Profile): Boolean =
        dialogue && (profile.manualOnly || profile.target !in PRESENCE_TARGETS)

    /** The layers' combined response at [hz], in dB, for [profile]. */
    fun responseDb(profile: Profile, hz: Double): Double {
        var db = 0.0
        if (dialogueApplies(profile)) db += dialogueDb(hz)
        if (lowVolumeBassDb > 0.0) db += lowVolumeBassDb * lowShelf(hz) * rolloffFade(hz, profile.rolloffHz)
        return db
    }

    /** Status words for the service line, e.g. "dialogue +2 dB + low-volume bass +1.5 dB". */
    fun label(profile: Profile): String = listOfNotNull(
        "dialogue +2 dB".takeIf { dialogueApplies(profile) },
        "low-volume bass +%.1f dB".format(java.util.Locale.US, lowVolumeBassDb).takeIf { lowVolumeBassDb > 0.0 }
    ).joinToString(" + ")

    companion object {
        val NONE = ToneLayers()

        /**
         * Research pass 6 (ANSI S3.5 SII): +2 dB across 2.2–4.5 kHz, back to
         * neutral by 6.5 kHz so sibilance is never lifted. The same raised-cosine
         * box the Dialogue target uses.
         */
        const val DIALOGUE_DB = 2.0

        /**
         * The remote-hardware research pass (§2.2): a +2 to +3 dB smooth rise
         * below about 100 Hz, layered on the corrected room. The full ISO 226
         * difference would be +15 dB at 100 Hz and re-excite the modes the
         * correction removed.
         */
        const val MAX_LOW_VOLUME_BASS_DB = 3.0
        const val LOW_SHELF_CORNER_HZ = 100.0

        /** Share of the ISO 226 difference the shelf follows below the reference volume. */
        const val ISO_SHARE = 0.5

        /** The reference listening level the ISO 226 difference is taken from. */
        const val REFERENCE_PHON = 80.0

        /** Lift steps, so holding the volume key reprograms the effect a handful of times, not dozens. */
        const val STEP_DB = 0.5

        val PRESENCE_TARGETS = setOf("dialogue", "house")

        private val ISO_63_HZ = EqualLoudness.FREQUENCIES_HZ.indexOfFirst { it == 63.0 }

        fun dialogueDb(hz: Double): Double = Targets.presenceDb(doubleArrayOf(hz), DIALOGUE_DB)[0]

        /** 1 well below the corner, 0.5 at it, 0 an octave above: the shape of [Targets.shelfDb], mirrored. */
        fun lowShelf(hz: Double): Double =
            0.5 * (1.0 - tanh(log2(max(hz, 1e-9) / LOW_SHELF_CORNER_HZ) / 0.35))

        /**
         * 0 below half an octave under the speaker's measured roll-off, 1 from
         * the roll-off up, raised-cosine between: boosting what the speaker
         * cannot play only spends headroom.
         */
        fun rolloffFade(hz: Double, rolloffHz: Double): Double {
            if (rolloffHz <= 0.0) return 1.0
            val low = rolloffHz / sqrt(2.0)
            return when {
                hz >= rolloffHz -> 1.0
                hz <= low -> 0.0
                else -> 0.5 * (1.0 - cos(PI * log2(hz / low) / log2(rolloffHz / low)))
            }
        }

        /**
         * Shelf height for a volume [belowReferenceDb] under the reference: half
         * the ISO 226:2003 difference at 63 Hz between [REFERENCE_PHON] and the
         * quieter level, capped at [MAX_LOW_VOLUME_BASS_DB] and floored to
         * [STEP_DB]. At or above the reference it is 0.
         */
        fun lowVolumeBassDb(belowReferenceDb: Double): Double {
            if (!belowReferenceDb.isFinite() || belowReferenceDb <= 0.0) return 0.0
            val playback = max(20.0, REFERENCE_PHON - belowReferenceDb)
            val iso = EqualLoudness.compensationDb(REFERENCE_PHON, playback)[ISO_63_HZ]
            val lift = min(MAX_LOW_VOLUME_BASS_DB, max(0.0, ISO_SHARE * iso))
            return floor(lift / STEP_DB + 1e-9) * STEP_DB
        }
    }
}
