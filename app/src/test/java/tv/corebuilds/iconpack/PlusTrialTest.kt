package tv.corebuilds.iconpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 24-hour trial's clock: a start time and a now in, a state out. */
class PlusTrialTest {

    private val start = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test fun neverStartedIsUnused() {
        assertEquals(Plus.Trial.Unused, Plus.trial(null, start))
    }

    @Test fun itRunsForTwentyFourHoursAndThenEnds() {
        assertEquals(24 * hour, Plus.TRIAL_MS)
        assertEquals(Plus.Trial.Running(24 * hour), Plus.trial(start, start))
        assertEquals(Plus.Trial.Running(hour), Plus.trial(start, start + 23 * hour))
        assertEquals(Plus.Trial.Running(1), Plus.trial(start, start + Plus.TRIAL_MS - 1))
        assertEquals(Plus.Trial.Ended, Plus.trial(start, start + Plus.TRIAL_MS))
        assertEquals(Plus.Trial.Ended, Plus.trial(start, start + 30 * 24 * hour))
    }

    @Test fun aClockSetBackEndsItInsteadOfRestartingIt() {
        assertEquals(Plus.Trial.Ended, Plus.trial(start, start - 1))
        assertEquals(Plus.Trial.Ended, Plus.trial(start, start - 365 * 24 * hour))
    }

    @Test fun timeLeftRoundsUpSoItNeverReadsZeroWhileOn() {
        assertEquals(1L, Plus.trialMinutesLeft(1))
        assertEquals(1L, Plus.trialMinutesLeft(60_000))
        assertEquals(2L, Plus.trialMinutesLeft(60_001))
        assertEquals(24L * 60, Plus.trialMinutesLeft(Plus.TRIAL_MS))
        for (left in listOf(1L, 59_999L, hour, Plus.TRIAL_MS)) {
            assertTrue(Plus.trialMinutesLeft(left) > 0)
        }
    }
}
