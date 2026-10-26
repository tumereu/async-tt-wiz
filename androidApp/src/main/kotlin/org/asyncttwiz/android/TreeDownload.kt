package org.asyncttwiz.android

import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.SequenceInputStream
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.zip.GZIPInputStream

data class TreeDownloadChunk(val index: Int, val bytes: Int, val digest: String)

/** LOCAL adapter input from the verified existing baseline transport manifest, not a new
 * HTTP/wire protocol. Each independent gzip chunk may split a record/UTF-8 character at any byte.
 */
data class TreeDownloadPlan(val id: String, val gameId: String, val playId: String, val revision: Long,
    val recordCount: Int, val chunks: List<TreeDownloadChunk>, val transportManifest: StoredPayload) {
    fun identity(): StoredPayload = StoredPayload(1, DeckJson.canonicalElement(buildJsonObject {
        put("id", id); put("gameId", gameId); put("playId", playId); put("revision", revision.toString())
        put("recordCount", recordCount); put("transportVersion", transportManifest.version.toString())
        put("transportManifest", transportManifest.json)
        putJsonArray("chunks") { chunks.forEach { chunk -> add(buildJsonObject {
            put("index", chunk.index); put("bytes", chunk.bytes); put("digest", chunk.digest)
        }) } }
    }))
}

/** Local changed-record adapter; production GameSession maps GameRevision.treeDelta into it. */
data class TreeRecordDelta(val gameId: String, val playId: String, val baseRevision: Long, val revision: Long,
    val upserts: List<TreeSyncRecord>, val removals: List<Pair<String, String>> = emptyList())
data class StagedDownloadedTree(val plan: TreeDownloadPlan, val generation: DownloadedTree,
    val history: HistoryTree = HistoryTree.ingest(generation.tree, plan.revision.toString()))
data class PublishedDownloadedTree(val version: TreeCacheVersion, val generation: DownloadedTree,
    val history: HistoryTree = HistoryTree.ingest(generation.tree, version.revision.toString()))
sealed interface TreePublication {
    data class Published(val value: PublishedDownloadedTree) : TreePublication
    data object RestartRequired : TreePublication
}

/** Blocking disk/decode coordinator. Native callers use DownloadedHistory's serial worker.
 * Staging is incomplete until integrity/topology checks + contiguous catch-up + atomic publication.
 * No outgoing/request-recovery state is touched, and the previous complete tree stays readable.
 */
class TreeDownload(private val cache: GameplayTreeStore, private val uiThread: Thread) {
    var failure: String? = null
        private set
    private fun fail(reason: String): TreePublication { failure = reason; return TreePublication.RestartRequired }
    private var materialized: PublishedDownloadedTree? = null
    private var annotations: TreeAnnotationProjection? = null
    private fun worker() { check(Thread.currentThread() !== uiThread) { "Tree work must run off the UI thread" } }

    fun accepted(): PublishedDownloadedTree? {
        worker()
        val version = cache.version() ?: return null.also { materialized = null }
        materialized?.takeIf { it.version == version }?.let { return it }
        return cache.accepted()?.let { snapshot ->
            // Legacy caches have no revision-bound endpoint. Fetch a fresh authoritative baseline
            // rather than making process restart an implicit exhaustive maintenance operation.
            val saved = snapshot.records.singleOrNull { it.key == ENDPOINT } ?: return@let null
            require(saved.payload.version == 1L)
            val payload = GameWire.json.parseToJsonElement(saved.payload.json).jsonObject
            val content = payload.getValue("content").jsonObject
            require(payload.getValue("digest").jsonPrimitive.content == TreeSync.digest(DeckJson.canonicalElement(content).toByteArray()))
            require(content.getValue("revision").jsonPrimitive.content == snapshot.version.revision.toString())
            val state = GameWire.json.decodeFromJsonElement<PlayState>(content.getValue("state"))
            val projectionRecords = snapshot.records.asSequence().filter(TreeAnnotationProjection::owns)
            val projection = content["annotations"]?.let { identity ->
                TreeAnnotationProjection.read(projectionRecords).also { require(it.identity == identity) { "Corrupt annotation projection" } }
            }
            require(projection != null || projectionRecords.none()) { "Unbound annotation projection" }
            val generation = TreeSync.restore(snapshot.records.asSequence()
                .filter { it.key != ENDPOINT && !TreeAnnotationProjection.owns(it) }.map(::decode),
                cache.gameId, cache.playId, state, boundedEndpoint = true, annotations = projection?.state())
            val restored = projection ?: TreeAnnotationProjection.from(requireNotNull(generation.tree.preparedState(generation.tree.currentNodeId)).annotations)
            // Upgrade an endpoint-only cache exactly once, without advancing the server revision or
            // rewriting retained history. Ownership/publication CAS also fences this derived write.
            val version = if (projection != null) snapshot.version else cache.amend(snapshot.version,
                restored.records().toList() + endpoint(generation, snapshot.version.revision, restored)) ?: return@let null
            annotations = restored
            PublishedDownloadedTree(version, generation)
        }?.also { materialized = it }
    }

    fun stage(plan: TreeDownloadPlan, openChunk: (Int) -> InputStream?): StagedDownloadedTree? {
        worker(); validate(plan)
        require(plan.transportManifest.version == 1L)
        val manifest = GameWire.json.decodeFromString(GameplayManifest.serializer(), plan.transportManifest.json)
        GameplaySync.validate(manifest)
        require(manifest.id == plan.id && manifest.gameId == plan.gameId && manifest.playId == plan.playId &&
            manifest.revision == plan.revision.toString() && manifest.recordCount == plan.recordCount &&
            manifest.chunks.map { TreeDownloadChunk(it.index, it.bytes, it.digest) } == plan.chunks)
        val head = requireNotNull(manifest.head)
        val endpoint = requireNotNull(head.play)
        val identity = plan.identity()
        cache.begin(TreeCacheTransfer(plan.id, plan.revision, plan.chunks.size, identity))
        fun current() = cache.transfer()?.manifest == identity
        for (chunk in plan.chunks) {
            if (!current()) return null
            val saved = cache.chunk(plan.id, chunk.index)
            if (saved == null) {
                // Bound compressed bytes independently; never allocate from untrusted declared lengths.
                val compressed = (openChunk(chunk.index) ?: return null).use { it.bounded(MAX_CHUNK_BYTES) }
                if (!current()) return null
                val bytes = GZIPInputStream(compressed.inputStream()).use { it.bounded(chunk.bytes) }
                verify(chunk, bytes)
                cache.putChunk(plan.id, chunk.index, StoredPayload(1, Base64.getEncoder().encodeToString(bytes)))
            } else verify(chunk, bytes(saved))
        }
        // Lazy enumeration keeps only one decoded disk chunk, not the complete transfer byte blob.
        var missingChunk = false
        val streams = plan.chunks.asSequence().map { chunk ->
            val payload = cache.chunk(plan.id, chunk.index)
            if (payload == null) {
                missingChunk = true
                return@map byteArrayOf().inputStream() as InputStream
            }
            val bytes = bytes(payload)
            verify(chunk, bytes); bytes.inputStream() as InputStream
        }.iterator()
        val enumeration = object : java.util.Enumeration<InputStream> {
            override fun hasMoreElements() = streams.hasNext()
            override fun nextElement() = streams.next()
        }
        val generation = try {
            SequenceInputStream(enumeration).use { stream ->
                val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                InputStreamReader(stream, decoder).buffered().use { reader ->
                    var count = 0
                    val records = reader.lineSequence().map { line ->
                        require(line.isNotEmpty()); count++
                        GameWire.json.decodeFromString(TreeSyncRecord.serializer(), line)
                    }
                    val tree = TreeSync.restore(records, cache.gameId, cache.playId, endpoint, head.boundedEndpoint)
                    require(count == plan.recordCount) { "Incomplete tree record coverage" }
                    tree
                }
            }
        } catch (failure: Exception) {
            // Replacement may remove a chunk during lazy decoding. Discard that partial decode,
            // but keep actual corruption/format failures visible to the caller's recovery path.
            if (missingChunk || !current()) return null
            throw failure
        }
        if (missingChunk || !current()) return null
        return StagedDownloadedTree(plan, generation)
    }

    /** Caller supplies an independently observed head and arrivals interleaved with the download.
     * A missing/pruned suffix restarts instead of mixing pages or rolling back a newer publication.
     * The final storage CAS also serializes publication against a different live writer.
     */
    fun publish(staged: StagedDownloadedTree?, tail: List<TreeRecordDelta>, verifiedHead: Long): TreePublication {
        worker()
        if (staged == null) return fail("stagingMissing")
        validate(staged.plan)
        if (cache.transfer()?.manifest != staged.plan.identity()) return fail("manifestChanged")
        val expected = cache.version()
        var generation = staged.generation
        var history = staged.history
        var revision = staged.plan.revision
        for (delta in tail) {
            validate(delta)
            if (delta.revision <= revision) continue
            if (delta.baseRevision != revision) return restart(staged.plan.id)
            generation = merge(generation, delta)
            history = HistoryTree.ingest(generation.tree, delta.revision.toString(), history)
            revision = delta.revision
        }
        if (revision < verifiedHead || (expected != null && revision < expected.revision)) return restart(staged.plan.id)
        // Recheck mutable adapter input before crossing the accepted boundary.
        val projection = TreeAnnotationProjection.from(requireNotNull(generation.tree.preparedState(generation.tree.currentNodeId)).annotations)
        val records = TreeSync.records(generation).map(::encode).toList() + projection.records().toList() + endpoint(generation, revision, projection)
        val next = cache.publish(staged.plan.id, expected, revision, records) ?: return fail("publishCAS")
        annotations = projection
        return TreePublication.Published(PublishedDownloadedTree(next, generation, history).also { materialized = it })
    }

    private fun restart(id: String): TreePublication { cache.restart(id); return fail("stagedTailGap") }

    fun apply(delta: TreeRecordDelta): TreePublication {
        worker(); failure = null; validate(delta)
        val current = accepted() ?: return fail("acceptedCacheMissing")
        if (delta.revision <= current.version.revision) return TreePublication.Published(current)
        if (delta.baseRevision != current.version.revision) return fail("revisionGap:cache=${current.version.revision}:base=${delta.baseRevision}:result=${delta.revision}")
        val generation = merge(current.generation, delta)
        val history = HistoryTree.ingest(generation.tree, delta.revision.toString(), current.history)
        val edit = requireNotNull(annotations).update(requireNotNull(generation.tree.preparedState(generation.tree.currentNodeId)).annotations,
            TreeSync.annotationChanges(current.generation.tree, generation.tree))
        val next = cache.apply(current.version, delta.revision,
            delta.upserts.map(::encode) + edit.upserts + endpoint(generation, delta.revision, edit.projection),
            delta.removals.map { RecordKey(it.first, it.second) } + edit.removals)
            ?: return fail("applyCAS")
        annotations = edit.projection
        return TreePublication.Published(PublishedDownloadedTree(next, generation, history).also { materialized = it })
    }

    private fun validate(plan: TreeDownloadPlan) {
        require(plan.gameId == cache.gameId && plan.playId == cache.playId && DeckValidation.uuid(plan.id))
        require(plan.revision >= 0 && plan.recordCount > 0 && plan.chunks.isNotEmpty())
        require(plan.chunks.map { it.index } == plan.chunks.indices.toList())
        require(plan.chunks.all { it.bytes in 1..MAX_CHUNK_BYTES && DeckValidation.digest(it.digest) })
    }

    private fun validate(delta: TreeRecordDelta) {
        require(delta.gameId == cache.gameId && delta.playId == cache.playId && delta.baseRevision >= 0 && delta.revision > delta.baseRevision)
        val keys = delta.upserts.map { it.kind to it.id }
        require(keys.distinct().size == keys.size && delta.removals.distinct().size == delta.removals.size && keys.none { it in delta.removals })
    }

    private fun merge(generation: DownloadedTree, delta: TreeRecordDelta): DownloadedTree {
        return TreeSync.apply(generation, GameplayTreeDelta(delta.upserts, delta.removals.map { GameplayTreeKey(it.first, it.second) }))
    }

    private fun verify(chunk: TreeDownloadChunk, bytes: ByteArray) {
        require(bytes.size == chunk.bytes && TreeSync.digest(bytes) == chunk.digest) { "Corrupt tree chunk" }
    }
    private fun bytes(payload: StoredPayload): ByteArray { require(payload.version == 1L); return Base64.getDecoder().decode(payload.json) }

    companion object {
        const val MAX_CHUNK_BYTES = 4 * 1024 * 1024
        private val ENDPOINT = RecordKey("tree-cache-endpoint", "current")
        internal fun endpoint(generation: DownloadedTree, revision: Long,
            projection: TreeAnnotationProjection = TreeAnnotationProjection.from(requireNotNull(generation.tree.preparedState(generation.tree.currentNodeId)).annotations)): AcceptedRecord {
            val content = buildJsonObject {
                put("revision", revision.toString())
                // Bind the sparse materialized annotation records to the SAME accepted revision.
                put("annotations", projection.identity)
                put("state", GameWire.json.encodeToJsonElement(requireNotNull(generation.tree.preparedState(generation.tree.currentNodeId))
                    .copy(annotations = EventAnnotationState())))
            }
            return AcceptedRecord(ENDPOINT, StoredPayload(1, buildJsonObject {
                put("content", content)
                put("digest", TreeSync.digest(DeckJson.canonicalElement(content).toByteArray()))
            }.toString()))
        }
        fun encode(record: TreeSyncRecord) = AcceptedRecord(RecordKey(record.kind, record.id), StoredPayload(record.version.toLong(), record.payload.toString()))
        fun decode(record: AcceptedRecord): TreeSyncRecord {
            require(record.payload.version == 1L)
            return TreeSyncRecord(record.key.kind, record.key.id, GameWire.json.parseToJsonElement(record.payload.json))
        }
    }
}

private fun InputStream.bounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (count < 0) return output.toByteArray()
        require(output.size() + count <= limit) { "Tree chunk exceeds declared/hard length" }
        output.write(buffer, 0, count)
    }
}
