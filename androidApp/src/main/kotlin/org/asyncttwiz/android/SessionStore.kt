package org.asyncttwiz.android

import org.asyncttwiz.protocol.AccountIdentity

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
    /** Whether [failure] (from this store) means what it keeps can never be read again (Android: [ProtectedSessionStore]'s
     * Keystore failures). Unknown failures remain retryable, never destructive. */
    fun permanentFailure(failure: Throwable): Boolean = false
}

data class RememberedAccount(val identity: AccountIdentity, val email: String)

/** Credentials never enter Compose display state or diagnostic strings. */
class RememberedSession(val token: String, val account: RememberedAccount) {
    override fun toString() = "RememberedSession[redacted]"
}
