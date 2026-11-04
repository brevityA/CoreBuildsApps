package tv.corebuilds.eq.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress

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

    /**
     * The first IPv4 address in [addresses]. A dual-stack LAN can resolve a TV to an IPv6
     * address first, and the companion link is IPv4 only, so the whole list is searched.
     */
    internal fun firstIpv4(addresses: List<InetAddress>): Inet4Address? =
        addresses.filterIsInstance<Inet4Address>().firstOrNull()

    /** Lookups refused because another was still running get this many more tries. */
    internal const val RESOLVE_RETRIES = 2
    internal const val RESOLVE_RETRY_DELAY_MS = 300L

    /**
     * Whether a refused lookup is worth another try: only "already active", which means an
     * earlier lookup (from a search started a moment ago) still holds the resolver.
     */
    internal fun shouldRetryResolve(errorCode: Int, triesSoFar: Int): Boolean =
        errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && triesSoFar < RESOLVE_RETRIES

    /** Advertises the running companion listener on the LAN while it is open. */
    class Advertiser(context: Context) {
        private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        // NSD calls back on its own thread, and the legacy registerService gives no ordering
        // between a registration's failure and a later stop()/start(), so every read and
        // write of the current registration goes through this lock.
        private val lock = Any()
        private var listener: NsdManager.RegistrationListener? = null

        fun start(listenPort: Int) {
            val manager = nsd ?: return
            // `listenPort`, not `port`: inside apply{} the receiver's own `port`
            // shadows the parameter, which is how the call came to be written
            // as setPort(port) in the first place.
            val info = NsdServiceInfo().apply {
                serviceName = displayName(Build.MODEL)
                serviceType = SERVICE_TYPE
                port = listenPort
            }
            val registration = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "advertising as ${serviceInfo.serviceName}")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "advertise failed: $errorCode")
                    // A late failure from a registration that stop() already let go of must
                    // not clear the one that replaced it, or the next stop() leaves that
                    // one advertising.
                    synchronized(lock) { if (listener === this) listener = null }
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            }
            synchronized(lock) {
                if (listener != null) return
                listener = registration
            }
            try {
                manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
            } catch (e: IllegalArgumentException) {
                synchronized(lock) { if (listener === registration) listener = null }
                Log.w(TAG, "advertise rejected", e)
            }
        }

        fun stop() {
            val registration = synchronized(lock) { listener.also { listener = null } } ?: return
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
     * With [firstOnly] (the default) the search stops at the first TV; reconnecting
     * passes false so it can try every TV that answers until one accepts the token.
     */
    class Search(
        context: Context,
        private val timeoutMs: Long,
        private val firstOnly: Boolean = true,
        private val onFound: (FoundTv) -> Unit,
        private val onDone: (Boolean) -> Unit
    ) {
        private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        private val main = Handler(Looper.getMainLooper())
        private var discovery: NsdManager.DiscoveryListener? = null
        private var finished = false
        private var foundAny = false
        private val timeout = Runnable { finish() }
        // Main thread only. Older NSD stacks refuse a second resolve while one is in flight
        // (FAILURE_ALREADY_ACTIVE), so services found together are resolved one at a time.
        private val pending = ArrayDeque<NsdServiceInfo>()
        private var resolving = false
        /** The lookup in flight, so a finished search can stop it (API 34+). */
        private var activeResolve: NsdManager.ResolveListener? = null
        /** Retries spent per service name, for [shouldRetryResolve]. */
        private val retries = HashMap<String, Int>()

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
                    main.post {
                        if (finished) return@post
                        pending.addLast(serviceInfo)
                        resolveNext(manager)
                    }
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
        private fun resolveNext(manager: NsdManager) {
            if (resolving || finished) return
            val serviceInfo = pending.removeFirstOrNull() ?: return
            resolving = true
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve failed: $errorCode")
                    main.post {
                        resolving = false
                        activeResolve = null
                        if (finished) return@post
                        // A search started right after another can find the resolver still
                        // busy with the old one; dropping the TV then read as "not found".
                        val name = serviceInfo.serviceName.orEmpty()
                        val tries = retries[name] ?: 0
                        if (shouldRetryResolve(errorCode, tries)) {
                            retries[name] = tries + 1
                            pending.addFirst(serviceInfo)
                            main.postDelayed({ resolveNext(manager) }, RESOLVE_RETRY_DELAY_MS)
                        } else {
                            resolveNext(manager)
                        }
                    }
                }
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = ipv4Of(resolved)
                    val found = host?.let {
                        FoundTv(name = resolved.serviceName.orEmpty(), host = it, port = resolved.port)
                    }
                    main.post {
                        resolving = false
                        activeResolve = null
                        if (finished) return@post
                        if (found != null) {
                            foundAny = true
                            onFound(found)
                            if (firstOnly) {
                                finish()
                                return@post
                            }
                        }
                        resolveNext(manager)
                    }
                }
            }
            activeResolve = listener
            try {
                manager.resolveService(serviceInfo, listener)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "resolve rejected", e)
                resolving = false
                activeResolve = null
                resolveNext(manager)
            }
        }

        /** The TV's IPv4 address: from the full address list where Android reports one. */
        @Suppress("DEPRECATION") // host is the only accessor below API 34.
        private fun ipv4Of(resolved: NsdServiceInfo): String? {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                resolved.hostAddresses
            } else {
                listOfNotNull(resolved.host)
            }
            return firstIpv4(addresses)?.hostAddress
        }

        private fun finish() {
            if (finished) return
            finished = true
            main.removeCallbacks(timeout)
            pending.clear()
            // Let go of the resolver too, so the next search does not find it busy (API 34+;
            // older stacks have no way to stop a lookup and the retry above covers them).
            val inFlight = activeResolve
            activeResolve = null
            if (inFlight != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    nsd?.stopServiceResolution(inFlight)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "resolve already finished", e)
                }
            }
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
