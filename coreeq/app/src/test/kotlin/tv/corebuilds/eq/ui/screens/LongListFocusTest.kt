package tv.corebuilds.eq.ui.screens

import android.app.Application
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import tv.corebuilds.eq.ProfilesActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.export.ProfileStore

/**
 * Delete on a list longer than the screen: the profile that takes the deleted
 * row's place gets focus, also when that row had to be scrolled to. Robolectric
 * screens; no TV remote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class LongListFocusTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun tv() {
        ScreenHarness.prepareTv()
        val store = ProfileStore(app)
        // Newest first on screen: "Room 00" at the top, "Room 39" at the bottom.
        for (i in 0 until 40) {
            store.saveProfile(ScreenSeed.livingRoom("p-%02d".format(i), "Room %02d".format(i)).copy(timestampMs = 1_793_750_000_000L - i * 1_000L))
        }
    }

    private fun focusedName(activity: ProfilesActivity): String? =
        activity.window.currentFocus?.findViewById<TextView>(R.id.item_profile_name)?.text?.toString()

    private fun deleteRow(activity: ProfilesActivity, list: RecyclerView, position: Int) {
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
        ScreenHarness.idle()
        list.layoutManager!!.findViewByPosition(position)!!.performClick()
        ScreenHarness.idle()
        activity.findViewById<Button>(R.id.btn_delete_profile).performClick()
        ScreenHarness.idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        ScreenHarness.idle()
        ScreenHarness.idle()
    }

    @Test fun `deleting a row far down a long list focuses the profile that took its place`() {
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.recycler_profiles)
                ScreenHarness.idle()
                deleteRow(activity, list, 30)
                assertEquals("Room 31", focusedName(activity))
                // The last row of a long list: focus moves up to the one before it.
                deleteRow(activity, list, 38)
                assertEquals("Room 38", focusedName(activity))
            }
        }
    }

    @Test fun `deleting near the top after scrolling away still lands beside the deleted row`() {
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.recycler_profiles)
                ScreenHarness.idle()
                deleteRow(activity, list, 2)
                assertEquals("Room 03", focusedName(activity))
            }
        }
    }
}
