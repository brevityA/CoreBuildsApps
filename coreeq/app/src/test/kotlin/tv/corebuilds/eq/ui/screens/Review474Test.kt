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
}
