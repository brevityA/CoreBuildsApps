package tv.corebuilds.iconpack

/**
 * The motion loops that can play as the system live wallpaper.
 *
 * The twelve Deep Space loops are the `series-9-deep-space` walls themselves,
 * moving (rendered from the stills by tools/build_deep_space_loops.py: each
 * scene's own stars twinkle and its glow shimmers). They live in
 * `Motion/live/` in the repo and are fetched on demand from raw GitHub, exactly
 * like a full-size still: nothing is bundled except a still frame for the grid
 * and for the engine's fallback while the MP4 is not on the device yet.
 *
 * One loop per wall and nothing else: the list is exactly the Deep Space set
 * in Motion/live-feed.json (tests/test_live_wallpaper.py holds the two
 * equal). Only the chosen loop is downloaded, ~1.3-3.4 MB of video.
 */
data class Loop(
    /** Stable preference value: the wall's slug, e.g. "event-horizon". Never renamed once shipped. */
    val id: String,
    /** Display name, e.g. "Nebula Drift". */
    val title: String,
    /** MP4 file name in `Motion/live/`, e.g. "coremotion-live-11-deep-space-event-horizon.mp4". */
    val fileName: String,
    /** Full-resolution MP4 URL (raw GitHub). */
    val url: String,
    /** Thumbnail URL (raw GitHub). Fallback only; the frame is bundled. */
    val thumbUrl: String,
    /** Bundled still frame under assets/, shown in the grid and by the engine
     *  before the MP4 has been downloaded. */
    val thumbAsset: String
)

object LiveLoop {

    /**
     * The series the loops ride with. They are the moving half of Deep Space,
     * so they appear under that series chip in the browser grid, not in a
     * series of their own that would split one wallpaper's two forms.
     */
    const val SERIES = "series-9-deep-space"

    /** Asset directory holding the bundled still frames. */
    const val THUMB_DIR = "live_thumbs"

    private const val RAW_BASE =
        "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/live/"

    /**
     * The twelve Deep Space loops, in wall order (85-96).
     *
     * The first entry is the default: [Prefs.liveLoopId] falls back to it, and
     * so does the engine when it reads a value it does not recognise, so a
     * preference written by a newer build can never leave the home screen
     * blank.
     */
    val LOOPS: List<Loop> = listOf(
        Loop(
            id = "event-horizon",
            title = "Event Horizon",
            fileName = "coremotion-live-11-deep-space-event-horizon.mp4",
            url = RAW_BASE + "coremotion-live-11-deep-space-event-horizon.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-11-deep-space-event-horizon.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-11-deep-space-event-horizon.jpg"
        ),
        Loop(
            id = "nebula-drift",
            title = "Nebula Drift",
            fileName = "coremotion-live-12-deep-space-nebula-drift.mp4",
            url = RAW_BASE + "coremotion-live-12-deep-space-nebula-drift.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-12-deep-space-nebula-drift.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-12-deep-space-nebula-drift.jpg"
        ),
        Loop(
            id = "starfield",
            title = "Starfield",
            fileName = "coremotion-live-13-deep-space-starfield.mp4",
            url = RAW_BASE + "coremotion-live-13-deep-space-starfield.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-13-deep-space-starfield.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-13-deep-space-starfield.jpg"
        ),
        Loop(
            id = "ringed-planet",
            title = "Ringed Planet",
            fileName = "coremotion-live-14-deep-space-ringed-planet.mp4",
            url = RAW_BASE + "coremotion-live-14-deep-space-ringed-planet.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-14-deep-space-ringed-planet.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-14-deep-space-ringed-planet.jpg"
        ),
        Loop(
            id = "galaxy-spiral",
            title = "Galaxy Spiral",
            fileName = "coremotion-live-15-deep-space-galaxy-spiral.mp4",
            url = RAW_BASE + "coremotion-live-15-deep-space-galaxy-spiral.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-15-deep-space-galaxy-spiral.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-15-deep-space-galaxy-spiral.jpg"
        ),
        Loop(
            id = "aurora-orbit",
            title = "Aurora Orbit",
            fileName = "coremotion-live-16-deep-space-aurora-orbit.mp4",
            url = RAW_BASE + "coremotion-live-16-deep-space-aurora-orbit.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-16-deep-space-aurora-orbit.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-16-deep-space-aurora-orbit.jpg"
        ),
        Loop(
            id = "comet-lane",
            title = "Comet Lane",
            fileName = "coremotion-live-17-deep-space-comet-lane.mp4",
            url = RAW_BASE + "coremotion-live-17-deep-space-comet-lane.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-17-deep-space-comet-lane.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-17-deep-space-comet-lane.jpg"
        ),
        Loop(
            id = "deep-field",
            title = "Deep Field",
            fileName = "coremotion-live-18-deep-space-deep-field.mp4",
            url = RAW_BASE + "coremotion-live-18-deep-space-deep-field.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-18-deep-space-deep-field.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-18-deep-space-deep-field.jpg"
        ),
        Loop(
            id = "ember-nova",
            title = "Ember Nova",
            fileName = "coremotion-live-19-deep-space-ember-nova.mp4",
            url = RAW_BASE + "coremotion-live-19-deep-space-ember-nova.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-19-deep-space-ember-nova.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-19-deep-space-ember-nova.jpg"
        ),
        Loop(
            id = "hex-station",
            title = "Hex Station",
            fileName = "coremotion-live-20-deep-space-hex-station.mp4",
            url = RAW_BASE + "coremotion-live-20-deep-space-hex-station.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-20-deep-space-hex-station.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-20-deep-space-hex-station.jpg"
        ),
        Loop(
            id = "cyan-supernova",
            title = "Cyan Supernova",
            fileName = "coremotion-live-21-deep-space-cyan-supernova.mp4",
            url = RAW_BASE + "coremotion-live-21-deep-space-cyan-supernova.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-21-deep-space-cyan-supernova.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-21-deep-space-cyan-supernova.jpg"
        ),
        Loop(
            id = "dark-side-moon",
            title = "Dark Side Moon",
            fileName = "coremotion-live-22-deep-space-dark-side-moon.mp4",
            url = RAW_BASE + "coremotion-live-22-deep-space-dark-side-moon.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-22-deep-space-dark-side-moon.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-22-deep-space-dark-side-moon.jpg"
        ),
    )

    /**
     * The loop [id] names, or the first loop for anything unrecognised.
     *
     * Total by design: the wallpaper engine runs with no UI to report an error
     * to, so a stale or hand-edited preference gets the shipped default rather
     * than a blank home screen.
     */
    fun byId(id: String?): Loop = LOOPS.firstOrNull { it.id == id } ?: LOOPS[0]

    /**
     * The loops as browser-grid wallpapers, in [LOOPS] order. Appended to the
     * catalog by `WallpapersActivity`, so they share the grid, the preview
     * screen and the D-pad chain with the stills; [Wallpaper.isLive] is what
     * keeps them out of the stills export path.
     */
    fun asWallpapers(): List<Wallpaper> = LOOPS.map { loop ->
        Wallpaper(
            name = loop.title,
            series = SERIES,
            url = loop.url,
            thumbUrl = loop.thumbUrl,
            resolution = "1920x1080",
            thumbAsset = loop.thumbAsset,
            isLive = true
        )
    }

    /**
     * The loop a grid/preview [wallpaper] is, or null if it is a still.
     *
     * Matched on the cache name (the MP4 file name) and the URL, because the
     * preview screen receives [Wallpaper]s through a Parcelable and has to
     * recover the loop from the row the user pressed.
     */
    fun loopFor(wallpaper: Wallpaper): Loop? =
        LOOPS.firstOrNull {
            it.fileName == wallpaper.cacheName || it.url == wallpaper.url
        }
}
