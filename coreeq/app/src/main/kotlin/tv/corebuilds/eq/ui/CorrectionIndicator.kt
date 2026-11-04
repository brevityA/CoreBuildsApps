package tv.corebuilds.eq.ui

/**
 * The glance state of the Home screen's correction indicator.
 *
 * - [OFF] – correction is switched off. Nothing is shown.
 * - [FAULT] – the current apply status reports an error; the status line
 *   names it (other eligible sessions may still be corrected).
 * - [STANDBY] – correction is on (switched on and not in fault), but no qualifying
 *   playback is reported yet; the badge reads `ON · WAITING FOR PLAYBACK`
 *   on the output-mix path (or no session effect can be associated with it).
 * - [LIVE] – the output-mix effect is configured and Android reports media
 *   playback. The UI labels this `PLAYING · COVERAGE UNKNOWN` and pulses in
 *   this state only; the state does not prove the stream traverses the effect.
 *   The optional overlay chip uses the same wording while that playback is
 *   reported, and says `SESSION MATCH UNKNOWN` on the per-session path.
 *
 * Pure on purpose: [tv.corebuilds.eq.apply.EqService] supplies the playback
 * hint and the Home screen supplies enabled/fault state. Public callbacks hide
 * player identity and do not correlate playback with an output-mix effect or a
 * held per-session effect, so [LIVE] is a playback hint rather than a coverage
 * claim. [CorrectionIndicatorTest] pins the state table.
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
