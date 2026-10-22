package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLQueryComponent
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import java.io.Closeable
import java.net.URI

class LobbyFailure(val code: String) : Exception("Lobby request failed")
interface Lobbies : Closeable {
    suspend fun search(token: String, query: String): UserSearch
    suspend fun contacts(token: String, after: String? = null): ContactList
    suspend fun add(token: String, input: AddContact): AccountIdentity
    suspend fun create(token: String, input: CreateInvitation): GameSummary
    suspend fun games(token: String, invitations: Boolean = false, after: String? = null): GameList
    suspend fun head(token: String, id: String): GameSummary
}

/** Private HTTP transport, no redirect/caching or provider clients. Existing-game writes join I09b's socket. */
class LobbyApi(private val origin: String, private val client: HttpClient = AccountApi.client(),
    private val authenticationRequired: (String) -> Unit = {}) : Lobbies {
    init { require(origin.isEmpty() || URI(origin).let { it.host != null && it.rawUserInfo == null &&
        it.rawQuery == null && it.rawFragment == null && it.rawPath.isNullOrEmpty() &&
        (it.scheme == "https" || (BuildConfig.DEBUG && it.scheme == "http" && it.host == "127.0.0.1")) }) }
    override suspend fun search(token: String, query: String): UserSearch = call(token, "/v1/users?username=${query.encodeURLQueryComponent()}")
    override suspend fun contacts(token: String, after: String?): ContactList = call(token, page("contacts", after))
    override suspend fun add(token: String, input: AddContact): AccountIdentity = call(token, "/v1/contacts", DeckJson.format.encodeToString(input))
    override suspend fun create(token: String, input: CreateInvitation): GameSummary = call(token, "/v1/invitations", DeckJson.format.encodeToString(input))
    override suspend fun games(token: String, invitations: Boolean, after: String?): GameList = call(token, page(if (invitations) "invitations" else "games", after))
    override suspend fun head(token: String, id: String): GameSummary {
        if (!DeckValidation.uuid(id)) throw LobbyFailure("invalid_request")
        return call(token, "/v1/games/$id/head")
    }
    private fun page(route: String, after: String?): String {
        if (after != null && !DeckValidation.uuid(after)) throw LobbyFailure("invalid_request")
        return "/v1/$route" + (after?.let { "?after=$it" } ?: "")
    }
    private suspend inline fun <reified T> call(token: String, path: String, body: String? = null): T {
        if (origin.isEmpty()) throw LobbyFailure("not_configured")
        if (!AccountApi.validToken(token)) throw LobbyFailure("authentication_required")
        try {
            val response = client.request(origin + path) {
                method = if (body == null) HttpMethod.Get else HttpMethod.Post
                header("Authorization", "Bearer $token"); header("Accept", "application/json")
                if (body != null) { header("Content-Type", "application/json; charset=UTF-8"); setBody(body) }
            }
            if (response.status.value == 401) { authenticationRequired(token); throw LobbyFailure("authentication_required") }
            val bytes = response.bodyAsChannel().readRemaining(4L * 1024 * 1024 + 1).readByteArray()
            if (bytes.size > 4 * 1024 * 1024) throw LobbyFailure("invalid_response")
            val text = bytes.decodeToString(throwOnInvalidSequence = true)
            if (response.status.value !in 200..299)
                throw LobbyFailure(runCatching { DeckJson.format.decodeFromString<LobbyError>(text).code }.getOrDefault("unavailable"))
            return try { DeckJson.format.decodeFromString<T>(text) } catch (_: Exception) { throw LobbyFailure("invalid_response") }
        } catch (failure: LobbyFailure) { throw failure
        } catch (_: Exception) { throw LobbyFailure("network") }
    }
    override fun close() = client.close()
}
