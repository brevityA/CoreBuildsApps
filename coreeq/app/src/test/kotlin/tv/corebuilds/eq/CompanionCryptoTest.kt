package tv.corebuilds.eq

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.companion.CompanionCrypto

class CompanionCryptoTest {

    @Test
    fun `rpc envelope round trips and does not reveal the payload`() {
        val key = CompanionCrypto.randomKey()
        val payload = JSONObject().put("op", "status").put("room", "Living room")

        val envelope = CompanionCrypto.encryptJson(key, "/v1/rpc", payload)
        val text = String(envelope, Charsets.UTF_8)

        assertNotEquals(payload.toString(), text)
        assertTrue(text.contains("nonce"))
        assertEquals(payload.toString(), CompanionCrypto.decryptJson(key, "/v1/rpc", envelope).toString())
    }

    @Test
    fun `wrong key and endpoint cannot authenticate a captured envelope`() {
        val key = CompanionCrypto.randomKey()
        val envelope = CompanionCrypto.encryptJson(key, "/v1/rpc", JSONObject().put("op", "status"))

        assertNull(runCatching { CompanionCrypto.decryptJson(CompanionCrypto.randomKey(), "/v1/rpc", envelope) }.getOrNull())
        assertNull(runCatching { CompanionCrypto.decryptJson(key, "/v1/pair", envelope) }.getOrNull())
    }

    @Test
    fun `pairing key text is strict and canonicalized`() {
        val secret = CompanionCrypto.randomKey(CompanionCrypto.PAIRING_KEY_BYTES)
        val text = CompanionCrypto.encodeKey(secret)

        assertEquals(secret.toList(), CompanionCrypto.decodeKey(text.chunked(4).joinToString("-"), secret.size)?.toList())
        assertNull(CompanionCrypto.decodeKey("0123", secret.size))
        assertNull(CompanionCrypto.decodeKey("z".repeat(secret.size * 2), secret.size))
    }
}
