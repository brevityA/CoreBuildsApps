package dev.corebuilds.line

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import org.json.JSONObject

/**
 * What this device's VPN looks like right now, as far as a third-party app is
 * allowed to see it.
 *
 * Android TV has no status bar, so a VPN running on the box leaves no mark on
 * screen: the viewer watching a streamed game cannot tell whether the tunnel
 * they configured in Settings → Network → VPN is still up. That is the whole
 * reason the overlay dot exists, and this object is the only thing that feeds
 * it.
 *
 * Three facts, three different questions:
 *
 * - [tunnelUp] — *some* network on the device carries the VPN transport.
 *   That is "a VPN is connected", which is what the VPN app's own screen says.
 * - [covering] — this app's default route goes through that tunnel. With
 *   per-app VPN (split tunnelling, which ExpressVPN, NordVPN and Surfshark
 *   all ship on TV) a VPN can be up while Core Line — or the player next to
 *   it — is excluded. "A VPN is on" and "your traffic is in it" are different
 *   claims and the dot must not conflate them.
 * - [validated] — the system's own reachability probe last succeeded on the
 *   active network. Reported for the settings pane, deliberately **not** used
 *   to raise a fault: local VPNs are documented to report VALIDATED even in
 *   airplane mode (NetGuard/AdGuard, Android 11), so a red or amber dot
 *   driven by it would cry wolf. The clients that get this wrong are the
 *   ones whose dots stay green behind a kill switch; the honest answer is
 *   that public API cannot prove traffic is flowing, so we do not claim it.
 *
 * Nothing here names the VPN app. `NetworkCapabilities.getOwnerUid()` reads
 * the owner only for the app that owns the network (API 30+), and enumerating
 * installed VPN clients would need QUERY_ALL_PACKAGES. The dot says *state*,
 * never *provider* — a limitation the research doc records in full.
 */
data class VpnSnapshot(
    val tunnelUp: Boolean,
    val covering: Boolean,
    val validated: Boolean,
    val transport: String,
) {
    /** ok | partial | down — the three states the dot can draw. */
    val state: String
        get() = when {
            !tunnelUp -> STATE_DOWN
            !covering -> STATE_PARTIAL
            else -> STATE_OK
        }

    fun toJson(): String = JSONObject()
        .put("tunnelUp", tunnelUp)
        .put("covering", covering)
        .put("validated", validated)
        .put("transport", transport)
        .put("state", state)
        .toString()

    companion object {
        const val STATE_OK = "ok"
        const val STATE_PARTIAL = "partial"
        const val STATE_DOWN = "down"

        val NONE = VpnSnapshot(tunnelUp = false, covering = false, validated = false, transport = "none")
    }
}

object VpnState {
    /**
     * Read the whole picture. Cheap (two framework calls), safe to run on the
     * main thread, and never throws: ACCESS_NETWORK_STATE is a normal
     * permission but an app-ops change can still revoke it, and a dot that
     * cannot read the network must say "down" rather than crash the service
     * that is drawing it.
     */
    fun read(context: Context): VpnSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return VpnSnapshot.NONE
        return try {
            var tunnelUp = false
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    tunnelUp = true
                    break
                }
            }
            val active = cm.activeNetwork
            val activeCaps = active?.let { cm.getNetworkCapabilities(it) }
            VpnSnapshot(
                tunnelUp = tunnelUp,
                covering = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
                validated = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                transport = underlyingTransport(cm),
            )
        } catch (err: Exception) {
            VpnSnapshot.NONE
        }
    }

    /**
     * The transport the viewer cares about is the one *under* the tunnel —
     * the hotel Wi-Fi, the box's own Ethernet — and a VPN network's own
     * capability list is not a dependable place to read it from across
     * vendors. So the label comes from the best non-VPN internet-capable
     * network on the device instead.
     */
    private fun underlyingTransport(cm: ConnectivityManager): String {
        var best = "other"
        var bestRank = 0
        val networks = try {
            cm.allNetworks
        } catch (err: Exception) {
            return best
        }
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            val (label, rank) = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet" to 4
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi" to 3
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular" to 2
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth" to 1
                else -> "other" to 0
            }
            if (rank > bestRank) {
                best = label
                bestRank = rank
            }
        }
        return best
    }

    /**
     * Live updates while the dot is on screen.
     *
     * Two registrations, on purpose. A VPN transport request only fires when a
     * tunnel appears or disappears, so it can never report the *underlying*
     * network changing underneath a tunnel that stayed up; the default-network
     * callback covers that. Both funnel into [emit], which re-reads the whole
     * snapshot rather than trusting the callback's own arguments — the
     * callback tells us *when*, never *what*, and callbacks can arrive out of
     * order.
     */
    class Watcher(context: Context, private val onChange: (VpnSnapshot) -> Unit) {
        private val appContext = context.applicationContext
        private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        private var started = false

        private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emit()
            override fun onLost(network: Network) = emit()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = emit()
        }

        private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emit()
            override fun onLost(network: Network) = emit()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = emit()
        }

        /** False when the platform refused the registration; the caller keeps polling. */
        fun start(): Boolean {
            val manager = cm ?: return false
            if (started) return true
            started = true
            return try {
                manager.registerDefaultNetworkCallback(defaultCallback)
                manager.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .build(),
                    vpnCallback,
                )
                true
            } catch (err: Exception) {
                // TooManyRequestsException (API 31+), a revoked permission, or a
                // vendor manager that refuses callbacks. Not fatal: the dot also
                // refreshes on a timer.
                false
            }
        }

        fun stop() {
            val manager = cm ?: return
            if (!started) return
            started = false
            try {
                manager.unregisterNetworkCallback(defaultCallback)
            } catch (err: Exception) {
                // never registered, or already torn down with the process
            }
            try {
                manager.unregisterNetworkCallback(vpnCallback)
            } catch (err: Exception) {
                // as above
            }
        }

        private fun emit() = onChange(VpnState.read(appContext))
    }
}
