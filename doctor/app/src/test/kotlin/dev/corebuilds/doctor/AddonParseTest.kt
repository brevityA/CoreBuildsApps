package dev.corebuilds.doctor

import dev.corebuilds.doctor.diagnostics.AddonChecks
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonParseTest {

    @Test
    fun configuredAddonDetected() {
        val url = "https://v6-4.aiostreams.elfhosted.com/E_%2FRXKvm9kFN8rjw%2BHiQ6RmEWqW7cq/configure"
        assertTrue(AddonChecks.isConfiguredAddon(url))
    }

    @Test
    fun bareInstallRejected() {
        val url = "https://v6-4.aiostreams.elfhosted.com/configure"
        assertFalse(AddonChecks.isConfiguredAddon(url))
    }
}
