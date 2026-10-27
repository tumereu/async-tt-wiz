package org.asyncttwiz.server.activity

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.accounts.AccountRateLimits
import org.asyncttwiz.server.database.OwnershipFailure

/** Explicit route adapter for the public listener; no cookies, redirects or game mutation. */
class ActivityHttp(private val runtime: ServiceRuntime) {
    private val accounts = AccountService(runtime)
    private val activity = ActivityService(runtime)
    private val limits = AccountRateLimits()
    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val path = request.requestURI
        if (path !in listOf("/activity", "/activity/installations", "/activity/target", "/activity/presence")) return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        fun reply(status: Int, body: String = "") {
            response.status = status; response.contentType = "application/json;charset=UTF-8"; response.writer.write(body)
        }
        fun error(status: Int, code: String) = reply(status, ActivityWire.json.encodeToString(AccountError(code)))
        try {
            if (!runtime.ready()) { error(503, "unavailable"); return true }
            val token = request.getHeaders("Authorization").toList().singleOrNull()?.takeIf { it.startsWith("Bearer ", true) }?.substring(7)
            val principal = token?.let(accounts::authenticate)
            if (principal == null) { error(401, "authentication_required"); return true }
            if (!limits.allow("activity", principal.account.accountId, 600, 60)) {
                response.setHeader("Retry-After", "60"); error(429, "try_later"); return true
            }
            when (request.method to path) {
                "GET" to "/activity/target" -> {
                    val kind = ActivityKind.entries.find { it.name == request.getParameter("kind") }
                        ?: throw ActivityFailure(400, "invalid_request")
                    reply(200, ActivityWire.json.encodeToString(activity.target(principal, kind,
                        request.getParameter("game").orEmpty(), request.getParameter("identity").orEmpty())))
                }
                "GET" to "/activity" -> reply(200, ActivityWire.json.encodeToString(activity.page(principal, request.getParameter("after"))))
                "PUT" to "/activity/presence", "PUT" to "/activity/installations" -> {
                    val media = request.contentType.orEmpty().split(';').map(String::trim)
                    if (!media.first().equals("application/json", true) || media.drop(1).any {
                            !it.equals("charset=UTF-8", true) && !it.equals("charset=\"UTF-8\"", true)
                        }) { error(415, "json_required"); return true }
                    val bytes = request.inputStream.readNBytes(8193)
                    if (bytes.size > 8192) { error(413, "request_too_large"); return true }
                    val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    if (path == "/activity/presence") {
                        activity.heartbeat(principal, ActivityWire.json.decodeFromString(text)); reply(204)
                    } else reply(200, ActivityWire.json.encodeToString(activity.register(principal, ActivityWire.json.decodeFromString(text))))
                }
                "DELETE" to "/activity/installations" -> { activity.unregister(principal, request.getParameter("id").orEmpty()); reply(204) }
                else -> { response.setHeader("Allow", when (path) { "/activity/presence" -> "PUT"; "/activity/installations" -> "PUT, DELETE"; else -> "GET" }); error(405, "method_not_allowed") }
            }
        } catch (failure: ActivityFailure) { error(failure.status, failure.code)
        } catch (_: SerializationException) { error(400, "invalid_request")
        } catch (_: java.nio.charset.CharacterCodingException) { error(400, "invalid_request")
        } catch (_: java.io.IOException) { error(400, "invalid_request")
        } catch (_: OwnershipFailure) { error(503, "unavailable")
        } catch (_: RuntimeException) { error(500, "internal_error") }
        return true
    }
}
