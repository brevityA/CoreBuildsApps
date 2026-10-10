package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.apply.SetupGuide
import tv.corebuilds.eq.apply.SetupGuide.State
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
}
