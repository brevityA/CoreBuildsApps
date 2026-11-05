package tv.corebuilds.eq.companion

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallClient
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import tv.corebuilds.eq.R
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** Android phone UI for local TV control and a foreground-only phone-mic measurement. */
class PhoneCompanionActivity : AppCompatActivity() {

    internal data class RemoteProfile(val id: String, val name: String, val active: Boolean, val manualOnly: Boolean)
    internal data class Dashboard(
        val deviceName: String,
        val version: String,
        val output: String,
        val mode: String,
        val correctionEnabled: Boolean,
        val profileName: String,
        val serviceStatus: String,
        val serviceError: Boolean,
        val profiles: List<RemoteProfile>,
        val serviceRunning: Boolean? = null,
        val bypassEnabled: Boolean = false
    )
    private enum class PhoneTab { CONTROL, MEASURE, TV }

    private data class RoomChoice(val label: String, val volumeM3: Double?)

    private lateinit var credentialStore: PhoneCredentialStore
    private lateinit var hostField: EditText
    private lateinit var portField: EditText
    private lateinit var codeField: EditText
    private lateinit var connectionLine: TextView
    private lateinit var detailLine: TextView
    private lateinit var statusLine: TextView
    private lateinit var remotePanel: LinearLayout
    private lateinit var profileList: LinearLayout
    private lateinit var correctionButton: Button
    private lateinit var pairButton: Button
    private lateinit var connectButton: Button
    private lateinit var revokeButton: Button
    private lateinit var forgetButton: Button
    private lateinit var measureButton: Button
    private lateinit var cancelMeasureButton: Button
    private lateinit var targetButton: Button
    private lateinit var roomButton: Button
    private lateinit var repeatCheck: CheckBox
    private lateinit var measureProgress: ProgressBar
    private lateinit var findTvButton: Button
    private lateinit var scanQrButton: Button
    private lateinit var discoveryLine: TextView
    private lateinit var contentScroll: ScrollView
    private lateinit var controlPage: LinearLayout
    private lateinit var measurePage: LinearLayout
    private lateinit var tvPage: LinearLayout
    private lateinit var controlEmpty: LinearLayout
    private lateinit var manualPairPanel: LinearLayout
    private lateinit var manualPairButton: Button
    private lateinit var savedTvPanel: LinearLayout
    private lateinit var savedTvLine: TextView
    private lateinit var connectionHint: TextView
    private lateinit var correctionState: TextView
    private lateinit var outputLine: TextView
    private lateinit var serviceLine: TextView
    private lateinit var profileButton: Button
    private lateinit var measureAvailability: TextView
    private lateinit var measureFeedback: TextView
    private val modeButtons = linkedMapOf<String, Button>()
    private val tabButtons = linkedMapOf<PhoneTab, Button>()
    private var selectedTab = PhoneTab.TV
    private var pairedCredentials: PhoneCredentials? = null
    private var tvConnected = false
    private var screenResumed = false
    private var searchedOnFirstOpen = false
    private var initialConnectionRequested = false
    private var qrInProgress = false
    private var statusRefreshInProgress = false
    private var dashboardRevision = 0L
    private var lastSyncMs: Long? = null
    private var orientationBeforeCapture: Int? = null
    private var focusBeforeOperation: View? = null
    private var tvSearch: TvDiscovery.Search? = null
    private var searchGeneration = 0L
    private var scannerInstallClient: ModuleInstallClient? = null
    private var scannerInstallListener: InstallStatusListener? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private val refreshTicker = object : Runnable {
        override fun run() {
            if (!screenResumed) return
            if (pairedCredentials != null && hasLocalNetworkAccess() && !qrInProgress && tvSearch == null) {
                // Reads only. Never retry a state-changing command or silently turn EQ on.
                refreshDashboard(quiet = true, allowRediscovery = false)
            }
            uiHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }
    /** Other TV addresses from the QR, tried only when a connection to the first one fails. */
    private var fallbackHosts: List<String> = emptyList()

    private var dashboard: Dashboard? = null
    private var targetIndex = 0
    private var roomIndex = 0
    private var pendingLocalNetworkAction: (() -> Unit)? = null
    private var pendingMicrophoneAction: (() -> Unit)? = null
    private var captureEngine: PhoneCaptureEngine? = null
    @Volatile private var measurementInProgress = false
    @Volatile private var measurementCancelled = false
    @Volatile private var activeSessionId: String? = null
    /** Server-retained first sweep; cancelling the optional repeat must not cancel this session. */
    @Volatile private var repeatFallbackSessionId: String? = null
    @Volatile private var operationInProgress = false

    // Labels come from Targets.label so the phone and the TV name a profile's target
    // the same way; `house` was shown here as "Harman", the name `room` replaced.
    private val targets = listOf("dialogue", "room", "bk", "flat", "olive", "house")
        .map { it to tv.corebuilds.eq.dsp.Targets.label(it) }
    private val rooms = listOf(
        RoomChoice("Not provided", null),
        RoomChoice("20 m³", 20.0),
        RoomChoice("35 m³", 35.0),
        RoomChoice("50 m³", 50.0),
        RoomChoice("75 m³", 75.0),
        RoomChoice("100 m³", 100.0)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialStore = PhoneCredentialStore(this)
        pairedCredentials = credentialStore.load()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        targetIndex = prefs.getInt(KEY_TARGET, 0).coerceIn(targets.indices)
        roomIndex = prefs.getInt(KEY_ROOM, 0).coerceIn(rooms.indices)
        selectedTab = savedInstanceState?.getString(KEY_TAB)?.let { key ->
            PhoneTab.entries.firstOrNull { it.name == key }
        } ?: if (pairedCredentials == null) PhoneTab.TV else PhoneTab.CONTROL
        setContentView(buildContent())
        pairedCredentials?.let { saved ->
            hostField.setText(saved.host)
            portField.setText(saved.port.toString())
        }
        selectTab(selectedTab, moveFocus = false)
        updateConnectionUi()
        setRemoteEnabled(false)
        refreshMeasurementChoices()
        focusFirstControl()
        contentScroll.post { if (!isFinishing && !isDestroyed) focusFirstControl() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    measurementInProgress -> confirmCancelCapture()
                    selectedTab != PhoneTab.CONTROL && pairedCredentials != null -> selectTab(PhoneTab.CONTROL)
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_TAB, selectedTab.name)
        super.onSaveInstanceState(outState)
    }

    /** A phone-sized shell: one scrolling page and navigation that never scrolls away. */
    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.cb_night))
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                insets
            }
        }
        val header = column(padding = 20).apply { setPadding(dp(20), dp(12), dp(20), dp(8)) }
        header.addView(label(getString(R.string.phone_brand), 26f, true, R.color.cb_ink).apply {
            isAccessibilityHeading = true
        })
        header.addView(label(getString(R.string.phone_brand_caption), 12f, false, R.color.cb_signal_cyan))
        root.addView(header, matchWrap())
        contentScroll = ScrollView(this).apply {
            id = R.id.phone_scroll
            isFillViewport = true
            clipToPadding = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            isFocusable = false
        }
        val content = column(padding = 20).apply { setPadding(dp(20), dp(8), dp(20), dp(24)) }
        contentScroll.addView(content)
        root.addView(contentScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val connectionCard = section()
        connectionLine = label(getString(R.string.phone_not_paired), 18f, true, R.color.cb_ink).apply {
            id = R.id.phone_connection_state
        }
        connectionCard.addView(connectionLine)
        connectionHint = label("", 13f, false, R.color.cb_slate)
        connectionCard.addView(connectionHint, matchWrap(top = 2))
        connectButton = actionButton(getString(R.string.phone_connect_button), primary = false).apply {
            id = R.id.phone_connect
            setOnClickListener {
                if (pairedCredentials == null) selectTab(PhoneTab.TV)
                else withLocalNetwork { refreshDashboard() }
            }
        }
        connectionCard.addView(connectButton, matchWrap(top = 8))
        content.addView(connectionCard, matchWrap())
        statusLine = cardText("").apply {
            id = R.id.phone_feedback
            visibility = View.GONE
        }
        content.addView(statusLine, matchWrap(top = 10))
        controlPage = column().apply { id = R.id.phone_control_page }
        measurePage = column().apply { id = R.id.phone_measure_page }
        tvPage = column().apply { id = R.id.phone_tv_page }
        content.addView(controlPage, matchWrap(top = 18))
        content.addView(measurePage, matchWrap(top = 18))
        content.addView(tvPage, matchWrap(top = 18))
        buildControlPage()
        buildMeasurePage()
        buildTvPage()

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(color(R.color.cb_card_surface))
        }
        listOf(
            PhoneTab.CONTROL to R.string.phone_tab_control,
            PhoneTab.MEASURE to R.string.phone_tab_measure,
            PhoneTab.TV to R.string.phone_tab_tv
        ).forEach { (tab, title) ->
            val button = actionButton(getString(title), primary = false).apply {
                id = when (tab) {
                    PhoneTab.CONTROL -> R.id.phone_tab_control
                    PhoneTab.MEASURE -> R.id.phone_tab_measure
                    PhoneTab.TV -> R.id.phone_tab_tv
                }
                setBackgroundResource(R.drawable.bg_segment)
                setTextColor(ContextCompat.getColorStateList(this@PhoneCompanionActivity, R.color.segment_text))
                setOnClickListener { selectTab(tab) }
            }
            tabButtons[tab] = button
            navigation.addView(button, weightedWrap())
        }
        root.addView(navigation, matchWrap())
        return root
    }

    private fun buildControlPage() {
        controlEmpty = section().apply {
            addView(heading(R.string.phone_control_empty_title))
            addView(label(getString(R.string.phone_control_empty_body), 15f, false, R.color.cb_slate), matchWrap(top = 6))
            addView(actionButton(getString(R.string.phone_open_tv_setup), primary = true).apply {
                id = R.id.phone_open_tv_setup
                setOnClickListener { selectTab(PhoneTab.TV) }
            }, matchWrap(top = 12))
        }
        controlPage.addView(controlEmpty, matchWrap())
        remotePanel = column().apply { visibility = View.GONE }
        controlPage.addView(remotePanel, matchWrap())
        val correctionCard = section()
        correctionCard.addView(eyebrow(R.string.phone_correction_heading))
        correctionState = label("", 26f, true, R.color.cb_ink).apply { id = R.id.phone_correction_state }
        correctionCard.addView(correctionState, matchWrap(top = 4))
        serviceLine = label("", 14f, false, R.color.cb_slate)
        correctionCard.addView(serviceLine, matchWrap(top = 4))
        correctionButton = actionButton(getString(R.string.phone_correction_off), primary = true).apply {
            id = R.id.phone_correction_toggle
            setOnClickListener {
                val current = dashboard ?: return@setOnClickListener
                runControl(JSONObject().put("op", "set_enabled").put("enabled", !current.correctionEnabled))
            }
        }
        correctionCard.addView(correctionButton, matchWrap(top = 12))
        outputLine = label("", 13f, false, R.color.cb_slate)
        correctionCard.addView(outputLine, matchWrap(top = 10))
        remotePanel.addView(correctionCard, matchWrap())

        val modeCard = section()
        modeCard.addView(heading(R.string.phone_mode_heading))
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "movie_tv" to R.string.phone_mode_movie,
            "everyday" to R.string.phone_mode_everyday,
            "gaming" to R.string.phone_mode_gaming
        ).forEach { (mode, title) ->
            val button = actionButton(getString(title), primary = false).apply {
                id = when (mode) {
                    "movie_tv" -> R.id.phone_mode_movie
                    "gaming" -> R.id.phone_mode_gaming
                    else -> R.id.phone_mode_everyday
                }
                textSize = 14f
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setBackgroundResource(R.drawable.bg_segment)
                setTextColor(ContextCompat.getColorStateList(this@PhoneCompanionActivity, R.color.segment_text))
                setOnClickListener {
                    if (dashboard?.mode != mode) runControl(JSONObject().put("op", "set_mode").put("mode", mode))
                }
            }
            modeButtons[mode] = button
            modeRow.addView(button, weightedWrap())
        }
        modeCard.addView(modeRow, matchWrap(top = 8))
        modeCard.addView(label(getString(R.string.phone_mode_note), 13f, false, R.color.cb_slate), matchWrap(top = 8))
        remotePanel.addView(modeCard, matchWrap(top = 12))

        val profileCard = section()
        profileCard.addView(heading(R.string.phone_profiles_heading))
        profileList = column()
        profileButton = actionButton(getString(R.string.phone_no_profiles), primary = false).apply {
            id = R.id.phone_profile_picker
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { chooseProfile() }
        }
        profileList.addView(profileButton, matchWrap(top = 10))
        profileCard.addView(profileList, matchWrap())
        remotePanel.addView(profileCard, matchWrap(top = 12))
    }

    private fun buildMeasurePage() {
        measurePage.addView(heading(R.string.phone_measure_heading))
        measureAvailability = label("", 14f, false, R.color.cb_slate)
        measurePage.addView(measureAvailability, matchWrap(top = 4))
        measureFeedback = cardText("").apply {
            id = R.id.phone_measure_feedback
            visibility = View.GONE
        }
        measurePage.addView(measureFeedback, matchWrap(top = 10))
        measureProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            id = R.id.phone_measure_progress
            max = PROGRESS_MAX
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(color(R.color.cb_signal_cyan))
            contentDescription = getString(R.string.phone_measure_progress_accessibility)
        }
        measurePage.addView(measureProgress, matchWrap(top = 12))
        cancelMeasureButton = actionButton(getString(R.string.phone_cancel_measure), primary = false).apply {
            id = R.id.phone_cancel_measure
            visibility = View.GONE
            setOnClickListener { cancelCapture(sendRemote = true) }
        }
        measurePage.addView(cancelMeasureButton, matchWrap(top = 8))
        val preparation = section()
        preparation.addView(heading(R.string.phone_measure_prepare))
        preparation.addView(label(getString(R.string.phone_measure_checklist), 15f, false, R.color.cb_ink), matchWrap(top = 8))
        preparation.addView(label(getString(R.string.phone_capture_warning), 13f, false, R.color.cb_slate), matchWrap(top = 10))
        measurePage.addView(preparation, matchWrap(top = 14))
        val choices = section()
        choices.addView(heading(R.string.phone_measure_options))
        targetButton = actionButton("", primary = false).apply {
            id = R.id.phone_target_picker
            setOnClickListener { chooseTarget() }
        }
        roomButton = actionButton("", primary = false).apply {
            id = R.id.phone_room_picker
            setOnClickListener { chooseRoom() }
        }
        choices.addView(targetButton, matchWrap(top = 10))
        choices.addView(roomButton, matchWrap(top = 8))
        repeatCheck = CheckBox(this).apply {
            id = R.id.phone_repeat_check
            text = getString(R.string.phone_repeat_option)
            textSize = 15f
            minimumHeight = dp(48)
            isFocusable = true
            isClickable = true
            setTextColor(color(R.color.cb_ink))
            buttonTintList = android.content.res.ColorStateList.valueOf(color(R.color.cb_signal_cyan))
            isChecked = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_REPEAT, false)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_REPEAT, checked).apply()
            }
        }
        choices.addView(repeatCheck, matchWrap(top = 10))
        choices.addView(label(getString(R.string.phone_repeat_explanation), 13f, false, R.color.cb_slate), matchWrap(top = 4))
        measurePage.addView(choices, matchWrap(top = 12))
        measureButton = actionButton(getString(R.string.phone_measure_button), primary = true).apply {
            id = R.id.phone_start_measure
            setOnClickListener {
                withLocalNetwork {
                    if (repeatFallbackSessionId != null) retrySaveFirstCapture() else requestMicrophoneThenMeasure()
                }
            }
        }
        measurePage.addView(measureButton, matchWrap(top = 16))
        measurePage.addView(label(getString(R.string.phone_measure_foreground), 13f, false, R.color.cb_slate), matchWrap(top = 8))
    }

    private fun buildTvPage() {
        tvPage.addView(heading(R.string.phone_tv_heading))
        savedTvPanel = section()
        savedTvPanel.addView(heading(R.string.phone_saved_tv_heading))
        savedTvLine = label("", 15f, false, R.color.cb_ink)
        savedTvPanel.addView(savedTvLine, matchWrap(top = 6))
        detailLine = label("", 13f, false, R.color.cb_slate)
        savedTvPanel.addView(detailLine, matchWrap(top = 6))
        revokeButton = actionButton(getString(R.string.phone_unpair_button), primary = false).apply {
            id = R.id.phone_unpair
            setOnClickListener { confirmUnpair() }
        }
        forgetButton = actionButton(getString(R.string.phone_forget_button), primary = false).apply {
            id = R.id.phone_forget
            setOnClickListener { confirmForget() }
        }
        savedTvPanel.addView(revokeButton, matchWrap(top = 12))
        savedTvPanel.addView(forgetButton, matchWrap(top = 8))
        savedTvPanel.addView(label(getString(R.string.phone_forget_note), 13f, false, R.color.cb_slate), matchWrap(top = 6))
        tvPage.addView(savedTvPanel, matchWrap(top = 14))
        val pairing = section()
        pairing.addView(heading(R.string.phone_pair_heading))
        pairing.addView(label(getString(R.string.phone_pair_steps), 14f, false, R.color.cb_slate), matchWrap(top = 6))
        scanQrButton = actionButton(getString(R.string.phone_scan_qr_button), primary = true).apply {
            id = R.id.phone_scan_qr
            setOnClickListener { scanTvQr() }
        }
        pairing.addView(scanQrButton, matchWrap(top = 12))
        findTvButton = actionButton(getString(R.string.phone_find_tv_button), primary = false).apply {
            id = R.id.phone_find_tv
            setOnClickListener { withLocalNetwork { startTvSearch() } }
        }
        pairing.addView(findTvButton, matchWrap(top = 8))
        discoveryLine = label("", 14f, false, R.color.cb_slate).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        pairing.addView(discoveryLine, matchWrap(top = 6))
        manualPairButton = actionButton(getString(R.string.phone_manual_pair_show), primary = false).apply {
            id = R.id.phone_manual_pair_toggle
            setOnClickListener { showManualPairing(manualPairPanel.visibility != View.VISIBLE) }
        }
        pairing.addView(manualPairButton, matchWrap(top = 10))
        manualPairPanel = column().apply {
            id = R.id.phone_manual_pair_panel
            visibility = View.GONE
        }
        hostField = editField(getString(R.string.phone_tv_ip_hint), InputType.TYPE_CLASS_PHONE).apply {
            id = R.id.phone_host
            contentDescription = getString(R.string.phone_tv_ip_accessibility)
        }
        portField = editField(getString(R.string.phone_tv_port_hint), InputType.TYPE_CLASS_NUMBER).apply {
            id = R.id.phone_port
            contentDescription = getString(R.string.phone_tv_port_accessibility)
        }
        codeField = editField(getString(R.string.phone_pair_code_hint), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply {
            id = R.id.phone_pair_code
            filters = arrayOf(InputFilter.LengthFilter(64))
            typeface = android.graphics.Typeface.MONOSPACE
            imeOptions = EditorInfo.IME_ACTION_DONE
            contentDescription = getString(R.string.phone_pair_code_accessibility)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    withLocalNetwork { pairWithTv() }; true
                } else false
            }
        }
        manualPairPanel.addView(eyebrow(R.string.phone_ip_label), matchWrap(top = 12))
        manualPairPanel.addView(hostField, matchWrap(top = 4))
        manualPairPanel.addView(eyebrow(R.string.phone_port_label), matchWrap(top = 10))
        manualPairPanel.addView(portField, matchWrap(top = 4))
        manualPairPanel.addView(eyebrow(R.string.phone_code_label), matchWrap(top = 10))
        manualPairPanel.addView(codeField, matchWrap(top = 4))
        pairButton = actionButton(getString(R.string.phone_pair_button), primary = true).apply {
            id = R.id.phone_pair
            setOnClickListener { withLocalNetwork { pairWithTv() } }
        }
        manualPairPanel.addView(pairButton, matchWrap(top = 12))
        pairing.addView(manualPairPanel, matchWrap())
        tvPage.addView(pairing, matchWrap(top = 14))
        tvPage.addView(label(getString(R.string.phone_link_visibility), 14f, false, R.color.cb_slate), matchWrap(top = 12))
    }

    private fun selectTab(tab: PhoneTab, moveFocus: Boolean = true) {
        val changed = selectedTab != tab
        selectedTab = tab
        controlPage.visibility = if (tab == PhoneTab.CONTROL) View.VISIBLE else View.GONE
        measurePage.visibility = if (tab == PhoneTab.MEASURE) View.VISIBLE else View.GONE
        tvPage.visibility = if (tab == PhoneTab.TV) View.VISIBLE else View.GONE
        connectButton.visibility = if (tab == PhoneTab.TV && pairedCredentials == null) View.GONE else View.VISIBLE
        tabButtons.forEach { (key, button) ->
            button.isActivated = key == tab
            button.isSelected = key == tab
            button.contentDescription = getString(
                if (key == tab) R.string.phone_tab_selected else R.string.phone_tab_accessibility,
                button.text
            )
        }
        if (changed) {
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(contentScroll.windowToken, 0)
            contentScroll.scrollTo(0, 0)
        }
        if (moveFocus) focusFirstControl()
    }

    private fun showManualPairing(show: Boolean) {
        manualPairPanel.visibility = if (show) View.VISIBLE else View.GONE
        manualPairButton.text = getString(if (show) R.string.phone_manual_pair_hide else R.string.phone_manual_pair_show)
        if (show) hostField.requestFocus() else manualPairButton.requestFocus()
    }

    private fun chooseTarget() {
        if (!uiState().canChangeSetup) return
        AlertDialog.Builder(this)
            .setTitle(R.string.phone_target_title)
            .setSingleChoiceItems(targets.map { it.second }.toTypedArray(), targetIndex) { dialog, which ->
                if (uiState().canChangeSetup) {
                    targetIndex = which
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_TARGET, which).apply()
                    refreshMeasurementChoices()
                }
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun chooseRoom() {
        if (!uiState().canChangeSetup) return
        AlertDialog.Builder(this)
            .setTitle(R.string.phone_room_title)
            .setSingleChoiceItems(rooms.map { it.label }.toTypedArray(), roomIndex) { dialog, which ->
                if (uiState().canChangeSetup) {
                    roomIndex = which
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_ROOM, which).apply()
                    refreshMeasurementChoices()
                }
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun chooseProfile() {
        val value = dashboard ?: return
        if (!uiState().canControl || value.profiles.isEmpty()) return
        val profiles = value.profiles
        val titles = profiles.map { profile ->
            if (profile.manualOnly) getString(R.string.phone_profile_manual, profile.name) else profile.name
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.phone_profiles_heading)
            .setSingleChoiceItems(titles.toTypedArray(), profiles.indexOfFirst { it.active }) { dialog, which ->
                if (!profiles[which].active) runControl(JSONObject().put("op", "activate_profile").put("id", profiles[which].id))
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun confirmUnpair() {
        AlertDialog.Builder(this).setTitle(R.string.phone_unpair_title)
            .setMessage(R.string.phone_unpair_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.phone_unpair_action) { _, _ -> withLocalNetwork { revokePairing() } }.show()
    }

    private fun confirmForget() {
        AlertDialog.Builder(this).setTitle(R.string.phone_forget_title)
            .setMessage(R.string.phone_forget_note)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.phone_forget_action) { _, _ -> forgetLocally() }.show()
    }

    private fun confirmCancelCapture() {
        AlertDialog.Builder(this).setTitle(R.string.phone_cancel_measure)
            .setMessage(R.string.phone_cancel_measure_confirm)
            .setNegativeButton(R.string.phone_keep_measuring, null)
            .setPositiveButton(R.string.phone_cancel_measure) { _, _ -> cancelCapture(sendRemote = true) }.show()
    }

    private fun pairWithTv(replaceConfirmed: Boolean = false) {
        if (operationInProgress || measurementInProgress || repeatFallbackSessionId != null) return
        val host = parsePrivateIpv4(hostField.text?.toString().orEmpty())
        if (host == null) {
            setStatus(getString(R.string.phone_invalid_tv_ip), error = true)
            hostField.error = getString(R.string.phone_invalid_tv_ip)
            hostField.requestFocus()
            return
        }
        val port = portField.text?.toString()?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }
        if (port == null) {
            setStatus(getString(R.string.phone_invalid_tv_port), error = true)
            portField.error = getString(R.string.phone_invalid_tv_port)
            portField.requestFocus()
            return
        }
        val pairingKey = CompanionCrypto.decodeKey(codeField.text?.toString().orEmpty(), CompanionCrypto.PAIRING_KEY_BYTES)
        if (pairingKey == null) {
            setStatus(getString(R.string.phone_invalid_pair_code), error = true)
            codeField.error = getString(R.string.phone_invalid_pair_code)
            codeField.requestFocus()
            return
        }
        hostField.error = null
        portField.error = null
        codeField.error = null
        if (pairedCredentials != null && !replaceConfirmed) {
            AlertDialog.Builder(this).setTitle(R.string.phone_replace_tv_title)
                .setMessage(R.string.phone_replace_tv_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.phone_pair_button) { _, _ -> pairWithTv(replaceConfirmed = true) }.show()
            return
        }
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(codeField.windowToken, 0)
        // The user is pairing with these fields now; a search still running must not
        // replace the address underneath them.
        stopTvSearch()
        dashboardRevision++
        setOperationBusy(true)
        setStatus(getString(R.string.phone_pairing), error = false)
        thread(name = "CoreEqPhonePair") {
            val saved = try {
                val token = CompanionCrypto.randomKey()
                val clientName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(60).ifBlank { "Android phone" }
                val response = CompanionClient(host, port, pairingKey).pair(pairingKey, token, clientName)
                requireOk(response)
                PhoneCredentials(host, port, token).also { credentialStore.save(it) }
            } catch (e: Exception) {
                val unreachable = e is java.net.ConnectException ||
                    e is java.net.SocketTimeoutException ||
                    e is java.net.NoRouteToHostException
                onUi {
                    setOperationBusy(false)
                    val next = fallbackHosts.firstOrNull()
                    if (unreachable && next != null) {
                        // The first address did not answer; try the next one from the QR.
                        fallbackHosts = fallbackHosts.drop(1)
                        hostField.setText(next)
                        setStatus(getString(R.string.phone_trying_next_address, next), error = false)
                        pairWithTv(replaceConfirmed = true)
                    } else {
                        fallbackHosts = emptyList()
                        setStatus(getString(R.string.phone_pair_failed, readableError(e)), error = true)
                    }
                }
                return@thread
            }
            // Paired: the TV has spent the one-time key and the token is saved. A failure from
            // here on is a connection problem to report, never a reason to pair again with the
            // QR's next address, which would only fail on the spent key.
            val newDashboard = try {
                loadDashboard(saved)
            } catch (e: Exception) {
                onUi {
                    setOperationBusy(false)
                    fallbackHosts = emptyList()
                    codeField.text?.clear()
                    pairedCredentials = saved
                    dashboard = null
                    markDisconnected()
                    setStatus(getString(R.string.phone_paired_not_loaded, readableError(e)), error = true)
                }
                return@thread
            }
            onUi {
                setOperationBusy(false)
                fallbackHosts = emptyList()
                codeField.text?.clear()
                pairedCredentials = saved
                setStatus(getString(R.string.phone_pair_success), error = false)
                showDashboard(newDashboard)
                selectTab(PhoneTab.CONTROL)
            }
        }
    }

    private fun refreshDashboard(quiet: Boolean = false, allowRediscovery: Boolean = true) {
        if (operationInProgress || measurementInProgress || repeatFallbackSessionId != null || qrInProgress) return
        if (statusRefreshInProgress) {
            if (!quiet) setStatus(getString(R.string.phone_connecting), error = false)
            return
        }
        val credentials = pairedCredentials
        if (credentials == null) {
            dashboard = null
            markDisconnected()
            return
        }
        statusRefreshInProgress = true
        val revision = dashboardRevision
        // Quiet reads never disable controls (which would drop keyboard/D-pad
        // focus every ten seconds). A user action invalidates this read instead.
        if (!quiet) setOperationBusy(true)
        if (!quiet) setStatus(getString(R.string.phone_connecting), error = false)
        thread(name = "CoreEqPhoneStatus") {
            try {
                val newDashboard = loadDashboard(credentials)
                onUi {
                    statusRefreshInProgress = false
                    if (!samePairing(credentials) || revision != dashboardRevision) return@onUi
                    val reconnected = !tvConnected
                    if (!quiet) setOperationBusy(false)
                    showDashboard(newDashboard)
                    if (reconnected && quiet) setStatus(getString(R.string.phone_connection_restored), error = false)
                    else if (!quiet) setStatus("", error = false)
                }
            } catch (e: Exception) {
                onUi {
                    statusRefreshInProgress = false
                    if (!samePairing(credentials) || revision != dashboardRevision) return@onUi
                    val wasConnected = tvConnected
                    if (!quiet) setOperationBusy(false)
                    markDisconnected()
                    if (!quiet || wasConnected) setStatus(getString(R.string.phone_connect_failed, readableError(e)), error = true)
                    if (allowRediscovery && screenResumed) rediscoverSavedTv(credentials)
                }
            }
        }
    }

    /**
     * The saved address may have changed (DHCP, a new router). Search the LAN for Core EQ
     * TVs at a different address and try each with the saved token, one at a time, until
     * one accepts it or the search ends. The token is the identity check: a TV that was
     * not paired with this phone refuses it, so a second Core EQ TV answering first only
     * costs one attempt. The new address is saved only after a successful connection.
     * All of this state lives on the main thread.
     */
    private fun rediscoverSavedTv(saved: PhoneCredentials) {
        if (operationInProgress || measurementInProgress || repeatFallbackSessionId != null) return
        stopTvSearch()
        val generation = searchGeneration
        setStatus(getString(R.string.phone_rediscovering), error = false)
        val candidates = ArrayDeque<TvDiscovery.FoundTv>()
        val seen = hashSetOf("${saved.host}:${saved.port}")
        var trying = false
        var tried = false
        var searching = true
        var connected = false
        lateinit var search: TvDiscovery.Search
        fun tryNext() {
            if (trying || connected || generation != searchGeneration || !screenResumed || !samePairing(saved)) return
            val next = candidates.removeFirstOrNull()
            if (next == null) {
                // Nothing left to try. A failed attempt already left its own message.
                if (!searching && !tried) setStatus(getString(R.string.phone_rediscover_none), error = true)
                return
            }
            trying = true
            tried = true
            retryWithFoundTv(saved, next.host, next.port, generation) { ok ->
                trying = false
                if (ok) {
                    connected = true
                    search.cancel()
                } else {
                    tryNext()
                }
            }
        }
        search = TvDiscovery.Search(
            context = this,
            timeoutMs = FIND_TV_TIMEOUT_MS,
            firstOnly = false,
            onFound = { found ->
                if (screenResumed && samePairing(saved) && seen.add("${found.host}:${found.port}")) {
                    candidates.addLast(found)
                    tryNext()
                }
            },
            onDone = { _ ->
                searching = false
                // stopTvSearch releases this reference before cancelling: a cancelled
                // search must not launch another connection from its onDone callback.
                val current = tvSearch === search
                if (current) tvSearch = null
                if (current && screenResumed && !isFinishing && !isDestroyed) tryNext()
            }
        )
        tvSearch = search
        search.start()
    }

    /** One reconnect attempt; [onResult] runs on the main thread with whether it connected. */
    private fun retryWithFoundTv(
        saved: PhoneCredentials,
        host: String,
        port: Int,
        generation: Long,
        onResult: (Boolean) -> Unit
    ) {
        dashboardRevision++
        setOperationBusy(true)
        setStatus(getString(R.string.phone_connecting), error = false)
        thread(name = "CoreEqPhoneRediscover") {
            try {
                val creds = PhoneCredentials(host, port, saved.token)
                val newDashboard = loadDashboard(creds)
                onUi {
                    if (generation != searchGeneration || !screenResumed || !samePairing(saved)) {
                        setOperationBusy(false)
                        return@onUi
                    }
                    // Only the address changed; keep the existing encrypted token.
                    if (!credentialStore.updateAddress(saved, host, port)) {
                        setOperationBusy(false)
                        onResult(false)
                        return@onUi
                    }
                    pairedCredentials = creds
                    setOperationBusy(false)
                    hostField.setText(host)
                    portField.setText(port.toString())
                    showDashboard(newDashboard)
                    setRemoteEnabled(true)
                    setStatus(getString(R.string.phone_rediscover_ok), error = false)
                    onResult(true)
                }
            } catch (e: Exception) {
                onUi {
                    setOperationBusy(false)
                    if (generation != searchGeneration || !screenResumed || !samePairing(saved)) return@onUi
                    setStatus(getString(R.string.phone_connect_failed, readableError(e)), error = true)
                    onResult(false)
                }
            }
        }
    }

    private fun loadDashboard(credentials: PhoneCredentials): Dashboard {
        val client = clientFor(credentials)
        val status = requireOk(client.rpc(JSONObject().put("op", "status")))
        val profilesResponse = requireOk(client.rpc(JSONObject().put("op", "profiles")))
        val profilesArray = profilesResponse.optJSONArray("profiles")
        val profiles = buildList {
            if (profilesArray != null) {
                for (i in 0 until profilesArray.length()) {
                    val item = profilesArray.optJSONObject(i) ?: continue
                    val id = item.optString("id", "").take(120)
                    if (id.isBlank()) continue
                    add(RemoteProfile(
                        id = id,
                        name = item.optString("name", "Profile").take(120),
                        active = item.optBoolean("active", false),
                        manualOnly = item.optBoolean("manual_only", false)
                    ))
                }
            }
        }
        val outputName = status.optString("output_name", "").takeIf { it.isNotBlank() && it != "null" }
            ?: status.optString("output_kind", "").takeIf { it.isNotBlank() && it != "null" }
            ?: getString(R.string.phone_output_unreported)
        val outputEvidence = status.optString("output_evidence", "").takeIf { it.isNotBlank() && it != "null" }
        val outputSummary = if (outputEvidence == null) outputName else "$outputName ($outputEvidence)"
        return Dashboard(
            deviceName = status.optString("device_name", "Android TV").take(100),
            version = status.optString("app_version", "?").take(30),
            output = outputSummary,
            mode = status.optString("mode", "everyday").take(30),
            correctionEnabled = status.optBoolean("correction_enabled", false),
            profileName = status.optString("profile_name", "").takeIf { it.isNotBlank() && it != "null" }
                ?: getString(R.string.phone_no_active_profile),
            serviceStatus = status.optString("service_status", "").take(180),
            serviceError = status.optBoolean("service_error", false),
            profiles = profiles,
            serviceRunning = if (status.has("service_running")) status.optBoolean("service_running") else null,
            bypassEnabled = status.optBoolean("bypass_enabled", false)
        )
    }

    /** Render a read-only TV snapshot; this never starts or stops correction. */
    internal fun showDashboard(value: Dashboard) {
        dashboard = value
        tvConnected = true
        lastSyncMs = System.currentTimeMillis()
        updateConnectionUi()
        val currentModeTitle = modeTitle(value.mode)
        val onOff = getString(if (value.correctionEnabled) R.string.phone_state_on else R.string.phone_state_off)
        detailLine.text = getString(R.string.phone_tv_version, value.version) + "\n" + getString(
            R.string.phone_dashboard_details, value.output, currentModeTitle, onOff, value.profileName,
            value.serviceStatus.ifBlank { getString(R.string.phone_status_unknown) }
        )
        correctionState.text = getString(when {
            !value.correctionEnabled -> R.string.phone_correction_state_off
            value.serviceRunning == false -> R.string.phone_correction_state_stopped
            value.bypassEnabled -> R.string.phone_correction_state_bypass
            else -> R.string.phone_correction_state_on
        })
        serviceLine.text = value.serviceStatus.ifBlank { getString(R.string.phone_status_unknown) }
        serviceLine.setTextColor(color(if (value.serviceError || (value.correctionEnabled && value.serviceRunning == false)) R.color.cb_warning else R.color.cb_slate))
        outputLine.text = getString(R.string.phone_output_label, value.output)
        correctionButton.text = getString(if (value.correctionEnabled) R.string.phone_correction_on else R.string.phone_correction_off)
        correctionButton.isActivated = value.correctionEnabled
        modeButtons.forEach { (mode, button) ->
            val selected = mode == value.mode
            button.isActivated = selected
            button.isSelected = selected
            button.text = if (selected) getString(R.string.phone_choice_selected, modeTitle(mode)) else modeTitle(mode)
            button.contentDescription = getString(
                if (selected) R.string.phone_tab_selected else R.string.phone_tab_accessibility, modeTitle(mode)
            )
        }
        // Stable controls: polling must not rebuild a list or steal the D-pad/keyboard focus.
        profileButton.text = if (value.profiles.isEmpty()) getString(R.string.phone_no_profiles)
            else getString(R.string.phone_profile_choice, value.profileName)
        setRemoteEnabled(true)
        refreshMeasurementChoices()
    }

    private fun modeTitle(mode: String): String = getString(when (mode) {
        "movie_tv" -> R.string.phone_mode_movie
        "gaming" -> R.string.phone_mode_gaming
        else -> R.string.phone_mode_everyday
    })

    /**
     * Every remote action goes through the local-network check, as Find, Pair and Connect do.
     * Without it, a phone on Android 17 whose permission was denied timed out after 5 s with
     * "connect timed out" instead of saying what was wrong.
     */
    private fun runControl(request: JSONObject) = withLocalNetwork { runControlNow(request) }

    private fun runControlNow(request: JSONObject) {
        if (!uiState().canControl) return
        val credentials = pairedCredentials ?: return
        dashboardRevision++
        setOperationBusy(true)
        setStatus(getString(R.string.phone_sending_command), error = false)
        thread(name = "CoreEqPhoneControl") {
            try {
                val response = requireOk(clientFor(credentials).rpc(request))
                // A command may succeed even when the subsequent read fails. Never call
                // that a failed toggle, or retry the write automatically.
                val snapshot = runCatching { loadDashboard(credentials) }
                onUi {
                    if (!samePairing(credentials)) return@onUi
                    setOperationBusy(false)
                    snapshot.getOrNull()?.let(::showDashboard) ?: run {
                        markDisconnected()
                        setStatus(getString(R.string.phone_command_sent_no_status), error = true)
                        return@onUi
                    }
                    val message = response.optString("profile_name", "").takeIf { it.isNotBlank() }
                        ?: response.optString("title", "").takeIf { it.isNotBlank() }
                        ?: getString(R.string.phone_command_complete)
                    setStatus(message, error = false)
                }
            } catch (e: Exception) {
                onUi {
                    if (!samePairing(credentials)) return@onUi
                    setOperationBusy(false)
                    val uncertain = e is java.io.IOException
                    if (uncertain) markDisconnected()
                    setStatus(getString(if (uncertain) R.string.phone_command_unknown else R.string.phone_command_failed, readableError(e)), error = true)
                }
            }
        }
    }

    private fun requestMicrophoneThenMeasure() = withLocalNetwork { requestMicrophoneThenMeasureNow() }

    private fun requestMicrophoneThenMeasureNow() {
        if (measurementInProgress || operationInProgress || repeatFallbackSessionId != null) return
        val engine = PhoneCaptureEngine(this)
        if (!engine.hasPermission()) {
            pendingMicrophoneAction = { startPhoneMeasurement() }
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MICROPHONE)
            return
        }
        startPhoneMeasurement()
    }

    private fun startPhoneMeasurement() {
        if (!uiState().canControl) return
        val credentials = pairedCredentials
        if (credentials == null) {
            setStatus(getString(R.string.phone_pair_first), error = true)
            return
        }
        val engine = PhoneCaptureEngine(this)
        if (!engine.hasPermission()) {
            pendingMicrophoneAction = { startPhoneMeasurement() }
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MICROPHONE)
            return
        }
        dashboardRevision++
        measurementInProgress = true
        measurementCancelled = false
        activeSessionId = null
        repeatFallbackSessionId = null
        captureEngine = engine
        val repeatRequested = repeatCheck.isChecked
        setMeasurementUi(true)
        measureProgress.progress = 0
        setStatus(getString(R.string.phone_measure_preparing), error = false)
        val selectedTarget = targets[targetIndex]
        val selectedRoom = rooms[roomIndex]
        thread(name = "CoreEqPhoneMeasurement") {
            var firstRecording: PhoneRecording? = null
            var repeatSessionId: String? = null
            try {
                fun recordPass(pass: Int, sessionId: String? = null): PhoneRecording = engine.capture(
                    onRecordingReady = { _ ->
                        if (measurementCancelled) throw InterruptedException("Phone measurement cancelled.")
                        val startRequest = if (pass == 1) {
                            JSONObject()
                                .put("op", "start_measurement")
                                .put("target", selectedTarget.first)
                                .put("repeatability", repeatRequested)
                        } else {
                            JSONObject()
                                .put("op", "repeat_measurement")
                                .put("session_id", sessionId ?: throw IllegalStateException("No repeat session is available."))
                        }
                        if (pass == 1) selectedRoom.volumeM3?.let { startRequest.put("volume_m3", it) }
                        val response = requireOk(clientFor(credentials).rpc(startRequest))
                        val start = PhoneCaptureStart(
                            sessionId = response.optString("session_id", ""),
                            captureSeconds = response.optDouble("capture_seconds", Double.NaN)
                        )
                        activeSessionId = start.sessionId
                        if (measurementCancelled) throw InterruptedException("Phone measurement cancelled.")
                        start
                    },
                    onProgress = { fraction ->
                        onUi {
                            if (!measurementCancelled && !isDestroyed) {
                                val totalFraction = if (repeatRequested) (pass - 1 + fraction) / 2.0 else fraction.toDouble()
                                measureProgress.progress = (totalFraction * PROGRESS_MAX).roundToInt().coerceIn(0, PROGRESS_MAX)
                                if (repeatRequested) {
                                    setStatus(
                                        getString(R.string.phone_measure_recording_pass, pass, 2, (fraction * 100).roundToInt()),
                                        error = false
                                    )
                                } else {
                                    setStatus(getString(R.string.phone_measure_recording, (fraction * 100).roundToInt()), error = false)
                                }
                            }
                        }
                    }
                )

                fun upload(recording: PhoneRecording): JSONObject {
                    if (measurementCancelled) throw InterruptedException("Phone measurement cancelled.")
                    onUi {
                        if (!isDestroyed) setStatus(getString(R.string.phone_measure_uploading), error = false)
                    }
                    val request = JSONObject()
                        .put("op", "upload_measurement")
                        .put("session_id", recording.sessionId)
                        .put("sample_rate_hz", recording.sampleRateHz)
                        .put("pcm16_le_base64", encodePcm16Le(recording.pcm))
                        .put("capture", recording.captureInfo)
                    return requireOk(clientFor(credentials).rpc(request))
                }

                val first = recordPass(1)
                firstRecording = first
                if (measurementCancelled) throw InterruptedException("Phone measurement cancelled.")
                val firstResponse = upload(first)
                val result = if (firstResponse.optBoolean("awaiting_repeat", false)) {
                    repeatFallbackSessionId = first.sessionId
                    repeatSessionId = first.sessionId
                    onUi {
                        if (!isDestroyed) setStatus(getString(R.string.phone_measure_repeat_waiting), error = false)
                    }
                    if (measurementCancelled) throw InterruptedException("Phone measurement cancelled.")
                    val repeated = recordPass(2, first.sessionId)
                    if (measurementCancelled) throw InterruptedException("Phone measurement cancelled.")
                    upload(repeated)
                } else {
                    firstResponse
                }
                activeSessionId = null
                repeatSessionId = null
                repeatFallbackSessionId = null
                val newDashboard = runCatching { loadDashboard(credentials) }.getOrNull()
                onUi {
                    measurementInProgress = false
                    captureEngine = null
                    setMeasurementUi(false)
                    measureProgress.progress = PROGRESS_MAX
                    newDashboard?.let(::showDashboard)
                    val saved = getString(
                        R.string.phone_measure_saved,
                        result.optString("profile_name", "Phone measurement"),
                        result.optDouble("snr_db", Double.NaN).takeIf { it.isFinite() }?.let {
                            String.format(java.util.Locale.US, "%.1f", it)
                        } ?: getString(R.string.phone_value_unreported),
                        captureSummary(first.captureInfo, first.sampleRateHz)
                    )
                    setStatus(listOfNotNull(saved, repeatabilityMessage(result)).joinToString("\n"), error = false)
                }
            } catch (e: Exception) {
                val pending = repeatSessionId ?: repeatFallbackSessionId
                val firstSaveAttempt = if (pending != null && firstRecording != null) {
                    runCatching {
                        requireOk(clientFor(credentials).rpc(
                            JSONObject().put("op", "save_first_capture").put("session_id", pending)
                        ))
                    }
                } else null
                val keepFirst = firstSaveAttempt?.getOrNull()

                if (keepFirst != null) {
                    activeSessionId = null
                    repeatFallbackSessionId = null
                    val newDashboard = runCatching { loadDashboard(credentials) }.getOrNull()
                    onUi {
                        measurementInProgress = false
                        captureEngine = null
                        setMeasurementUi(false)
                        measureProgress.progress = PROGRESS_MAX
                        newDashboard?.let(::showDashboard)
                        val first = firstRecording!!
                        val saved = getString(
                            R.string.phone_measure_saved,
                            keepFirst.optString("profile_name", "Phone measurement"),
                            keepFirst.optDouble("snr_db", Double.NaN).takeIf { it.isFinite() }?.let {
                                String.format(java.util.Locale.US, "%.1f", it)
                            } ?: getString(R.string.phone_value_unreported),
                            captureSummary(first.captureInfo, first.sampleRateHz)
                        )
                        val why = getString(R.string.phone_measure_repeat_unscored, readableError(e))
                        setStatus("$saved\n$why\nThe first sweep was saved on its own; no repeat was averaged.", error = false)
                    }
                } else {
                    val sessionId = activeSessionId
                    if (pending == null && repeatFallbackSessionId == null && !sessionId.isNullOrBlank()) {
                        runCatching {
                            clientFor(credentials).rpc(JSONObject().put("op", "cancel_measurement").put("session_id", sessionId))
                        }
                    }
                    activeSessionId = null
                    onUi {
                        measurementInProgress = false
                        captureEngine = null
                        setMeasurementUi(false)
                        measureProgress.progress = 0
                        if (pending != null && firstRecording != null) {
                            val saveError = firstSaveAttempt?.exceptionOrNull() ?: e
                            setStatus(
                                getString(R.string.phone_measure_repeat_save_failed, readableError(saveError)),
                                error = true
                            )
                        } else if (e is InterruptedException || measurementCancelled) {
                            setStatus(getString(R.string.phone_measure_cancelled), error = false)
                        } else {
                            setStatus(getString(R.string.phone_measure_failed, readableError(e)), error = true)
                        }
                    }
                }
            }
        }
    }

    private fun repeatabilityMessage(result: JSONObject): String? {
        val check = result.optJSONObject("repeatability") ?: return null
        val metrics = check.optJSONObject("metrics")
        if (metrics == null) {
            val note = check.optString("note", "The captures did not share enough clean bands.")
            return getString(R.string.phone_measure_repeat_unscored, note)
        }
        val range = metrics.optJSONArray("comparison_range_hz")
        val summary = String.format(
            java.util.Locale.US,
            getString(R.string.phone_measure_repeat_summary),
            metrics.optInt("compared_bands", 0),
            metrics.optDouble("median_abs_difference_db", Double.NaN),
            metrics.optDouble("rms_difference_db", Double.NaN),
            metrics.optDouble("p95_abs_difference_db", Double.NaN),
            metrics.optDouble("level_offset_db", Double.NaN)
        )
        val rangeNote = if (range != null && range.length() >= 2) {
            "Compared ${range.optDouble(0).toInt()} Hz–${(range.optDouble(1) / 1000.0)} kHz."
        } else ""
        val note = check.optString("note", "")
        return listOfNotNull(summary, rangeNote.takeIf(String::isNotBlank), note.takeIf(String::isNotBlank))
            .joinToString("\n")
    }

    private fun cancelCapture(sendRemote: Boolean) {
        if (!measurementInProgress) return
        measurementCancelled = true
        captureEngine?.cancel()
        val sessionId = activeSessionId
        val firstCaptureCanBeSaved = repeatFallbackSessionId != null
        if (sendRemote && !firstCaptureCanBeSaved && !sessionId.isNullOrBlank()) {
            val credentials = pairedCredentials
            if (credentials != null) {
                thread(name = "CoreEqPhoneCancel") {
                    runCatching {
                        clientFor(credentials).rpc(JSONObject().put("op", "cancel_measurement").put("session_id", sessionId))
                    }
                }
            }
        }
        setStatus(getString(R.string.phone_measure_cancelling), error = false)
    }

    /** Retry the TV-side first-sweep save without recording another sweep. */
    private fun retrySaveFirstCapture() {
        if (operationInProgress || measurementInProgress) return
        val sessionId = repeatFallbackSessionId ?: return
        val credentials = pairedCredentials
        if (credentials == null) {
            setStatus(getString(R.string.phone_pair_first), error = true)
            return
        }
        setOperationBusy(true)
        setStatus(getString(R.string.phone_measure_saving_first), error = false)
        thread(name = "CoreEqPhoneSaveFirst") {
            try {
                val response = requireOk(clientFor(credentials).rpc(
                    JSONObject().put("op", "save_first_capture").put("session_id", sessionId)
                ))
                repeatFallbackSessionId = null
                activeSessionId = null
                val newDashboard = runCatching { loadDashboard(credentials) }.getOrNull()
                onUi {
                    setOperationBusy(false)
                    newDashboard?.let(::showDashboard)
                    setStatus(
                        getString(
                            R.string.phone_measure_first_saved,
                            response.optString("profile_name", "Phone measurement")
                        ),
                        error = false
                    )
                }
            } catch (e: Exception) {
                onUi {
                    setOperationBusy(false)
                    setStatus(
                        getString(R.string.phone_measure_repeat_save_failed, readableError(e)),
                        error = true
                    )
                }
            }
        }
    }

    private fun revokePairing() {
        if (operationInProgress || measurementInProgress || repeatFallbackSessionId != null) return
        val credentials = pairedCredentials ?: return
        dashboardRevision++
        setOperationBusy(true)
        setStatus(getString(R.string.phone_unpairing), error = false)
        thread(name = "CoreEqPhoneRevoke") {
            try {
                requireOk(clientFor(credentials).rpc(JSONObject().put("op", "revoke")))
                credentialStore.clear()
                onUi {
                    pairedCredentials = null
                    stopTvSearch()
                    dashboard = null
                    detailLine.text = ""
                    setOperationBusy(false)
                    markDisconnected()
                    selectTab(PhoneTab.TV)
                    setStatus(getString(R.string.phone_unpaired), error = false)
                }
            } catch (e: Exception) {
                onUi {
                    setOperationBusy(false)
                    setStatus(getString(R.string.phone_unpair_failed, readableError(e)), error = true)
                }
            }
        }
    }

    private fun forgetLocally() {
        if (!uiState().canChangeSetup) return
        dashboardRevision++
        stopTvSearch()
        credentialStore.clear()
        pairedCredentials = null
        dashboard = null
        detailLine.text = ""
        hostField.text?.clear()
        portField.text?.clear()
        codeField.text?.clear()
        fallbackHosts = emptyList()
        markDisconnected()
        selectTab(PhoneTab.TV)
        setStatus(getString(R.string.phone_forgotten), error = false)
    }

    private fun withLocalNetwork(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingLocalNetworkAction = action
            requestPermissions(arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK), REQ_LOCAL_NETWORK)
            return
        }
        action()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQ_LOCAL_NETWORK -> {
                val action = pendingLocalNetworkAction
                pendingLocalNetworkAction = null
                if (granted) action?.invoke() else setStatus(getString(R.string.phone_local_network_denied), error = true)
            }
            REQ_MICROPHONE -> {
                val action = pendingMicrophoneAction
                pendingMicrophoneAction = null
                if (granted) action?.invoke() else setStatus(getString(R.string.phone_microphone_denied), error = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        screenResumed = true
        if (!qrInProgress && pendingMicrophoneAction == null && pendingLocalNetworkAction == null) {
            if (pairedCredentials != null) {
                if (!initialConnectionRequested) {
                    initialConnectionRequested = true
                    withLocalNetwork { refreshDashboard(quiet = dashboard != null) }
                } else if (hasLocalNetworkAccess()) refreshDashboard(quiet = dashboard != null)
            } else if (!searchedOnFirstOpen) {
                searchedOnFirstOpen = true
                // Discovery is passive: it cannot open a code or change anything on the TV.
                withLocalNetwork { startTvSearch() }
            }
        }
        uiHandler.removeCallbacks(refreshTicker)
        uiHandler.postDelayed(refreshTicker, REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        screenResumed = false
        uiHandler.removeCallbacks(refreshTicker)
        super.onPause()
    }

    override fun onStop() {
        if (measurementInProgress && !isChangingConfigurations) cancelCapture(sendRemote = true)
        stopTvSearch()
        super.onStop()
    }

    override fun onDestroy() {
        if (measurementInProgress) cancelCapture(sendRemote = true)
        stopTvSearch()
        uiHandler.removeCallbacksAndMessages(null)
        clearScannerInstall()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    /**
     * Reads the QR on the TV's pairing screen. The QR carries the same one-time
     * key as the typed code, so scanning fills in the form and runs the same
     * encrypted pairing. Nothing is sent until the phone has the TV's key.
     */
    private fun scanTvQr() {
        if (operationInProgress || measurementInProgress || repeatFallbackSessionId != null) return
        qrInProgress = true
        updateControls()
        val scanner = GmsBarcodeScanning.getClient(this)
        val installer = ModuleInstall.getClient(this)
        // The scanner is a Play services module that is not in the APK. On a phone that has
        // never used it, startScan() fails while the module downloads, which read as "not
        // available on this phone". Ask first, and download it with the user told why.
        installer.areModulesAvailable(scanner)
            .addOnSuccessListener { availability ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (availability.areModulesAvailable()) {
                    startTvQrScan(scanner)
                } else {
                    setStatus(getString(R.string.phone_scan_downloading), error = false)
                    installScannerThenScan(installer, scanner)
                }
            }
            .addOnFailureListener { scanUnavailable() }
    }

    private fun installScannerThenScan(installer: ModuleInstallClient, scanner: GmsBarcodeScanner) {
        val listener = object : InstallStatusListener {
            override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                when (update.installState) {
                    ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                        installer.unregisterListener(this)
                        if (!isFinishing && !isDestroyed) startTvQrScan(scanner)
                    }
                    ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                    ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> {
                        installer.unregisterListener(this)
                        if (!isFinishing && !isDestroyed) scanUnavailable()
                    }
                    else -> Unit // pending or downloading: the status line already says so
                }
            }
        }
        scannerInstallClient = installer
        scannerInstallListener = listener
        val request = ModuleInstallRequest.newBuilder()
            .addApi(scanner)
            .setListener(listener)
            .build()
        installer.installModules(request)
            .addOnSuccessListener { response ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (response.areModulesAlreadyInstalled()) {
                    installer.unregisterListener(listener)
                    startTvQrScan(scanner)
                }
            }
            .addOnFailureListener {
                installer.unregisterListener(listener)
                scanUnavailable()
            }
    }

    private fun startTvQrScan(scanner: GmsBarcodeScanner) {
        if (isFinishing || isDestroyed) return
        if (!screenResumed) {
            finishQrScan()
            setStatus(getString(R.string.phone_scan_ready), error = false)
            return
        }
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                finishQrScan()
                handleTvQr(barcode.rawValue)
            }
            .addOnCanceledListener { finishQrScan() }
            .addOnFailureListener { scanUnavailable() }
    }

    private fun clearScannerInstall() {
        scannerInstallListener?.let { listener -> scannerInstallClient?.unregisterListener(listener) }
        scannerInstallListener = null
        scannerInstallClient = null
    }

    private fun finishQrScan() {
        clearScannerInstall()
        qrInProgress = false
        if (!isFinishing && !isDestroyed) updateControls()
    }

    private fun scanUnavailable() {
        finishQrScan()
        if (isFinishing || isDestroyed) return
        showManualPairing(true)
        setStatus(getString(R.string.phone_scan_unavailable), error = true)
    }

    private fun handleTvQr(raw: String?) {
        val payload = TvPairingQr.parse(raw)
        if (payload == null) {
            setStatus(getString(R.string.phone_scan_not_tv), error = true)
            return
        }
        fallbackHosts = payload.hosts.drop(1)
        hostField.setText(payload.hosts.first())
        portField.setText(payload.port.toString())
        codeField.setText(payload.key)
        setStatus(getString(R.string.phone_scan_ok), error = false)
        withLocalNetwork { pairWithTv() }
    }

    /**
     * Looks for a Core EQ TV on this LAN and fills in its address and port. It
     * never pairs or sends anything: the one-time code on the TV still gates
     * pairing, and a wrong or stale address simply fails the handshake.
     */
    private fun startTvSearch() {
        if (!uiState().canChangeSetup || qrInProgress) return
        stopTvSearch()
        findTvButton.isEnabled = false
        discoveryLine.text = getString(R.string.phone_find_tv_searching)
        // The search starts by itself when nothing is paired, so the user may type an
        // address (or scan the QR) while it runs. What they entered wins: a found TV only
        // fills fields that are still as they were when the search started.
        val hostAtStart = hostField.text?.toString().orEmpty()
        val portAtStart = portField.text?.toString().orEmpty()
        lateinit var search: TvDiscovery.Search
        search = TvDiscovery.Search(
            context = this,
            timeoutMs = FIND_TV_TIMEOUT_MS,
            onFound = { found ->
                val edited = hostField.text?.toString().orEmpty() != hostAtStart ||
                    portField.text?.toString().orEmpty() != portAtStart
                if (edited) {
                    discoveryLine.text = getString(R.string.phone_find_tv_found_kept, found.name, found.host)
                } else {
                    hostField.setText(found.host)
                    portField.setText(found.port.toString())
                    discoveryLine.text = getString(R.string.phone_find_tv_found, found.name, found.host)
                    // Do not summon a TV screen or focus the keyboard during passive discovery.
                    // Manual entry is available on demand; scanning remains the primary path.
                }
            },
            onDone = { foundAny ->
                // Runs synchronously when a previous search is cancelled, before the new one
                // is stored. stopTvSearch() lets go of it first, which reads as "stopped".
                val stopped = tvSearch !== search
                if (tvSearch === search) tvSearch = null
                updateControls()
                if (!foundAny) discoveryLine.text = if (stopped) "" else getString(R.string.phone_find_tv_none)
            }
        )
        tvSearch = search
        search.start()
    }

    /** Ends any running search on purpose (pairing has started), without a "not found". */
    private fun stopTvSearch() {
        searchGeneration++ // also invalidates a reconnect already in flight after a search timed out
        val search = tvSearch ?: return
        tvSearch = null
        search.cancel()
    }

    private fun refreshMeasureButtonLabel() {
        if (::measureButton.isInitialized) {
            measureButton.setText(
                if (repeatFallbackSessionId == null) R.string.phone_measure_button else R.string.phone_save_first_capture
            )
        }
    }

    private fun setMeasurementUi(active: Boolean) {
        refreshMeasureButtonLabel()
        cancelMeasureButton.visibility = if (active) View.VISIBLE else View.GONE
        measureProgress.visibility = if (active) View.VISIBLE else View.GONE
        if (active) {
            selectTab(PhoneTab.MEASURE, moveFocus = false)
            contentScroll.scrollTo(0, 0)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (orientationBeforeCapture == null) {
                orientationBeforeCapture = requestedOrientation
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            orientationBeforeCapture?.let { requestedOrientation = it }
            orientationBeforeCapture = null
        }
        updateControls()
    }

    private fun setOperationBusy(busy: Boolean) {
        if (busy && !operationInProgress) focusBeforeOperation = window.currentFocus
        operationInProgress = busy
        refreshMeasureButtonLabel()
        updateControls()
        if (!busy) {
            val previous = focusBeforeOperation
            focusBeforeOperation = null
            if (previous?.isShown == true && previous.isEnabled &&
                (!packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) || !contentScroll.isInTouchMode)
            ) previous.requestFocus()
        }
    }

    private fun uiState() = PhoneUiState(
        connected = tvConnected && dashboard != null,
        operationInProgress = operationInProgress || qrInProgress,
        measurementInProgress = measurementInProgress,
        firstCapturePending = repeatFallbackSessionId != null
    )

    private fun updateControls() {
        val state = uiState()
        val canSetUp = state.canChangeSetup && !qrInProgress
        pairButton.isEnabled = canSetUp
        findTvButton.isEnabled = canSetUp && tvSearch == null
        scanQrButton.isEnabled = canSetUp
        manualPairButton.isEnabled = canSetUp
        hostField.isEnabled = canSetUp
        portField.isEnabled = canSetUp
        codeField.isEnabled = canSetUp
        connectButton.isEnabled = canSetUp
        correctionButton.isEnabled = state.canControl
        modeButtons.values.forEach { it.isEnabled = state.canControl }
        setProfileButtonsEnabled(state.canControl && dashboard?.profiles?.isNotEmpty() == true)
        revokeButton.isEnabled = canSetUp && pairedCredentials != null && tvConnected
        forgetButton.isEnabled = canSetUp && pairedCredentials != null
        targetButton.isEnabled = state.canChangeSetup
        roomButton.isEnabled = state.canChangeSetup
        repeatCheck.isEnabled = state.canChangeSetup
        measureButton.isEnabled = state.canStartOrSave
        measureAvailability.text = getString(when {
            measurementInProgress -> R.string.phone_measure_in_progress
            repeatFallbackSessionId != null -> R.string.phone_measure_save_pending
            !tvConnected -> R.string.phone_measure_connect_first
            else -> R.string.phone_measure_connected
        })
    }

    private fun setRemoteEnabled(enabled: Boolean) {
        val wasHidden = remotePanel.visibility != View.VISIBLE
        val showControls = enabled && dashboard != null
        remotePanel.visibility = if (showControls) View.VISIBLE else View.GONE
        controlEmpty.visibility = if (showControls) View.GONE else View.VISIBLE
        updateControls()
        // Never steal focus on a quiet status refresh, only when controls first appear.
        if (showControls && wasHidden && selectedTab == PhoneTab.CONTROL) focusFirstControl()
    }

    private fun updateConnectionUi() {
        savedTvPanel.visibility = if (pairedCredentials == null) View.GONE else View.VISIBLE
        connectButton.visibility = if (selectedTab == PhoneTab.TV && pairedCredentials == null) View.GONE else View.VISIBLE
        pairedCredentials?.let { saved -> savedTvLine.text = getString(R.string.phone_saved_tv_address, saved.host, saved.port) }
        connectionLine.text = when {
            tvConnected -> getString(R.string.phone_connected_device, dashboard?.deviceName.orEmpty())
            pairedCredentials != null -> getString(R.string.phone_saved_not_connected)
            else -> getString(R.string.phone_not_paired)
        }
        connectionLine.setTextColor(color(if (tvConnected) R.color.cb_success else R.color.cb_ink))
        connectionHint.text = when {
            tvConnected -> getString(R.string.phone_sync_at, android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(lastSyncMs ?: System.currentTimeMillis())))
            dashboard != null -> getString(R.string.phone_last_known_note)
            pairedCredentials != null -> getString(R.string.phone_reconnect_hint)
            else -> getString(R.string.phone_pair_short_hint)
        }
        connectButton.text = getString(when {
            pairedCredentials == null -> R.string.phone_open_tv_setup
            tvConnected -> R.string.phone_refresh_button
            else -> R.string.phone_reconnect_button
        })
    }

    private fun markDisconnected() {
        tvConnected = false
        updateConnectionUi()
        // Keep a previous snapshot visible but never let stale controls change the TV.
        setRemoteEnabled(dashboard != null)
    }

    private fun samePairing(saved: PhoneCredentials): Boolean =
        pairedCredentials?.token?.contentEquals(saved.token) == true

    private fun hasLocalNetworkAccess(): Boolean = Build.VERSION.SDK_INT < 37 ||
        checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun setProfileButtonsEnabled(enabled: Boolean) {
        for (i in 0 until profileList.childCount) {
            profileList.getChildAt(i).isEnabled = enabled
        }
    }

    private fun refreshMeasurementChoices() {
        if (!::targetButton.isInitialized) return
        val selectedTarget = targets[targetIndex].second
        targetButton.text = getString(R.string.phone_target_choice, selectedTarget)
        val selectedRoom = rooms[roomIndex]
        roomButton.text = if (selectedRoom.volumeM3 == null) {
            getString(R.string.phone_room_choice_unknown)
        } else {
            getString(R.string.phone_room_choice, selectedRoom.label)
        }
    }

    private fun setStatus(message: String, error: Boolean) {
        // Capture/provenance reports belong to Measure, not above daily controls.
        val destination = if (measurementInProgress || selectedTab == PhoneTab.MEASURE) measureFeedback else statusLine
        if (destination === measureFeedback) {
            statusLine.visibility = View.GONE
            statusLine.text = ""
        }
        destination.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
        // Recording percentages are visible, not a stream of TalkBack interruptions.
        destination.accessibilityLiveRegion = if (measurementInProgress) View.ACCESSIBILITY_LIVE_REGION_NONE
            else View.ACCESSIBILITY_LIVE_REGION_POLITE
        destination.text = message
        destination.setTextColor(color(if (error) R.color.cb_warning else R.color.cb_ink))
    }

    private fun onUi(block: () -> Unit) {
        runOnUiThread { if (!isFinishing && !isDestroyed) block() }
    }

    private fun captureSummary(capture: JSONObject, sampleRateHz: Int): String {
        val device = capture.optString("capture_device_model", "").takeIf { it.isNotBlank() && it != "null" }
            ?: getString(R.string.phone_android_device)
        val inputName = capture.optString("input_device_name", "").takeIf { it.isNotBlank() && it != "null" }
        val inputType = capture.optString("input_device_type", "").takeIf { it.isNotBlank() && it != "null" }
        val input = listOfNotNull(inputName, inputType).distinct().joinToString(" / ")
            .ifBlank { getString(R.string.phone_value_unreported) }
        val source = capture.optString("audio_source", "").takeIf { it.isNotBlank() && it != "null" }
            ?: getString(R.string.phone_value_unreported)
        val processingValues = capture.optJSONArray("processing")
        val processing = if (processingValues == null || processingValues.length() == 0) {
            getString(R.string.phone_processing_unreported)
        } else {
            buildList {
                for (index in 0 until minOf(processingValues.length(), 16)) {
                    processingValues.optString(index, "").trim().takeIf(String::isNotEmpty)?.let(::add)
                }
            }.joinToString("; ").ifBlank { getString(R.string.phone_processing_unreported) }
        }
        return getString(R.string.phone_capture_summary, device, input, sampleRateHz, source, processing)
    }

    private fun clientFor(credentials: PhoneCredentials) =
        CompanionClient(credentials.host, credentials.port, credentials.token)

    private fun requireOk(response: JSONObject): JSONObject {
        if (!response.optBoolean("ok", false)) {
            throw IllegalStateException(response.optString("error", "TV did not accept the command").ifBlank { "TV did not accept the command" })
        }
        return response
    }

    private fun encodePcm16Le(samples: ShortArray): String {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, sample ->
            val value = sample.toInt()
            bytes[index * 2] = (value and 0xff).toByte()
            bytes[index * 2 + 1] = ((value ushr 8) and 0xff).toByte()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun parsePrivateIpv4(raw: String): String? {
        val pieces = raw.trim().split('.')
        if (pieces.size != 4) return null
        val octets = IntArray(4)
        for (index in pieces.indices) {
            val piece = pieces[index]
            if (piece.isEmpty() || piece.length > 3 || piece.any { it !in '0'..'9' }) return null
            octets[index] = piece.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }
        val privateLan = octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
        if (!privateLan || octets[0] == 0 || octets[0] >= 224 || octets[3] == 0 || octets[3] == 255) return null
        return try {
            (InetAddress.getByAddress(ByteArray(4) { octets[it].toByte() }) as? Inet4Address)?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private fun readableError(error: Throwable): String =
        error.message?.trim()?.takeIf(String::isNotEmpty)?.take(180) ?: error.javaClass.simpleName

    private fun editField(hintText: String, type: Int): EditText = EditText(this).apply {
        hint = hintText
        inputType = type
        isSingleLine = true
        setTextColor(color(R.color.cb_ink))
        setHintTextColor(color(R.color.cb_slate))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        minimumHeight = dp(52)
        setBackgroundResource(R.drawable.bg_card)
        textSize = 16f
    }

    private fun label(textValue: String, size: Float, bold: Boolean, colorId: Int): TextView = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color(colorId))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun cardText(textValue: String): TextView = label(
        textValue, 14f, false, R.color.cb_ink
    ).apply {
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundResource(R.drawable.bg_card)
    }

    /**
     * A button on this screen.
     *
     * Every control here is built in code rather than inflated from a layout,
     * so nothing gives it the focusability an XML `<Button>` has by default.
     * That is why this screen is reachable from a D-pad remote (MainActivity
     * sends any non-leanback device straight here) and yet had no focus target:
     * the cursor had nowhere to land and nothing to move between. Focusable,
     * clickable and focusable in touch mode keeps the initial focus available
     * even on a phone-style TV box. The selectors carry the focused ring.
     */
    private fun actionButton(textValue: String, primary: Boolean): Button = Button(this).apply {
        text = textValue
        isAllCaps = false
        textSize = 15f
        setTextColor(ContextCompat.getColorStateList(this@PhoneCompanionActivity, if (primary) R.color.cta_text else R.color.ghost_text))
        setBackgroundResource(if (primary) R.drawable.bg_cta else R.drawable.bg_ghost)
        minimumHeight = dp(52)
        minimumWidth = 0
        stateListAnimator = null // Touch controls must not scale or shift their neighbours.
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
    }

    /**
     * Put the cursor somewhere the moment the screen appears.
     *
     * A D-pad (or a TV remote, or a keyboard) only navigates from a focused
     * view; with nothing focused the first press is swallowed while Android
     * looks for a target. Called after the content is built and again when the
     * remote panel appears, so the paired screen also starts on a real control.
     */
    private fun focusFirstControl() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) && contentScroll.isInTouchMode) return
        // isShown includes the tab and parent visibility; a control hidden by its
        // page is never an opening focus target. Do not pop up the phone keyboard.
        val target = when (selectedTab) {
            PhoneTab.CONTROL -> if (correctionButton.isShown && correctionButton.isEnabled) correctionButton else connectButton
            PhoneTab.MEASURE -> if (measurementInProgress) cancelMeasureButton else targetButton
            PhoneTab.TV -> if (scanQrButton.isShown && scanQrButton.isEnabled) scanQrButton else findTvButton
        }
        if (target.isShown && target.isEnabled) target.requestFocus()
    }

    private fun column(padding: Int = 0): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (padding > 0) setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    private fun section(): LinearLayout = column(padding = 16).apply {
        setBackgroundResource(R.drawable.bg_section)
    }

    private fun heading(title: Int): TextView = label(getString(title), 20f, true, R.color.cb_ink).apply {
        isAccessibilityHeading = true
    }

    private fun eyebrow(title: Int): TextView = label(getString(title), 12f, true, R.color.cb_slate)

    private fun matchWrap(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (top > 0) topMargin = dp(top)
        }

    private fun weightedWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(3)
            marginStart = dp(3)
        }

    private fun color(id: Int): Int = ContextCompat.getColor(this, id)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQ_LOCAL_NETWORK = 731
        private const val REQ_MICROPHONE = 732
        private const val FIND_TV_TIMEOUT_MS = 8_000L
        private const val REFRESH_INTERVAL_MS = 10_000L
        private const val KEY_TAB = "phone_tab"
        private const val PROGRESS_MAX = 1000
        private const val PREFS = "core_eq_phone_measurement"
        private const val KEY_TARGET = "target_index"
        private const val KEY_ROOM = "room_index"
        private const val KEY_REPEAT = "repeat_measurement_check"
    }
}
