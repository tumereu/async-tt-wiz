package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.AccountIdentity
import java.util.concurrent.ExecutorService

enum class AuthPage { LOGIN, REGISTER }

/** Volatile only: not Serializable, rememberSaveable, SavedStateHandle, a DB row or a log value. */
data class AuthState(
    val page: AuthPage = AuthPage.LOGIN,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val restoring: Boolean = true,
    val restoreFailed: Boolean = false,
    val account: AccountIdentity? = null,
    val message: String? = null,
    val error: Boolean = false,
) { override fun toString() = "AuthState[redacted]" }

/** Main-thread state, one bounded background worker, callbacks fenced when the owner finishes. */
class AuthController(
    private val accounts: Accounts,
    private val store: SessionStore,
    private val worker: ExecutorService,
    private val post: (() -> Unit) -> Unit,
) {
    var state by mutableStateOf(AuthState())
        private set
    private var closed = false

    init { restore() }

    fun edit(username: String = state.username, email: String = state.email, password: String = state.password) {
        if (!closed && !state.busy && !state.restoring && state.account == null)
            state = state.copy(username = username, email = email, password = password, message = null)
    }

    fun page(page: AuthPage) {
        if (!closed && !state.busy && !state.restoring && state.account == null)
            state = state.copy(page = page, password = "", message = null, error = false)
    }

    fun resetNotYetAvailable() {
        if (!closed && !state.busy) state = state.copy(message = "Password reset is not available in this build yet.", error = false)
    }

    fun restore() {
        if (closed || state.busy) return
        state = AuthState(busy = true, restoring = true)
        work {
            when (val saved = store.load()) {
                StoredSession.Empty -> AuthState(restoring = false)
                StoredSession.Unavailable -> {
                    store.clear()
                    AuthState(restoring = false, message = "Saved login is unavailable. Please sign in again.", error = true)
                }
                is StoredSession.Present -> try {
                    AuthState(restoring = false, account = accounts.identity(saved.token))
                } catch (failure: AccountFailure) {
                    if (failure.code == "authentication_required") {
                        store.clear()
                        AuthState(restoring = false, message = "Your session is no longer valid. Please sign in again.", error = true)
                    } else AuthState(restoring = true, restoreFailed = true,
                        message = "Could not check your saved login. Check your connection and retry.", error = true)
                }
            }
        }
    }

    fun submit() {
        if (closed || state.busy || state.restoring || state.account != null) return
        val input = state
        state = input.copy(busy = true, message = null)
        work {
            try {
                if (input.page == AuthPage.REGISTER) {
                    accounts.register(input.username, input.email, input.password)
                    AuthState(restoring = false, email = input.email, message = "Account created. Sign in.")
                } else {
                    val session = accounts.login(input.email, input.password)
                    try { store.save(session.token) }
                    catch (_: Exception) {
                        // Do not enter with an unsaved credential or retain the reusable password.
                        runCatching { accounts.revoke(session.token) }
                        throw AccountFailure("credential_storage")
                    }
                    AuthState(restoring = false, account = session.account)
                }
            } catch (failure: AccountFailure) {
                input.copy(busy = false, message = errorMessage(failure), error = true,
                    password = if (failure.code == "credential_storage") "" else input.password)
            }
        }
    }

    private fun work(action: suspend () -> AuthState) {
        worker.execute {
            val next = try { runBlocking { action() } } catch (_: Exception) {
                AuthState(restoring = true, restoreFailed = true,
                    message = "Protected login storage could not be opened. Retry without uninstalling.", error = true)
            }
            post { if (!closed) state = next }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        state = AuthState(restoring = false)
        worker.execute { accounts.close() }
        worker.shutdown()
    }

    companion object {
        fun errorMessage(failure: AccountFailure): String = when (failure.code) {
            "invalid_field" -> when (failure.field) {
                "username" -> "Use a username of 1–64 characters without control characters."
                "email" -> "Enter a valid email with an ASCII mailbox name, such as player@example.com."
                "password" -> "Use 8–256 characters (at most 1024 UTF-8 bytes). Password spaces and Unicode are kept exactly."
                else -> "Check the fields and try again."
            }
            "account_unavailable" -> "That username or email cannot be used. Choose another or sign in."
            "invalid_credentials" -> "Email or password not recognised."
            "try_later", "authentication_busy" -> "Too many attempts or the service is busy. Wait a moment and try again."
            "credential_storage" -> "Your login could not be protected on this device. Please try signing in again."
            "not_configured" -> "The account service is not configured in this build."
            "network", "unavailable" -> "Could not reach the account service. Check your connection and try again."
            else -> "The account request could not be completed. Please try again."
        }
    }
}
