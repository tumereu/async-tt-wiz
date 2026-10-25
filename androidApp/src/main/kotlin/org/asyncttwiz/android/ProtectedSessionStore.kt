package org.asyncttwiz.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.asyncttwiz.protocol.AccountIdentity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.IllegalBlockSizeException
import javax.crypto.spec.GCMParameterSpec

sealed interface StoredSession {
    data object Empty : StoredSession
    data object Unavailable : StoredSession
    data object Unrecoverable : StoredSession
    class Present(val token: String) : StoredSession { override fun toString() = "StoredSession[redacted]" }
}

interface SessionStore {
    fun load(): StoredSession
    fun save(token: String)
    fun clear()
    /** Explicit local recovery only; healthy remembered credentials are retained. */
    fun resetUnrecoverable() = clear()
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
class ProtectedSessionStore internal constructor(
    origin: String,
    alias: String,
    file: File,
    keys: SessionKeys,
) : SessionStore {
    constructor(context: Context, origin: String, alias: String = "org.asyncttwiz.session.v1",
        file: File = File(context.noBackupFilesDir, "auth/session.bin")) : this(origin, alias, file, AndroidSessionKeys)

    private val current = ProtectedFile(file, alias, "ATW session v1\n$origin", 256, keys)
    private val remembered = ProtectedFile(File(file.parentFile, "remembered.bin"), "$alias.remembered",
        "ATW remembered sessions v1\n$origin", 1024 * 1024, keys)

    override fun load(): StoredSession = try {
        val bytes = current.read()
        if (bytes == null) StoredSession.Empty else decodeProtected {
            val token = bytes.decodeToString(throwOnInvalidSequence = true)
            require(AccountApi.validToken(token))
            StoredSession.Present(token)
        }
    } catch (failure: Exception) {
        if (permanentSessionFailure(failure)) StoredSession.Unrecoverable else StoredSession.Unavailable
    }

    override fun save(token: String) {
        require(AccountApi.validToken(token))
        current.write(token.toByteArray(Charsets.UTF_8))
    }

    override fun clear() = current.clear()

    override fun resetUnrecoverable() {
        // Inspect before deleting anything: temporary Keystore outages must not erase credentials.
        val brokenRemembered = try { remembered(); false } catch (failure: Exception) {
            if (!permanentSessionFailure(failure)) throw failure
            true
        }
        current.clear()
        if (brokenRemembered) remembered.clear()
    }

    override fun remembered(): List<RememberedSession> {
        val bytes = remembered.read() ?: return emptyList()
        return decodeProtected { DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == 1)
            val count = input.readInt()
            require(count in 0..1000)
            List(count) {
                val token = input.readUTF()
                require(AccountApi.validToken(token))
                RememberedSession(token, RememberedAccount(AccountIdentity(input.readUTF(), input.readUTF()), input.readUTF()))
            }.also { require(input.read() == -1) }
        } }
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

private class CorruptSession(cause: Exception? = null) : Exception("Protected login is unrecoverable", cause)

private inline fun <T> decodeProtected(decode: () -> T): T = try { decode() }
    catch (failure: Exception) { throw CorruptSession(failure) }

/** Providers may wrap the platform exception. Unknown failures remain retryable, never destructive. */
internal fun permanentSessionFailure(failure: Throwable): Boolean =
    generateSequence(failure) { it.cause?.takeUnless { cause -> cause === it } }.take(16).any {
        it is CorruptSession || it is KeyPermanentlyInvalidatedException || it is UnrecoverableKeyException ||
            it is BadPaddingException || it is IllegalBlockSizeException
    }

internal interface SessionKeys {
    fun get(alias: String): SecretKey?
    fun create(alias: String): SecretKey
    fun delete(alias: String)
}

private object AndroidSessionKeys : SessionKeys {
    private val keys get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    override fun get(alias: String) = keys.getKey(alias, null) as? SecretKey
    override fun create(alias: String): SecretKey = KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(false).build())
    }.generateKey()
    override fun delete(alias: String) { keys.deleteEntry(alias) }
}

/** Same Keystore/atomic-file envelope as the original single-session format. */
private class ProtectedFile(file: File, private val alias: String, aad: String, private val maxBytes: Int,
    private val keys: SessionKeys) {
    private val atomic = AtomicFile(file)
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
        if (bytes.size !in 30..maxBytes || bytes[0] != 1.toByte()) throw CorruptSession()
        // Never create a new key to pretend the old token can be recovered.
        val key = keys.get(alias) ?: throw UnrecoverableKeyException("Protected login key missing")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size))
    }

    fun write(bytes: ByteArray) {
        require(bytes.size + 29 <= maxBytes)
        val key = keys.get(alias) ?: keys.create(alias)
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
        keys.delete(alias) // Idempotent for missing keys; transient failures leave the blob intact.
        atomic.delete()
        check(!atomic.baseFile.exists())
    }
}
