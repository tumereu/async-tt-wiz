package org.asyncttwiz.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface StoredSession {
    data object Empty : StoredSession
    data object Unavailable : StoredSession
    class Present(val token: String) : StoredSession { override fun toString() = "StoredSession[redacted]" }
}

interface SessionStore {
    fun load(): StoredSession
    fun save(token: String)
    fun clear()
}

/** Worker-thread only. Atomic, private no-backup ciphertext; no password or identity on disk. */
class ProtectedSessionStore(
    context: Context,
    private val origin: String,
    private val alias: String = "org.asyncttwiz.session.v1",
    file: File = File(context.noBackupFilesDir, "auth/session.bin"),
) : SessionStore {
    private val atomic = AtomicFile(file)
    private val keys get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val aad get() = ("ATW session v1\n" + origin).toByteArray(Charsets.UTF_8)

    override fun load(): StoredSession {
        return try {
            val bytes = try { atomic.openRead().use { input ->
                val buffer = ByteArray(257)
                var count = 0
                while (count < buffer.size) {
                    val n = input.read(buffer, count, buffer.size - count)
                    if (n < 0) break
                    count += n
                }
                buffer.copyOf(count)
            } }
                catch (_: java.io.FileNotFoundException) { return StoredSession.Empty }
            require(bytes.size in 30..256 && bytes[0] == 1.toByte())
            // Never create a new key to pretend the old token can be recovered.
            val key = keys.getKey(alias, null) as? SecretKey ?: return StoredSession.Unavailable
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.updateAAD(aad)
            val token = cipher.doFinal(bytes.copyOfRange(13, bytes.size)).decodeToString(throwOnInvalidSequence = true)
            require(AccountApi.validToken(token))
            StoredSession.Present(token)
        } catch (_: Exception) { StoredSession.Unavailable }
    }

    override fun save(token: String) {
        require(AccountApi.validToken(token))
        val key = keys.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setUserAuthenticationRequired(false).build())
        }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key) // Keystore generates a new randomized nonce on every write.
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        require(cipher.iv.size == 12)
        check(atomic.baseFile.parentFile!!.let { it.isDirectory || it.mkdirs() })
        val output = atomic.startWrite()
        try {
            output.write(byteArrayOf(1) + cipher.iv + encrypted)
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            atomic.failWrite(output)
            throw failure
        }
    }

    override fun clear() {
        atomic.delete()
        check(!atomic.baseFile.exists())
        keys.deleteEntry(alias)
    }
}
