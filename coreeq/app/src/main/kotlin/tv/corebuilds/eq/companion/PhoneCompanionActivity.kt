package tv.corebuilds.eq.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

    private data class RemoteProfile(val id: String, val name: String, val active: Boolean, val manualOnly: Boolean)
    private data class Dashboard(
        val deviceName: String,
        val version: String,
        val output: String,
        val mode: String,
        val correctionEnabled: Boolean,
        val profileName: String,
        val serviceStatus: String,
        val serviceError: Boolean,
        val profiles: List<RemoteProfile>
    )
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
    private var tvSearch: TvDiscovery.Search? = null
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

    private val targets = listOf(
        "dialogue" to "Dialogue",
        "room" to "Room",
        "bk" to "B&K",
        "flat" to "Flat",
        "olive" to "Olive",
        "house" to "Harman"
    )
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
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        targetIndex = prefs.getInt(KEY_TARGET, 0).coerceIn(targets.indices)
        roomIndex = prefs.getInt(KEY_ROOM, 0).coerceIn(rooms.indices)
        setContentView(buildContent())

        credentialStore.load()?.let { saved ->
            hostField.setText(saved.host)
            portField.setText(saved.port.toString())
            remotePanel.visibility = View.VISIBLE
            connectionLine.setText(R.string.phone_saved_not_connected)
            setRemoteEnabled(true)
            withLocalNetwork { refreshDashboard() }
        } ?: run {
            remotePanel.visibility = View.GONE
            connectionLine.setText(R.string.phone_not_paired)
            setRemoteEnabled(false)
            // Nothing is saved yet, so look for the TV straight away.
            withLocalNetwork { startTvSearch() }
        }
        refreshMeasurementChoices()
    }

    private fun buildContent(): ScrollView {
        // targetSdk 37 draws edge-to-edge, so the scroll view keeps its background under the
        // status and navigation bars and pads its content clear of them. The bottom inset is
        // the keyboard when it is taller than the navigation bar, so the focused field and the
        // pair button stay on screen while typing.
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
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
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }
        scroll.addView(column)
        column.addView(label(getString(R.string.phone_companion_title), 25f, true, R.color.cb_ink))
        column.addView(label(getString(R.string.phone_companion_subtitle), 15f, false, R.color.cb_slate))
        column.addView(cardText(getString(R.string.phone_capture_warning)))

        val pairHeading = label(getString(R.string.phone_pair_heading), 19f, true, R.color.cb_ink)
        column.addView(pairHeading, matchWrap(top = 16))
        findTvButton = actionButton(getString(R.string.phone_find_tv_button), primary = false).apply {
            setOnClickListener { withLocalNetwork { startTvSearch() } }
        }
        column.addView(findTvButton, matchWrap(top = 6))
        scanQrButton = actionButton(getString(R.string.phone_scan_qr_button), primary = true).apply {
            setOnClickListener { scanTvQr() }
        }
        column.addView(scanQrButton, matchWrap(top = 6))
        discoveryLine = label("", 14f, false, R.color.cb_slate)
        column.addView(discoveryLine, matchWrap(top = 4))
        hostField = editField(getString(R.string.phone_tv_ip_hint), InputType.TYPE_CLASS_PHONE).apply {
            contentDescription = getString(R.string.phone_tv_ip_accessibility)
        }
        portField = editField(getString(R.string.phone_tv_port_hint), InputType.TYPE_CLASS_NUMBER).apply {
            contentDescription = getString(R.string.phone_tv_port_accessibility)
        }
        codeField = editField(getString(R.string.phone_pair_code_hint), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply {
            filters = arrayOf(InputFilter.LengthFilter(64))
            isSingleLine = true
            contentDescription = getString(R.string.phone_pair_code_accessibility)
        }
        column.addView(hostField, matchWrap(top = 6))
        column.addView(portField, matchWrap(top = 6))
        column.addView(codeField, matchWrap(top = 6))
        pairButton = actionButton(getString(R.string.phone_pair_button), primary = true)
        connectButton = actionButton(getString(R.string.phone_connect_button), primary = false)
        column.addView(pairButton, matchWrap(top = 8))
        column.addView(connectButton, matchWrap(top = 6))
        pairButton.setOnClickListener { withLocalNetwork { pairWithTv() } }
        connectButton.setOnClickListener { withLocalNetwork { refreshDashboard() } }

        connectionLine = label(getString(R.string.phone_not_paired), 15f, true, R.color.cb_signal_cyan)
        column.addView(connectionLine, matchWrap(top = 12))
        detailLine = label("", 14f, false, R.color.cb_slate)
        column.addView(detailLine, matchWrap(top = 5))
        statusLine = cardText(getString(R.string.phone_measure_ready))
        column.addView(statusLine, matchWrap(top = 8))

        remotePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        column.addView(remotePanel, matchWrap(top = 14))
        buildRemotePanel(remotePanel)
        return scroll
    }

    private fun buildRemotePanel(parent: LinearLayout) {
        parent.addView(label(getString(R.string.phone_remote_heading), 20f, true, R.color.cb_ink))
        correctionButton = actionButton(getString(R.string.phone_correction_off), primary = true)
        correctionButton.setOnClickListener {
            val current = dashboard ?: return@setOnClickListener
            runControl(JSONObject().put("op", "set_enabled").put("enabled", !current.correctionEnabled))
        }
        parent.addView(correctionButton, matchWrap(top = 8))

        parent.addView(label(getString(R.string.phone_profiles_heading), 16f, true, R.color.cb_ink), matchWrap(top = 10))
        profileList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(profileList, matchWrap(top = 4))

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        parent.addView(label(getString(R.string.phone_mode_heading), 16f, true, R.color.cb_ink), matchWrap(top = 10))
        listOf(
            "movie_tv" to R.string.phone_mode_movie,
            "everyday" to R.string.phone_mode_everyday,
            "gaming" to R.string.phone_mode_gaming
        ).forEach { (mode, titleRes) ->
            val button = actionButton(getString(titleRes), primary = false).apply {
                // Three equal columns on a narrow phone: let long labels wrap instead of clipping.
                minimumWidth = 0
                maxLines = 2
                setPadding(dp(6), dp(8), dp(6), dp(8))
                setOnClickListener { runControl(JSONObject().put("op", "set_mode").put("mode", mode)) }
            }
            modeRow.addView(button, weightedWrap())
        }
        parent.addView(modeRow, matchWrap(top = 5))

        parent.addView(label(getString(R.string.phone_measure_heading), 19f, true, R.color.cb_ink), matchWrap(top = 16))
        parent.addView(label(getString(R.string.phone_measure_explanation), 14f, false, R.color.cb_slate), matchWrap(top = 4))
        targetButton = actionButton("", primary = false)
        roomButton = actionButton("", primary = false)
        parent.addView(targetButton, matchWrap(top = 6))
        parent.addView(roomButton, matchWrap(top = 6))
        repeatCheck = CheckBox(this).apply {
            text = getString(R.string.phone_repeat_option)
            setTextColor(color(R.color.cb_ink))
            buttonTintList = android.content.res.ColorStateList.valueOf(color(R.color.cb_signal_cyan))
            setPadding(0, dp(4), 0, dp(4))
            isChecked = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_REPEAT, false)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_REPEAT, checked).apply()
            }
        }
        parent.addView(repeatCheck, matchWrap(top = 8))
        parent.addView(cardText(getString(R.string.phone_repeat_explanation)), matchWrap(top = 2))
        targetButton.setOnClickListener {
            targetIndex = (targetIndex + 1) % targets.size
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_TARGET, targetIndex).apply()
            refreshMeasurementChoices()
        }
        roomButton.setOnClickListener {
            roomIndex = (roomIndex + 1) % rooms.size
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_ROOM, roomIndex).apply()
            refreshMeasurementChoices()
        }
        measureButton = actionButton(getString(R.string.phone_measure_button), primary = true)
        measureButton.setOnClickListener {
            withLocalNetwork {
                if (repeatFallbackSessionId != null) retrySaveFirstCapture() else requestMicrophoneThenMeasure()
            }
        }
        parent.addView(measureButton, matchWrap(top = 8))
        measureProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = PROGRESS_MAX
            progress = 0
            visibility = View.GONE
            contentDescription = getString(R.string.phone_measure_progress_accessibility)
        }
        parent.addView(measureProgress, matchWrap(top = 8))
        cancelMeasureButton = actionButton(getString(R.string.phone_cancel_measure), primary = false).apply {
            visibility = View.GONE
            setOnClickListener { cancelCapture(sendRemote = true) }
        }
        parent.addView(cancelMeasureButton, matchWrap(top = 5))

        revokeButton = actionButton(getString(R.string.phone_unpair_button), primary = false).apply {
            setOnClickListener { withLocalNetwork { revokePairing() } }
        }
        forgetButton = actionButton(getString(R.string.phone_forget_button), primary = false).apply {
            setOnClickListener { forgetLocally() }
        }
        parent.addView(revokeButton, matchWrap(top = 18))
        parent.addView(forgetButton, matchWrap(top = 5))
        parent.addView(label(getString(R.string.phone_forget_note), 12f, false, R.color.cb_slate), matchWrap(top = 3))
    }

    private fun pairWithTv() {
        if (operationInProgress || measurementInProgress || repeatFallbackSessionId != null) return
        val host = parsePrivateIpv4(hostField.text?.toString().orEmpty())
        if (host == null) {
            setStatus(getString(R.string.phone_invalid_tv_ip), error = true)
            hostField.requestFocus()
            return
        }
        val port = portField.text?.toString()?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }
        if (port == null) {
            setStatus(getString(R.string.phone_invalid_tv_port), error = true)
            portField.requestFocus()
            return
        }
        val pairingKey = CompanionCrypto.decodeKey(codeField.text?.toString().orEmpty(), CompanionCrypto.PAIRING_KEY_BYTES)
        if (pairingKey == null) {
            setStatus(getString(R.string.phone_invalid_pair_code), error = true)
            codeField.requestFocus()
            return
        }
        // The user is pairing with these fields now; a search still running must not
        // replace the address underneath them.
        stopTvSearch()
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
                runOnUiThread {
                    setOperationBusy(false)
                    val next = fallbackHosts.firstOrNull()
                    if (unreachable && next != null) {
                        // The first address did not answer; try the next one from the QR.
                        fallbackHosts = fallbackHosts.drop(1)
                        hostField.setText(next)
                        setStatus(getString(R.string.phone_trying_next_address, next), error = false)
                        pairWithTv()
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
                runOnUiThread {
                    setOperationBusy(false)
                    fallbackHosts = emptyList()
                    codeField.text?.clear()
                    remotePanel.visibility = View.VISIBLE
                    connectionLine.setText(R.string.phone_saved_not_connected)
                    setRemoteEnabled(true)
                    setStatus(getString(R.string.phone_paired_not_loaded, readableError(e)), error = true)
                }
                return@thread
            }
            runOnUiThread {
                setOperationBusy(false)
                fallbackHosts = emptyList()
                codeField.text?.clear()
                remotePanel.visibility = View.VISIBLE
                setStatus(getString(R.string.phone_pair_success), error = false)
                showDashboard(newDashboard)
                setRemoteEnabled(true)
            }
        }
    }

    private fun refreshDashboard() {
        if (operationInProgress || measurementInProgress || repeatFallbackSessionId != null) return
        val credentials = credentialStore.load()
        if (credentials == null) {
            remotePanel.visibility = View.GONE
            connectionLine.setText(R.string.phone_not_paired)
            detailLine.text = ""
            setRemoteEnabled(false)
            setStatus(getString(R.string.phone_pair_first), error = false)
            return
        }
        hostField.setText(credentials.host)
        portField.setText(credentials.port.toString())
        remotePanel.visibility = View.VISIBLE
        setOperationBusy(true)
        setStatus(getString(R.string.phone_connecting), error = false)
        thread(name = "CoreEqPhoneStatus") {
            try {
                val newDashboard = loadDashboard(credentials)
                runOnUiThread {
                    setOperationBusy(false)
                    showDashboard(newDashboard)
                    setRemoteEnabled(true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setOperationBusy(false)
                    connectionLine.setText(R.string.phone_saved_not_connected)
                    setRemoteEnabled(true)
                    setStatus(getString(R.string.phone_connect_failed, readableError(e)), error = true)
                    rediscoverSavedTv(credentials)
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
        tvSearch?.cancel()
        setStatus(getString(R.string.phone_rediscovering), error = false)
        val candidates = ArrayDeque<TvDiscovery.FoundTv>()
        val seen = hashSetOf("${saved.host}:${saved.port}")
        var trying = false
        var tried = false
        var searching = true
        var connected = false
        lateinit var search: TvDiscovery.Search
        fun tryNext() {
            if (trying || connected) return
            val next = candidates.removeFirstOrNull()
            if (next == null) {
                // Nothing left to try. A failed attempt already left its own message.
                if (!searching && !tried) setStatus(getString(R.string.phone_rediscover_none), error = true)
                return
            }
            trying = true
            tried = true
            retryWithFoundTv(saved, next.host, next.port) { ok ->
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
                if (seen.add("${found.host}:${found.port}")) {
                    candidates.addLast(found)
                    tryNext()
                }
            },
            onDone = { _ ->
                searching = false
                if (tvSearch === search) tvSearch = null
                // onStop/onDestroy cancel the search; leaving the screen is not a cue to
                // start another connection attempt.
                if (!isFinishing && !isDestroyed) tryNext()
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
        onResult: (Boolean) -> Unit
    ) {
        setOperationBusy(true)
        setStatus(getString(R.string.phone_connecting), error = false)
        thread(name = "CoreEqPhoneRediscover") {
            try {
                val creds = PhoneCredentials(host, port, saved.token)
                val newDashboard = loadDashboard(creds)
                credentialStore.save(creds)
                runOnUiThread {
                    setOperationBusy(false)
                    hostField.setText(host)
                    portField.setText(port.toString())
                    showDashboard(newDashboard)
                    setRemoteEnabled(true)
                    setStatus(getString(R.string.phone_rediscover_ok), error = false)
                    onResult(true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setOperationBusy(false)
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
        return Dashboard(
            deviceName = status.optString("device_name", "Android TV").take(100),
            version = status.optString("app_version", "?").take(30),
            output = outputName,
            mode = status.optString("mode", "everyday").take(30),
            correctionEnabled = status.optBoolean("correction_enabled", false),
            profileName = status.optString("profile_name", "").takeIf { it.isNotBlank() && it != "null" }
                ?: getString(R.string.phone_no_active_profile),
            serviceStatus = status.optString("service_status", "").take(180),
            serviceError = status.optBoolean("service_error", false),
            profiles = profiles
        )
    }

    private fun showDashboard(value: Dashboard) {
        dashboard = value
        connectionLine.text = getString(R.string.phone_connected, value.deviceName, value.version)
        val modeTitle = when (value.mode) {
            "movie_tv" -> getString(R.string.phone_mode_movie)
            "gaming" -> getString(R.string.phone_mode_gaming)
            else -> getString(R.string.phone_mode_everyday)
        }
        val onOff = getString(if (value.correctionEnabled) R.string.phone_state_on else R.string.phone_state_off)
        detailLine.text = getString(
            R.string.phone_dashboard_details,
            value.output,
            modeTitle,
            onOff,
            value.profileName,
            value.serviceStatus.ifBlank { getString(R.string.phone_status_unknown) }
        )
        detailLine.setTextColor(color(if (value.serviceError) R.color.cb_warning else R.color.cb_slate))
        correctionButton.text = getString(
            if (value.correctionEnabled) R.string.phone_correction_on else R.string.phone_correction_off
        )
        correctionButton.isEnabled = !operationInProgress && !measurementInProgress
        profileList.removeAllViews()
        if (value.profiles.isEmpty()) {
            profileList.addView(label(getString(R.string.phone_no_profiles), 14f, false, R.color.cb_slate))
        } else {
            value.profiles.forEach { profile ->
                val title = when {
                    profile.active && profile.manualOnly -> getString(R.string.phone_profile_active_manual, profile.name)
                    profile.active -> getString(R.string.phone_profile_active, profile.name)
                    profile.manualOnly -> getString(R.string.phone_profile_manual, profile.name)
                    else -> profile.name
                }
                val button = actionButton(title, primary = profile.active).apply {
                    isEnabled = !operationInProgress && !measurementInProgress
                    setOnClickListener {
                        runControl(JSONObject().put("op", "activate_profile").put("id", profile.id))
                    }
                }
                profileList.addView(button, matchWrap(top = 4))
            }
        }
        setRemoteEnabled(true)
        refreshMeasurementChoices()
    }

    /**
     * Every remote action goes through the local-network check, as Find, Pair and Connect do.
     * Without it, a phone on Android 17 whose permission was denied timed out after 5 s with
     * "connect timed out" instead of saying what was wrong.
     */
    private fun runControl(request: JSONObject) = withLocalNetwork { runControlNow(request) }

    private fun runControlNow(request: JSONObject) {
        if (operationInProgress || measurementInProgress || repeatFallbackSessionId != null) return
        val credentials = credentialStore.load() ?: return
        setOperationBusy(true)
        setStatus(getString(R.string.phone_sending_command), error = false)
        thread(name = "CoreEqPhoneControl") {
            try {
                val response = requireOk(clientFor(credentials).rpc(request))
                val newDashboard = loadDashboard(credentials)
                runOnUiThread {
                    setOperationBusy(false)
                    showDashboard(newDashboard)
                    val message = response.optString("profile_name", "").takeIf { it.isNotBlank() }
                        ?: response.optString("title", "").takeIf { it.isNotBlank() }
                        ?: getString(R.string.phone_command_complete)
                    setStatus(message, error = false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setOperationBusy(false)
                    setStatus(getString(R.string.phone_command_failed, readableError(e)), error = true)
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
        if (measurementInProgress || operationInProgress || repeatFallbackSessionId != null) return
        val credentials = credentialStore.load()
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
                    onRecordingReady = { captureInfo ->
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
                        start
                    },
                    onProgress = { fraction ->
                        runOnUiThread {
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
                    runOnUiThread {
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
                    runOnUiThread {
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
                runOnUiThread {
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
                    runOnUiThread {
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
                    runOnUiThread {
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
            val credentials = credentialStore.load()
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
        val credentials = credentialStore.load()
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
                runOnUiThread {
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
                runOnUiThread {
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
        val credentials = credentialStore.load() ?: return
        setOperationBusy(true)
        setStatus(getString(R.string.phone_unpairing), error = false)
        thread(name = "CoreEqPhoneRevoke") {
            try {
                requireOk(clientFor(credentials).rpc(JSONObject().put("op", "revoke")))
                credentialStore.clear()
                runOnUiThread {
                    dashboard = null
                    remotePanel.visibility = View.GONE
                    detailLine.text = ""
                    connectionLine.setText(R.string.phone_not_paired)
                    setOperationBusy(false)
                    setRemoteEnabled(false)
                    setStatus(getString(R.string.phone_unpaired), error = false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setOperationBusy(false)
                    setStatus(getString(R.string.phone_unpair_failed, readableError(e)), error = true)
                }
            }
        }
    }

    private fun forgetLocally() {
        if (measurementInProgress || operationInProgress || repeatFallbackSessionId != null) return
        credentialStore.clear()
        dashboard = null
        remotePanel.visibility = View.GONE
        connectionLine.setText(R.string.phone_not_paired)
        detailLine.text = ""
        setRemoteEnabled(false)
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

    override fun onStop() {
        if (measurementInProgress && !isChangingConfigurations) cancelCapture(sendRemote = true)
        tvSearch?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        if (measurementInProgress) cancelCapture(sendRemote = true)
        tvSearch?.cancel()
        tvSearch = null
        super.onDestroy()
    }

    /**
     * Reads the QR on the TV's pairing screen. The QR carries the same one-time
     * key as the typed code, so scanning fills in the form and runs the same
     * encrypted pairing. Nothing is sent until the phone has the TV's key.
     */
    private fun scanTvQr() {
        if (operationInProgress || measurementInProgress || repeatFallbackSessionId != null) return
        scanQrButton.isEnabled = false
        val scanner = GmsBarcodeScanning.getClient(this)
        val installer = ModuleInstall.getClient(this)
        // The scanner is a Play services module that is not in the APK. On a phone that has
        // never used it, startScan() fails while the module downloads, which read as "not
        // available on this phone". Ask first, and download it with the user told why.
        installer.areModulesAvailable(scanner)
            .addOnSuccessListener { availability ->
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
        val request = ModuleInstallRequest.newBuilder()
            .addApi(scanner)
            .setListener(listener)
            .build()
        installer.installModules(request)
            .addOnSuccessListener { response ->
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
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                scanQrButton.isEnabled = true
                handleTvQr(barcode.rawValue)
            }
            .addOnCanceledListener { scanQrButton.isEnabled = true }
            .addOnFailureListener { scanUnavailable() }
    }

    private fun scanUnavailable() {
        scanQrButton.isEnabled = true
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
        if (operationInProgress || measurementInProgress) return
        tvSearch?.cancel()
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
                    discoveryLine.text = getString(R.string.phone_find_tv_woke, found.name, found.host)
                    codeField.requestFocus()
                    // Ask the TV to show its pairing code. A failed wake is harmless: the user
                    // can still type the code, or use Scan TV QR, from the TV's own screen.
                    thread(name = "CoreEqWake") {
                        runCatching { CompanionClient(found.host, found.port, ByteArray(0)).wake() }
                    }
                }
            },
            onDone = { foundAny ->
                // Runs synchronously when a previous search is cancelled, before the new one
                // is stored. stopTvSearch() lets go of it first, which reads as "stopped".
                val stopped = tvSearch !== search
                if (tvSearch === search) tvSearch = null
                findTvButton.isEnabled = true
                if (!foundAny) discoveryLine.text = if (stopped) "" else getString(R.string.phone_find_tv_none)
            }
        )
        tvSearch = search
        search.start()
    }

    /** Ends any running search on purpose (pairing has started), without a "not found". */
    private fun stopTvSearch() {
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
        val savePending = repeatFallbackSessionId != null
        val controlsEnabled = !active && !operationInProgress && !savePending
        measureButton.isEnabled = !active && !operationInProgress && dashboard != null
        targetButton.isEnabled = controlsEnabled
        roomButton.isEnabled = controlsEnabled
        repeatCheck.isEnabled = controlsEnabled
        cancelMeasureButton.visibility = if (active) View.VISIBLE else View.GONE
        measureProgress.visibility = if (active) View.VISIBLE else View.GONE
        correctionButton.isEnabled = controlsEnabled && dashboard != null
        pairButton.isEnabled = controlsEnabled
        connectButton.isEnabled = controlsEnabled
        revokeButton.isEnabled = controlsEnabled && credentialStore.load() != null
        forgetButton.isEnabled = controlsEnabled && credentialStore.load() != null
        setProfileButtonsEnabled(controlsEnabled)
    }

    private fun setOperationBusy(busy: Boolean) {
        operationInProgress = busy
        refreshMeasureButtonLabel()
        val controlsEnabled = !busy && !measurementInProgress && repeatFallbackSessionId == null
        pairButton.isEnabled = controlsEnabled
        connectButton.isEnabled = controlsEnabled
        correctionButton.isEnabled = controlsEnabled && dashboard != null
        revokeButton.isEnabled = controlsEnabled && credentialStore.load() != null
        forgetButton.isEnabled = controlsEnabled && credentialStore.load() != null
        targetButton.isEnabled = controlsEnabled
        roomButton.isEnabled = controlsEnabled
        repeatCheck.isEnabled = controlsEnabled
        measureButton.isEnabled = !busy && !measurementInProgress && dashboard != null
        setProfileButtonsEnabled(controlsEnabled)
    }

    private fun setRemoteEnabled(enabled: Boolean) {
        remotePanel.visibility = if (enabled) View.VISIBLE else View.GONE
        setOperationBusy(operationInProgress)
        if (!enabled) {
            pairButton.isEnabled = !operationInProgress
            connectButton.isEnabled = !operationInProgress
        }
    }

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
        statusLine.text = message
        statusLine.setTextColor(color(if (error) R.color.cb_warning else R.color.cb_ink))
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
        setPadding(dp(12), dp(6), dp(12), dp(6))
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

    private fun actionButton(textValue: String, primary: Boolean): Button = Button(this).apply {
        text = textValue
        isAllCaps = false
        textSize = 15f
        setTextColor(color(if (primary) R.color.cta_text else R.color.ghost_text))
        setBackgroundResource(if (primary) R.drawable.bg_cta else R.drawable.bg_ghost)
        minimumHeight = dp(48)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

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
        private const val PROGRESS_MAX = 1000
        private const val PREFS = "core_eq_phone_measurement"
        private const val KEY_TARGET = "target_index"
        private const val KEY_ROOM = "room_index"
        private const val KEY_REPEAT = "repeat_measurement_check"
    }
}
