package tv.corebuilds.iconpack

/**
 * The motion loops that can play as the system live wallpaper.
 *
 * Three of the Core Motion procedural loops are the Deep Space companions to
 * the `series-9-deep-space` stills — the same sky, moving. They live in
 * `Motion/live/` in the repo and are fetched on demand from raw GitHub, exactly
 * like a full-size still: nothing is bundled except a still frame for the grid
 * and for the engine's fallback while the MP4 is not on the device yet.
 *
 * The list is deliberately short. A live wallpaper plays behind every app on
 * the home screen, forever, so each entry is a real choice rather than a
 * catalogue; and each one is ~2-7 MB of video the device has to keep.
 */
data class Loop(
    /** Stable preference value, e.g. "nebula-drift". Never renamed once shipped. */
    val id: String,
    /** Display name, e.g. "Nebula Drift". */
    val title: String,
    /** MP4 file name in `Motion/live/`, e.g. "coremotion-live-11-nebula-drift.mp4". */
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
     * The three Deep Space companions, in picker order.
     *
     * The first entry is the default: [Prefs.liveLoopId] falls back to it, and
     * so does the engine when it reads a value it does not recognise, so a
     * preference written by a newer build can never leave the home screen
     * blank.
     */
    val LOOPS: List<Loop> = listOf(
        Loop(
            id = "nebula-drift",
            title = "Nebula Drift",
            fileName = "coremotion-live-11-nebula-drift.mp4",
            url = RAW_BASE + "coremotion-live-11-nebula-drift.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-11-nebula-drift.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-11-nebula-drift.jpg"
        ),
        Loop(
            id = "event-horizon",
            title = "Event Horizon",
            fileName = "coremotion-live-12-event-horizon.mp4",
            url = RAW_BASE + "coremotion-live-12-event-horizon.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-12-event-horizon.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-12-event-horizon.jpg"
        ),
        Loop(
            id = "ion-storm",
            title = "Ion Storm",
            fileName = "coremotion-live-13-ion-storm.mp4",
            url = RAW_BASE + "coremotion-live-13-ion-storm.mp4",
            thumbUrl = RAW_BASE + "thumbs/coremotion-live-13-ion-storm.jpg",
            thumbAsset = "$THUMB_DIR/coremotion-live-13-ion-storm.jpg"
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
