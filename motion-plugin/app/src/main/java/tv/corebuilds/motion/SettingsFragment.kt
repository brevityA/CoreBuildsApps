package tv.corebuilds.motion

import android.os.Bundle
import androidx.appcompat.content.res.AppCompatResources
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction

/**
 * Two things: the feed URL, and a discovery self-check.
 *
 * The self-check runs the same `queryIntentServices` Projectivy runs, so a user
 * who reports "the plugin isn't detected" can read the answer off the screen
 * instead of guessing. See docs/PROJECTIVY_DETECTION.md.
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

        val report = Diagnostics.run(requireContext())
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_DIAGNOSTICS)
                .title(
                    getString(
                        if (report.allPassed) {
                            R.string.diagnostics_title_ok
                        } else {
                            R.string.diagnostics_title_problem
                        },
                    ),
                )
                .description(report.render())
                .multilineDescription(true)
                .infoOnly(false)
                .focusable(true)
                .build(),
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_ID_FEED_URL -> {
                val url = action.editDescription?.toString().orEmpty()
                Preferences.setFeedUrl(requireContext(), url)
                findActionById(ACTION_ID_FEED_URL)?.let { a ->
                    a.description = url
                    notifyActionChanged(findActionPositionById(ACTION_ID_FEED_URL))
                }
                (activity as? SettingsActivity)?.requestWallpaperUpdate()
                refreshDiagnostics()
            }

            // Re-run the check on demand — useful right after installing
            // Projectivy, or after a force-stop.
            ACTION_ID_DIAGNOSTICS -> refreshDiagnostics()
        }
    }

    private fun refreshDiagnostics() {
        val report = Diagnostics.run(requireContext())
        findActionById(ACTION_ID_DIAGNOSTICS)?.let { a ->
            a.title = getString(
                if (report.allPassed) {
                    R.string.diagnostics_title_ok
                } else {
                    R.string.diagnostics_title_problem
                },
            )
            a.description = report.render()
            notifyActionChanged(findActionPositionById(ACTION_ID_DIAGNOSTICS))
        }
    }

    companion object {
        private const val ACTION_ID_FEED_URL = 1L
        private const val ACTION_ID_DIAGNOSTICS = 2L
    }
}
