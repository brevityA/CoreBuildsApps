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

    /**
     * Legacy probe retained for protocol compatibility. It is not used for discovery:
     * the TV acknowledges it without opening pairing or changing any UI.
     */
    fun wake(): Int {
        val body = "{}".toByteArray(StandardCharsets.US_ASCII)
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val output = socket.getOutputStream()
            val headers = "POST ${CompanionServer.WAKE_PATH} HTTP/1.1\r\n" +
                "Host: $host:$port\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            output.write(headers.toByteArray(StandardCharsets.US_ASCII))
            output.write(body)
            output.flush()
            return readResponse(socket.getInputStream()).first
        }
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

/** A pairing token already encrypted under this phone's key, waiting for the TV to accept it. */
internal class SealedPhoneCredentials(
    val host: String,
    val port: Int,
    val nonce: ByteArray,
    val encrypted: ByteArray
)

/** The phone could not encrypt the pairing token, so it must not ask the TV to pair. */
internal class PhoneKeyStorageException(cause: Throwable) :
    Exception(cause.message?.trim()?.takeIf(String::isNotEmpty) ?: cause.javaClass.simpleName, cause)

/** The TV accepted the token, but the phone could not write it to its own storage. */
internal class PhonePairingNotSavedException :
    Exception("the paired token could not be written to this phone's storage")

/**
 * Keeps a phone's paired token encrypted by a device-keystore AES key.
 *
 * Pairing is two steps, [seal] and then [commit], because the TV spends its one-time pairing
 * key and records the phone the moment it accepts a token. Sealing first means a phone that
 * cannot keep the token stops before the TV pairs, instead of leaving the TV paired to a
 * phone that has nothing saved (the 1.13.0 failure). [keyProvider] is the Android Keystore
 * key in the app; tests pass a software AES key.
 */
internal class PhoneCredentialStore(
    context: Context,
    private val keyProvider: () -> SecretKey = { androidKeystoreKey() }
) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Encrypts [credentials] without storing them. Every failure is a [PhoneKeyStorageException].
     *
     * Android Keystore AES keys are made with randomized encryption required, so the Keystore
     * chooses the GCM IV and refuses one passed in ("Caller-provided IV not permitted"). The
     * cipher is initialised with the key alone and the IV it chose is read back and stored.
     */
    fun seal(credentials: PhoneCredentials): SealedPhoneCredentials = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val nonce = cipher.iv
        check(nonce != null && nonce.size == NONCE_BYTES) {
            "the key store chose a ${nonce?.size ?: 0}-byte GCM IV; Core EQ stores $NONCE_BYTES-byte IVs"
        }
        cipher.updateAAD(AAD)
        SealedPhoneCredentials(credentials.host, credentials.port, nonce.copyOf(), cipher.doFinal(credentials.token))
    } catch (e: PhoneKeyStorageException) {
        throw e
    } catch (e: Exception) {
        throw PhoneKeyStorageException(e)
    }

    /**
     * Stores a token [seal] encrypted, once the TV has accepted it. Written synchronously
     * (pairing runs off the main thread) so a failed write is reported, not lost: `apply()`
     * never says whether the disk write happened. Returns whether it did.
     */
    fun commit(sealed: SealedPhoneCredentials): Boolean =
        prefs.edit()
            .putString(KEY_HOST, sealed.host)
            .putInt(KEY_PORT, sealed.port)
            .putString(KEY_NONCE, Base64.getEncoder().encodeToString(sealed.nonce))
            .putString(KEY_CIPHER, Base64.getEncoder().encodeToString(sealed.encrypted))
            .commit()

    fun load(): PhoneCredentials? {
        return try {
            val host = prefs.getString(KEY_HOST, null) ?: return null
            val port = prefs.getInt(KEY_PORT, 0)
            val nonce = Base64.getDecoder().decode(prefs.getString(KEY_NONCE, null) ?: return null)
            val encrypted = Base64.getDecoder().decode(prefs.getString(KEY_CIPHER, null) ?: return null)
            if (port !in 1..65535 || nonce.size != NONCE_BYTES) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // Decryption is the one direction where the IV is passed in: it is the stored one.
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(AAD)
            val token = cipher.doFinal(encrypted)
            if (token.size != CompanionCrypto.TOKEN_BYTES) return null
            PhoneCredentials(host, port, token)
        } catch (_: Exception) {
            null
        }
    }

    /** Move a saved pairing after authenticated rediscovery, without re-encrypting its token. */
    fun updateAddress(expected: PhoneCredentials, host: String, port: Int): Boolean {
        val current = load() ?: return false
        if (!current.token.contentEquals(expected.token)) return false
        prefs.edit().putString(KEY_HOST, host).putInt(KEY_PORT, port).apply()
        return true
    }

    fun clear() {
        prefs.edit().clear().apply()
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
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val AAD = "tv.corebuilds.eq.phone-token.v1".toByteArray(StandardCharsets.UTF_8)

        /**
         * The phone's pairing key, created on first use. Synchronized: two first pairings at
         * once could both miss the alias, and the second generateKey() would replace the key
         * that sealed the first token.
         */
        @Synchronized
        private fun androidKeystoreKey(): SecretKey {
            val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
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

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
