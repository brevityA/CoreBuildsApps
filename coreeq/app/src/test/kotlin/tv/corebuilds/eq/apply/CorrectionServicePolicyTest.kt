package tv.corebuilds.eq.apply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionServicePolicyTest {
    @Test
    fun `an old stop cannot overwrite a newer on choice`() {
        assertEquals(
            CorrectionServicePolicy.Decision.IGNORE_STALE_STOP,
            CorrectionServicePolicy.decide(enabled = true, stopRequested = true)
        )
    }

    @Test
    fun `an explicit off rejects updates resumes and sticky restarts`() {
        for (stop in listOf(false, true)) {
            assertEquals(
                CorrectionServicePolicy.Decision.STOP,
                CorrectionServicePolicy.decide(enabled = false, stopRequested = stop)
            )
        }
    }

    @Test
    fun `an enabled correction handles ordinary updates`() {
        assertEquals(
            CorrectionServicePolicy.Decision.HANDLE,
            CorrectionServicePolicy.decide(enabled = true, stopRequested = false)
        )
    }

    @Test
    fun `off on then a queued stop keeps on and on off then a queued update stays off`() {
        var enabled = false // disable saved off before queuing STOP
        enabled = true // a newer enable saved on before that STOP was delivered
        assertEquals(CorrectionServicePolicy.Decision.IGNORE_STALE_STOP, CorrectionServicePolicy.decide(enabled, true))
        enabled = false // a later disable wins over any queued REAPPLY/RESUME
        assertEquals(CorrectionServicePolicy.Decision.STOP, CorrectionServicePolicy.decide(enabled, false))
    }

    @Test
    fun `async callbacks may apply only to the current running enabled unsuspended service`() {
        for (running in listOf(false, true)) {
            for (enabled in listOf(false, true)) {
                for (suspended in listOf(false, true)) {
                    assertEquals(running && enabled && !suspended, CorrectionServicePolicy.canApply(running, enabled, suspended))
                }
            }
        }
        assertTrue(CorrectionServicePolicy.canApply(true, true, false))
        assertFalse(CorrectionServicePolicy.canApply(false, true, false))
    }
}
