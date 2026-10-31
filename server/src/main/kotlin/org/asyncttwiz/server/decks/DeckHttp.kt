package org.asyncttwiz.server.decks

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

/** Ordinary complete HTTP requests. No upload sessions, provider access, game, or server artwork fetch. */
class DeckHttp(private val runtime: ServiceRuntime) {
    private val accounts = AccountService(runtime)
    private val decks = DeckService(runtime)
    private val cubes = org.asyncttwiz.server.cubes.CubeService(runtime)
    private val limits = AccountRateLimits()
    private val json = DeckJson.format
    // An HTTP resource bound, not a card-count/deck-legality limit or an application chunk protocol.
    private val bodyLimit = 16 * 1024 * 1024

    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val path = request.requestURI
        val cube = path == "/v1/cubes" || path.startsWith("/v1/cubes/")
        if (!cube && path != "/v1/definitions" && path != "/v1/decks" && !path.startsWith("/v1/decks/")) return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        try {
            if (!runtime.admitting()) { error(response, 503, "unavailable"); return true }
            val values = request.getHeaders("Authorization").toList()
            val token = values.singleOrNull()?.takeIf { it.startsWith("Bearer ", true) }?.substring(7)
            val principal = token?.let(accounts::authenticate)
            if (principal == null) { error(response, 401, "authentication_required"); return true }
            if (!limits.allow("deck-http", principal.account.accountId, 600, 60)) {
                response.setHeader("Retry-After", "60"); error(response, 429, "try_later"); return true
            }
            val parts = path.removePrefix("/v1/").split('/')
            val allowed = when {
                path == "/v1/definitions" -> "POST"
                path == "/v1/decks" || path == "/v1/cubes" -> "GET, POST"
                parts.size == 2 -> "GET, PUT, DELETE"
                cube && parts.size == 3 && parts[2] == "definitions" -> "POST"
                parts.size == 4 && parts[2] == "definitions" -> "GET"
                !cube && parts.size == 3 && parts[2] == "copy" -> "POST"
                else -> { error(response, 404, "not_found"); return true }
            }
            if (request.method !in allowed.split(", ")) {
                response.setHeader("Allow", allowed); error(response, 405, "method_not_allowed"); return true
            }
            val result = when {
                request.method in listOf("POST", "PUT", "DELETE") -> {
                    val text = body(request, response) ?: return true
                    if (cube) when (request.method) {
                        "PUT" -> cubes.update(principal, parts[1], json.decodeFromString<UpdateCube>(text))
                        "DELETE" -> cubes.delete(principal, parts[1], json.decodeFromString<DeleteCube>(text))
                        else -> if (parts.size == 3) cubes.definitions(principal, parts[1], json.decodeFromString<ReadCubeDefinitions>(text))
                            else cubes.create(principal, json.decodeFromString<CreateCube>(text))
                    }
                    else if (request.method == "PUT") decks.update(principal, parts[1], json.decodeFromString<UpdateDeck>(text))
                    else if (request.method == "DELETE") decks.delete(principal, parts[1], json.decodeFromString<DeleteDeck>(text))
                    else if (parts.size == 3) decks.copy(principal, parts[1], json.decodeFromString<CopyDeck>(text))
                    else if (path == "/v1/definitions") decks.submit(principal, json.decodeFromString<SubmitDefinitions>(text))
                    else decks.create(principal, json.decodeFromString<CreateDeck>(text))
                }
                path == "/v1/cubes" -> cubes.list(principal, request.getParameter("after"))
                cube -> cubes.read(principal, parts[1], parts.getOrNull(3))
                path == "/v1/decks" -> decks.list(principal, request.getParameter("after"), request.getParameter("drafts") == "true")
                else -> decks.read(principal, parts[1], parts.getOrNull(3))
            }
            reply(response, result)
        } catch (_: SerializationException) { error(response, 400, "invalid_request")
        } catch (_: java.nio.charset.CharacterCodingException) { error(response, 400, "invalid_request")
        } catch (_: java.io.IOException) { error(response, 400, "invalid_request")
        } catch (_: OwnershipFailure) { error(response, 503, "unavailable")
        } catch (_: RuntimeException) { error(response, 500, "internal_error") }
        return true
    }

    private fun body(request: HttpServletRequest, response: HttpServletResponse): String? {
        val media = request.contentType.orEmpty().split(';').map(String::trim)
        if (!media.first().equals("application/json", true) || media.drop(1).any {
            !it.equals("charset=UTF-8", true) && !it.equals("charset=\"UTF-8\"", true)
        }) { error(response, 415, "json_required"); return null }
        val bytes = request.inputStream.readNBytes(bodyLimit + 1)
        if (bytes.size > bodyLimit) { error(response, 413, "request_too_large"); return null }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun error(response: HttpServletResponse, status: Int, code: String) =
        reply(response, DeckService.Result(status, json.encodeToString(DeckError(code))))
    private fun reply(response: HttpServletResponse, result: DeckService.Result) {
        response.status = result.status
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(result.body)
    }
}
