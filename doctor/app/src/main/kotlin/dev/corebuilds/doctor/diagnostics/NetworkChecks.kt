package dev.corebuilds.doctor.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetAddress

object NetworkChecks {

    fun checkVpn(context: Context): CheckResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        return if (hasVpn) {
            CheckResult(
                name = "VPN detection",
                verdict = Verdict.WARN,
                summary = "Active VPN transport detected",
                fix = "A VPN can interfere with debrid streaming. If you are getting " +
                    "no streams, try disabling the VPN temporarily. Some debrid " +
                    "providers block VPN IPs."
            )
        } else {
            CheckResult(
                name = "VPN detection",
                verdict = Verdict.PASS,
                summary = "No VPN transport active"
            )
        }
    }

    fun checkDns(): CheckResult {
        val hosts = listOf(
            "api.real-debrid.com",
            "api.torbox.app",
            "v6-4.aiostreams.elfhosted.com"
        )
        val resolved = mutableListOf<Pair<String, Long>>()
        val failures = mutableListOf<String>()

        for (host in hosts) {
            val start = System.nanoTime()
            try {
                InetAddress.getByName(host)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                resolved.add(host to elapsed)
            } catch (_: Exception) {
                failures.add(host)
            }
        }

        return when {
            failures.isNotEmpty() -> CheckResult(
                name = "DNS resolution",
                verdict = Verdict.FAIL,
                summary = "DNS failed for: ${failures.joinToString(", ")}",
                fix = "Your DNS cannot resolve streaming infrastructure hostnames. " +
                    "Try switching to a public DNS (8.8.8.8 or 1.1.1.1). " +
                    "Your ISP may be blocking these domains."
            )
            resolved.any { it.second > 2000 } -> {
                val slow = resolved.filter { it.second > 2000 }
                CheckResult(
                    name = "DNS resolution",
                    verdict = Verdict.WARN,
                    summary = "DNS slow for: ${slow.joinToString(", ") {
                        "${it.first} (${it.second}ms)"
                    }}",
                    fix = "DNS is resolving but slowly. Consider switching to a " +
                        "faster DNS (8.8.8.8 or 1.1.1.1)."
                )
            }
            else -> CheckResult(
                name = "DNS resolution",
                verdict = Verdict.PASS,
                summary = "All ${resolved.size} hosts resolved " +
                    "(${resolved.maxOf { it.second }}ms worst)"
            )
        }
    }
}
