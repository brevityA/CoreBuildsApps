package tv.corebuilds.eq.export

import android.app.Application
import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.corebuilds.eq.ProfilesActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.ui.screens.ScreenHarness
import tv.corebuilds.eq.ui.screens.ScreenSeed

/**
 * A saved profile list that is not JSON at all (damage, or a format no build
 * knows) used to read as empty, and the next save replaced it with a new list:
 * every profile in it gone, without a word. The text is now kept, unchanged,
 * under its own key before anything is written over it, and Profiles says so.
 * Nothing is repaired or guessed, and reading still writes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = ScreenHarness.TV)
class UnreadableProfileListTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = app.getSharedPreferences("core_eq_profiles", Context.MODE_PRIVATE)
    private val damaged = "[{\"id\":\"living\",\"name\":\"Living room\",\"bands\":[1.0,2."

    @Before fun tv() = ScreenHarness.prepareTv()

    private fun keptCopies(): Map<String, Any?> =
        prefs.all.filterKeys { it.startsWith("profiles_json_unreadable_") }

    @Test fun `opening and reading an unreadable list writes nothing`() {
        prefs.edit().putString("profiles_json", damaged).commit()
        val before = prefs.all.toMap()
        val store = ProfileStore(app)
        assertTrue(store.getAllProfiles().isEmpty())
        assertTrue(store.hasUnreadableProfileList())
        assertEquals(before.filterKeys { it == "profiles_json" }, prefs.all.filterKeys { it == "profiles_json" })
        assertTrue(keptCopies().isEmpty())
    }

    @Test fun `the first save keeps the unreadable text before starting a new list`() {
        prefs.edit().putString("profiles_json", damaged).commit()
        val store = ProfileStore(app)
        store.saveProfile(ScreenSeed.livingRoom("fresh", "Fresh"))
        assertEquals(listOf(damaged), keptCopies().values.toList())
        val written = JSONArray(prefs.getString("profiles_json", null))
        assertEquals(1, written.length())
        assertEquals("fresh", written.getJSONObject(0).getString("id"))
        assertTrue(store.hasUnreadableProfileList())
        // Further saves on the new, readable list keep exactly one copy.
        store.saveProfile(ScreenSeed.livingRoom("second", "Second"))
        assertEquals(1, keptCopies().size)
    }

    @Test fun `a second damaged list is kept beside the first, never over it`() {
        prefs.edit().putString("profiles_json", damaged).commit()
        ProfileStore(app).saveProfile(ScreenSeed.livingRoom("a", "A"))
        prefs.edit().putString("profiles_json", "not json either").commit()
        ProfileStore(app).saveProfile(ScreenSeed.livingRoom("b", "B"))
        assertEquals(setOf(damaged, "not json either"), keptCopies().values.toSet())
    }

    @Test fun `a readable list with one damaged entry is not treated as unreadable`() {
        prefs.edit().putString("profiles_json", "[\"not an object\"]").commit()
        val store = ProfileStore(app)
        assertFalse(store.hasUnreadableProfileList())
        store.saveProfile(ScreenSeed.livingRoom("x", "X"))
        assertTrue(keptCopies().isEmpty())
        // The damaged entry itself is kept in place, as before.
        assertEquals("not an object", JSONArray(prefs.getString("profiles_json", null)).getString(0))
    }

    @Test fun `Profiles says the list could not be read and was kept`() {
        prefs.edit().putString("profiles_json", damaged).commit()
        ActivityScenario.launch(ProfilesActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ScreenHarness.idle()
                val line = activity.findViewById<TextView>(R.id.text_profile_route).text.toString()
                assertTrue(line, line.contains(activity.getString(R.string.profiles_unreadable_kept)))
            }
        }
    }
}
