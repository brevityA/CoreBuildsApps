package tv.corebuilds.eq

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import tv.corebuilds.eq.mode.ContentType
import tv.corebuilds.eq.mode.ContentTypePrefs
import tv.corebuilds.eq.mode.ContentTypeRegistry
import tv.corebuilds.eq.ui.showSwitch

/**
 * Content type selection screen: how strong the optional Extra effects are
 * for each kind of content.
 *
 * A content type sets the platform BassBoost strength and LoudnessEnhancer
 * gain ([tv.corebuilds.eq.apply.ExtraPlan]). It does **not** change the room
 * correction or the target curve: the correction is measured once per output
 * and the target is chosen on the Measure screen. With Extra effects off,
 * changing the content type changes nothing (the screen's own subtitle says
 * so).
 *
 * Automatic detection uses [ContentTypeRegistry] to identify known streaming
 * apps and select the matching extras. It can only switch extras; it never
 * applies a correction or a target.
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
        chipFor(selectedType).requestFocus()
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
        textCurrent.text = selectedType.title
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

        btnAuto.showSwitch(autoSwitch, R.string.content_type_auto_on_btn, R.string.content_type_auto_off_btn)

        // The selected type is the filled chip (1.3.0). Until then the others
        // were dimmed to 60% and the selection marked with a bullet, which
        // made nine of ten choices look disabled.
        for (type in ContentType.entries) {
            val chip = chipFor(type)
            chip.text = type.title
            chip.isActivated = type == selectedType
        }
    }

    private fun chipFor(type: ContentType): Button = when (type) {
        ContentType.MOVIE -> btnMovie
        ContentType.ANIME -> btnAnime
        ContentType.TV_SHOW -> btnTvShow
        ContentType.SITCOM -> btnSitcom
        ContentType.DOCUMENTARY -> btnDocumentary
        ContentType.NEWS -> btnNews
        ContentType.PODCAST -> btnPodcast
        ContentType.GAMING -> btnGaming
        ContentType.MUSIC -> btnMusic
        ContentType.GENERAL -> btnGeneral
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
