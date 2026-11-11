package tv.corebuilds.eq.ui.screens

import android.app.Application
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import tv.corebuilds.eq.CapabilityActivity
import tv.corebuilds.eq.ProfilesActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.ui.ProfileRouteLine

/**
 * Findings from the owner's source review of #474, each reproduced here
 * before it was fixed. Robolectric screens; no TV, remote or real audio route.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class Review474Test {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val store get() = ProfileStore(app)

    @Before fun tv() {
        ScreenHarness.prepareTv()
    }

    /** Finding 1: matching a profile to the output is not evidence that the service applied it. */
    @Test fun `the Profiles route line never claims a profile is applied`() {
        for (kind in ProfileRouteLine.Kind.entries) {
            val text = app.getString(ProfileRouteLine.stringRes(kind), "TV speakers", "HDMI ARC")
            assertFalse("$kind says '$text'", Regex("""(?i)\bapplied\b""").containsMatchIn(text.replace("not applied", "")))
            assertFalse("$kind says '$text'", text.contains("applied", ignoreCase = true))
        }
        // An ambiguous or unconfirmed route is not "another output".
        val chosen = ScreenSeed.livingRoom().copy(outputKind = "speaker")
        assertEquals(ProfileRouteLine.Kind.UNKNOWN_OUTPUT, ProfileRouteLine.kindOf(chosen, null, "ambiguous"))
        assertEquals(ProfileRouteLine.Kind.UNKNOWN_OUTPUT, ProfileRouteLine.kindOf(chosen, null, "unconfirmed"))
        assertEquals(ProfileRouteLine.Kind.MATCHES_OUTPUT, ProfileRouteLine.kindOf(chosen, chosen, "speaker"))
        assertEquals(ProfileRouteLine.Kind.OTHER_OUTPUT, ProfileRouteLine.kindOf(chosen, null, "bluetooth"))
    }

    /** Finding 2: the privacy header promised no profile or Bluetooth names, but service messages carry both. */
    @Test fun `diagnostics masks the profile and output names the service writes into its messages`() {
        ScreenSeed.measuredRoom()
        store.saveProfile(ScreenSeed.livingRoom("seed-bar", "Soundbar room").copy(outputKind = "bluetooth", outputName = "Sam's Sonos Roam"))
        store.setStatus(
            "Output-mix effect configured · Everyday · DynamicsProcessing · Living room · 25 bands · on Sam's Sonos Roam (track reported)",
            isError = false
        )
        ActivityScenario.launch(CapabilityActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ScreenHarness.idle()
                activity.findViewById<Button>(R.id.btn_diagnostics).performClick()
                ScreenHarness.idle()
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                val texts = mutableListOf<String>()
                fun walk(v: android.view.View) {
                    if (v is TextView && v !is Button) texts += v.text.toString()
                    if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
                }
                walk(dialog.window!!.decorView)
                val body = texts.first { it.startsWith("Core EQ diagnostics") }
                assertFalse(body, body.contains("Living room"))
                assertFalse(body, body.contains("Sonos Roam"))
                assertFalse(body, body.contains("Soundbar room"))
                assertTrue(body, body.contains("DynamicsProcessing · [name] · 25 bands · on [name]"))
            }
        }
    }

    /** Finding 3: after a delete, focus went to row zero or Import, not to the profile beside the deleted one. */
    @Test fun `delete leaves focus on the nearest surviving profile`() {
        for ((i, name) in listOf("Alpha", "Bravo", "Charlie").withIndex()) {
            store.saveProfile(ScreenSeed.livingRoom("p-$i", name).copy(timestampMs = 1_793_750_000_000L - i * 1_000L))
        }
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.recycler_profiles)
                ScreenHarness.idle()
                // Newest first: Alpha, Bravo, Charlie. Delete Bravo, the middle row.
                list.layoutManager!!.findViewByPosition(1)!!.performClick()
                ScreenHarness.idle()
                activity.findViewById<Button>(R.id.btn_delete_profile).performClick()
                ScreenHarness.idle()
                (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                ScreenHarness.idle()
                val focused = activity.window.currentFocus
                val focusedName = (focused?.findViewById<TextView>(R.id.item_profile_name))?.text?.toString()
                assertEquals("the row after the deleted one takes focus", "Charlie", focusedName)

                // Delete the last row: focus moves up to the row before it.
                activity.findViewById<Button>(R.id.btn_delete_profile).let {
                    list.layoutManager!!.findViewByPosition(1)!!.performClick()
                    ScreenHarness.idle()
                    it.performClick()
                }
                ScreenHarness.idle()
                (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                ScreenHarness.idle()
                assertEquals("Alpha", activity.window.currentFocus?.findViewById<TextView>(R.id.item_profile_name)?.text?.toString())
            }
        }
    }

    /** Finding 4: stable ids came from String.hashCode(), which collides ("Aa" and "BB" are both 2112). */
    @Test fun `profiles whose ids share a hash code get distinct stable ids`() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        store.saveProfile(ScreenSeed.livingRoom("Aa", "First"))
        store.saveProfile(ScreenSeed.livingRoom("BB", "Second").copy(timestampMs = 1L))
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ScreenHarness.idle()
                val adapter = activity.findViewById<RecyclerView>(R.id.recycler_profiles).adapter!!
                assertEquals(2, adapter.itemCount)
                assertNotEquals(adapter.getItemId(0), adapter.getItemId(1))
                assertEquals("an id is stable across lookups", adapter.getItemId(0), adapter.getItemId(0))
            }
        }
    }
}
