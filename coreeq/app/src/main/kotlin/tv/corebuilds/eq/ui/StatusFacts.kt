package tv.corebuilds.eq.ui

import android.content.Context
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.export.EqStatus
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore

/**
 * The facts Home's summary and the diagnostics summary are built from (1.14.0),
 * read in one place so the two can never describe different states.
 */
object StatusFacts {

    data class Snapshot(
        val facts: HomeSummary.Facts,
        /** The profile the output in use plays, else the one chosen in Profiles. */
        val profile: Profile?,
        val output: OutputRoute.Output?,
        val status: EqStatus?
    )

    fun read(context: Context, store: ProfileStore = ProfileStore(context)): Snapshot {
        val output = OutputRoute.current(context)
        val profiles = store.getAllProfiles()
        val routed = OutputRoute.pick(profiles, store.chosenId(), output?.kind, output?.name).profile
        val status = store.status()
        return Snapshot(
            facts(
                context, store, status,
                anyProfile = profiles.isNotEmpty(),
                profileForOutput = routed != null,
                outputKnown = output?.kind != null,
                outputLabel = output?.kind?.let { OutputRoute.label(it) }
            ),
            routed ?: profiles.firstOrNull { it.id == store.chosenId() },
            output,
            status
        )
    }

    /** The summary facts, given what the caller already looked up about profiles and the output. */
    fun facts(
        context: Context,
        store: ProfileStore,
        status: EqStatus?,
        anyProfile: Boolean,
        profileForOutput: Boolean,
        outputKnown: Boolean,
        outputLabel: String?
    ): HomeSummary.Facts {
        val prefs = EnhancedAudioPrefs(context)
        val running = EqService.running
        return HomeSummary.Facts(
            correctionOn = store.correctionEnabled,
            serviceRunning = running,
            measurementPaused = store.measurementPaused,
            anyProfile = anyProfile,
            profileForOutput = profileForOutput,
            outputKnown = outputKnown,
            outputLabel = outputLabel,
            appliedEngine = if (running) store.appliedEngine else null,
            playing = running && status?.playing == true,
            faultMessage = status?.message?.takeIf { status.isError },
            bypass = store.bypassEnabled,
            night = prefs.nightLevel,
            clarityDb = prefs.clarityDb,
            stereoSpeakers = prefs.stereoSpeakersEnabled,
            lowVolumeBass = prefs.lowVolumeBassEnabled,
            autoLoudness = prefs.autoLoudnessEnabled,
            bassBoost = prefs.bassBoostEnabled,
            loudnessEnhancer = prefs.loudnessEnhancerEnabled
        )
    }
}
