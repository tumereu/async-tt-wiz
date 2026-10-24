package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.AccountError
import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.CreateSession
import org.asyncttwiz.protocol.RegisterAccount
import org.asyncttwiz.protocol.SessionCreated
import org.asyncttwiz.protocol.RequestPasswordReset
import org.asyncttwiz.protocol.ResetPassword
import org.asyncttwiz.protocol.CheckPasswordResetLink
import java.io.Closeable
import java.net.URI

/** Fixed, local error vocabulary. Never display a response body/exception or log credentials. */
class AccountFailure(val code: String, val field: String? = null) : Exception("Account request failed")

interface Accounts : Closeable {
    suspend fun register(username: String, email: String, password: String): AccountIdentity
    suspend fun login(email: String, password: String): SessionCreated
    suspend fun identity(token: String): AccountIdentity
    suspend fun revoke(token: String)
}

interface PasswordResets : Closeable {
    suspend fun requestReset(email: String)
    suspend fun checkResetLink(token: String)
    suspend fun resetPassword(token: String, password: String)
}

class AccountApi(private val origin: String, private val client: HttpClient = client()) : Accounts, PasswordResets {
    init {
        require(origin.isEmpty() || URI(origin).let {
            it.host != null && it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null &&
                it.rawPath.isNullOrEmpty() && (it.scheme == "https" ||
                    (BuildConfig.DEBUG && it.scheme == "http" && it.host == "127.0.0.1"))
        })
    }

    override suspend fun register(username: String, email: String, password: String): AccountIdentity {
        validatePassword(password)
        validateScalars(username, "username")
        validateScalars(email, "email")
        return decode(call(HttpMethod.Post, "/accounts", AccountJson.format.encodeToString(RegisterAccount(username, email, password))))
    }

    override suspend fun login(email: String, password: String): SessionCreated {
        validatePassword(password)
        validateScalars(email, "email")
        return decode<SessionCreated>(call(HttpMethod.Post, "/sessions",
            AccountJson.format.encodeToString(CreateSession(email, password)))).also {
            if (!validToken(it.token)) throw AccountFailure("invalid_response")
        }
    }

    override suspend fun identity(token: String): AccountIdentity = decode(call(HttpMethod.Get, "/account", token = token))
    override suspend fun revoke(token: String) { call(HttpMethod.Delete, "/session", token = token) }

    internal suspend fun uploadCrashReport(token: String, report: org.asyncttwiz.protocol.CrashReport): Boolean {
        if (!BuildConfig.DEBUG) return false
        // Keep the complete trace locally, but don't send bodies the server would discard anyway.
        val bytes = report.body.toByteArray(Charsets.UTF_8)
        val body = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.IGNORE)
            .decode(java.nio.ByteBuffer.wrap(bytes, 0, minOf(bytes.size, 65_536))).toString()
        call(HttpMethod.Post, "/debug/crash-reports", AccountJson.format.encodeToString(report.copy(body = body)), token,
            discardRateLimited = true)
        return true
    }

    override suspend fun requestReset(email: String) {
        validateScalars(email, "email")
        call(HttpMethod.Post, "/password-reset-requests", AccountJson.format.encodeToString(RequestPasswordReset(email)))
    }

    override suspend fun checkResetLink(token: String) {
        if (!ResetLinks.canonicalToken(token)) throw AccountFailure("invalid_reset_link")
        call(HttpMethod.Post, "/password-reset-links", AccountJson.format.encodeToString(CheckPasswordResetLink(token)))
    }

    override suspend fun resetPassword(token: String, password: String) {
        if (!ResetLinks.canonicalToken(token)) throw AccountFailure("invalid_reset_link")
        validatePassword(password)
        call(HttpMethod.Post, "/password-resets", AccountJson.format.encodeToString(ResetPassword(token, password)))
    }

    private suspend fun call(method: HttpMethod, path: String, body: String? = null, token: String? = null,
        discardRateLimited: Boolean = false): String {
        if (origin.isEmpty()) throw AccountFailure("not_configured")
        if (token != null && !validToken(token)) throw AccountFailure("authentication_required")
        try {
            val response = client.request(origin + path) {
                this.method = method
                header("Accept", "application/json")
                if (body != null) { header("Content-Type", "application/json; charset=UTF-8"); setBody(body) }
                if (token != null) header("Authorization", "Bearer $token")
            }
            val bytes = response.bodyAsChannel().readRemaining(16_385).readByteArray()
            if (bytes.size > 16_384) throw AccountFailure("invalid_response")
            val text = bytes.decodeToString(throwOnInvalidSequence = true)
            if (discardRateLimited && response.status.value == 429) return ""
            if (response.status.value !in 200..299) {
                val error = runCatching { AccountJson.format.decodeFromString<AccountError>(text) }.getOrNull()
                throw AccountFailure(if (response.status.value == 401 && token != null) "authentication_required"
                    else error?.code ?: "unavailable", error?.field)
            }
            return text
        } catch (failure: AccountFailure) { throw failure
        } catch (_: Exception) { throw AccountFailure("network") }
    }

    private inline fun <reified T> decode(text: String): T = try {
        AccountJson.format.decodeFromString<T>(text)
    } catch (_: Exception) { throw AccountFailure("invalid_response") }

    override fun close() = client.close()

    companion object {
        fun client() = HttpClient(Android) {
            followRedirects = false // Never forward credentials to another origin.
            expectSuccess = false
            install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 15_000 }
            // No Logging, cookie jar, disk HTTP cache or global default Authorization header.
        }

        fun validToken(token: String) = Regex("[A-Za-z0-9_-]{43}").matches(token)

        fun validateScalars(value: String, field: String) {
            var index = 0
            while (index < value.length) {
                val c = value[index++]
                if (c.isHighSurrogate()) {
                    if (index == value.length || !value[index++].isLowSurrogate()) throw AccountFailure("invalid_field", field)
                } else if (c.isLowSurrogate()) throw AccountFailure("invalid_field", field)
            }
        }

        fun validatePassword(value: String) {
            validateScalars(value, "password")
            if (value.codePointCount(0, value.length) !in 8..256 || value.toByteArray(Charsets.UTF_8).size > 1024)
                throw AccountFailure("invalid_field", "password")
        }
    }
}
