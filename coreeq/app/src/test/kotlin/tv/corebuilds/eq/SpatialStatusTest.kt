package tv.corebuilds.eq

import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.SpatialStatus

class SpatialStatusTest {

    @Test
    fun olderAndroidSaysItCannotBeRead() {
        val text = SpatialStatus.detail(30, null, null, null)
        assertTrue(text.contains("API ${SpatialStatus.MIN_SDK}"))
    }

    @Test
    fun aDeviceWithNoSpatializerSaysSo() {
        val text = SpatialStatus.detail(34, false, null, null)
        assertTrue(text.contains("no spatial audio processing"))
    }

    @Test
    fun anEnabledSpatializerIsReadOnlyAndSaysSo() {
        val text = SpatialStatus.detail(34, true, true, true)
        assertTrue(text.contains("on and available for this output"))
        assertTrue(text.contains("cannot turn it on or off"))
    }

    @Test
    fun aDisabledAndUnavailableSpatializerIsReported() {
        val text = SpatialStatus.detail(34, true, false, false)
        assertTrue(text.contains("off and not available"))
    }
}
