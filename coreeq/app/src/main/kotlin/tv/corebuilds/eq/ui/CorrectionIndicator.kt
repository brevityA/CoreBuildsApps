package tv.corebuilds.eq.ui

/**
 * The glance state of the Home screen's correction indicator: the answer to
 * "is this working on the audio playing right now?".
 *
 * - [OFF] – correction is switched off. Nothing is shown.
 * - [FAULT] – the switch is on but nothing reached the audio (another equaliser
 *   app holds priority, Android refused, the profile vanished). The status
 *   line below the badge says why.
 * - [STANDBY] – the switch is on but nothing is audible through the
 *   correction right now: armed and the TV is silent, or waiting for a player
 *   to attach to.
 * - [LIVE] – audio is playing and the correction is attached to it. The badge
 *   pulses in this state only.
 *
 * Pure on purpose: the inputs are known by [tv.corebuilds.eq.apply.EqService]
 * (playing) and the Home screen (enabled, fault), and the table between them is
 * what [CorrectionIndicatorTest] pins. "Playing" is the TV's, not one app's:
 * Android hides player identity from third-party apps, so the service watches
 * playback state and the status line names what is actually being corrected.
 */
enum class CorrectionIndicator { OFF, FAULT, STANDBY, LIVE }

/** The indicator state, in priority order: off, then fault, then playing, then armed. */
fun correctionIndicator(enabled: Boolean, playing: Boolean, fault: Boolean): CorrectionIndicator =
    when {
        !enabled -> CorrectionIndicator.OFF
        fault -> CorrectionIndicator.FAULT
        playing -> CorrectionIndicator.LIVE
        else -> CorrectionIndicator.STANDBY
    }
