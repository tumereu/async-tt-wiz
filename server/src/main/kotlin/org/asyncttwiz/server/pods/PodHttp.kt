package org.asyncttwiz.server.pods

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountRateLimits
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.OwnershipFailure
import org.asyncttwiz.server.decks.DeckService.Result

class PodHttp(private val runtime: ServiceRuntime) {
    private val accounts = AccountService(runtime)
    private val pods = PodService(runtime)
    private val limits = AccountRateLimits()
    private val json = DeckJson.format

    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val path = request.requestURI
        if (path != "/v1/pods" && !path.startsWith("/v1/pods/")) return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        try {
            if (!runtime.ready()) { error(response, 503, "unavailable"); return true }
            val token = request.getHeaders("Authorization").toList().singleOrNull()
                ?.takeIf { it.startsWith("Bearer ", true) }?.substring(7)
            val principal = token?.let(accounts::authenticate)
            if (principal == null) { error(response, 401, "authentication_required"); return true }
            if (!limits.allow("pod-http", principal.account.accountId, 600, 60)) {
                response.setHeader("Retry-After", "60"); error(response, 429, "try_later"); return true
            }
            val parts = path.removePrefix("/v1/pods").removePrefix("/").split('/')
            val id = parts[0]
            val action = parts.drop(1).joinToString("/")
            val allowed = when {
                path == "/v1/pods" -> "GET, POST"
                parts.size == 1 && id.isNotEmpty() -> "GET, PUT, DELETE"
                action in listOf("invitations", "accept", "decline", "start", "pick", "grid/select", "submit", "leave", "cancel", "challenge") -> "POST"
                action in listOf("build/move", "build/basics") -> "PUT"
                else -> { error(response, 404, "not_found"); return true }
            }
            if (request.method !in allowed.split(", ")) {
                response.setHeader("Allow", allowed); error(response, 405, "method_not_allowed"); return true
            }
            val result = if (request.method == "GET") {
                if (path == "/v1/pods") pods.list(principal) else pods.read(principal, id)
            } else {
                val media = request.contentType.orEmpty().split(';').map(String::trim)
                if (!media.first().equals("application/json", true) || media.drop(1).any {
                    !it.equals("charset=UTF-8", true) && !it.equals("charset=\"UTF-8\"", true)
                }) { error(response, 415, "json_required"); return true }
                // Only source creation/replacement needs room for an entire set and its definitions.
                val maxBytes = if ((path == "/v1/pods" && request.method == "POST") ||
                    (parts.size == 1 && request.method == "PUT")) 8 * 1024 * 1024 else 65536
                val bytes = request.inputStream.readNBytes(maxBytes + 1)
                if (bytes.size > maxBytes) { error(response, 413, "request_too_large"); return true }
                val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                when {
                    path == "/v1/pods" -> pods.create(principal, json.decodeFromString<CreatePod>(text))
                    request.method == "DELETE" -> pods.destroy(principal, id, json.decodeFromString<PodRequest>(text))
                    action == "" -> pods.update(principal, id, json.decodeFromString<UpdatePod>(text))
                    action == "invitations" -> pods.invite(principal, id, json.decodeFromString<InviteToPod>(text))
                    action == "accept" || action == "decline" -> pods.invitation(principal, id, json.decodeFromString<PodRequest>(text), action == "accept")
                    action == "start" -> pods.start(principal, id, json.decodeFromString<PodRequest>(text))
                     action == "pick" -> pods.pick(principal, id, json.decodeFromString<PickPod>(text))
                     action == "grid/select" -> pods.selectGrid(principal, id, json.decodeFromString<SelectPodGrid>(text))
                    action == "build/move" -> pods.move(principal, id, json.decodeFromString<MovePodCopy>(text))
                    action == "build/basics" -> pods.basics(principal, id, json.decodeFromString<SetPodBasics>(text))
                     action == "submit" -> pods.submit(principal, id, json.decodeFromString<PodDraftRequest>(text))
                     action == "challenge" -> pods.challenge(principal, id, json.decodeFromString<ChallengeFromPod>(text))
                    action == "leave" -> pods.leave(principal, id, json.decodeFromString<PodRequest>(text))
                    else -> pods.cancel(principal, id, json.decodeFromString<PodDraftRequest>(text))
                }
            }
            reply(response, result)
        } catch (_: SerializationException) { error(response, 400, "invalid_request")
        } catch (_: java.nio.charset.CharacterCodingException) { error(response, 400, "invalid_request")
        } catch (_: java.io.IOException) { error(response, 400, "invalid_request")
        } catch (_: OwnershipFailure) { error(response, 503, "unavailable")
        } catch (_: RuntimeException) { error(response, 500, "internal_error") }
        return true
    }

    private fun error(response: HttpServletResponse, status: Int, code: String) = reply(response, Result(status, json.encodeToString(PodError(code))))
    private fun reply(response: HttpServletResponse, result: Result) {
        response.status = result.status
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(result.body)
    }
}
