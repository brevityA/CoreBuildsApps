package tv.corebuilds.eq.companion

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import tv.corebuilds.eq.R
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.ui.screens.ScreenHarness

/** Real phone views. These tests do not pair, send commands or record a microphone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneCompanionScreenTest {
    @Before
    fun phone() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, false)
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        app.getSharedPreferences("core_eq_phone_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("core_eq_phone_measurement", Context.MODE_PRIVATE).edit().clear().commit()
        ProfileStore(app).correctionEnabled = false
    }

    private fun withPhone(block: (PhoneCompanionActivity) -> Unit) {
        ActivityScenario.launch(PhoneCompanionActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> ScreenHarness.idle(); block(activity) }
        }
    }

    private fun snapshot() = PhoneCompanionActivity.Dashboard(
        deviceName = "Living room TV", version = "1.12.0", output = "TV speakers (connected-output estimate)",
        mode = "everyday", correctionEnabled = true, profileName = "Living room",
        serviceStatus = "Output-mix effect configured; coverage unknown", serviceError = false,
        profiles = listOf(
            PhoneCompanionActivity.RemoteProfile("room", "Living room", true, false),
            PhoneCompanionActivity.RemoteProfile("night", "Quiet evenings", false, true)
        ), serviceRunning = true
    )

    private fun connected(activity: PhoneCompanionActivity, value: PhoneCompanionActivity.Dashboard = snapshot()) {
        field(activity, "pairedCredentials", PhoneCredentials("192.168.1.20", ListenerPort.PREFERRED, ByteArray(32) { 1 }))
        activity.showDashboard(value)
    }

    private fun call(activity: PhoneCompanionActivity, name: String) {
        activity.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
    }

    private fun callBoolean(activity: PhoneCompanionActivity, name: String, value: Boolean) {
        activity.javaClass.getDeclaredMethod(name, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(activity, value)
    }

    private fun field(activity: PhoneCompanionActivity, name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }

    @Test
    fun `first visit prioritises QR pairing and hides the technical form`() = withPhone { activity ->
        assertTrue(activity.findViewById<View>(R.id.phone_tv_page).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_control_page).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_manual_pair_panel).isShown)
        assertTrue(activity.findViewById<View>(R.id.phone_scan_qr).hasOnClickListeners())
        activity.findViewById<View>(R.id.phone_manual_pair_toggle).performClick()
        assertTrue(activity.findViewById<View>(R.id.phone_manual_pair_panel).isShown)
        activity.findViewById<View>(R.id.phone_manual_pair_toggle).performClick()
        assertFalse(activity.findViewById<View>(R.id.phone_manual_pair_panel).isShown)
        assertFalse(ProfileStore(activity).correctionEnabled)
        ScreenHarness.render(activity, "phone-tv-360")
    }

    @Test
    fun `tabs isolate controls from setup and measurement`() = withPhone { activity ->
        activity.findViewById<View>(R.id.phone_tab_control).performClick()
        assertTrue(activity.findViewById<View>(R.id.phone_control_page).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_tv_page).isShown)
        activity.findViewById<View>(R.id.phone_tab_measure).performClick()
        assertTrue(activity.findViewById<View>(R.id.phone_measure_page).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_control_page).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_start_measure).isEnabled)
        assertTrue(activity.findViewById<View>(R.id.phone_tab_measure).isSelected)
        ScreenHarness.render(activity, "phone-measure-360")
    }

    @Test
    fun `target choice is a named list not a blind cycle`() = withPhone { activity ->
        activity.findViewById<View>(R.id.phone_tab_measure).performClick()
        val target = activity.findViewById<Button>(R.id.phone_target_picker)
        target.performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog.listView)
        assertEquals(6, dialog.listView.adapter.count)
        dialog.listView.performItemClick(dialog.listView.getChildAt(3), 3, 3L)
        assertEquals(3, activity.getSharedPreferences("core_eq_phone_measurement", Context.MODE_PRIVATE).getInt("target_index", -1))
        assertTrue(target.text.toString().contains("Flat"))
    }

    @Test
    fun `snapshots select the current mode and do not recreate controls or change correction`() = withPhone { activity ->
        connected(activity)
        activity.findViewById<View>(R.id.phone_tab_control).performClick()
        assertTrue(activity.findViewById<View>(R.id.phone_mode_everyday).isActivated)
        assertFalse(activity.findViewById<View>(R.id.phone_mode_movie).isActivated)
        assertTrue(activity.findViewById<Button>(R.id.phone_mode_everyday).text.startsWith("✓"))
        val profile = activity.findViewById<View>(R.id.phone_profile_picker)
        profile.requestFocus()
        activity.showDashboard(snapshot().copy(mode = "gaming"))
        assertSame(profile, activity.findViewById<View>(R.id.phone_profile_picker))
        assertSame(profile, activity.window.currentFocus)
        assertTrue(activity.findViewById<View>(R.id.phone_mode_gaming).isActivated)
        assertFalse(ProfileStore(activity).correctionEnabled)
        ScreenHarness.render(activity, "phone-control-360")
    }

    @Test
    fun `mode and correction buttons are blocked while a command is in flight`() = withPhone { activity ->
        connected(activity)
        callBoolean(activity, "setOperationBusy", true)
        for (id in listOf(R.id.phone_correction_toggle, R.id.phone_mode_everyday, R.id.phone_mode_movie, R.id.phone_mode_gaming, R.id.phone_profile_picker)) {
            assertFalse(activity.findViewById<View>(id).isEnabled)
        }
        callBoolean(activity, "setOperationBusy", false)
        assertTrue(activity.findViewById<View>(R.id.phone_mode_everyday).isEnabled)
    }

    @Test
    fun `disconnect keeps last known values visible but blocks stale commands`() = withPhone { activity ->
        connected(activity)
        activity.findViewById<View>(R.id.phone_tab_control).performClick()
        call(activity, "markDisconnected")
        assertTrue(activity.findViewById<View>(R.id.phone_correction_state).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_correction_toggle).isEnabled)
        assertFalse(activity.findViewById<View>(R.id.phone_mode_everyday).isEnabled)
        assertFalse(activity.findViewById<View>(R.id.phone_start_measure).isEnabled)
        assertTrue(activity.findViewById<TextView>(R.id.phone_correction_state).text.toString().contains("on"))
    }

    @Test
    fun `capture UI keeps the screen awake and blocks conflicting controls`() = withPhone { activity ->
        connected(activity)
        field(activity, "measurementInProgress", true)
        callBoolean(activity, "setMeasurementUi", true)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        assertTrue(activity.findViewById<View>(R.id.phone_cancel_measure).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_mode_gaming).isEnabled)
        field(activity, "measurementInProgress", false)
        callBoolean(activity, "setMeasurementUi", false)
        assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Test
    fun `forget asks first and does not turn TV correction on or off`() = withPhone { activity ->
        val credentials = PhoneCredentials("192.168.1.20", ListenerPort.PREFERRED, ByteArray(32) { 1 })
        field(activity, "pairedCredentials", credentials)
        connected(activity)
        activity.findViewById<View>(R.id.phone_tab_tv).performClick()
        val forget = activity.findViewById<View>(R.id.phone_forget)
        forget.performClick()
        val connection = activity.findViewById<TextView>(R.id.phone_connection_state)
        val before = connection.text.toString()
        val first = ShadowDialog.getLatestDialog() as AlertDialog
        // A dialog button posts its listener (and the dismiss) to the main looper,
        // so run the queue before reading what the click did.
        first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        ScreenHarness.idle()
        assertFalse(first.isShowing)
        assertEquals(before, connection.text.toString())
        assertTrue(forget.isEnabled)
        forget.performClick()
        val second = ShadowDialog.getLatestDialog() as AlertDialog
        second.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        ScreenHarness.idle()
        assertEquals(activity.getString(R.string.phone_not_paired), connection.text.toString())
        assertFalse(ProfileStore(activity).correctionEnabled)
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h640dp-port-mdpi")
    fun `small portrait keeps navigation visible and lets the page scroll`() = withPhone { activity ->
        connected(activity)
        activity.findViewById<View>(R.id.phone_tab_control).performClick()
        ScreenHarness.render(activity, "phone-control-320")
        for (id in listOf(R.id.phone_tab_control, R.id.phone_tab_measure, R.id.phone_tab_tv)) {
            assertTrue(ScreenHarness.onScreen(activity.findViewById(id)))
        }
        assertEquals(emptyList<String>(), ScreenHarness.textProblems(activity))
    }

    @Test
    fun `connected is one strip with Refresh and offline is the full card with Reconnect`() = withPhone { activity ->
        connected(activity)
        activity.findViewById<View>(R.id.phone_tab_control).performClick()
        assertTrue(activity.findViewById<View>(R.id.phone_refresh).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_connect).isShown)
        call(activity, "markDisconnected")
        assertFalse(activity.findViewById<View>(R.id.phone_refresh).isShown)
        val reconnect = activity.findViewById<Button>(R.id.phone_connect)
        assertTrue(reconnect.isShown)
        assertEquals(activity.getString(R.string.phone_reconnect_button), reconnect.text.toString())
    }

    @Test
    fun `listening modes are chips that keep the last choice visible but dim while offline`() = withPhone { activity ->
        connected(activity, snapshot().copy(mode = "gaming"))
        activity.findViewById<View>(R.id.phone_tab_control).performClick()
        val gaming = activity.findViewById<Button>(R.id.phone_mode_gaming)
        val movie = activity.findViewById<Button>(R.id.phone_mode_movie)
        assertTrue(gaming.isActivated && gaming.isEnabled)
        val enabledColour = movie.currentTextColor
        call(activity, "markDisconnected")
        assertFalse(gaming.isEnabled)
        assertTrue(gaming.isActivated) // the last known mode still reads as chosen
        assertFalse(movie.isEnabled)
        assertTrue("an offline mode must look unavailable", movie.currentTextColor != enabledColour)
    }

    @Test
    fun `a saved TV folds pairing into one button that opens it`() = withPhone { activity ->
        connected(activity)
        activity.findViewById<View>(R.id.phone_tab_tv).performClick()
        val another = activity.findViewById<View>(R.id.phone_pair_another)
        assertTrue(another.isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_pairing_panel).isShown)
        another.performClick()
        assertTrue(activity.findViewById<View>(R.id.phone_pairing_panel).isShown)
        assertFalse(another.isShown)
        assertTrue(activity.findViewById<View>(R.id.phone_scan_qr).isShown)
        // A finger gets a scroll, not a keyboard focus ring on Scan.
        assertFalse(activity.findViewById<View>(R.id.phone_scan_qr).isFocused)
    }

    @Test
    fun `an unpaired phone shows pairing without the fold`() = withPhone { activity ->
        assertTrue(activity.findViewById<View>(R.id.phone_pairing_panel).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_pair_another).isShown)
    }

    @Test
    fun `measuring shows only what applies mid-capture and restores setup afterwards`() = withPhone { activity ->
        connected(activity)
        activity.findViewById<View>(R.id.phone_tab_measure).performClick()
        assertTrue(activity.findViewById<View>(R.id.phone_measure_setup).isShown)
        field(activity, "measurementInProgress", true)
        callBoolean(activity, "setMeasurementUi", true)
        assertTrue(activity.findViewById<View>(R.id.phone_measure_live).isShown)
        assertTrue(activity.findViewById<View>(R.id.phone_measure_progress).isShown)
        assertTrue(activity.findViewById<View>(R.id.phone_cancel_measure).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_measure_setup).isShown)
        assertFalse(activity.findViewById<View>(R.id.phone_start_measure).isShown)
        field(activity, "measurementInProgress", false)
        callBoolean(activity, "setMeasurementUi", false)
        assertFalse(activity.findViewById<View>(R.id.phone_measure_live).isShown)
        assertTrue(activity.findViewById<View>(R.id.phone_measure_setup).isShown)
    }

    @Test
    fun `every control answers a touch with a ripple over the TV's own states`() = withPhone { activity ->
        connected(activity)
        for (id in listOf(
            R.id.phone_correction_toggle, R.id.phone_mode_movie, R.id.phone_profile_picker, R.id.phone_refresh,
            R.id.phone_tab_control, R.id.phone_target_picker, R.id.phone_forget, R.id.phone_scan_qr
        )) {
            val name = activity.resources.getResourceEntryName(id)
            assertTrue("$name has no touch ripple", activity.findViewById<View>(id).background is android.graphics.drawable.RippleDrawable)
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h640dp-port-mdpi")
    fun `every tab and state fits its text on a 320dp phone`() {
        val problems = mutableListOf<String>()
        fun check(state: String, activity: PhoneCompanionActivity) {
            ScreenHarness.idle()
            ScreenHarness.textProblems(activity).forEach { problems += "$state: $it" }
        }
        withPhone { activity -> check("first open", activity) }
        withPhone { activity ->
            activity.findViewById<View>(R.id.phone_manual_pair_toggle).performClick()
            check("manual pairing", activity)
        }
        withPhone { activity ->
            connected(activity)
            for ((tab, name) in listOf(R.id.phone_tab_control to "control", R.id.phone_tab_measure to "measure", R.id.phone_tab_tv to "tv")) {
                activity.findViewById<View>(tab).performClick()
                check(name, activity)
            }
            activity.findViewById<View>(R.id.phone_pair_another).performClick()
            check("pair another", activity)
            activity.findViewById<View>(R.id.phone_tab_control).performClick()
            call(activity, "markDisconnected")
            check("offline", activity)
        }
        withPhone { activity ->
            connected(activity)
            activity.findViewById<View>(R.id.phone_tab_measure).performClick()
            field(activity, "measurementInProgress", true)
            callBoolean(activity, "setMeasurementUi", true)
            check("measuring", activity)
        }
        assertEquals(emptyList<String>(), problems)
    }
}
