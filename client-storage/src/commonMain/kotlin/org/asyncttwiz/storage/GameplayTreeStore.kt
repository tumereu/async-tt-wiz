package org.asyncttwiz.storage

data class TreeCacheVersion(val revision: Long, val publicationId: String)
data class CachedGameplayTree(val version: TreeCacheVersion, val records: List<AcceptedRecord>)

/** Local staging descriptor, NOT a second network/sync protocol. Manifest bytes belong to the
 * pinned transport adapter. Chunks are private, explicitly incomplete data, never accepted records.
 */
data class TreeCacheTransfer(val id: String, val revision: Long, val chunkCount: Int, val manifest: StoredPayload)

/** The read/stage/accepted boundary used by native coordinators. Production uses SQLDelight below.
 * Obsolete/closed owners read null and ignore writes; publication conflicts return null for recovery. */
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
    /** Null means ownership, staging or the expected publication moved on; nothing was written. */
    fun publish(id: String, expected: TreeCacheVersion?, revision: Long, records: List<AcceptedRecord>): TreeCacheVersion?
    fun apply(expected: TreeCacheVersion, revision: Long, upserts: List<AcceptedRecord>, removals: List<RecordKey>): TreeCacheVersion?
    /** CAS upgrade of derived local records only; never advances the accepted server revision. */
    fun amend(expected: TreeCacheVersion, upserts: List<AcceptedRecord>): TreeCacheVersion?
}
