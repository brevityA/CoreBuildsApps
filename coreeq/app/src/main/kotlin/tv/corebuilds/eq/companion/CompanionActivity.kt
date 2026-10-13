package tv.corebuilds.eq.companion

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tv.corebuilds.eq.R
import tv.corebuilds.eq.TvActivity
import tv.corebuilds.eq.ui.QrBitmap
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.concurrent.thread

/** A user-visible, opt-in TV screen for pairing an Android phone with Core EQ. */
class CompanionActivity : TvActivity() {

    private lateinit var addressLine: TextView
    private lateinit var portLine: TextView
    private lateinit var pairingCode: TextView
    private lateinit var pairingQr: ImageView
    private var listenPort = 0
    private lateinit var pairingExpiry: TextView
    private lateinit var statusLine: TextView
    private lateinit var pairedLine: TextView
    private lateinit var startPairing: Button
    private lateinit var stopPairing: Button
    private lateinit var revokePairing: Button
    private var server: CompanionServer? = null
    /** Advertises the open listener over mDNS so the phone can find it; stopped with the server. */
    private val advertiser by lazy { TvDiscovery.Advertiser(this) }
    private var activeCode: String? = null
    private var expiresAt = 0L
    private var screenVisible = false
    private var permissionRequestInFlight = false
    private var permissionDenied = false

    private val handler = Handler(Looper.getMainLooper())
    private val expiryTicker = object : Runnable {
        override fun run() {
            val remaining = expiresAt - SystemClock.elapsedRealtime()
            if (activeCode != null && remaining > 0L) {
                pairingExpiry.text = "Pairing code expires in ${((remaining + 999) / 1000)} seconds."
                handler.postDelayed(this, 1_000L)
            } else if (activeCode != null) {
                activeCode = null
                hidePairingQr()
                pairingCode.text = getString(R.string.companion_pairing_closed)
                pairingExpiry.text = getString(R.string.companion_pairing_expired)
                stopPairing.isEnabled = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        startPairing.setOnClickListener { openPairing() }
        stopPairing.setOnClickListener { closePairing() }
        revokePairing.setOnClickListener {
            server?.revokePairing()
            refreshPairedState()
        }
    }

    override fun onStart() {
        super.onStart()
        screenVisible = true
        startServerWhenPermitted()
    }

    override fun onStop() {
        screenVisible = false
        handler.removeCallbacks(expiryTicker)
        activeCode = null
        expiresAt = 0L
        hidePairingQr()
        pairingCode.setText(R.string.companion_pairing_closed)
        pairingExpiry.setText(R.string.companion_pairing_stopped)
        stopPairing.isEnabled = false
        closeVisibleServer()
        super.onStop()
    }

    private fun closeVisibleServer() {
        advertiser.stop()
        val closingServer = server ?: return
        server = null
        closingServer.stopListening()
        // stopListening closes the listener and accepted sockets immediately;
        // finish measurement/player cleanup away from the UI thread.
        thread(name = "CoreEqCompanionClose") { closingServer.close() }
    }

    private fun buildContent(): ScrollView {
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(24), dp(36), dp(24))
            background = getDrawable(R.color.cb_night)
        }
        scroll.addView(column)
        column.addView(label(getString(R.string.companion_tv_title), 26f, true, R.color.cb_ink))
        column.addView(label(getString(R.string.companion_tv_subtitle), 16f, false, R.color.cb_slate))
        addressLine = label(getString(R.string.companion_finding_address), 18f, true, R.color.cb_ink).apply {
            setTextIsSelectable(true)
        }
        column.addView(addressLine)
        portLine = label("", 18f, true, R.color.cb_ink).apply {
            setTextIsSelectable(true)
        }
        column.addView(portLine)
        column.addView(label(getString(R.string.companion_tv_pair_instructions), 16f, false, R.color.cb_slate))
        pairingCode = label(getString(R.string.companion_pairing_closed), 24f, true, R.color.cb_signal_cyan).apply {
            gravity = Gravity.CENTER
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundResource(R.drawable.bg_card)
        }
        column.addView(pairingCode, matchWrap(top = 8))
        pairingQr = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = getString(R.string.companion_tv_qr_accessibility)
        }
        column.addView(pairingQr, LinearLayout.LayoutParams(dp(260), dp(260)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(10)
        })
        pairingExpiry = label(getString(R.string.companion_pairing_stopped), 14f, false, R.color.cb_slate)
        column.addView(pairingExpiry)
        startPairing = actionButton(getString(R.string.companion_start_pairing), primary = true)
        stopPairing = actionButton(getString(R.string.companion_stop_pairing), primary = false).apply { isEnabled = false }
        revokePairing = actionButton(getString(R.string.companion_revoke_pairing), primary = false)
        column.addView(startPairing, matchWrap(top = 12))
        column.addView(stopPairing, matchWrap(top = 6))
        column.addView(revokePairing, matchWrap(top = 6))
        pairedLine = label(getString(R.string.companion_not_paired), 14f, false, R.color.cb_slate)
        column.addView(pairedLine)
        statusLine = label(getString(R.string.companion_starting), 14f, false, R.color.cb_slate)
        column.addView(statusLine)
        val close = actionButton(getString(R.string.companion_done), primary = false)
        close.setOnClickListener { finish() }
        column.addView(close, matchWrap(top = 12))
        return scroll
    }

    private fun startServerWhenPermitted() {
        if (!screenVisible || server != null) return
        if (Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            statusLine.setText(
                if (permissionDenied) R.string.companion_local_network_denied
                else R.string.companion_local_network_permission
            )
            if (!permissionRequestInFlight && !permissionDenied) {
                permissionRequestInFlight = true
                requestPermissions(arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK), REQ_LOCAL_NETWORK)
            }
            return
        }
        permissionRequestInFlight = false
        permissionDenied = false
        var candidateServer: CompanionServer? = null
        try {
            val runningServer = CompanionServer(this) { message ->
                if (screenVisible && server === candidateServer && !isFinishing && !isDestroyed) {
                    statusLine.text = message
                    refreshPairedState()
                    if (runningServerPaired()) {
                        activeCode = null
                        expiresAt = 0L
                        handler.removeCallbacks(expiryTicker)
                        hidePairingQr()
                        pairingCode.setText(R.string.companion_pairing_closed)
                        pairingExpiry.setText(R.string.companion_pairing_stopped)
                        stopPairing.isEnabled = false
                    }
                }
            }
            candidateServer = runningServer
            val port = runningServer.start()
            listenPort = port
            server = runningServer
            addressLine.text = getString(R.string.companion_tv_address, localIpv4Addresses().ifEmpty { "not reported" })
            portLine.text = getString(R.string.companion_tv_port, port)
            advertiser.start(port)
            startPairing.isEnabled = true
            refreshPairedState()
            statusLine.text = getString(R.string.companion_waiting_phone)
        } catch (e: Exception) {
            candidateServer?.close()
            statusLine.text = getString(R.string.companion_server_failed, e.message ?: e.javaClass.simpleName)
            startPairing.isEnabled = false
        }
    }

    private fun openPairing() {
        val activeServer = server ?: run {
            startServerWhenPermitted()
            return
        }
        try {
            activeCode = activeServer.startPairing()
            expiresAt = SystemClock.elapsedRealtime() + PAIR_WINDOW_MS
            pairingCode.text = activeCode!!.chunked(4).joinToString(" ")
            showPairingQr(activeCode!!)
            pairingExpiry.text = getString(R.string.companion_pairing_expiry, PAIR_WINDOW_SECONDS)
            stopPairing.isEnabled = true
            handler.removeCallbacks(expiryTicker)
            handler.post(expiryTicker)
            statusLine.setText(R.string.companion_pairing_active)
        } catch (e: Exception) {
            statusLine.text = e.message ?: getString(R.string.companion_pairing_failed)
        }
    }

    private fun closePairing() {
        server?.stopPairing()
        activeCode = null
        expiresAt = 0L
        handler.removeCallbacks(expiryTicker)
        hidePairingQr()
        pairingCode.setText(R.string.companion_pairing_closed)
        pairingExpiry.setText(R.string.companion_pairing_stopped)
        stopPairing.isEnabled = false
        refreshPairedState()
    }

    private fun runningServerPaired(): Boolean = server?.isPaired() == true

    private fun refreshPairedState() {
        val name = server?.pairedClientName()
        pairedLine.text = if (name.isNullOrBlank()) {
            getString(R.string.companion_not_paired)
        } else {
            getString(R.string.companion_paired_to, name)
        }
        revokePairing.isEnabled = server?.isPaired() == true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCAL_NETWORK) return
        permissionRequestInFlight = false
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            permissionDenied = false
            startServerWhenPermitted()
        } else {
            permissionDenied = true
            statusLine.setText(R.string.companion_local_network_denied)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(expiryTicker)
        closeVisibleServer()
        super.onDestroy()
    }

    /** Private IPv4 addresses for the QR, in the order Android lists them. */
    private fun lanIpv4List(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { address ->
                val bytes = address.address.map { it.toInt() and 0xff }
                bytes.size == 4 && (
                    bytes[0] == 10 ||
                    (bytes[0] == 172 && bytes[1] in 16..31) ||
                    (bytes[0] == 192 && bytes[1] == 168)
                )
            }
            .mapNotNull { it.hostAddress }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * The QR carries the same one-time key as the typed code, plus the address
     * and port. It is shown only while pairing is open and is removed with it.
     * The phone treats it as a pre-filled form: pairing still goes through the
     * encrypted handshake, and a wrong address simply fails.
     */
    private fun showPairingQr(code: String) {
        val hosts = lanIpv4List()
        if (hosts.isEmpty() || listenPort == 0) {
            pairingQr.visibility = View.GONE
            return
        }
        val text = TvPairingQr.build(hosts, listenPort, code)
        pairingQr.setImageBitmap(QrBitmap.render(text, 520))
        pairingQr.visibility = View.VISIBLE
    }

    private fun hidePairingQr() {
        pairingQr.setImageDrawable(null)
        pairingQr.visibility = View.GONE
    }

    private fun localIpv4Addresses(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { address ->
                val bytes = address.address.map { it.toInt() and 0xff }
                bytes.size == 4 && (
                    bytes[0] == 10 ||
                    (bytes[0] == 172 && bytes[1] in 16..31) ||
                    (bytes[0] == 192 && bytes[1] == 168)
                )
            }
            .map { it.hostAddress ?: "" }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
            .joinToString(" · ")
    } catch (_: Exception) {
        ""
    }

    private fun label(textValue: String, size: Float, bold: Boolean, color: Int): TextView = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(getColor(color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun actionButton(textValue: String, primary: Boolean): Button = Button(this).apply {
        text = textValue
        isAllCaps = false
        setBackgroundResource(if (primary) R.drawable.bg_cta else R.drawable.bg_ghost)
        setTextColor(getColor(if (primary) R.color.cta_text else R.color.ghost_text))
        minHeight = dp(52)
        isFocusable = true
    }

    private fun matchWrap(top: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_LOCAL_NETWORK = 71
        private const val PAIR_WINDOW_SECONDS = 120
        private const val PAIR_WINDOW_MS = 120_000L
    }
}
