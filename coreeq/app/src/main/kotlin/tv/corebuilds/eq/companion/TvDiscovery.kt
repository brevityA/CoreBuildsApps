package tv.corebuilds.eq.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet4Address

/**
 * Local-network discovery for the phone companion, over mDNS (`_coreeq._tcp`).
 *
 * The TV advertises only what a phone needs to find the listener: a display
 * name and the port. It never advertises the pairing code, the token or any
 * key material, and a discovered address is only a hint: pairing still needs
 * the one-time code shown on the TV and the encrypted handshake.
 */
internal object TvDiscovery {
    const val SERVICE_TYPE = "_coreeq._tcp."
    private const val TAG = "CoreEqDiscovery"

    /** A TV found on the LAN. [host] is an IPv4 address string. */
    data class FoundTv(val name: String, val host: String, val port: Int)

    /** Short, sanitised display name; mDNS names are limited to 63 bytes. */
    internal fun displayName(model: String?): String {
        // ASCII only: keeps the label simple for every mDNS responder and well inside 63 bytes.
        val cleaned = (model ?: "")
            .filter { it.code < 128 && (it.isLetterOrDigit() || it == ' ' || it == '-') }
            .trim()
            .take(40)
        return if (cleaned.isEmpty()) "Core EQ TV" else "Core EQ - $cleaned"
    }

    /** Advertises the running companion listener on the LAN while it is open. */
    class Advertiser(context: Context) {
        private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        private var listener: NsdManager.RegistrationListener? = null

        fun start(port: Int) {
            val manager = nsd ?: return
            if (listener != null) return
            val info = NsdServiceInfo().apply {
                serviceName = displayName(Build.MODEL)
                serviceType = SERVICE_TYPE
                setPort(port)
            }
            val registration = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "advertising as ${serviceInfo.serviceName}")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "advertise failed: $errorCode")
                    listener = null
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            }
            listener = registration
            try {
                manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
            } catch (e: IllegalArgumentException) {
                listener = null
                Log.w(TAG, "advertise rejected", e)
            }
        }

        fun stop() {
            val registration = listener ?: return
            listener = null
            try {
                nsd?.unregisterService(registration)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "advertise already stopped", e)
            }
        }
    }

    /**
     * One-shot search from the phone. Calls [onFound] on the main thread for each
     * resolved IPv4 TV, then [onDone] once when discovery is stopped or times out.
     */
    class Search(
        context: Context,
        private val timeoutMs: Long,
        private val onFound: (FoundTv) -> Unit,
        private val onDone: (Boolean) -> Unit
    ) {
        private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        private val main = Handler(Looper.getMainLooper())
        private var discovery: NsdManager.DiscoveryListener? = null
        private var finished = false
        private var foundAny = false
        private val timeout = Runnable { finish() }

        fun start() {
            val manager = nsd
            if (manager == null) {
                finished = true
                onDone(false)
                return
            }
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "discovery start failed: $errorCode")
                    main.post { finish() }
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolve(manager, serviceInfo)
                }
            }
            discovery = listener
            main.postDelayed(timeout, timeoutMs)
            try {
                manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "discovery rejected", e)
                finish()
            }
        }

        fun cancel() = finish()

        @Suppress("DEPRECATION") // resolveService is the only resolver on minSdk 30 devices.
        private fun resolve(manager: NsdManager, serviceInfo: NsdServiceInfo) {
            try {
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "resolve failed: $errorCode")
                    }
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host as? Inet4Address ?: return
                        val found = FoundTv(
                            name = resolved.serviceName.orEmpty(),
                            host = host.hostAddress ?: return,
                            port = resolved.port
                        )
                        main.post {
                            if (finished) return@post
                            foundAny = true
                            onFound(found)
                            finish()
                        }
                    }
                })
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "resolve rejected", e)
            }
        }

        private fun finish() {
            if (finished) return
            finished = true
            main.removeCallbacks(timeout)
            discovery?.let { listener ->
                try {
                    nsd?.stopServiceDiscovery(listener)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "discovery already stopped", e)
                }
            }
            discovery = null
            onDone(foundAny)
        }
    }
}
