package org.asyncttwiz.storage

import com.google.gson.Gson
import java.util.UUID

data class TreeCacheVersion(val revision: Long, val publicationId: String)
data class CachedGameplayTree(val version: TreeCacheVersion, val records: List<AcceptedRecord>)

/** Local staging descriptor, NOT a second network/sync protocol. Manifest bytes belong to the
 * pinned transport adapter. Chunks are private, explicitly incomplete data, never accepted records.
 */
data class TreeCacheTransfer(val id: String, val revision: Long, val chunkCount: Int, val manifest: StoredPayload)

/** The read/stage/accepted boundary used by native coordinators. Production uses SQLDelight below. */
interface GameplayTreeStore {
    val accountId: String
    val gameId: String
    val playId: String
    fun invalidate()
    /** Extra process/session fence evaluated INSIDE the existing storage transaction. */
    fun guard(current: () -> Boolean) {}
    fun isCurrent(): Boolean
    fun accepted(): CachedGameplayTree?
    fun version(): TreeCacheVersion?
    fun transfer(): TreeCacheTransfer?
    fun begin(transfer: TreeCacheTransfer)
    fun putChunk(id: String, index: Int, payload: StoredPayload)
    fun chunk(id: String, index: Int): StoredPayload?
    fun restart(id: String)
    fun reviewCursor(): String?
    fun reviewCursor(nodeId: String?)
    fun publish(id: String, expected: TreeCacheVersion?, revision: Long, records: List<AcceptedRecord>): TreeCacheVersion
    fun apply(expected: TreeCacheVersion, revision: Long, upserts: List<AcceptedRecord>, removals: List<RecordKey>): TreeCacheVersion
}

/** One accepted game scope in the existing SQLDelight replica. No boards, outgoing requests or
 * predictions are stored here. All methods except invalidate() block and belong on a worker.
 * A newly bound game/incarnation fences old callbacks, including across separate storage handles.
 */
class GameplayTreeCache private constructor(private val storage: ClientStorage, override val accountId: String,
    override val gameId: String, override val playId: String, private val token: String) : GameplayTreeStore {
    private val scope = "game-tree:$gameId"
    private val staging = "game-tree-staging:$gameId"
    private val fenceKey = "game-tree-fence:$gameId"
    private val fence = StoredPayload(1, "$playId/$token")
    @Volatile private var active = true
    @Volatile private var sessionCurrent: () -> Boolean = { true }

    /** Immediate process fence; no disk work on the caller's (possibly UI) thread. */
    override fun invalidate() { active = false }
    override fun guard(current: () -> Boolean) { sessionCurrent = current }

    override fun isCurrent(): Boolean = storage.atomic { current() }

    private fun current() = active && sessionCurrent() && storage.personal("installation", "account-owner") == StoredPayload(1, accountId) &&
        storage.personal("installation", fenceKey) == fence

    private fun <T> fenced(block: () -> T): T = storage.atomic {
        check(current()) { "Obsolete tree callback" }
        block()
    }

    private data class Publication(val playId: String, val id: String)
    private data class Stage(val playId: String, val transfer: TreeCacheTransfer)
    private fun stage(): Stage? = storage.personal(staging, "transfer")?.let { gson.fromJson(it.json, Stage::class.java) }
    private fun publication(snapshot: AcceptedSnapshot): Publication = snapshot.records.single { it.key == HEADER }.let {
        check(it.payload.version == 1L)
        gson.fromJson(it.payload.json, Publication::class.java)
    }

    private fun read(): CachedGameplayTree? = storage.accepted(scope)?.let { snapshot ->
        val header = publication(snapshot)
        if (header.playId != playId) null else CachedGameplayTree(TreeCacheVersion(snapshot.revision, header.id),
            snapshot.records.filter { it.key != HEADER })
    }

    override fun accepted(): CachedGameplayTree? = fenced { read() }

    /** Includes an obsolete incarnation's publication for replacement CAS, never its readable data. */
    override fun version(): TreeCacheVersion? = fenced {
        storage.accepted(scope)?.let { TreeCacheVersion(it.revision, publication(it).id) }
    }

    override fun transfer(): TreeCacheTransfer? = fenced { stage()?.takeIf { it.playId == playId }?.transfer }

    override fun begin(transfer: TreeCacheTransfer) = fenced {
        require(transfer.id.isNotEmpty() && transfer.revision >= 0 && transfer.chunkCount > 0)
        val previous = stage()
        if (previous == Stage(playId, transfer)) return@fenced
        clearStage(previous)
        storage.savePersonal(staging, "transfer", StoredPayload(1, gson.toJson(Stage(playId, transfer))))
    }

    private fun requireStage(id: String): TreeCacheTransfer {
        val stage = checkNotNull(stage()) { "No tree transfer" }
        check(stage.playId == playId && stage.transfer.id == id) { "Obsolete tree transfer" }
        return stage.transfer
    }

    /** Payload is one verified decoded chunk (base64); interpretation/validation is the codec's job. */
    override fun putChunk(id: String, index: Int, payload: StoredPayload) = fenced {
        require(index in 0 until requireStage(id).chunkCount)
        val previous = storage.personal(staging, "chunk:$index")
        check(previous == null || previous == payload) { "Conflicting tree chunk" }
        storage.savePersonal(staging, "chunk:$index", payload)
    }

    override fun chunk(id: String, index: Int): StoredPayload? = fenced {
        require(index in 0 until requireStage(id).chunkCount)
        storage.personal(staging, "chunk:$index")
    }

    /** Personal review position is not the accepted sync cursor or shared current pointer. */
    override fun reviewCursor(): String? = fenced {
        storage.personal("game-tree-review:$gameId", "cursor")?.takeIf { it.version == 1L && it.json.startsWith("$playId/") }
            ?.json?.substringAfter('/')
    }

    override fun reviewCursor(nodeId: String?) = fenced {
        if (nodeId == null) storage.removePersonal("game-tree-review:$gameId", "cursor")
        else {
            require(nodeId.isNotEmpty() && '/' !in nodeId)
            storage.savePersonal("game-tree-review:$gameId", "cursor", StoredPayload(1, "$playId/$nodeId"))
        }
    }

    override fun restart(id: String) = fenced { requireStage(id); clearStage(stage()) }

    private fun clearStage(stage: Stage?) {
        stage?.let { repeat(it.transfer.chunkCount) { index -> storage.removePersonal(staging, "chunk:$index") } }
        storage.removePersonal(staging, "transfer")
    }

    private fun checkBase(expected: TreeCacheVersion?): AcceptedSnapshot? {
        val actual = storage.accepted(scope)
        val version = actual?.let { TreeCacheVersion(it.revision, publication(it).id) }
        check(version == expected) { "Accepted tree publication changed" }
        return actual
    }

    /** Caller has decoded/replayed the whole coherent generation, including a contiguous delta tail.
     * Failures (also during stage cleanup) roll back records, header, cursor and staging together.
     */
    override fun publish(id: String, expected: TreeCacheVersion?, revision: Long, records: List<AcceptedRecord>): TreeCacheVersion = fenced {
        val transfer = requireStage(id)
        require(revision >= transfer.revision)
        repeat(transfer.chunkCount) { checkNotNull(storage.personal(staging, "chunk:$it")) { "Incomplete tree transfer" } }
        val actual = checkBase(expected)
        require(records.none { it.key == HEADER })
        val next = TreeCacheVersion(revision, UUID.randomUUID().toString())
        storage.replaceAccepted(scope, actual?.revision, revision, records + header(next))
        clearStage(stage())
        next
    }

    /** Genuine changed-record persistence, not a full generation rewrite for an ordinary delta. */
    override fun apply(expected: TreeCacheVersion, revision: Long, upserts: List<AcceptedRecord>, removals: List<RecordKey>): TreeCacheVersion = fenced {
        val actual = checkNotNull(checkBase(expected))
        check(publication(actual).playId == playId)
        require(upserts.none { it.key == HEADER } && HEADER !in removals)
        val next = TreeCacheVersion(revision, UUID.randomUUID().toString())
        storage.commitAccepted(scope, expected.revision, revision, upserts + header(next), removals)
        next
    }

    private fun header(version: TreeCacheVersion) = AcceptedRecord(HEADER, StoredPayload(1, gson.toJson(Publication(playId, version.publicationId))))

    companion object {
        private val gson = Gson()
        private val HEADER = RecordKey("tree-cache-publication", "complete")

        /** Lifecycle binding, never called by a download callback. Account activation remains the
         * existing auth adapter's responsibility. A fresh binding can resume same-play staging.
         */
        fun bind(storage: ClientStorage, accountId: String, gameId: String, playId: String): GameplayTreeCache {
            require(listOf(accountId, gameId, playId).all { it.isNotEmpty() && '/' !in it && ':' !in it })
            val cache = GameplayTreeCache(storage, accountId, gameId, playId, UUID.randomUUID().toString())
            storage.atomic {
                check(storage.personal("installation", "account-owner") == StoredPayload(1, accountId)) { "Inactive account" }
                storage.savePersonal("installation", cache.fenceKey, cache.fence)
                cache.stage()?.takeIf { it.playId != playId }?.let { cache.clearStage(it) }
            }
            return cache
        }
    }
}
