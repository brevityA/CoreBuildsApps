package tv.corebuilds.eq.mode

/**
 * Content type classification for use-case-specific EQ optimization.
 *
 * Each content type maps to a curated target curve in [UseCaseTargets]
 * and a set of known package names for automatic detection.
 *
 * The system supports both manual selection and automatic detection
 * based on the active playback app.
 */
enum class ContentType(
    val key: String,
    val title: String,
    val description: String,
    val icon: String
) {
    MOVIE(
        key = "movie",
        title = "Movies",
        description = "Dialogue-first optimization · Center channel clarity · Theatrical presence",
        icon = "🎬"
    ),
    ANIME(
        key = "anime",
        title = "Anime",
        description = "Music + dialogue balance · Wide dynamic range · Enhanced bass impact",
        icon = "🎌"
    ),
    TV_SHOW(
        key = "tv_show",
        title = "TV Shows",
        description = "Natural dialogue · Reduced fatigue · Smooth long-session listening",
        icon = "📺"
    ),
    GAMING(
        key = "gaming",
        title = "Gaming",
        description = "Spatial cues · Low-end impact · Low-latency clarity",
        icon = "🎮"
    ),
    MUSIC(
        key = "music",
        title = "Music",
        description = "Flat reference · Respects mastering · Optional bass enhancement",
        icon = "🎵"
    ),
    GENERAL(
        key = "general",
        title = "General",
        description = "Balanced for all content · Safe default",
        icon = "🔊"
    );

    companion object {
        fun fromKey(key: String?): ContentType =
            entries.firstOrNull { it.key == key } ?: GENERAL
    }
}

/**
 * Maps Android package names to content types for automatic detection.
 *
 * This registry is based on research into the primary content type
 * each app delivers. Apps with mixed content (e.g., Netflix has both
 * movies and TV shows) are mapped to their dominant use case, with
 * the user able to override manually.
 *
 * **Design principles:**
 * - Explicit mappings take precedence over heuristics
 * - Unknown apps default to GENERAL (safe, balanced)
 * - User can override any auto-detection via manual selection
 * - Mappings are kept conservative to avoid incorrect optimization
 */
object ContentTypeRegistry {

    /**
     * Known package-to-content-type mappings.
     *
     * Organized by category for maintainability. Add new apps here
     * as they're identified in the wild.
     */
    private val PACKAGE_MAP = mapOf(
        // === MOVIE / STREAMING SERVICES ===
        // Netflix: Primarily movies and original series (movie-optimized)
        "com.netflix.ninja" to ContentType.MOVIE,
        "com.netflix.mediaclient" to ContentType.MOVIE,

        // Disney+: Heavy on movies (Marvel, Star Wars, Pixar, Disney)
        "com.disney.disneyplus" to ContentType.MOVIE,
        "com.disney.disneyplus.prod" to ContentType.MOVIE,

        // Amazon Prime Video: Movies and TV shows (movie-optimized for cinematic content)
        "com.amazon.amazonvideo.livingroom" to ContentType.MOVIE,
        "com.amazon.avod.thirdpartyclient" to ContentType.MOVIE,

        // Apple TV+: Primarily original series and movies
        "com.apple.tv" to ContentType.MOVIE,

        // HBO Max / Max: Premium movies and series
        "com.hbo.hbonow" to ContentType.MOVIE,
        "com.wbd.streamer" to ContentType.MOVIE,

        // Paramount+: Movies and TV
        "com.cbs.app" to ContentType.MOVIE,
        "com.cbs.ott" to ContentType.MOVIE,

        // Hulu: TV shows and movies (TV show optimized for episodic content)
        "com.hulu.livingroomplus" to ContentType.TV_SHOW,
        "com.hulu.plus" to ContentType.TV_SHOW,

        // Peacock: TV shows (NBC library)
        "com.peacocktv.peacockandroid.tv" to ContentType.TV_SHOW,

        // === ANIME SERVICES ===
        // Crunchyroll: Dedicated anime streaming
        "com.crunchyroll.crunchyroid" to ContentType.ANIME,
        "com.crunchyroll.crunchyroid.tv" to ContentType.ANIME,

        // Funimation (now merged with Crunchyroll, but still exists)
        "com.funimation.funimationnow" to ContentType.ANIME,

        // HiDive: Anime streaming
        "com.hidive.video" to ContentType.ANIME,
        "com.hidive.android.tv" to ContentType.ANIME,

        // RetroCrush: Classic anime
        "com.retrotv.retrocrush" to ContentType.ANIME,

        // Tubi: Has significant anime library
        "com.tubitv" to ContentType.ANIME,  // Mixed, but known for anime

        // === GAMING ===
        // Steam Link
        "com.valvesoftware.steamlink" to ContentType.GAMING,

        // GeForce NOW
        "com.nvidia.geforcenow" to ContentType.GAMING,

        // Moonlight (game streaming)
        "com.limelight" to ContentType.GAMING,

        // Google Stadia (deprecated but still in use)
        "com.google.stadia.android" to ContentType.GAMING,

        // Native Android games (common package patterns)
        "com.epicgames.fortnite" to ContentType.GAMING,
        "com.mojang.minecraftpe" to ContentType.GAMING,
        "com.roblox.client" to ContentType.GAMING,

        // === MUSIC ===
        // Spotify
        "com.spotify.tv.android" to ContentType.MUSIC,
        "com.spotify.music" to ContentType.MUSIC,

        // YouTube Music
        "com.google.android.apps.youtube.music" to ContentType.MUSIC,

        // Tidal
        "com.aspiro.tidal" to ContentType.MUSIC,

        // Amazon Music
        "com.amazon.bueller.music" to ContentType.MUSIC,

        // Apple Music
        "com.apple.android.music" to ContentType.MUSIC,

        // Deezer
        "deezer.android.app" to ContentType.MUSIC,

        // Pandora
        "com.pandora.android" to ContentType.MUSIC,

        // SoundCloud
        "com.soundcloud.android" to ContentType.MUSIC,

        // === TV / LIVE CONTENT ===
        // YouTube (mixed content, default to TV show for vlogs/casual)
        "com.google.android.youtube.tv" to ContentType.TV_SHOW,
        "com.google.android.youtube" to ContentType.TV_SHOW,

        // YouTube TV (live TV)
        "com.google.android.apps.youtube.unplugged" to ContentType.TV_SHOW,

        // Plex (personal media server, often used for TV shows)
        "com.plexapp.android" to ContentType.TV_SHOW,

        // Kodi (media center, mixed content)
        "org.xbmc.kodi" to ContentType.GENERAL,

        // VLC (media player, mixed content)
        "org.videolan.vlc" to ContentType.GENERAL,

        // IPTV apps (live TV)
        "ru.iptvremote.android" to ContentType.TV_SHOW,
        "com.lamatic.ipptv" to ContentType.TV_SHOW,

        // News apps
        "com.cnn.mobile.android.tv" to ContentType.TV_SHOW,
        "com.foxnews.android" to ContentType.TV_SHOW,
        "com.bbc.mediaplayer" to ContentType.TV_SHOW
    )

    /**
     * Look up the content type for a given package name.
     *
     * Returns the mapped [ContentType] or null if the package is unknown.
     * Unknown packages should default to [ContentType.GENERAL] in the UI.
     */
    fun lookup(packageName: String): ContentType? = PACKAGE_MAP[packageName]

    /**
     * Resolve the content type from a set of active packages.
     *
     * **Rules:**
     * 1. If all recognized packages map to the same content type, use that
     * 2. If multiple content types are detected, use GENERAL (safe default)
     * 3. If no packages are recognized, return null (caller decides default)
     *
     * This prevents incorrect optimization when multiple apps with
     * different content types are active simultaneously.
     */
    fun resolve(activePackages: Set<String>): ContentType? {
        val recognized = activePackages.mapNotNull { lookup(it) }.toSet()

        return when {
            recognized.isEmpty() -> null  // Unknown apps
            recognized.size == 1 -> recognized.first()  // Consensus
            else -> ContentType.GENERAL  // Mixed content, play it safe
        }
    }

    /**
     * Get all known packages for a given content type.
     * Useful for UI display or debugging.
     */
    fun packagesFor(type: ContentType): Set<String> =
        PACKAGE_MAP.filterValues { it == type }.keys

    /**
     * Get a human-readable summary of the registry.
     */
    fun summary(): String {
        val counts = ContentType.entries.associateWith { packagesFor(it).size }
        return buildString {
            append("Content Type Registry:\n")
            for (type in ContentType.entries) {
                append("  ${type.icon} ${type.title}: ${counts[type]} apps\n")
            }
            append("  Total: ${PACKAGE_MAP.size} apps\n")
        }
    }
}

/**
 * User preferences for content type handling.
 */
data class ContentTypePrefs(
    /** Whether to automatically switch content types based on detected apps. */
    val autoSwitch: Boolean = true,

    /** The user's manually selected content type (used when autoSwitch is false). */
    val manualType: ContentType = ContentType.GENERAL,

    /** Whether to show a notification when auto-switching occurs. */
    val showSwitchNotification: Boolean = true,

    /** Whether to preserve manual overrides even when apps change. */
    val stickyManualOverride: Boolean = false
) {
    companion object {
        const val PREFS_NAME = "core_eq_content_type"
        const val KEY_AUTO_SWITCH = "auto_switch"
        const val KEY_MANUAL_TYPE = "manual_type"
        const val KEY_SHOW_NOTIFICATION = "show_notification"
        const val KEY_STICKY_OVERRIDE = "sticky_override"
    }
}
