package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.apply.SetupGuide
import tv.corebuilds.eq.apply.SetupGuide.State
import tv.corebuilds.eq.dsp.DeviceIdentity
import tv.corebuilds.eq.dsp.HardwarePresets

class SetupGuideTest {

    private fun step(steps: List<SetupGuide.Step>, id: String) = steps.single { it.id == id }

    @Test
    fun aFullyGrantedSetupNeedsNoCommands() {
        val steps = SetupGuide.steps(true, true, OutputRoute.SPEAKER, null, null)
        assertEquals(State.DONE, step(steps, "dump").state)
        assertEquals(State.DONE, step(steps, "overlay").state)
        assertNull(step(steps, "dump").command)
        assertNull(step(steps, "overlay").command)
        assertTrue(steps.none { it.id == "hardware" })
    }

    @Test
    fun missingPermissionsGiveTheExactCommandForThisPackage() {
        val steps = SetupGuide.steps(false, false, null, null, null)
        assertEquals(State.NEEDED, step(steps, "dump").state)
        assertEquals("adb shell pm grant tv.corebuilds.eq android.permission.DUMP", step(steps, "dump").command)
        assertEquals("adb shell appops set tv.corebuilds.eq SYSTEM_ALERT_WINDOW allow", step(steps, "overlay").command)
    }

    @Test
    fun theConnectLineKeepsItsPlaceholderSoItCannotRunUnedited() {
        val connect = step(SetupGuide.steps(true, true, null, null, null), "connect")
        assertEquals("adb connect <TV-IP>:5555", connect.command)
        assertTrue(connect.detail.contains("<TV-IP>"))
    }

    @Test
    fun aDetectedSoundbarAddsItsRowAndAnOutputNameOnlyWhenItDiffers() {
        val bar = HardwarePresets.MODELS.single { it.id == "jbl-bar-2-0-all-in-one" }
        val steps = SetupGuide.steps(true, true, OutputRoute.HDMI_ARC, "JBL BAR 2.0", bar)
        assertEquals("Detected: JBL Bar 2.0", step(steps, "hardware").title)
        assertEquals("Routed to HDMI ARC (soundbar or receiver): JBL BAR 2.0.", step(steps, "output").detail)

        val generic = SetupGuide.steps(true, true, OutputRoute.HDMI_ARC, OutputRoute.label(OutputRoute.HDMI_ARC), null)
        assertEquals("Routed to HDMI ARC (soundbar or receiver).", step(generic, "output").detail)
    }

    @Test
    fun theReportListsEveryRowAndEveryCommandToCopy() {
        val report = SetupGuide.report(SetupGuide.steps(false, true, null, null, null))
        assertTrue(report.startsWith("Core EQ setup check\n"))
        assertTrue(report.contains("[needed] Read the playing app (DUMP)"))
        assertTrue(report.contains("    adb shell pm grant tv.corebuilds.eq android.permission.DUMP"))
        assertTrue(report.contains("[done] Show the on-screen card"))
    }

    @Test
    fun aSoundbarNamedAfterTheTvIsNotClaimedAsOne() {
        val tv = DeviceIdentity.brandForManufacturer("samsung")
        val detection = DeviceIdentity.detect("samsung", OutputRoute.HDMI_ARC, "Samsung Q80", listOf("Samsung Q80"))
        val steps = SetupGuide.steps(true, true, OutputRoute.HDMI_ARC, "Samsung Q80", null, detection, measured = false)
        val device = step(steps, "device").detail
        assertTrue(device.contains("TV: Samsung."))
        assertTrue(device.contains("may be the TV itself"))
        assertEquals(tv, detection.tvBrand)
        assertEquals("Measure this output", step(steps, "measure").title)
        assertEquals(State.INFO, step(steps, "measure").state)
    }

    @Test
    fun aMeasuredOutputShowsNoMeasureRow() {
        val steps = SetupGuide.steps(true, true, OutputRoute.SPEAKER, null, null, measured = true)
        assertTrue(steps.none { it.id == "measure" })
        assertTrue(steps.none { it.id == "device" })
    }

    @Test
    fun aPossiblePassthroughAddsAWarningRowWithNoCommand() {
        val steps = SetupGuide.steps(true, true, OutputRoute.HDMI, "TV", null, passthroughRisk = true)
        val row = step(steps, "passthrough")
        assertEquals(State.INFO, row.state)
        assertNull(row.command)
        assertTrue(row.detail.contains("PCM"))
        assertEquals(0, SetupGuide.steps(true, true, OutputRoute.HDMI, "TV", null, passthroughRisk = true)
            .count { it.state == State.NEEDED })
    }

    @Test
    fun noPassthroughRiskAddsNoRow() {
        val steps = SetupGuide.steps(true, true, OutputRoute.SPEAKER, null, null)
        assertTrue(steps.none { it.id == "passthrough" })
    }

    @Test
    fun aSpatialLineIsAReadOnlyInfoRow() {
        val steps = SetupGuide.steps(true, true, OutputRoute.SPEAKER, null, null, spatialDetail = "Spatial audio is on.")
        val row = step(steps, "spatial")
        assertEquals(State.INFO, row.state)
        assertNull(row.command)
    }

    @Test
    fun noSpatialLineWhenNotRead() {
        val steps = SetupGuide.steps(true, true, OutputRoute.SPEAKER, null, null)
        assertTrue(steps.none { it.id == "spatial" })
    }
}
