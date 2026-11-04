package tv.corebuilds.eq.companion

/**
 * The text carried in the TV's pairing QR: `coreeq://pair?h=<ipv4>[,<ipv4>...]&p=<port>&k=<key>`.
 * A TV with more than one LAN address lists them in order; the phone tries the next
 * one only when a connection fails.
 *
 * It holds the same one-time key the TV shows as text, so scanning is only a
 * faster way to type it. Parsing is strict and pure Kotlin on purpose: any
 * malformed field returns null, and nothing here touches the network. The
 * phone still runs the encrypted pairing handshake before it trusts anything.
 */
internal object TvPairingQr {
    private const val PREFIX = "coreeq://pair?"
    private val ipv4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    const val MAX_HOSTS = 3

    data class Payload(val hosts: List<String>, val port: Int, val key: String)

    fun build(hosts: List<String>, port: Int, key: String): String =
        "${PREFIX}h=${hosts.take(MAX_HOSTS).joinToString(",")}&p=$port&k=$key"

    private fun isIpv4(host: String): Boolean =
        ipv4.matches(host) && host.split('.').all { it.toInt() <= 255 }

    fun parse(raw: String?): Payload? {
        if (raw == null || !raw.startsWith(PREFIX)) return null
        val params = HashMap<String, String>()
        for (part in raw.substring(PREFIX.length).split('&')) {
            val split = part.indexOf('=')
            if (split <= 0) return null
            val name = part.substring(0, split)
            if (params.put(name, part.substring(split + 1)) != null) return null // duplicate field
        }
        val hosts = params["h"]?.split(',')?.takeIf { it.size in 1..MAX_HOSTS }
            ?.takeIf { list -> list.all { isIpv4(it) } } ?: return null
        val port = params["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val key = params["k"]?.takeIf { CompanionCrypto.decodeKey(it, CompanionCrypto.PAIRING_KEY_BYTES) != null } ?: return null
        return Payload(hosts, port, key)
    }
}
