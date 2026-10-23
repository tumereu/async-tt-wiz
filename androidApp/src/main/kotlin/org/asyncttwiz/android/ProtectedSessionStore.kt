package org.asyncttwiz.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.asyncttwiz.protocol.AccountIdentity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
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
    fun remembered(): List<RememberedSession> = emptyList()
    fun remember(sessions: List<RememberedSession>) = Unit
}

data class RememberedAccount(val identity: AccountIdentity, val email: String)

/** Credentials never enter Compose display state or diagnostic strings. */
class RememberedSession(val token: String, val account: RememberedAccount) {
    override fun toString() = "RememberedSession[redacted]"
}

/** Worker-thread only. Current session and optional remembered sessions are protected separately.
 * Clearing the current session must not destroy other accounts' credentials. No passwords on disk. */
class ProtectedSessionStore(
    context: Context,
    origin: String,
    alias: String = "org.asyncttwiz.session.v1",
    file: File = File(context.noBackupFilesDir, "auth/session.bin"),
) : SessionStore {
    private val current = ProtectedFile(file, alias, "ATW session v1\n$origin", 256)
    private val remembered = ProtectedFile(File(file.parentFile, "remembered.bin"), "$alias.remembered",
        "ATW remembered sessions v1\n$origin", 1024 * 1024)

    override fun load(): StoredSession = try {
        val bytes = current.read()
        if (bytes == null) StoredSession.Empty else {
            val token = bytes.decodeToString(throwOnInvalidSequence = true)
            require(AccountApi.validToken(token))
            StoredSession.Present(token)
        }
    } catch (_: Exception) { StoredSession.Unavailable }

    override fun save(token: String) {
        require(AccountApi.validToken(token))
        current.write(token.toByteArray(Charsets.UTF_8))
    }

    override fun clear() = current.clear()

    override fun remembered(): List<RememberedSession> {
        val bytes = remembered.read() ?: return emptyList()
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == 1)
            val count = input.readInt()
            require(count in 0..1000)
            List(count) {
                val token = input.readUTF()
                require(AccountApi.validToken(token))
                RememberedSession(token, RememberedAccount(AccountIdentity(input.readUTF(), input.readUTF()), input.readUTF()))
            }.also { require(input.read() == -1) }
        }
    }

    override fun remember(sessions: List<RememberedSession>) {
        if (sessions.isEmpty()) { remembered.clear(); return }
        require(sessions.size <= 1000)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(1)
            output.writeInt(sessions.size)
            sessions.forEach {
                require(AccountApi.validToken(it.token))
                output.writeUTF(it.token)
                output.writeUTF(it.account.identity.accountId)
                output.writeUTF(it.account.identity.username)
                output.writeUTF(it.account.email)
            }
        }
        remembered.write(bytes.toByteArray())
    }
}

/** Same Keystore/atomic-file envelope as the original single-session format. */
private class ProtectedFile(file: File, private val alias: String, aad: String, private val maxBytes: Int) {
    private val atomic = AtomicFile(file)
    private val keys get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val aad = aad.toByteArray(Charsets.UTF_8)

    fun read(): ByteArray? {
        val bytes = try { atomic.openRead().use { input ->
            val buffer = ByteArray(maxBytes + 1)
            var count = 0
            while (count < buffer.size) {
                val n = input.read(buffer, count, buffer.size - count)
                if (n < 0) break
                count += n
            }
            buffer.copyOf(count)
        } }
            catch (_: java.io.FileNotFoundException) { return null }
        require(bytes.size in 30..maxBytes && bytes[0] == 1.toByte())
        // Never create a new key to pretend the old token can be recovered.
        val key = keys.getKey(alias, null) as? SecretKey ?: error("Protected login key unavailable")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size))
    }

    fun write(bytes: ByteArray) {
        require(bytes.size + 29 <= maxBytes)
        val key = keys.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setUserAuthenticationRequired(false).build())
        }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key) // Keystore generates a new randomized nonce on every write.
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(bytes)
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

    fun clear() {
        atomic.delete()
        check(!atomic.baseFile.exists())
        keys.deleteEntry(alias)
    }
}
