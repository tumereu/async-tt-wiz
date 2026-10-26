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

class PodFailure(val code: String) : Exception("Pod request failed: $code")

interface Pods : Closeable {
    suspend fun list(token: String): PodList
    suspend fun detail(token: String, id: String): PodDetail
    suspend fun create(token: String, input: CreatePod): PodDetail
    suspend fun update(token: String, id: String, input: UpdatePod): PodDetail
    suspend fun invite(token: String, id: String, input: InviteToPod): PodDetail
    suspend fun accept(token: String, id: String, input: PodRequest): PodDetail
    suspend fun decline(token: String, id: String, input: PodRequest): PodAcknowledged
    suspend fun start(token: String, id: String, input: PodRequest): PodDetail
    suspend fun pick(token: String, id: String, input: PickPod): PodDetail
    suspend fun gridSelect(token: String, id: String, input: SelectPodGrid): PodDetail
    suspend fun move(token: String, id: String, input: MovePodCopy): PodDetail
    suspend fun basics(token: String, id: String, input: SetPodBasics): PodDetail
    suspend fun submit(token: String, id: String, input: PodDraftRequest): PodDetail
    suspend fun cancel(token: String, id: String, input: PodDraftRequest): PodDetail
    suspend fun leave(token: String, id: String, input: PodRequest): PodAcknowledged
    suspend fun destroy(token: String, id: String, input: PodRequest): PodAcknowledged
}

class PodApi(private val origin: String, private val client: HttpClient = AccountApi.client(),
    private val authenticationRequired: (String) -> Unit = {}) : Pods {
    init { require(origin.isEmpty() || URI(origin).let { it.host != null && it.rawUserInfo == null &&
        it.rawQuery == null && it.rawFragment == null && it.rawPath.isNullOrEmpty() &&
        (it.scheme == "https" || (BuildConfig.DEBUG && it.scheme == "http" && it.host == "127.0.0.1")) }) }
    private fun path(id: String, action: String = ""): String {
        if (!DeckValidation.uuid(id)) throw PodFailure("invalid_request")
        return "/v1/pods/$id$action"
    }
    override suspend fun list(token: String): PodList = call(token, "/v1/pods")
    override suspend fun detail(token: String, id: String): PodDetail = call(token, path(id))
    override suspend fun create(token: String, input: CreatePod): PodDetail = call(token, "/v1/pods", DeckJson.format.encodeToString(input))
    override suspend fun update(token: String, id: String, input: UpdatePod): PodDetail = call(token, path(id), DeckJson.format.encodeToString(input), HttpMethod.Put)
    override suspend fun invite(token: String, id: String, input: InviteToPod): PodDetail = call(token, path(id, "/invitations"), DeckJson.format.encodeToString(input))
    override suspend fun accept(token: String, id: String, input: PodRequest): PodDetail = call(token, path(id, "/accept"), DeckJson.format.encodeToString(input))
    override suspend fun decline(token: String, id: String, input: PodRequest): PodAcknowledged = call(token, path(id, "/decline"), DeckJson.format.encodeToString(input))
    override suspend fun start(token: String, id: String, input: PodRequest): PodDetail = call(token, path(id, "/start"), DeckJson.format.encodeToString(input))
    override suspend fun pick(token: String, id: String, input: PickPod): PodDetail = call(token, path(id, "/pick"), DeckJson.format.encodeToString(input))
    override suspend fun gridSelect(token: String, id: String, input: SelectPodGrid): PodDetail = call(token, path(id, "/grid/select"), DeckJson.format.encodeToString(input))
    override suspend fun move(token: String, id: String, input: MovePodCopy): PodDetail = call(token, path(id, "/build/move"), DeckJson.format.encodeToString(input), HttpMethod.Put)
    override suspend fun basics(token: String, id: String, input: SetPodBasics): PodDetail = call(token, path(id, "/build/basics"), DeckJson.format.encodeToString(input), HttpMethod.Put)
    override suspend fun submit(token: String, id: String, input: PodDraftRequest): PodDetail = call(token, path(id, "/submit"), DeckJson.format.encodeToString(input))
    override suspend fun cancel(token: String, id: String, input: PodDraftRequest): PodDetail = call(token, path(id, "/cancel"), DeckJson.format.encodeToString(input))
    override suspend fun leave(token: String, id: String, input: PodRequest): PodAcknowledged = call(token, path(id, "/leave"), DeckJson.format.encodeToString(input))
    override suspend fun destroy(token: String, id: String, input: PodRequest): PodAcknowledged = call(token, path(id), DeckJson.format.encodeToString(input), HttpMethod.Delete)
    private suspend inline fun <reified T> call(token: String, path: String, body: String? = null, verb: HttpMethod? = null): T {
        if (origin.isEmpty()) throw PodFailure("not_configured")
        if (!AccountApi.validToken(token)) throw PodFailure("authentication_required")
        try {
            val response = client.request(origin + path) {
                method = verb ?: if (body == null) HttpMethod.Get else HttpMethod.Post
                header("Authorization", "Bearer $token"); header("Accept", "application/json")
                if (body != null) { header("Content-Type", "application/json; charset=UTF-8"); setBody(body) }
            }
            if (response.status.value == 401) { authenticationRequired(token); throw PodFailure("authentication_required") }
            val bytes = response.bodyAsChannel().readRemaining(16L * 1024 * 1024 + 1).readByteArray()
            if (bytes.size > 16 * 1024 * 1024) throw PodFailure("invalid_response")
            val text = bytes.decodeToString(throwOnInvalidSequence = true)
            if (response.status.value !in 200..299)
                throw PodFailure(runCatching { DeckJson.format.decodeFromString<PodError>(text).code }.getOrDefault("unavailable"))
            return try { DeckJson.format.decodeFromString<T>(text) } catch (_: Exception) { throw PodFailure("invalid_response") }
        } catch (failure: PodFailure) { throw failure
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: Exception) { throw PodFailure("network") }
    }
    override fun close() = client.close()
}
