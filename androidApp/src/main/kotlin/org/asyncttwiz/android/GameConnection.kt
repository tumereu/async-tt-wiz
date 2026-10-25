package org.asyncttwiz.android

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.asyncttwiz.protocol.*
import kotlinx.serialization.encodeToString
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.nio.file.Files
import java.io.InputStream
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.decodeFromStream
import org.asyncttwiz.storage.StoredPayload

data class GameplayTreeTransfer(val manifest: GameplayManifest, val plan: TreeDownloadPlan,
    val openChunk: (Int) -> InputStream)

/** One ordered socket. Credentials are headers only; redirects never forward them. */
interface GameConnection : AutoCloseable {
    interface Listener {
        fun opened()
        fun message(message: GameServerMessage)
        fun disconnected(unauthorized: Boolean = false)
        fun disconnected(unauthorized: Boolean, cause: String) = disconnected(unauthorized)
    }
    fun connect(token: String, listener: Listener)
    fun send(message: GameClientMessage): Boolean
    fun disconnect()
    /** Read-only accepted transfer. No original is retried or rejected by an HTTP absence. */
    fun baseline(gameId: String, requestIds: List<String>, complete: (GameplayBaseline?) -> Unit): Boolean = false
    fun treeTransfer(gameId: String, requestIds: List<String>, complete: (GameplayTreeTransfer?) -> Unit): Boolean = false
    fun chatPage(gameId: String, before: String? = null, after: String? = null, complete: (ChatPage?) -> Unit): Boolean = false
}

class SocketGameConnection(private val origin: String) : GameConnection {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    @Volatile private var token: String? = null
    private val reads = Executors.newSingleThreadExecutor()
    init {
        require(origin.isEmpty() || URI(origin).let { it.host != null && it.rawUserInfo == null &&
            it.rawQuery == null && it.rawFragment == null && it.rawPath.isNullOrEmpty() &&
            (it.scheme == "https" || (BuildConfig.DEBUG && it.scheme == "http" && it.host == "127.0.0.1")) })
    }
    override fun connect(token: String, listener: GameConnection.Listener) {
        disconnect()
        if (origin.isEmpty() || !AccountApi.validToken(token)) { listener.disconnected(false, "invalidConnectionConfiguration"); return }
        this.token = token
        socket = client.newWebSocket(Request.Builder().url(origin + "/v1/socket")
            .header("Authorization", "Bearer $token").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.opened()
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching {
                    require(text.toByteArray().size <= 1048576)
                    GameWire.json.decodeFromString<GameServerMessage>(text)
                }.getOrNull()
                if (message == null) { webSocket.cancel(); listener.disconnected(false, "invalidServerMessage") } else listener.message(message)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { webSocket.cancel(); listener.disconnected(false, "unexpectedBinaryMessage") }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                listener.disconnected(false, "socketClose:$code:${reason.take(123)}")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.disconnected(response?.code == 401,
                if (t is java.net.SocketTimeoutException && t.message?.contains("ping", ignoreCase = true) == true) "socketPingTimeout"
                else "socketFailure:${t.javaClass.simpleName}:http=${response?.code}")
        })
    }
    override fun send(message: GameClientMessage) = socket?.send(GameWire.json.encodeToString(message)) == true
    override fun chatPage(gameId: String, before: String?, after: String?, complete: (ChatPage?) -> Unit): Boolean {
        val credential = token ?: return false
        if (!DeckValidation.uuid(gameId) || (before != null && after != null)) return false
        val cursor = before ?: after
        if (cursor != null && runCatching { GameWire.ordinal(cursor) }.isFailure) return false
        reads.execute {
            val page = runCatching {
                check(token == credential)
                val query = if (before != null) "?before=$before" else if (after != null) "?after=$after" else ""
                client.newCall(Request.Builder().url("$origin/v1/games/$gameId/chat$query")
                    .header("Authorization", "Bearer $credential").build()).execute().use { response ->
                    require(response.isSuccessful)
                    GameWire.json.decodeFromString<ChatPage>(requireNotNull(response.body).byteStream()
                        .bounded(ChatPaging.MAX_BYTES).toString(Charsets.UTF_8)).also {
                        require(it.gameId == gameId && it.messages.size <= ChatPaging.SIZE)
                    }
                }
            }.getOrNull()
            complete(page)
        }
        return true
    }
    override fun treeTransfer(gameId: String, requestIds: List<String>, complete: (GameplayTreeTransfer?) -> Unit): Boolean {
        val credential = token ?: return false
        if (!DeckValidation.uuid(gameId)) return false
        reads.execute {
            val result = runCatching {
                fun request(path: String, body: RequestBody? = null): Response {
                    check(token == credential)
                    val builder = Request.Builder().url(origin + path).header("Authorization", "Bearer $credential")
                    if (body != null) builder.post(body)
                    return client.newCall(builder.build()).execute().also { if (!it.isSuccessful) { it.close(); error("Baseline unavailable") } }
                }
                val manifest = request("/v1/games/$gameId/baselines", GameWire.json.encodeToString(LookupGameResults(requestIds))
                    .toRequestBody("application/json".toMediaType())).use {
                    GameWire.json.decodeFromString<GameplayManifest>(requireNotNull(it.body).byteStream()
                        .bounded(GameplaySync.MAX_CHUNK_BYTES).toString(Charsets.UTF_8)) }
                GameplaySync.validate(manifest)
                require(manifest.gameId == gameId && manifest.head != null && manifest.recordCount > 0 &&
                    manifest.statuses.map { it.requestId }.toSet() == requestIds.toSet())
                val plan = TreeDownloadPlan(manifest.id, gameId, manifest.playId, GameWire.ordinal(manifest.revision),
                    manifest.recordCount, manifest.chunks.map { TreeDownloadChunk(it.index, it.bytes, it.digest) },
                    StoredPayload(1, GameWire.json.encodeToString(manifest)))
                GameplayTreeTransfer(manifest, plan) { index ->
                    request("/v1/baselines/${manifest.id}/chunks/$index").use {
                        requireNotNull(it.body).byteStream().bounded(GameplaySync.MAX_CHUNK_BYTES).inputStream() }
                }
            }.getOrNull()
            complete(result)
        }
        return true
    }
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun baseline(gameId: String, requestIds: List<String>, complete: (GameplayBaseline?) -> Unit): Boolean {
        val credential = token ?: return false
        if (!DeckValidation.uuid(gameId)) return false
        reads.execute {
            val result = runCatching {
                fun request(path: String, body: RequestBody? = null): Response {
                    check(token == credential)
                    val builder = Request.Builder().url(origin + path).header("Authorization", "Bearer $credential")
                    if (body != null) builder.post(body)
                    return client.newCall(builder.build()).execute().also { if (!it.isSuccessful) { it.close(); error("Baseline unavailable") } }
                }
                val manifest = request("/v1/games/$gameId/baselines", GameWire.json.encodeToString(LookupGameResults(requestIds))
                    .toRequestBody("application/json".toMediaType())).use { response ->
                    val bytes = requireNotNull(response.body).byteStream().bounded(GameplaySync.MAX_CHUNK_BYTES)
                    require(bytes.size <= GameplaySync.MAX_CHUNK_BYTES)
                    GameWire.json.decodeFromString<GameplayManifest>(bytes.toString(Charsets.UTF_8))
                }
                GameplaySync.validate(manifest)
                require(manifest.gameId == gameId && manifest.statuses.map { it.requestId }.toSet() == requestIds.toSet())
                // Accepted-only staging on disk; an interrupted download never replaces the live generation.
                val file = Files.createTempFile("gameplay-baseline-", ".json")
                try {
                    Files.newOutputStream(file).use { output ->
                        for (chunk in manifest.chunks) request("/v1/baselines/${manifest.id}/chunks/${chunk.index}").use { response ->
                            val compressed = requireNotNull(response.body).byteStream().bounded(GameplaySync.MAX_CHUNK_BYTES)
                            require(compressed.size <= GameplaySync.MAX_CHUNK_BYTES)
                            val bytes = GZIPInputStream(compressed.inputStream()).use { it.bounded(chunk.bytes) }
                            require(bytes.size == chunk.bytes && GameplaySync.digest(bytes) == chunk.digest)
                            output.write(bytes)
                        }
                    }
                    val snapshot = if (manifest.recordCount > 0) Files.newBufferedReader(file).use { reader ->
                        val records = reader.lineSequence().map { GameWire.json.decodeFromString<TreeSyncRecord>(it) }.toList()
                        require(records.size == manifest.recordCount)
                        GameplaySnapshot(requireNotNull(manifest.head), emptyList(), records)
                    } else Files.newInputStream(file).use { GameWire.json.decodeFromStream<GameplaySnapshot>(it) }
                    require(snapshot.head.gameId == gameId && snapshot.head.revision == manifest.revision && snapshot.head.play?.id == manifest.playId)
                    GameplaySync.validate(snapshot)
                    GameplayBaseline(snapshot, manifest.statuses)
                } finally { Files.deleteIfExists(file) }
            }.getOrNull()
            complete(result)
        }
        return true
    }
    override fun disconnect() { socket?.cancel(); socket = null; token = null }
    override fun close() { disconnect(); reads.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}

private fun InputStream.bounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (count < 0) return output.toByteArray()
        require(output.size() + count <= limit)
        output.write(buffer, 0, count)
    }
}
