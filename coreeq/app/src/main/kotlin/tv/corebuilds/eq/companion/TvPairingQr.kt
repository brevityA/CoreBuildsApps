package tv.corebuilds.eq.companion

/**
 * The text carried in the TV's pairing QR: `coreeq://pair?h=<ipv4>&p=<port>&k=<key>`.
 *
 * It holds the same one-time key the TV shows as text, so scanning is only a
 * faster way to type it. Parsing is strict and pure Kotlin on purpose: any
 * malformed field returns null, and nothing here touches the network. The
 * phone still runs the encrypted pairing handshake before it trusts anything.
 */
internal object TvPairingQr {
    private const val PREFIX = "coreeq://pair?"
    private val ipv4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    data class Payload(val host: String, val port: Int, val key: String)

    fun build(host: String, port: Int, key: String): String = "${PREFIX}h=$host&p=$port&k=$key"

    fun parse(raw: String?): Payload? {
        if (raw == null || !raw.startsWith(PREFIX)) return null
        val params = HashMap<String, String>()
        for (part in raw.substring(PREFIX.length).split('&')) {
            val split = part.indexOf('=')
            if (split <= 0) return null
            val name = part.substring(0, split)
            if (params.put(name, part.substring(split + 1)) != null) return null // duplicate field
        }
        val host = params["h"]?.takeIf { ipv4.matches(it) && it.split('.').all { octet -> octet.toInt() <= 255 } } ?: return null
        val port = params["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val key = params["k"]?.takeIf { CompanionCrypto.decodeKey(it, CompanionCrypto.PAIRING_KEY_BYTES) != null } ?: return null
        return Payload(host, port, key)
    }
}
