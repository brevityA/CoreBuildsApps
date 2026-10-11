package tv.corebuilds.eq.companion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class PhoneCredentials(
    val host: String,
    val port: Int,
    val token: ByteArray
)

/** Encrypted, bounded, one-request-per-socket client for the paired TV endpoint. */
internal class CompanionClient(
    private val host: String,
    private val port: Int,
    private val secret: ByteArray
) {
    fun pair(pairingKey: ByteArray, token: ByteArray, clientName: String): JSONObject = post(
        CompanionServer.PAIR_PATH,
        pairingKey,
        JSONObject()
            .put("op", "pair")
            .put("token", Base64.getEncoder().encodeToString(token))
            .put("client_name", clientName.take(60))
    )

    fun rpc(payload: JSONObject): JSONObject {
        payload.put("request_id", UUID.randomUUID().toString())
        payload.put("sent_at_ms", System.currentTimeMillis())
        return post(CompanionServer.RPC_PATH, secret, payload)
    }

    private fun post(path: String, key: ByteArray, payload: JSONObject): JSONObject {
        val requestBody = CompanionCrypto.encryptJson(key, path, payload)
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val output = socket.getOutputStream()
            val headers = "POST $path HTTP/1.1\r\n" +
                "Host: $host:$port\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${requestBody.size}\r\n" +
                "Connection: close\r\n\r\n"
            output.write(headers.toByteArray(StandardCharsets.US_ASCII))
            output.write(requestBody)
            output.flush()
            val (status, responseBody) = readResponse(socket.getInputStream())
            if (status !in 200..299) {
                val error = runCatching { JSONObject(String(responseBody, StandardCharsets.UTF_8)).optString("error") }
                    .getOrNull().orEmpty()
                throw IllegalStateException(error.ifBlank { "TV returned HTTP $status" })
            }
            return CompanionCrypto.decryptJson(key, path, responseBody)
        }
    }

    private fun readResponse(input: InputStream): Pair<Int, ByteArray> {
        val headerBuffer = ByteArrayOutputStream()
        var state = 0
        while (headerBuffer.size() < MAX_HEADER_BYTES && state < 4) {
            val byte = input.read()
            if (byte < 0) throw IllegalStateException("TV closed the connection before its response")
            headerBuffer.write(byte)
            state = when {
                state == 0 && byte == '\r'.code -> 1
                state == 1 && byte == '\n'.code -> 2
                state == 2 && byte == '\r'.code -> 3
                state == 3 && byte == '\n'.code -> 4
                byte == '\r'.code -> 1
                else -> 0
            }
        }
        if (state != 4) throw IllegalStateException("TV response headers were incomplete")
        val lines = String(headerBuffer.toByteArray(), StandardCharsets.US_ASCII).split("\r\n")
        val status = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull()
            ?: throw IllegalStateException("TV returned an invalid HTTP status")
        val length = lines.drop(1).firstNotNullOfOrNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0 || !line.substring(0, separator).equals("Content-Length", ignoreCase = true)) null
            else line.substring(separator + 1).trim().toIntOrNull()
        } ?: 0
        if (length < 0 || length > MAX_RESPONSE_BYTES) throw IllegalStateException("TV response exceeded the size limit")
        val body = ByteArray(length)
        var filled = 0
        while (filled < length) {
            val read = input.read(body, filled, length - filled)
            if (read < 0) throw IllegalStateException("TV response body was truncated")
            filled += read
        }
        return status to body
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_HEADER_BYTES = 8192
        private const val MAX_RESPONSE_BYTES = 3 * 1024 * 1024
    }
}

/** Keeps a phone's paired token encrypted by a device-keystore AES key. */
internal class PhoneCredentialStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(credentials: PhoneCredentials) {
        val key = getOrCreateKey()
        val nonce = ByteArray(NONCE_BYTES).also(java.security.SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(credentials.token)
        prefs.edit()
            .putString(KEY_HOST, credentials.host)
            .putInt(KEY_PORT, credentials.port)
            .putString(KEY_NONCE, Base64.getEncoder().encodeToString(nonce))
            .putString(KEY_CIPHER, Base64.getEncoder().encodeToString(encrypted))
            .apply()
    }

    fun load(): PhoneCredentials? {
        return try {
            val host = prefs.getString(KEY_HOST, null) ?: return null
            val port = prefs.getInt(KEY_PORT, 0)
            val nonce = Base64.getDecoder().decode(prefs.getString(KEY_NONCE, null) ?: return null)
            val encrypted = Base64.getDecoder().decode(prefs.getString(KEY_CIPHER, null) ?: return null)
            if (port !in 1..65535 || nonce.size != NONCE_BYTES) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(AAD)
            val token = cipher.doFinal(encrypted)
            if (token.size != CompanionCrypto.TOKEN_BYTES) return null
            PhoneCredentials(host, port, token)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "core_eq_phone_pairing"
        private const val ALIAS = "coreeq-phone-pairing-v1"
        private const val KEY_HOST = "tv_host"
        private const val KEY_PORT = "tv_port"
        private const val KEY_NONCE = "token_nonce"
        private const val KEY_CIPHER = "token_cipher"
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private val AAD = "tv.corebuilds.eq.phone-token.v1".toByteArray(StandardCharsets.UTF_8)
    }
}
