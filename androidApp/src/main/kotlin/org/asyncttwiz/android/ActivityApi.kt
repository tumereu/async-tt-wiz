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

class ActivityFetchFailure(val status: Int, val retryAfter: Long = 0) : Exception("Activity unavailable") {
    val transient get() = status == 0 || status == 429 || status >= 500
}
class ActivityApi(private val origin: String, private val client: HttpClient = AccountApi.client()) : Closeable {
    init { require(origin.isEmpty() || URI(origin).let {
        it.host != null && it.rawUserInfo == null && it.rawPath.isNullOrEmpty() && it.rawQuery == null && it.rawFragment == null &&
            (it.scheme == "https" || (BuildConfig.DEBUG && it.scheme == "http" && it.host == "127.0.0.1"))
    }) }
    suspend fun register(token: String, input: RegisterInstallation): ActivityRegistration =
        ActivityWire.json.decodeFromString(call(token, HttpMethod.Put, "/activity/installations", ActivityWire.json.encodeToString(input)))
    suspend fun page(token: String, cursor: String?): ActivityPage = ActivityWire.json.decodeFromString(
        call(token, HttpMethod.Get, "/activity" + (cursor?.let { "?after=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")))
    suspend fun target(token: String, route: ActivityRoute): ActivityTarget = ActivityWire.json.decodeFromString(
        call(token, HttpMethod.Get, "/activity/target?kind=${route.kind.name}&game=${route.gameId}&identity=${route.identity}"))
    suspend fun heartbeat(token: String, presence: GamePresence) {
        call(token, HttpMethod.Put, "/activity/presence", ActivityWire.json.encodeToString(presence))
    }
    private suspend fun call(token: String, method: HttpMethod, path: String, body: String? = null): String {
        if (origin.isEmpty()) throw ActivityFetchFailure(400)
        if (!AccountApi.validToken(token)) throw ActivityFetchFailure(401)
        try {
            val response = client.request(origin + path) {
                this.method = method; header("Authorization", "Bearer $token"); header("Accept", "application/json")
                if (body != null) { header("Content-Type", "application/json; charset=UTF-8"); setBody(body) }
            }
            if (response.status.value !in 200..299) throw ActivityFetchFailure(response.status.value,
                retryAfter(response.headers["Retry-After"]))
            val bytes = response.bodyAsChannel().readRemaining(65537).readByteArray()
            if (bytes.size > 65536) throw ActivityFetchFailure(400)
            return bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (failure: ActivityFetchFailure) { throw failure
        } catch (_: Exception) { throw ActivityFetchFailure(0) }
    }
    override fun close() = client.close()
    companion object {
        fun retryAfter(value: String?, now: java.time.Instant = java.time.Instant.now()): Long =
            value?.toLongOrNull()?.coerceAtLeast(0) ?: runCatching {
                java.time.Duration.between(now, java.time.ZonedDateTime.parse(value,
                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).seconds.coerceAtLeast(0)
            }.getOrDefault(0)
    }
}
