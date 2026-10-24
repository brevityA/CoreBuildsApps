package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoApplyPolicyTest {

    @Test
    fun `first correction for a device auto-applies, later ones only suggest`() {
        val first = AutoApplyPolicy.decide("tv-a", emptySet())
        assertEquals(AutoApplyPolicy.Action.AUTO_APPLY, first)
        val applied = AutoApplyPolicy.recordApplied("tv-a", emptySet())
        assertEquals(AutoApplyPolicy.Action.SUGGEST_ONLY, AutoApplyPolicy.decide("tv-a", applied))
    }

    @Test
    fun `each device gets its own once`() {
        val applied = AutoApplyPolicy.recordApplied("tv-a", emptySet())
        assertEquals(AutoApplyPolicy.Action.AUTO_APPLY, AutoApplyPolicy.decide("tv-b", applied))
    }

    @Test
    fun `a device without a stable key never auto-applies`() {
        assertEquals(AutoApplyPolicy.Action.SUGGEST_ONLY, AutoApplyPolicy.decide(null, emptySet()))
        assertEquals(AutoApplyPolicy.Action.SUGGEST_ONLY, AutoApplyPolicy.decide(" ", emptySet()))
        assertEquals(emptySet<String>(), AutoApplyPolicy.recordApplied("", emptySet()))
    }
}
