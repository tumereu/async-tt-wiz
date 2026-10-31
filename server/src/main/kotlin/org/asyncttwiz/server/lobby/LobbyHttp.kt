package org.asyncttwiz.server.lobby

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

class LobbyHttp(private val runtime: ServiceRuntime) {
    private val accounts = AccountService(runtime)
    private val lobby = LobbyService(runtime)
    private val limits = AccountRateLimits()
    private val json = DeckJson.format
    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val path = request.requestURI
        if (path !in listOf("/v1/users", "/v1/contacts", "/v1/games", "/v1/invitations") && !path.startsWith("/v1/games/") && !path.startsWith("/v1/contacts/")) return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        fun reply(result: LobbyService.Result) {
            response.status = result.status; response.contentType = "application/json;charset=UTF-8"; response.writer.write(result.body)
        }
        fun error(status: Int, code: String) = reply(LobbyService.Result(status, json.encodeToString(LobbyError(code))))
        try {
            if (!runtime.admitting()) { error(503, "unavailable"); return true }
            val token = request.getHeaders("Authorization").toList().singleOrNull()?.takeIf { it.startsWith("Bearer ", true) }?.substring(7)
            val principal = token?.let(accounts::authenticate)
            if (principal == null) { error(401, "authentication_required"); return true }
            if (!limits.allow("lobby-http", principal.account.accountId, 600, 60)) {
                response.setHeader("Retry-After", "60"); error(429, "try_later"); return true
            }
            val parts = path.split('/')
            if (path.startsWith("/v1/games/") && (parts.size != 5 || parts[4] != "head")) {
                error(404, "not_found"); return true
            }
            val removing = path.startsWith("/v1/contacts/")
            if (removing && (parts.size != 4 || !DeckValidation.uuid(parts[3]))) {
                error(404, "not_found"); return true
            }
            val allowed = if (removing) listOf("DELETE") else if (path in listOf("/v1/contacts", "/v1/invitations")) listOf("GET", "POST") else listOf("GET")
            if (request.method !in allowed) {
                response.setHeader("Allow", allowed.joinToString(", ")); error(405, "method_not_allowed"); return true
            }
            val result = if (removing) lobby.remove(principal, parts[3]) else if (request.method == "POST") {
                val media = request.contentType.orEmpty().split(';').map(String::trim)
                if (!media.first().equals("application/json", true) || media.drop(1).any {
                        !it.equals("charset=UTF-8", true) && !it.equals("charset=\"UTF-8\"", true)
                    }) { error(415, "json_required"); return true }
                val bytes = request.inputStream.readNBytes(128 * 1024 + 1)
                if (bytes.size > 128 * 1024) { error(413, "request_too_large"); return true }
                val body = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                if (path == "/v1/contacts") lobby.add(principal, json.decodeFromString<AddContact>(body))
                else lobby.create(principal, json.decodeFromString<CreateInvitation>(body))
            } else when (path) {
                "/v1/users" -> lobby.search(principal, request.getParameter("username").orEmpty())
                "/v1/contacts" -> lobby.contacts(principal, request.getParameter("after"))
                "/v1/games", "/v1/invitations" -> lobby.list(principal, request.getParameter("after"), path == "/v1/invitations")
                else -> lobby.head(principal, parts[3])
            }
            reply(result)
        } catch (_: SerializationException) { error(400, "invalid_request")
        } catch (_: java.nio.charset.CharacterCodingException) { error(400, "invalid_request")
        } catch (_: java.io.IOException) { error(400, "invalid_request")
        } catch (_: OwnershipFailure) { error(503, "unavailable")
        } catch (_: RuntimeException) { error(500, "internal_error") }
        return true
    }
}
