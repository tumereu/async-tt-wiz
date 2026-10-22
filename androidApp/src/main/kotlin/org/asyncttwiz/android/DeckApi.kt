package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import java.io.Closeable
import java.net.URI

class DeckFailure(val code: String) : Exception("Deck request failed")

interface Decks : Closeable {
    suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted
    suspend fun create(token: String, value: CreateDeck): SavedDeck
    suspend fun list(token: String, after: String? = null): DeckList
    suspend fun read(token: String, id: String): SavedDeck
    suspend fun definition(token: String, id: String, digest: String): SubmittedDefinition
    suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck
    suspend fun delete(token: String, id: String, value: DeleteDeck): DeckDeleted
}

/** Dedicated authenticated service transport; never shares provider caches or follows redirects. */
class DeckApi(private val origin: String, private val client: HttpClient = AccountApi.client(),
    private val authenticationRequired: (String) -> Unit = {}) : Decks {
    init {
        require(origin.isEmpty() || URI(origin).let {
            it.host != null && it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null &&
                it.rawPath.isNullOrEmpty() && (it.scheme == "https" ||
                (BuildConfig.DEBUG && it.scheme == "http" && it.host == "127.0.0.1"))
        })
    }
    override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted =
        call(token, HttpMethod.Post, "/v1/definitions", DeckJson.format.encodeToString(value))
    override suspend fun create(token: String, value: CreateDeck): SavedDeck =
        call(token, HttpMethod.Post, "/v1/decks", DeckJson.format.encodeToString(value))
    override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck {
        if (!DeckValidation.uuid(id)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Put, "/v1/decks/$id", DeckJson.format.encodeToString(value))
    }
    override suspend fun delete(token: String, id: String, value: DeleteDeck): DeckDeleted {
        if (!DeckValidation.uuid(id)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Delete, "/v1/decks/$id", DeckJson.format.encodeToString(value))
    }
    override suspend fun list(token: String, after: String?): DeckList {
        if (after != null && !DeckValidation.uuid(after)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Get, "/v1/decks" + (after?.let { "?after=$it" } ?: ""))
    }
    override suspend fun read(token: String, id: String): SavedDeck {
        if (!DeckValidation.uuid(id)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Get, "/v1/decks/$id")
    }
    override suspend fun definition(token: String, id: String, digest: String): SubmittedDefinition {
        if (!DeckValidation.uuid(id) || !DeckValidation.digest(digest)) throw DeckFailure("invalid_response")
        return call<SubmittedDefinition>(token, HttpMethod.Get, "/v1/decks/$id/definitions/$digest").also {
            if (it.digest != digest || !DeckValidation.definition(it.definition) || DeckJson.digest(it.definition) != digest)
                throw DeckFailure("invalid_response")
        }
    }
    private suspend inline fun <reified T> call(token: String, method: HttpMethod, path: String, body: String? = null): T {
        if (origin.isEmpty()) throw DeckFailure("not_configured")
        if (!AccountApi.validToken(token)) throw DeckFailure("authentication_required")
        if (body != null && body.encodeToByteArray().size > MAX_BODY) throw DeckFailure("request_too_large")
        try {
            val response = client.request(origin + path) {
                this.method = method
                header("Accept", "application/json"); header("Authorization", "Bearer $token")
                if (body != null) { header("Content-Type", "application/json; charset=UTF-8"); setBody(body) }
            }
            // Status is authoritative even if a gateway supplies a missing/malformed error body.
            if (response.status.value == 401) {
                authenticationRequired(token)
                throw DeckFailure("authentication_required")
            }
            val bytes = response.bodyAsChannel().readRemaining(MAX_BODY.toLong() + 1).readByteArray()
            if (bytes.size > MAX_BODY) throw DeckFailure("invalid_response")
            val text = bytes.decodeToString(throwOnInvalidSequence = true)
            if (response.status.value !in 200..299) {
                val error = runCatching { DeckJson.format.decodeFromString<DeckError>(text) }.getOrNull()
                throw DeckFailure(error?.code ?: "unavailable")
            }
            return try { DeckJson.format.decodeFromString<T>(text) } catch (_: Exception) { throw DeckFailure("invalid_response") }
        } catch (failure: DeckFailure) { throw failure
        } catch (_: Exception) { throw DeckFailure("network") }
    }
    override fun close() = client.close()
    companion object { const val MAX_BODY = 16 * 1024 * 1024 }
}
