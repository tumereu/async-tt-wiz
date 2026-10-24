package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.AccountIdentity
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
    val destination: ShellDestination = ShellDestination.MENU,
    val backStack: List<ShellDestination> = emptyList(),
    val drawerOpen: Boolean = false,
    val logoutFailed: Boolean = false,
    val rememberedAccounts: List<RememberedAccount> = emptyList(),
) { override fun toString() = "AuthState[redacted]" }

/** Main-thread state, one bounded background worker, callbacks fenced when the owner finishes. */
class AuthController(
    private val accounts: Accounts,
    private val store: SessionStore,
    private val worker: ExecutorService,
    private val local: AccountLocalData = object : AccountLocalData {},
    private val revoke: (String) -> Unit = { token -> worker.execute { runCatching { runBlocking { accounts.revoke(token) } } } },
    private val clearPrivateWork: () -> Unit = {},
    val accountSwitcher: Boolean = false,
    private val post: (() -> Unit) -> Unit,
) {
    var state by mutableStateOf(AuthState())
        private set
    private var closed = false
    private val generation = AtomicLong()
    private val token = AtomicReference<String?>()
    private val ownerLock = Any()
    private var reauthenticationContext: AuthState? = null
    private var logoutRevokeAttempted = false // Auth worker only; cleanup retries must still forget the token.
    @Volatile private var retrySavedLogin = false
    private var nextRestore = 0L
    fun foreground() {
        val now = System.nanoTime() / 1_000_000
        if (retrySavedLogin && state.restoring && state.restoreFailed && !state.busy && now >= nextRestore) {
            nextRestore = now + 5_000
            restore()
        }
    }

    private fun bestEffortRevoke(value: String) { runCatching { revoke(value) } }

    /** Capture on the UI thread for one ordinary private request; never expose it in display state. */
    fun sessionToken(): String? = if (!closed && state.account != null && !state.busy) token.get() else null

    /** Transport callback: only an explicit rejection of the current credential asks for login.
     * Invalidation is not Logout: same-process drafts and uncertain request identities survive. */
    fun authenticationRequired(rejectedToken: String) {
        val epoch = generation.get()
        post {
            if (!closed && generation.get() == epoch && state.account != null && token.get() == rejectedToken) {
                reauthenticationContext = state.copy(password = "", drawerOpen = false)
                synchronized(ownerLock) { generation.incrementAndGet(); token.set(null) }
                state = AuthState(restoring = false, busy = true)
                work {
                    val email = forget(rejectedToken)
                    store.clear()
                    AuthState(restoring = false, email = email,
                        message = "Your session is no longer valid. Please sign in again.", error = true)
                }
            }
        }
    }

    private fun rememberToken(value: String, epoch: Long, revokeIfStale: Boolean = false) {
        synchronized(ownerLock) {
            if (generation.get() == epoch) token.set(value)
            else if (revokeIfStale) bestEffortRevoke(value)
        }
    }

    /** Worker-only; flag-off never touches the remembered-account store. */
    private fun forget(value: String): String {
        if (!accountSwitcher) return ""
        val saved = store.remembered()
        val email = saved.firstOrNull { it.token == value }?.account?.email.orEmpty()
        store.remember(saved.filterNot { it.token == value })
        return email
    }

    private fun remember(session: RememberedSession) {
        if (accountSwitcher) store.remember(store.remembered().filterNot {
            it.account.identity.accountId == session.account.identity.accountId
        } + session)
    }

    init { restore() }

    fun edit(username: String = state.username, email: String = state.email, password: String = state.password) {
        if (!closed && !state.busy && !state.restoring && state.account == null)
            state = state.copy(username = username, email = email, password = password, message = null)
    }

    fun page(page: AuthPage) {
        if (!closed && !state.busy && !state.restoring && state.account == null)
            state = state.copy(page = page, password = "", message = null, error = false)
    }

    fun passwordResetCompleted() {
        if (closed) return
        reauthenticationContext = null
        clearPrivateWork()
        synchronized(ownerLock) { generation.incrementAndGet(); token.set(null) }
        state = AuthState(restoring = false, busy = true)
        work {
            local.resumeLogout(store) // A link cannot bypass an earlier interrupted/failed local sign-out.
            (store.load() as? StoredSession.Present)?.let { forget(it.token) }
            store.clear()
            AuthState(restoring = false, message = "Password changed. Sign in with your new password.")
        }
    }

    fun restore() {
        if (closed || state.busy) return
        val epoch = generation.get()
        state = AuthState(busy = true, restoring = true)
        retrySavedLogin = false
        work {
            local.resumeLogout(store)
            restoreSaved(epoch)
        }
    }

    private suspend fun restoreSaved(epoch: Long): AuthState? =
            when (val saved = store.load()) {
                StoredSession.Empty -> AuthState(restoring = false)
                StoredSession.Unavailable -> {
                    store.clear()
                    AuthState(restoring = false, message = "Saved login is unavailable. Please sign in again.", error = true)
                }
                is StoredSession.Present -> try {
                    val identity = serviceRetry(current = { generation.get() == epoch }) { accounts.identity(saved.token) }
                    if (generation.get() != epoch) return null
                    if (accountSwitcher) {
                        val email = store.remembered().firstOrNull { it.token == saved.token }?.account?.email.orEmpty()
                        remember(RememberedSession(saved.token, RememberedAccount(identity, email)))
                    }
                    val destination = local.activate(identity.accountId)
                    rememberToken(saved.token, epoch)
                    AuthState(restoring = false, account = identity, destination = destination)
                } catch (failure: AccountFailure) {
                    if (failure.code == "authentication_required") {
                        val email = forget(saved.token)
                        store.clear()
                        AuthState(restoring = false, email = email,
                            message = "Your session is no longer valid. Please sign in again.", error = true)
                    } else {
                        retrySavedLogin = transientServiceFailure(failure)
                        AuthState(restoring = true, restoreFailed = true,
                            message = if (retrySavedLogin) "Checking saved login…" else "Could not check your saved login. Retry.",
                            error = !retrySavedLogin)
                    }
                }
            }

    fun submit() {
        if (closed || state.busy || state.restoring || state.logoutFailed || state.account != null) return
        val input = state
        val epoch = generation.get()
        state = input.copy(busy = true, message = null)
        work {
            try {
                if (input.page == AuthPage.REGISTER) {
                    accounts.register(input.username, input.email, input.password)
                    AuthState(restoring = false, email = input.email, message = "Account created. Sign in.")
                } else {
                    val session = accounts.login(input.email, input.password)
                    if (generation.get() != epoch) {
                        bestEffortRevoke(session.token)
                        return@work null
                    }
                    try {
                        remember(RememberedSession(session.token, RememberedAccount(session.account, input.email)))
                        store.save(session.token)
                    }
                    catch (_: Exception) {
                        // Do not enter with an unsaved credential or retain the reusable password.
                        runCatching { accounts.revoke(session.token) }
                        runCatching { forget(session.token) }
                        throw AccountFailure("credential_storage")
                    }
                    val destination = local.activate(session.account.accountId)
                    rememberToken(session.token, epoch, revokeIfStale = true)
                    AuthState(restoring = false, account = session.account, destination = destination)
                }
            } catch (failure: AccountFailure) {
                input.copy(busy = false, message = errorMessage(failure), error = true,
                    password = if (failure.code == "credential_storage") "" else input.password)
            }
        }
    }

    private fun work(action: suspend () -> AuthState?) {
        val epoch = generation.get()
        worker.execute {
            if (generation.get() != epoch) return@execute
            val next = try { runBlocking { action()?.let(::withRemembered) } } catch (_: Exception) {
                AuthState(restoring = true, restoreFailed = true,
                    message = "Protected login storage could not be opened. Retry without uninstalling.", error = true)
            } ?: return@execute
            post { if (!closed && generation.get() == epoch) {
                val context = reauthenticationContext
                if (next.account != null && context != null) {
                    if (next.account.accountId != context.account?.accountId) clearPrivateWork()
                    reauthenticationContext = null
                    state = if (next.account.accountId == context.account?.accountId)
                        next.copy(destination = context.destination, backStack = context.backStack) else next
                } else state = next
            } }
        }
    }

    private fun withRemembered(next: AuthState): AuthState = if (accountSwitcher)
        next.copy(rememberedAccounts = store.remembered().map { it.account }) else next

    fun drawer(open: Boolean) {
        if (!closed && state.account != null) state = state.copy(drawerOpen = open)
    }

    fun navigate(destination: ShellDestination) {
        if (closed || state.account == null) return
        val stack = if (destination == ShellDestination.MENU) emptyList()
            else if (destination == state.destination) state.backStack
            else state.backStack + state.destination
        state = state.copy(destination = destination, backStack = stack, drawerOpen = false)
        saveDestination()
    }

    fun back() {
        if (state.drawerOpen) drawer(false)
        else if (state.account != null && state.destination != ShellDestination.MENU) {
            val destination = state.backStack.lastOrNull() ?: ShellDestination.MENU
            state = state.copy(destination = destination, backStack = state.backStack.dropLast(1))
            saveDestination()
        }
    }

    private fun saveDestination() {
        val epoch = generation.get()
        val account = state.account ?: return
        val destination = state.destination
        worker.execute {
            if (generation.get() == epoch) runCatching { local.destination(account.accountId, destination) }
            // Personal navigation is best effort; never turn a failed UI save into a game request.
        }
    }

    fun switchAccount(accountId: String) {
        if (!accountSwitcher || closed || state.busy || state.account == null ||
            state.account?.accountId == accountId || state.rememberedAccounts.none { it.identity.accountId == accountId }) return
        leaveAccount(revokeOutgoing = false, nextAccountId = accountId)
    }

    fun addAccount() {
        if (!accountSwitcher || closed || state.busy || state.account == null) return
        leaveAccount(revokeOutgoing = false)
    }

    fun logout() = leaveAccount(revokeOutgoing = true)

    /** One teardown for Logout, Switch and Add: fence callbacks, close private work and erase
     * private storage/downloads. Only explicit Logout revokes or forgets the outgoing credential. */
    private fun leaveAccount(revokeOutgoing: Boolean, nextAccountId: String? = null) {
        if (closed) return
        val retryingCleanup = state.logoutFailed
        reauthenticationContext = null
        clearPrivateWork()
        val oldToken = synchronized(ownerLock) {
            generation.incrementAndGet() // Fence old display AND worker-side storage callbacks first.
            token.getAndSet(null)
        }
        state = AuthState(restoring = false, busy = true, message = "Signing out on this device…")
        val epoch = generation.get()
        worker.execute {
            if (!retryingCleanup) logoutRevokeAttempted = false
            var outgoing = oldToken
            val next = try {
                if (revokeOutgoing) {
                    outgoing = outgoing ?: (store.load() as? StoredSession.Present)?.token
                    outgoing?.let { forget(it) }
                }
                local.logout(store)
                val saved = if (nextAccountId != null) store.remembered().firstOrNull {
                    it.account.identity.accountId == nextAccountId
                } else null
                if (saved != null) {
                    store.save(saved.token)
                    runBlocking { restoreSaved(epoch) }?.let(::withRemembered) ?: AuthState(restoring = false)
                } else withRemembered(AuthState(restoring = false))
            } catch (_: Exception) {
                AuthState(restoring = false, logoutFailed = true, error = true,
                    message = "Local sign-out could not finish. Retry before signing in.")
            }
            if (revokeOutgoing && !logoutRevokeAttempted) outgoing?.let {
                logoutRevokeAttempted = true
                bestEffortRevoke(it) // Never a local sign-out prerequisite.
            }
            post { if (!closed && generation.get() == epoch) state = next }
        }
    }

    fun close() {
        if (closed) return
        reauthenticationContext = null
        closed = true
        synchronized(ownerLock) { generation.incrementAndGet(); token.set(null) }
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
