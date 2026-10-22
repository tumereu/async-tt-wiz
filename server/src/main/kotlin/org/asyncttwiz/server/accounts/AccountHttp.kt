package org.asyncttwiz.server.accounts

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.AccountError
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.CreateSession
import org.asyncttwiz.protocol.RegisterAccount
import org.asyncttwiz.protocol.RequestPasswordReset
import org.asyncttwiz.protocol.ResetPassword
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.OwnershipFailure
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.access.ExceptionTranslationFilter
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.context.NullSecurityContextRepository
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.savedrequest.NullRequestCache
import org.springframework.web.filter.OncePerRequestFilter

/** Explicit stateless Spring Security chain on the public listener; no cookie login/CSRF session. */
class AccountHttp @JvmOverloads constructor(private val runtime: ServiceRuntime,
    private val resets: PasswordResetService? = null, settings: ResetMailSettings? = null) {
    private val service = AccountService(runtime)
    private val limits = AccountRateLimits()
    private val json = AccountJson.format
    private val form = if (resets != null && settings != null) ResetFormHttp(resets, settings, limits) else null
    private val paths = setOf("/accounts", "/sessions", "/session", "/account", "/password-reset-requests", "/password-resets",
        "/reset-password", "/reset-password/request")
    private val security = FilterChainProxy(DefaultSecurityFilterChain({ true }, listOf(
        SecurityContextHolderFilter(NullSecurityContextRepository()),
        object : OncePerRequestFilter() {
            override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
                if (request.requestURI == "/account") {
                    val principal = bearer(request)?.let(service::authenticate)
                    if (principal != null) {
                        SecurityContextHolder.getContext().authentication =
                            UsernamePasswordAuthenticationToken.authenticated(principal, null, emptyList())
                    }
                }
                chain.doFilter(request, response)
            }
        },
        ExceptionTranslationFilter({ _, response, _ -> error(response, 401, "authentication_required") }, NullRequestCache()),
        AuthorizationFilter { authentication, request ->
            AuthorizationDecision(request.requestURI != "/account" || authentication.get().isAuthenticated)
        },
    )))

    /** False means unknown path: the existing detail-free 404 remains authoritative. */
    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        if (request.requestURI !in paths) return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        // Secrets are header/POST-body only. No cookie authentication or forwarded-IP trust.
        try {
            if (!runtime.ready()) { error(response, 503, "unavailable"); return true }
            if (form?.handle(request, response) == true) return true
            security.doFilter(request, response) { _, _ -> dispatch(request, response) }
        } catch (failure: InvalidAccountField) {
            error(response, 400, "invalid_field", failure.field)
        } catch (_: SerializationException) {
            error(response, 400, "invalid_request")
        } catch (_: java.nio.charset.CharacterCodingException) {
            error(response, 400, "invalid_request")
        } catch (_: HashingBusy) {
            response.setHeader("Retry-After", "1")
            error(response, 503, "authentication_busy")
        } catch (_: OwnershipFailure) {
            error(response, 503, "unavailable")
        } catch (_: java.io.IOException) {
            error(response, 400, "invalid_request")
        } catch (_: RuntimeException) {
            // No parser, security, driver or framework exception may reflect a submitted secret.
            error(response, 500, "internal_error")
        }
        return true
    }

    private fun dispatch(request: HttpServletRequest, response: HttpServletResponse) {
        when (request.method to request.requestURI) {
            "POST" to "/password-reset-requests" -> {
                val reset = resets ?: run { error(response, 503, "unavailable"); return }
                if (!allow(response, "reset-ip", request.remoteAddr, 20, 3600)) return
                val input = json.decodeFromString<RequestPasswordReset>(body(request, response) ?: return)
                val email = AccountFields.email(input.email)
                if (!allow(response, "reset-email", email.key, 3, 3600)) return
                reset.request(input.email)
                response.status = 202
            }
            "POST" to "/password-resets" -> {
                val reset = resets ?: run { error(response, 503, "unavailable"); return }
                if (!allow(response, "reset-confirm-ip", request.remoteAddr, 20, 60)) return
                val input = json.decodeFromString<ResetPassword>(body(request, response) ?: return)
                if (reset.confirm(input.token, input.password)) response.status = 204
                else error(response, 400, "invalid_reset_link")
            }
            "POST" to "/accounts" -> {
                if (!allow(response, "register-ip", request.remoteAddr, 5, 3600)) return
                val body = body(request, response) ?: return
                val input = json.decodeFromString<RegisterAccount>(body)
                val account = service.register(input.username, input.email, input.password)
                if (account == null) error(response, 409, "account_unavailable")
                else reply(response, 201, json.encodeToString(account))
            }
            "POST" to "/sessions" -> {
                if (!allow(response, "login-ip", request.remoteAddr, 10, 60)) return
                val body = body(request, response) ?: return
                val input = json.decodeFromString<CreateSession>(body)
                val email = AccountFields.email(input.email)
                if (!allow(response, "login-email", email.key, 5, 60)) return
                val session = service.login(input.email, input.password)
                if (session == null) error(response, 401, "invalid_credentials")
                else reply(response, 201, json.encodeToString(session))
            }
            "GET" to "/account" -> {
                val principal = SecurityContextHolder.getContext().authentication!!.principal as AccountService.Principal
                reply(response, 200, json.encodeToString(principal.account))
            }
            "DELETE" to "/session" -> {
                val token = bearer(request)
                if (token == null || !service.validTokenShape(token)) error(response, 401, "authentication_required")
                else {
                    // Possession may only revoke this token. Revoked/unknown tokens get the same result.
                    service.revokeToken(token)
                    response.status = 204
                }
            }
            else -> { response.setHeader("Allow", when (request.requestURI) {
                "/accounts", "/sessions", "/password-reset-requests", "/password-resets" -> "POST"
                "/account" -> "GET"
                else -> "DELETE"
            }); error(response, 405, "method_not_allowed") }
        }
    }

    private fun bearer(request: HttpServletRequest): String? {
        val values = request.getHeaders("Authorization").toList()
        if (values.size != 1) return null
        val value = values.single()
        if (!value.startsWith("Bearer ", ignoreCase = true)) return null
        return value.substring(7)
    }

    private fun body(request: HttpServletRequest, response: HttpServletResponse): String? {
        val contentType = request.contentType.orEmpty().split(';').map(String::trim)
        if (!contentType.first().equals("application/json", true) || contentType.drop(1).any {
            !it.equals("charset=UTF-8", true) && !it.equals("charset=\"UTF-8\"", true)
        }) { error(response, 415, "json_required"); return null }
        val bytes = request.inputStream.readNBytes(8193)
        if (bytes.size > 8192) { error(response, 413, "request_too_large"); return null }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun allow(response: HttpServletResponse, scope: String, key: String, count: Int, seconds: Long): Boolean {
        if (limits.allow(scope, key, count, seconds)) return true
        response.setHeader("Retry-After", seconds.toString())
        error(response, 429, "try_later")
        return false
    }

    private fun reply(response: HttpServletResponse, status: Int, body: String) {
        response.status = status
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(body)
    }

    private fun error(response: HttpServletResponse, status: Int, code: String, field: String? = null) =
        reply(response, status, json.encodeToString(AccountError(code, field)))
}
