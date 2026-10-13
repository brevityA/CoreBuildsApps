package tv.corebuilds.eq.companion

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Application-layer authenticated encryption for the on-demand LAN companion channel. */
internal object CompanionCrypto {

    const val TOKEN_BYTES = 32
    const val PAIRING_KEY_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun randomKey(size: Int = TOKEN_BYTES): ByteArray = ByteArray(size).also(random::nextBytes)

    fun encodeKey(value: ByteArray): String = value.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun decodeKey(value: String, expectedBytes: Int): ByteArray? {
        val compact = value.filterNot(Char::isWhitespace).filterNot { it == '-' }
        if (compact.length != expectedBytes * 2 || compact.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(expectedBytes) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    /** The HTTP route is authenticated as AAD so an envelope cannot be replayed on another endpoint. */
    fun encrypt(secret: ByteArray, route: String, plaintext: ByteArray): ByteArray {
        require(secret.size == 16 || secret.size == 24 || secret.size == 32) { "AES key must be 128, 192 or 256 bits" }
        val nonce = randomKey(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(route.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)
        return JSONObject()
            .put("nonce", Base64.getEncoder().encodeToString(nonce))
            .put("data", Base64.getEncoder().encodeToString(ciphertext))
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
    }

    fun decrypt(secret: ByteArray, route: String, envelopeBytes: ByteArray): ByteArray {
        require(secret.size == 16 || secret.size == 24 || secret.size == 32) { "AES key must be 128, 192 or 256 bits" }
        val envelope = JSONObject(String(envelopeBytes, StandardCharsets.UTF_8))
        val nonce = Base64.getDecoder().decode(envelope.getString("nonce"))
        require(nonce.size == NONCE_BYTES) { "invalid AES-GCM nonce" }
        val ciphertext = Base64.getDecoder().decode(envelope.getString("data"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(route.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    fun decryptJson(secret: ByteArray, route: String, envelopeBytes: ByteArray): JSONObject =
        JSONObject(String(decrypt(secret, route, envelopeBytes), StandardCharsets.UTF_8))

    fun encryptJson(secret: ByteArray, route: String, value: JSONObject): ByteArray =
        encrypt(secret, route, value.toString().toByteArray(StandardCharsets.UTF_8))
}
