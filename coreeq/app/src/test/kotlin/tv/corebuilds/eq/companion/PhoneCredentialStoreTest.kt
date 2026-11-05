package tv.corebuilds.eq.companion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The phone's saved pairing, with a software AES key standing in for Android Keystore
 * (Robolectric has no Keystore). The Keystore-only rule that broke 1.13.0 pairing, that the
 * Keystore picks the GCM IV itself, is exercised on emulators by
 * src/androidTest/.../PhoneCredentialStoreKeystoreTest.kt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneCredentialStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val token = ByteArray(CompanionCrypto.TOKEN_BYTES) { (it * 7 + 3).toByte() }
    private val credentials = PhoneCredentials("192.168.50.250", 29517, token)

    private fun store(provider: () -> SecretKey = { key }) = PhoneCredentialStore(context, provider)

    @After
    fun clear() = store().clear()

    @Test
    fun `a sealed and committed pairing loads back in a new store`() {
        val first = store()
        first.commit(first.seal(credentials))

        val loaded = store().load()

        assertNotNull(loaded)
        assertEquals("192.168.50.250", loaded!!.host)
        assertEquals(29517, loaded.port)
        assertArrayEquals(token, loaded.token)
    }

    @Test
    fun `sealing stores nothing until the TV has accepted the token`() {
        val store = store()
        store.seal(credentials)

        assertNull(store.load())
    }

    @Test
    fun `each seal reads back a fresh 12-byte IV chosen by the cipher`() {
        val store = store()
        val a = store.seal(credentials)
        val b = store.seal(credentials)

        assertEquals(12, a.nonce.size)
        assertEquals(12, b.nonce.size)
        assertFalse(a.nonce.contentEquals(b.nonce))
        assertFalse(a.encrypted.contentEquals(b.encrypted))
    }

    @Test
    fun `a key that cannot be used is a key-storage failure and stores nothing`() {
        val broken = store { throw java.security.KeyStoreException("AndroidKeyStore not found") }

        try {
            broken.seal(credentials)
            fail("seal should refuse without a usable key")
        } catch (e: PhoneKeyStorageException) {
            assertEquals("AndroidKeyStore not found", e.message)
            assertTrue(e.cause is java.security.KeyStoreException)
        }
        assertNull(store().load())
    }

    @Test
    fun `a changed ciphertext does not load`() {
        val store = store()
        val sealed = store.seal(credentials)
        sealed.encrypted[0] = (sealed.encrypted[0].toInt() xor 1).toByte()
        store.commit(sealed)

        assertNull(store.load())
    }

    @Test
    fun `a pairing made under another key does not load`() {
        val store = store()
        store.commit(store.seal(credentials))
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        assertNull(store { otherKey }.load())
    }

    @Test
    fun `rediscovery moves the address and keeps the token`() {
        val store = store()
        store.commit(store.seal(credentials))

        assertTrue(store.updateAddress(credentials, "192.168.50.251", 29518))
        val moved = store.load()!!
        assertEquals("192.168.50.251", moved.host)
        assertEquals(29518, moved.port)
        assertArrayEquals(token, moved.token)

        val stranger = PhoneCredentials("192.168.50.251", 29518, ByteArray(CompanionCrypto.TOKEN_BYTES))
        assertFalse(store.updateAddress(stranger, "10.0.0.9", 29519))
        assertEquals("192.168.50.251", store.load()!!.host)
    }
}
