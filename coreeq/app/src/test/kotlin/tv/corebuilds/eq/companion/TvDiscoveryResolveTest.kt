package tv.corebuilds.eq.companion

import android.net.nsd.NsdManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDiscoveryResolveTest {

    @Test
    fun `a lookup refused because the resolver is busy is retried twice`() {
        assertTrue(TvDiscovery.shouldRetryResolve(NsdManager.FAILURE_ALREADY_ACTIVE, 0))
        assertTrue(TvDiscovery.shouldRetryResolve(NsdManager.FAILURE_ALREADY_ACTIVE, 1))
        assertFalse(TvDiscovery.shouldRetryResolve(NsdManager.FAILURE_ALREADY_ACTIVE, TvDiscovery.RESOLVE_RETRIES))
    }

    @Test
    fun `other lookup failures are not retried`() {
        assertFalse(TvDiscovery.shouldRetryResolve(NsdManager.FAILURE_INTERNAL_ERROR, 0))
        assertFalse(TvDiscovery.shouldRetryResolve(NsdManager.FAILURE_MAX_LIMIT, 0))
    }
}
