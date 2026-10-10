package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.dsp.DeviceIdentity

class DeviceIdentityTest {

    @Test
    fun theTvBrandComesFromItsManufacturerString() {
        assertEquals("sony", DeviceIdentity.brandForManufacturer(" Sony ")?.id)
        assertEquals("hisense", DeviceIdentity.brandForManufacturer("HISENSE")?.id)
        assertEquals("philips", DeviceIdentity.brandForManufacturer("TP Vision")?.id)
        // Samsung (Tizen) and LG (webOS) cannot run Core EQ, so they are never a TV brand.
        assertNull(DeviceIdentity.brandForManufacturer("LGE"))
        assertNull(DeviceIdentity.brandForManufacturer("Samsung"))
        assertNull(DeviceIdentity.brandForManufacturer("Acme Unknown"))
        assertNull(DeviceIdentity.brandForManufacturer(null))
    }

    @Test
    fun theTvsOwnSpeakersIdentifyOnlyTheTv() {
        val d = DeviceIdentity.detect("Sony", OutputRoute.SPEAKER, "TV speakers", emptyList())
        assertEquals("sony", d.tvBrand?.id)
        assertNull(d.outputBrand)
        assertFalse(d.ambiguous)
    }

    @Test
    fun aNamedSoundbarOverArcIsClaimedWithItsBrand() {
        val d = DeviceIdentity.detect("Sony", OutputRoute.HDMI_ARC, "Sonos Arc", listOf("Sonos Arc"))
        assertEquals("sonos", d.outputBrand?.id)
        assertFalse(d.ambiguous)
    }

    @Test
    fun aTvNameOverArcIsFlaggedNotClaimed() {
        val d = DeviceIdentity.detect("Sony", OutputRoute.HDMI_ARC, "Sony TV", listOf("Sony TV"))
        assertTrue(d.ambiguous)
        assertNull(d.outputBrand)
    }

    @Test
    fun aBluetoothSpeakerIsClaimedByItsName() {
        // Only HDMI and ARC routes can carry the TV's own name; Bluetooth names its device.
        val d = DeviceIdentity.detect("Bose", OutputRoute.BLUETOOTH, "Bose QC45", listOf("Bose QC45"))
        assertEquals("bose", d.outputBrand?.id)
        assertFalse(d.ambiguous)
    }

    @Test
    fun aModelMatchReportsItsBrandEvenWithoutABrandWordInTheName() {
        val d = DeviceIdentity.detect("Sony", OutputRoute.HDMI_ARC, "BAR 800", listOf("BAR 800"))
        assertEquals("jbl", d.outputBrand?.id)
        assertEquals("JBL Bar 800", d.hardware?.name)
        assertFalse(d.ambiguous)
    }

    @Test
    fun wholeWordsOnlyAndAnUnnamedOutputClaimNothing() {
        assertNull(DeviceIdentity.detect(null, OutputRoute.HDMI, "Sonosphere", listOf("Sonosphere")).outputBrand)
        assertNull(DeviceIdentity.detect(null, OutputRoute.HDMI, "HDMI", listOf("HDMI")).outputBrand)
        assertNull(DeviceIdentity.detect(null, null, "Sonos Arc", emptyList()).outputBrand)
    }
}
