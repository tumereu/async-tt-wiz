package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.ResolvedDeckEntry
import org.asyncttwiz.storage.definition as catalogDefinition
import java.io.Closeable
import java.net.URI

class DeckFailure(val code: String) : Exception("Deck request failed")

/** A deck's card as the phone makes it from its card records, the card the server builds for the same printing. */
internal fun ResolvedDeckEntry.definition(catalog: CatalogMetadata?): CardDefinition = catalogDefinition(catalog)

internal fun deckError(failure: Exception): String = when ((failure as? DeckFailure)?.code) {
    "not_found" -> "This saved deck is no longer available. Return to your collection to refresh."
    "invalid_cover" -> "Choose a card that is still in this deck."
    "invalid_deck" -> "Enter a deck name and check the quantities. Your pasted list is kept."
    "invalid_definition", "definitions_unavailable" -> "Card metadata could not be accepted. Edit the list or update card records and retry."
    "request_too_large" -> "This import exceeds the service transfer limit. Your pasted list is kept."
    "authentication_required" -> "Your session is no longer valid. Sign in again to access saved decks."
    "request_conflict" -> "This save identity conflicts with an earlier request. Edit the list or name before saving again."
    "try_later" -> "The service is busy. Wait a moment, then retry."
    else -> "Could not reach the deck service. Your input is kept. Check your connection and retry."
}

interface Decks : Closeable {
    suspend fun create(token: String, value: CreateDeck): SavedDeck
    suspend fun list(token: String, after: String? = null): DeckList
    suspend fun read(token: String, id: String): SavedDeck
    /** The card data of [digests] in deck [id], in one request per [CubeValidation.MAX_DEFINITIONS_PER_READ] cards, never
     * one per card (owner playtest 2026-11-06: that ran into the deck service's per-account limit). */
    suspend fun definitions(token: String, id: String, digests: List<String>): List<SubmittedDefinition>
    suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck
    suspend fun delete(token: String, id: String, value: DeleteDeck): DeckDeleted
    /** Draft decks, newest first. */
    suspend fun drafts(token: String): DeckList = DeckList(emptyList())
    /** "Save as deck": an independent ordinary copy. */
    suspend fun copy(token: String, id: String, value: CopyDeck): SavedDeck = throw DeckFailure("unsupported")
    /** I-D51: stores the deck's Automatum support for its revision (not an edit; draft decks too). */
}

/** Dedicated authenticated service transport; never shares provider caches or follows redirects. */
class DeckApi(private val origin: String, private val client: HttpClient = AccountApi.client(),
    private val authenticationRequired: (String) -> Unit = {}) : Decks, Cubes {
    init {
        require(origin.isEmpty() || URI(origin).let {
            it.host != null && it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null &&
                it.rawPath.isNullOrEmpty() && (it.scheme == "https" ||
                (BuildConfig.LOOPBACK_API && it.scheme == "http" && it.host == "127.0.0.1"))
        })
    }
    override suspend fun create(token: String, value: CreateCube): SavedCube =
        call(token, HttpMethod.Post, "/v1/cubes", DeckJson.format.encodeToString(value))
    override suspend fun update(token: String, id: String, value: UpdateCube): SavedCube {
        if (!DeckValidation.uuid(id)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Put, "/v1/cubes/$id", DeckJson.format.encodeToString(value))
    }
    override suspend fun delete(token: String, id: String, value: DeleteCube): CubeDeleted {
        if (!DeckValidation.uuid(id)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Delete, "/v1/cubes/$id", DeckJson.format.encodeToString(value))
    }
    override suspend fun listCubes(token: String, after: String?): CubeList {
        if (after != null && !DeckValidation.uuid(after)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Get, "/v1/cubes" + (after?.let { "?after=$it" } ?: ""))
    }
    override suspend fun readCube(token: String, id: String): SavedCube {
        if (!DeckValidation.uuid(id)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Get, "/v1/cubes/$id")
    }
    override suspend fun cubeDefinitions(token: String, id: String, value: ReadCubeDefinitions): SubmittedDefinitions {
        if (!DeckValidation.uuid(id) || !CubeValidation.definitionRead(value.digests)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Post, "/v1/cubes/$id/definitions", DeckJson.format.encodeToString(value))
    }
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
    override suspend fun drafts(token: String): DeckList = call(token, HttpMethod.Get, "/v1/decks?drafts=true")
    override suspend fun copy(token: String, id: String, value: CopyDeck): SavedDeck {
        if (!DeckValidation.uuid(id)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Post, "/v1/decks/$id/copy", DeckJson.format.encodeToString(value))
    }
    override suspend fun read(token: String, id: String): SavedDeck {
        if (!DeckValidation.uuid(id)) throw DeckFailure("invalid_response")
        return call(token, HttpMethod.Get, "/v1/decks/$id")
    }
    override suspend fun definitions(token: String, id: String, digests: List<String>): List<SubmittedDefinition> {
        if (!DeckValidation.uuid(id)) throw DeckFailure("invalid_response")
        return digests.distinct().chunked(CubeValidation.MAX_DEFINITIONS_PER_READ).flatMap { batch ->
            val value = ReadCubeDefinitions(batch)
            if (!CubeValidation.definitionRead(batch)) throw DeckFailure("invalid_response")
            call<SubmittedDefinitions>(token, HttpMethod.Post, "/v1/decks/$id/definitions", DeckJson.format.encodeToString(value)).definitions
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
        } catch (failure: Exception) {
            AppLog.warn("http", "${path.substringBefore('?')} failed", failure)
            throw DeckFailure("network")
        }
    }
    override fun close() = client.close()
    companion object { const val MAX_BODY = 16 * 1024 * 1024 }
}
