package org.asyncttwiz.android

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Test
import org.junit.Assert.*

/** Uses the device's real AndroidKeyStore, not a software fake key provider. */
class ProtectedSessionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "test.session." + UUID.randomUUID()
    private val file = File(context.noBackupFilesDir, "test-session-" + UUID.randomUUID())
    private val store = ProtectedSessionStore(context, "https://accounts.example", alias, file)
    private val token = "A".repeat(43)

    @After fun cleanup() { store.clear() }

    @Test fun realKeystoreEncryptsReopensAndUsesDistinctNonces() {
        assertEquals(StoredSession.Empty, store.load())
        store.save(token)
        val first = file.readBytes()
        assertFalse(first.toString(Charsets.ISO_8859_1).contains(token))
        assertEquals(token, (ProtectedSessionStore(context, "https://accounts.example", alias, file).load() as StoredSession.Present).token)
        store.save(token)
        val second = file.readBytes()
        assertFalse(first.copyOfRange(1, 13).contentEquals(second.copyOfRange(1, 13)))
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null)
        assertNull(key.encoded) // Non-exportable platform key, no biometric on reopen.
        store.clear(); assertEquals(StoredSession.Empty, store.load())
    }

    @Test fun missingKeyDamagedCiphertextAndChangedOriginNeverYieldAPlaintextFallback() {
        store.save(token)
        assertEquals(StoredSession.Unrecoverable, ProtectedSessionStore(context, "https://other.example", alias, file).load())
        val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        assertEquals(StoredSession.Unrecoverable, store.load())
        store.save(token)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        assertEquals(StoredSession.Unrecoverable, store.load())
        // A replacement key with the same alias cannot decrypt the original bytes either.
        val old = file.readBytes(); store.save(token); file.writeBytes(old)
        assertEquals(StoredSession.Unrecoverable, store.load())
    }
}
