package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicLong

enum class ResetPage { REQUEST, PASSWORD, INVALID }

/** Volatile, separate from the authenticated account: opening mail never signs out or changes identity. */
data class ResetState(
    val page: ResetPage,
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val checking: Boolean = false,
    val checkFailed: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
) { override fun toString() = "ResetState[redacted]" }

class ResetController(
    private val api: PasswordResets,
    private val origin: String,
    private val worker: ExecutorService,
    private val succeeded: () -> Unit,
    private val post: (() -> Unit) -> Unit,
) {
    var state by mutableStateOf<ResetState?>(null)
        private set
    private var token: String? = null
    private val generation = AtomicLong()
    private var closed = false

    fun request(email: String = "") {
        if (closed) return
        generation.incrementAndGet(); token = null
        state = ResetState(ResetPage.REQUEST, email = email)
    }

    fun open(link: String?) {
        if (closed) return
        val parsed = ResetLinks.token(link, origin)
        if (parsed != null && parsed == token && state?.page == ResetPage.PASSWORD) return
        generation.incrementAndGet(); token = parsed; state = null
        if (parsed == null) invalid() else checkLink()
    }

    private fun invalid() {
        token = null
        state = ResetState(ResetPage.INVALID, message = "This reset link is invalid, expired or already used.", error = true)
    }

    fun checkLink() {
        if (closed || state?.busy == true) return
        val secret = token ?: return
        state = ResetState(ResetPage.PASSWORD, busy = true, checking = true)
        work({ api.checkResetLink(secret) }) {
            state = ResetState(ResetPage.PASSWORD)
        }
    }

    fun edit(email: String = state?.email.orEmpty(), password: String = state?.password.orEmpty()) {
        val current = state ?: return
        if (!closed && !current.busy && !current.checking && current.page != ResetPage.INVALID)
            state = current.copy(email = email, password = password, message = null, error = false)
    }

    fun submit() {
        val input = state ?: return
        if (closed || input.busy || input.checking || input.page == ResetPage.INVALID) return
        val secret = token
        state = input.copy(busy = true, message = null)
        work({
            if (input.page == ResetPage.REQUEST) api.requestReset(input.email)
            else api.resetPassword(requireNotNull(secret), input.password)
        }) {
            if (input.page == ResetPage.REQUEST) state = input.copy(
                message = "If that email is registered, a reset link will be sent.", password = "", error = false)
            else { dismiss(); succeeded() }
        }
    }

    private fun work(action: suspend () -> Unit, success: () -> Unit) {
        val epoch = generation.get()
        worker.execute {
            if (generation.get() != epoch) return@execute
            val failure = try { runBlocking { action() }; null }
                catch (error: AccountFailure) { error }
                catch (_: Exception) { AccountFailure("network") }
            post {
                if (!closed && generation.get() == epoch) {
                    if (failure == null) success()
                    else if (failure.code == "invalid_reset_link") invalid()
                    else state = state?.copy(busy = false, checkFailed = state?.checking == true,
                        message = AuthController.errorMessage(failure), error = true)
                }
            }
        }
    }

    fun dismiss() {
        generation.incrementAndGet(); token = null; state = null
    }

    fun close() {
        if (closed) return
        closed = true; dismiss()
        worker.execute { api.close() }; worker.shutdown()
    }
}
