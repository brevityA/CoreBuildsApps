package tv.corebuilds.eq.companion

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * The phone's saved pairing against the device's real Android Keystore.
 *
 * 1.13.0 passed every JVM test and failed on every phone with "Caller-provided IV not
 * permitted": a Keystore key made with randomized encryption required picks its own GCM IV
 * and refuses one passed in, and Robolectric has no Keystore to refuse it. The first test
 * proves this device enforces that rule, so the round trip after it means something.
 */
@RunWith(AndroidJUnit4::class)
class PhoneCredentialStoreKeystoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Only the probe key: the round trip removes its own pairing, and nothing else is touched. */
    @After
    fun cleanUp() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(PROBE_ALIAS)
    }

    @Test
    fun theKeystoreRefusesAnIvPassedInForEncryption() {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(PROBE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        val key = generator.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, ByteArray(12)))
            fail("this device's Keystore accepted a caller IV, so it cannot reproduce the 1.13.0 failure")
        } catch (expected: InvalidAlgorithmParameterException) {
            // "Caller-provided IV not permitted", the error a Pixel 9 Pro XL showed on 1.13.0.
        }
    }

    @Test
    fun aPairingSealsCommitsAndLoadsBackWithTheKeystoreKey() {
        val token = ByteArray(CompanionCrypto.TOKEN_BYTES) { (it * 5 + 1).toByte() }
        val store = PhoneCredentialStore(context)
        // A debug install someone has really paired is left alone: this test would overwrite it.
        assumeTrue("this debug install already has a pairing; not overwriting it", store.load() == null)

        try {
            val sealed = store.seal(PhoneCredentials("192.168.50.250", 29517, token))
            assertEquals(12, sealed.nonce.size)
            assertNull("sealing alone must not store the pairing", store.load())
            assertTrue("the pairing was not written", store.commit(sealed))

            val loaded = PhoneCredentialStore(context).load()
            assertNotNull("the committed pairing did not decrypt with the Keystore key", loaded)
            assertEquals("192.168.50.250", loaded!!.host)
            assertEquals(29517, loaded.port)
            assertArrayEquals(token, loaded.token)
        } finally {
            store.clear()
        }
    }

    private companion object {
        const val PROBE_ALIAS = "coreeq-test-caller-iv-probe"
    }
}
