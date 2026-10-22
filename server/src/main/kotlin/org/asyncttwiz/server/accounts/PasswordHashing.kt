package org.asyncttwiz.server.accounts

import java.util.concurrent.Semaphore
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

class HashingBusy : RuntimeException("Authentication busy")

/** Work runs on bounded servlet threads, at most two memory-hard operations, with no hash queue. */
class PasswordHashing {
    private val encoder = Argon2PasswordEncoder(16, 32, 1, 65536, 3)
    private val permits = Semaphore(2)
    private val dummy = encoder.encode("not-an-account-password")

    internal fun <T> bounded(work: () -> T): T {
        if (!permits.tryAcquire()) throw HashingBusy()
        try { return work() } finally { permits.release() }
    }

    fun hash(password: String): String = bounded { checkNotNull(encoder.encode(password)) }
    fun verify(password: String, phc: String?): Boolean = bounded { encoder.matches(password, phc ?: dummy) && phc != null }
    fun needsRehash(phc: String) = encoder.upgradeEncoding(phc)
}
