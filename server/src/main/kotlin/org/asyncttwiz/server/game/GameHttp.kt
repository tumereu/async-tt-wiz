package org.asyncttwiz.server.game

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.websocket.Endpoint
import jakarta.websocket.server.ServerContainer
import jakarta.websocket.server.ServerEndpointConfig
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.ServiceRuntime.UnavailabilityBoundary
import org.asyncttwiz.server.accounts.AccountService

/** Socket upgrade and ordinary authorized reads, never a second existing-game write ingress. */
class GameHttp(private val runtime: ServiceRuntime, private val hub: GameHub) {
    private val accounts = AccountService(runtime)
    private val json = GameWire.json
    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val path = request.requestURI
        val parts = path.split('/')
        val socket = path == "/v1/socket"
        val baselineRead = parts.size >= 5 && parts.take(3) == listOf("", "v1", "baselines")
        val read = parts.size >= 5 && parts.take(3) == listOf("", "v1", "games") &&
            parts[4] in listOf("revisions", "results", "results:lookup", "baselines", "plays", "chat")
        if (!socket && !read && !baselineRead) return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        fun error(status: Int, code: String) {
            response.status = status; response.contentType = "application/json;charset=UTF-8"
            response.writer.write(json.encodeToString(LobbyError(code)))
        }
        var boundary = UnavailabilityBoundary.GAME_HTTP_READY
        try {
            if (!runtime.ready()) {
                runtime.recordUnavailability(boundary, null)
                error(503, "unavailable"); return true
            }
            boundary = UnavailabilityBoundary.GAME_HTTP_AUTHENTICATION
            val token = request.getHeaders("Authorization").toList().singleOrNull()?.takeIf { it.startsWith("Bearer ", true) }?.substring(7)
            val principal = token?.let(accounts::authenticate)
            if (principal == null) { error(401, "authentication_required"); return true }
            if (socket) {
                boundary = UnavailabilityBoundary.GAME_HTTP_SOCKET
                if (request.method != "GET" || request.queryString != null) { error(400, "invalid_request"); return true }
                val container = request.servletContext.getAttribute("jakarta.websocket.server.ServerContainer") as ServerContainer
                val config = ServerEndpointConfig.Builder.create(Endpoint::class.java, "/v1/socket")
                    .configurator(object : ServerEndpointConfig.Configurator() {
                        override fun <T : Any> getEndpointInstance(clazz: Class<T>): T = clazz.cast(hub.endpoint(token, principal.account.accountId))
                    }).build()
                container.upgradeHttpToWebSocket(request, response, config, emptyMap())
                return true
            }
            if (baselineRead) {
                boundary = UnavailabilityBoundary.GAME_HTTP_BASELINE
                val manifest = parts.size == 5 && parts[4] == "manifest"
                val chunk = parts.size == 6 && parts[4] == "chunks"
                if (!(manifest || chunk)) { error(404, "not_found"); return true }
                if (request.method != "GET") { error(405, "method_not_allowed"); return true }
                if (manifest) {
                    response.contentType = "application/json;charset=UTF-8"
                    response.writer.write(json.encodeToString(hub.games.baselineManifest(principal, parts[3])))
                } else {
                    val bytes = hub.games.baselineChunk(principal, parts[3], parts[5].toIntOrNull() ?: throw IllegalArgumentException())
                    response.contentType = "application/gzip"; response.setContentLength(bytes.size)
                    response.outputStream.write(bytes)
                }
                return true
            }
            val lookup = parts.size == 5 && parts[4] == "results:lookup"
            val baseline = parts.size == 5 && parts[4] == "baselines"
            val revisions = parts.size == 5 && parts[4] == "revisions"
            val result = parts.size == 6 && parts[4] == "results"
            val history = parts.size == 7 && parts[4] == "plays" && parts[6] == "mutations"
            val chat = parts.size == 5 && parts[4] == "chat"
            if (!(lookup || revisions || result || baseline || history || chat)) { error(404, "not_found"); return true }
            if (request.method != if (lookup || baseline) "POST" else "GET") { error(405, "method_not_allowed"); return true }
            boundary = when {
                history -> UnavailabilityBoundary.GAME_HTTP_HISTORY
                revisions -> UnavailabilityBoundary.GAME_HTTP_REVISIONS
                baseline -> UnavailabilityBoundary.GAME_HTTP_BASELINE
                else -> UnavailabilityBoundary.GAME_HTTP_RESULTS
            }
            val body = when {
                chat -> json.encodeToString(hub.games.chat(principal, parts[3], request.getParameter("before"), request.getParameter("after")))
                history -> json.encodeToString(hub.games.history(principal, parts[3], parts[5], request.getParameter("after") ?: "0"))
                revisions -> json.encodeToString(hub.games.revisions(principal, parts[3], request.getParameter("after") ?: "0"))
                result -> json.encodeToString(hub.games.results(principal, parts[3], listOf(parts[5])).statuses.single())
                else -> {
                    if (!request.contentType.orEmpty().substringBefore(';').equals("application/json", true)) {
                        error(415, "json_required"); return true
                    }
                    val bytes = request.inputStream.readNBytes(128 * 1024 + 1)
                    if (bytes.size > 128 * 1024) { error(413, "request_too_large"); return true }
                    val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString()
                    val input = json.decodeFromString<LookupGameResults>(text)
                    if (baseline) json.encodeToString(hub.games.baseline(principal, parts[3], input.requestIds))
                    else json.encodeToString(hub.games.results(principal, parts[3], input.requestIds))
                }
            }
            boundary = UnavailabilityBoundary.GAME_HTTP_WRITE
            response.status = 200; response.contentType = "application/json;charset=UTF-8"; response.writer.write(body)
        } catch (failure: GameProtocolFailure) {
            error(when (failure.code) {
                "accessDenied" -> 404
                "baselineExpired" -> 410
                "baselineRateLimited" -> 429
                "baselineTooLarge" -> 413
                "requestIdentityMismatch" -> 409
                else -> 400
            }, failure.code)
        } catch (_: IllegalArgumentException) { error(400, "invalid_request")
        } catch (_: java.nio.charset.CharacterCodingException) { error(400, "invalid_request")
        } catch (failure: Exception) {
            runtime.recordUnavailability(boundary, failure)
            error(503, "unavailable")
        }
        return true
    }
}
