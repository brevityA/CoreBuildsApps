package tv.corebuilds.eq

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import tv.corebuilds.eq.mode.ContentType
import tv.corebuilds.eq.mode.ContentTypePrefs
import tv.corebuilds.eq.mode.ContentTypeRegistry

/**
 * Content type selection screen: choose the use-case-specific EQ
 * optimization for your primary content (Movies, Anime, TV Shows,
 * Gaming, Music).
 *
 * Each content type applies a curated target curve from
 * [UseCaseTargets] that optimizes the room correction for the
 * acoustic characteristics of that content:
 *
 * - **Movies:** Dialogue-first, center channel clarity, theatrical presence
 * - **Anime:** Music + dialogue balance, wide dynamic range, enhanced bass
 * - **TV Shows:** Natural dialogue, reduced fatigue, smooth listening
 * - **Gaming:** Spatial cues, low-end impact, low-latency clarity
 * - **Music:** Flat reference, respects mastering, optional bass
 *
 * Automatic detection uses the [ContentTypeRegistry] to identify
 * known streaming apps and switch to the appropriate profile.
 */
class ContentTypeActivity : TvActivity() {

    private lateinit var textCurrent: TextView
    private lateinit var textDescription: TextView
    private lateinit var textAutoStatus: TextView
    private lateinit var btnMovie: Button
    private lateinit var btnAnime: Button
    private lateinit var btnTvShow: Button
    private lateinit var btnSitcom: Button
    private lateinit var btnDocumentary: Button
    private lateinit var btnNews: Button
    private lateinit var btnPodcast: Button
    private lateinit var btnGaming: Button
    private lateinit var btnMusic: Button
    private lateinit var btnGeneral: Button
    private lateinit var btnAuto: Button
    private lateinit var btnDone: Button

    private var selectedType: ContentType = ContentType.GENERAL
    private var autoSwitch: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_content_type)

        textCurrent = findViewById(R.id.text_content_type_current)
        textDescription = findViewById(R.id.text_content_type_description)
        textAutoStatus = findViewById(R.id.text_auto_detect_status)
        btnMovie = findViewById(R.id.btn_content_movie)
        btnAnime = findViewById(R.id.btn_content_anime)
        btnTvShow = findViewById(R.id.btn_content_tv)
        btnSitcom = findViewById(R.id.btn_content_sitcom)
        btnDocumentary = findViewById(R.id.btn_content_documentary)
        btnNews = findViewById(R.id.btn_content_news)
        btnPodcast = findViewById(R.id.btn_content_podcast)
        btnGaming = findViewById(R.id.btn_content_gaming)
        btnMusic = findViewById(R.id.btn_content_music)
        btnGeneral = findViewById(R.id.btn_content_general)
        btnAuto = findViewById(R.id.btn_content_auto)
        btnDone = findViewById(R.id.btn_content_done)

        loadPrefs()

        btnMovie.setOnClickListener { selectType(ContentType.MOVIE) }
        btnAnime.setOnClickListener { selectType(ContentType.ANIME) }
        btnTvShow.setOnClickListener { selectType(ContentType.TV_SHOW) }
        btnSitcom.setOnClickListener { selectType(ContentType.SITCOM) }
        btnDocumentary.setOnClickListener { selectType(ContentType.DOCUMENTARY) }
        btnNews.setOnClickListener { selectType(ContentType.NEWS) }
        btnPodcast.setOnClickListener { selectType(ContentType.PODCAST) }
        btnGaming.setOnClickListener { selectType(ContentType.GAMING) }
        btnMusic.setOnClickListener { selectType(ContentType.MUSIC) }
        btnGeneral.setOnClickListener { selectType(ContentType.GENERAL) }
        btnAuto.setOnClickListener { toggleAutoSwitch() }
        btnDone.setOnClickListener { finish() }

        refreshUI()
        btnAnime.requestFocus()  // Default focus for anime fans
    }

    override fun onPause() {
        savePrefs()
        super.onPause()
    }

    private fun selectType(type: ContentType) {
        selectedType = type
        refreshUI()
    }

    private fun toggleAutoSwitch() {
        autoSwitch = !autoSwitch
        refreshUI()
    }

    private fun refreshUI() {
        textCurrent.text = getString(
            R.string.content_type_current,
            selectedType.icon,
            selectedType.title
        )
        textDescription.text = selectedType.description

        textAutoStatus.text = if (autoSwitch) {
            val movieCount = ContentTypeRegistry.packagesFor(ContentType.MOVIE).size
            val animeCount = ContentTypeRegistry.packagesFor(ContentType.ANIME).size
            val gamingCount = ContentTypeRegistry.packagesFor(ContentType.GAMING).size
            getString(
                R.string.content_type_auto_on,
                movieCount + animeCount + gamingCount +
                    ContentTypeRegistry.packagesFor(ContentType.TV_SHOW).size +
                    ContentTypeRegistry.packagesFor(ContentType.MUSIC).size
            )
        } else {
            getString(R.string.content_type_auto_off)
        }

        btnAuto.text = getString(
            if (autoSwitch) R.string.content_type_auto_on_btn
            else R.string.content_type_auto_off_btn
        )

        // Visual feedback on selected button
        updateButtonState(btnMovie, ContentType.MOVIE)
        updateButtonState(btnAnime, ContentType.ANIME)
        updateButtonState(btnTvShow, ContentType.TV_SHOW)
        updateButtonState(btnSitcom, ContentType.SITCOM)
        updateButtonState(btnDocumentary, ContentType.DOCUMENTARY)
        updateButtonState(btnNews, ContentType.NEWS)
        updateButtonState(btnPodcast, ContentType.PODCAST)
        updateButtonState(btnGaming, ContentType.GAMING)
        updateButtonState(btnMusic, ContentType.MUSIC)
        updateButtonState(btnGeneral, ContentType.GENERAL)
    }

    private fun updateButtonState(button: Button, type: ContentType) {
        val isSelected = type == selectedType
        button.alpha = if (isSelected) 1.0f else 0.6f
        val label = "${type.icon} ${type.title}"
        button.text = if (isSelected) "● $label" else label
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences(ContentTypePrefs.PREFS_NAME, Context.MODE_PRIVATE)
        autoSwitch = prefs.getBoolean(ContentTypePrefs.KEY_AUTO_SWITCH, true)
        selectedType = ContentType.fromKey(
            prefs.getString(ContentTypePrefs.KEY_MANUAL_TYPE, null)
        )
    }

    private fun savePrefs() {
        val prefs = getSharedPreferences(ContentTypePrefs.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(ContentTypePrefs.KEY_AUTO_SWITCH, autoSwitch)
            .putString(ContentTypePrefs.KEY_MANUAL_TYPE, selectedType.key)
            .apply()
    }
}
