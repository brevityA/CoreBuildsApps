package tv.corebuilds.eq.ui.screens

import android.app.Application
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import tv.corebuilds.eq.MeasureActivity
import tv.corebuilds.eq.ProfilesActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.dsp.SweepResult
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore

/**
 * Profile and measurement safety (1.14.0), on the real screens under Robolectric.
 *
 * These drive the activities and their dialogs; they do not prove how a TV's
 * own dialog theme or remote behaves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class ProfileSafetyTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val store get() = ProfileStore(app)

    @Before
    fun tv() {
        ScreenHarness.prepareTv()
        ScreenSeed.measuredRoom()
    }

    private fun latestAlert(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    /** Choose the first row, as a person does before using the profile actions. */
    private fun chooseFirstRow(activity: ProfilesActivity) {
        val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_profiles)
        ScreenHarness.idle()
        list.layoutManager!!.findViewByPosition(0)!!.performClick()
        ScreenHarness.idle()
    }

    private fun focusName(dialog: AlertDialog): String {
        val b = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        return ScreenHarness.name(dialog.currentFocus) + " touchMode=" + b.isInTouchMode + " focusable=" + b.isFocusable +
            " panelDescFocus=" + (dialog.findViewById<View>(androidx.appcompat.R.id.buttonPanel) as? android.view.ViewGroup)?.descendantFocusability
    }

    @Test fun deleteAsksFirstAndCancelHasFocus() {
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                chooseFirstRow(activity)
                activity.findViewById<Button>(R.id.btn_delete_profile).performClick()
                ScreenHarness.idle()
                val dialog = latestAlert()
                assertTrue("Delete must ask before deleting", dialog.isShowing)
                assertEquals(2, store.getAllProfiles().size)
                assertTrue(
                    "Cancel must hold focus so a second OK cannot delete (focus: ${focusName(dialog)})",
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isFocused
                )
                // A second press behind the dialog must not stack another one.
                activity.findViewById<Button>(R.id.btn_delete_profile).performClick()
                ScreenHarness.idle()
                assertTrue(latestAlert() === dialog)

                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                ScreenHarness.idle()
                assertEquals("Cancel deletes nothing", 2, store.getAllProfiles().size)
            }
        }
    }

    @Test fun confirmedDeleteRemovesOnlyThatProfileAndKeepsFocusOnScreen() {
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                chooseFirstRow(activity)
                activity.findViewById<Button>(R.id.btn_delete_profile).performClick()
                ScreenHarness.idle()
                latestAlert().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                ScreenHarness.idle()
                val left = store.getAllProfiles().map { it.id }
                assertEquals(listOf("seed-bedroom"), left)
                val focused = ScreenHarness.focused(activity)
                assertNotNull("focus must land somewhere after a delete", focused)
                assertTrue("focus landed on something hidden", focused!!.isShown)
            }
        }
    }

    @Test fun duplicateIsIndependentAndChangesNothingThatPlays() {
        ContentModeStore(app).saveModeFilters("seed-living-room", ContentMode.MOVIE_TV, listOf(PeakingFilter(100.0, 1.0, 2.0)))
        val activeBefore = store.chosenId()
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                chooseFirstRow(activity)
                activity.findViewById<Button>(R.id.btn_profile_duplicate).performClick()
                ScreenHarness.idle()
            }
        }
        val copy = store.getAllProfiles().first { it.name == "Living room (copy)" }
        assertFalse(copy.id == "seed-living-room")
        assertEquals("duplicating must not change the chosen profile", activeBefore, store.chosenId())
        val modes = ContentModeStore(app)
        assertEquals(modes.allModeFilters("seed-living-room"), modes.allModeFilters(copy.id))

        // Editing the copy leaves the original alone, in the store and in mode EQ.
        store.saveProfile(copy.copy(manualFilters = listOf(PeakingFilter(1_000.0, 1.0, -3.0))))
        modes.saveModeFilters(copy.id, ContentMode.MOVIE_TV, emptyList())
        val original = store.getAllProfiles().first { it.id == "seed-living-room" }
        assertTrue(original.manualFilters.isEmpty())
        assertEquals(1, modes.modeFilters("seed-living-room", ContentMode.MOVIE_TV).size)

        // Deleting the copy keeps the original's mode EQ.
        store.deleteProfile(copy.id)
        assertEquals(1, ContentModeStore(app).modeFilters("seed-living-room", ContentMode.MOVIE_TV).size)
    }

    @Test fun renameKeepsIdentityAndSurvivesAReload() {
        val renamed = store.renameProfile("seed-living-room", "  Lounge   TV ")
        assertEquals("Lounge TV", renamed?.name)
        val reloaded = ProfileStore(app).getAllProfiles().first { it.id == "seed-living-room" }
        assertEquals("Lounge TV", reloaded.name)
        assertEquals(ScreenSeed.livingRoom().curve, reloaded.curve)
        assertNull("a blank name changes nothing", store.renameProfile("seed-living-room", "   "))
        assertEquals("Lounge TV", ProfileStore(app).getAllProfiles().first { it.id == "seed-living-room" }.name)
    }

    @Test fun detailsAndRouteAreOnScreenNotInAToast() {
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                chooseFirstRow(activity)
                val route = activity.findViewById<TextView>(R.id.text_profile_route).text.toString()
                assertTrue("route line is empty", route.isNotBlank())
                activity.findViewById<Button>(R.id.btn_profile_details).performClick()
                ScreenHarness.idle()
                val dialog = latestAlert()
                assertTrue(dialog.isShowing)
                val texts = mutableListOf<String>()
                fun walk(v: View) {
                    if (v is TextView) texts += v.text.toString()
                    if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
                }
                walk(dialog.window!!.decorView)
                val body = texts.joinToString("\n")
                assertTrue(body.contains("Reverberation (RT60): 0.42 s"))
                assertTrue("an old profile's missing output must read as not recorded", body.contains("Output: not recorded"))
            }
        }
    }

    @Test fun backWithAnUnsavedSweepAsksAndKeepIsFocused() {
        ActivityScenario.launch(MeasureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                MeasureActivity::class.java.getDeclaredField("result").apply {
                    isAccessible = true
                    set(activity, sweepResult())
                }
                activity.findViewById<Button>(R.id.btn_measure_save).visibility = View.VISIBLE
                activity.onBackPressedDispatcher.onBackPressed()
                ScreenHarness.idle()
                assertFalse("Back discarded an unsaved measurement", activity.isFinishing)
                val dialog = latestAlert()
                assertTrue(dialog.isShowing)
                assertTrue("Keep must hold focus (focus: ${focusName(dialog)})", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isFocused)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                ScreenHarness.idle()
                assertFalse("Keep must keep the screen", activity.isFinishing)

                activity.onBackPressedDispatcher.onBackPressed()
                ScreenHarness.idle()
                latestAlert().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
                ScreenHarness.idle()
                assertTrue("Discard must leave", activity.isFinishing)
            }
        }
    }

    @Test fun backWithNothingToLoseLeavesAtOnce() {
        ActivityScenario.launch(MeasureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val before = ShadowDialog.getLatestDialog()
                activity.onBackPressedDispatcher.onBackPressed()
                ScreenHarness.idle()
                assertTrue(activity.isFinishing)
                assertTrue("no dialog for nothing to lose", ShadowDialog.getLatestDialog() === before)
            }
        }
    }

    private fun sweepResult(): SweepResult = SweepResult(
        centresHz = doubleArrayOf(500.0, 1000.0, 2000.0, 4000.0),
        measuredDb = DoubleArray(4), targetDb = DoubleArray(4), correctionDb = DoubleArray(4),
        nullMask = BooleanArray(4), minPhaseOk = BooleanArray(4) { true },
        rt60Seconds = 0.3, schroederHz = null, transitionHz = 300.0,
        rolloffHz = 40.0, floorHz = 40.0, snrDb = 40.0, latencyMs = 0.0,
        filters = emptyList(), preampDb = 0.0, bandSnrDb = DoubleArray(4) { 40.0 },
        phaseGate = BooleanArray(4) { true }, confidence = DoubleArray(4) { 1.0 },
        fMaxHz = 8000.0
    )
}
