package tv.corebuilds.motion

import android.os.Bundle
import androidx.appcompat.content.res.AppCompatResources
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction

/**
 * One editable field: the feed URL. Defaults to the Core Builds live-wallpaper
 * feed; a user can point it at any Overflight-compatible JSON source (their own
 * footage, a local server, etc.).
 */
class SettingsFragment : GuidedStepSupportFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance =
        Guidance(
            getString(R.string.plugin_name),
            "v${BuildConfig.VERSION_NAME}\n\n${getString(R.string.plugin_description)}",
            getString(R.string.settings),
            AppCompatResources.getDrawable(requireActivity(), R.drawable.ic_plugin),
        )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val current = Preferences.feedUrl(requireContext())
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_FEED_URL)
                .title(R.string.setting_feed_url_title)
                .description(current)
                .editDescription(current)
                .descriptionEditable(true)
                .build(),
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (action.id != ACTION_ID_FEED_URL) return
        val url = action.editDescription?.toString().orEmpty()
        Preferences.setFeedUrl(requireContext(), url)
        findActionById(ACTION_ID_FEED_URL)?.let { a ->
            a.description = url
            notifyActionChanged(findActionPositionById(ACTION_ID_FEED_URL))
        }
        (activity as? SettingsActivity)?.requestWallpaperUpdate()
    }

    companion object {
        private const val ACTION_ID_FEED_URL = 1L
    }
}
