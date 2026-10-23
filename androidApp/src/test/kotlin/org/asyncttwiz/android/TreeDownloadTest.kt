package org.asyncttwiz.android

import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import kotlin.test.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

/** Typed LOCAL adapter fixtures, not the unfinished server exporter or a native device journey.
 * Actual SQLDelight rollback/durability/account fencing executes in GameplayTreeCacheTest.
 */
internal object DownloadedTreeFixture {
    fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    val a = id(7)
    val b = id(8)
    fun root(): GameplayTree {
        val definition = CardDefinition("Downloaded Wizard 🧙", listOf(DefinitionFace(id(11), "Retained face 🧙")))
        val digest = DeckJson.digest(definition)
        val initial = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), listOf(
                PlayCard(id(9), digest, a, id(10), id(11)), PlayCard(id(12), digest, b, id(13), id(11))),
                listOf(PlayContainer(id(10), PlayZone.LIBRARY, a, listOf(id(9))),
                    PlayContainer(id(13), PlayZone.LIBRARY, b, listOf(id(12)))), TurnState(a)),
            listOf(SubmittedDefinition(digest, definition)), emptyList(), InitialStarter(StarterChoice(a), a))
        return GameplayTree(initial)
    }
    fun tap(tree: GameplayTree, n: Int = 100, actor: String = a, card: Int = 9, tapped: Boolean = true): GameplayTree =
        requireNotNull(tree.play(actor, GameCommand(id(n), GameScope(id(1), playId = id(2)),
            GameAction.Play(PlayAction.SetTapped(id(card), tapped)), id(14), "1")).tree)
    fun undo(tree: GameplayTree) = requireNotNull(SelectiveUndo.change(tree, a, GameCommand(id(900),
        GameScope(id(1), playId = id(2)), GameAction.Undo, id(14), "1")).tree)
    fun generation(tree: GameplayTree) = DownloadedTree(tree, TreeSync.branchIds(tree).associateWith { branch ->
        buildJsonObject { put("hidden", branch == id(30)); put("archived", branch == id(30)) }
    })
    fun branched(): DownloadedTree {
        val root = root()
        val first = tap(root)
        val second = tap(first, 101, b, 12)
        val offshoot = tap(root, 102, b, 12).nodes.values.single().copy(branchId = id(30))
        return generation(second.copy(nodes = second.nodes + (offshoot.id to offshoot), currentNodeId = first.currentNodeId))
    }
    fun delta(before: DownloadedTree, after: DownloadedTree, base: Long = 10, revision: Long = 11): TreeRecordDelta {
        val old = TreeSync.records(before).associateBy { it.kind to it.id }
        val next = TreeSync.records(after).associateBy { it.kind to it.id }
        return TreeRecordDelta(id(1), id(2), base, revision, next.filter { (key, value) -> old[key] != value }.values.toList(),
            (old.keys - next.keys).toList())
    }
    data class Transfer(val plan: TreeDownloadPlan, val compressed: List<ByteArray>)
    fun transfer(generation: DownloadedTree, revision: Long = 10, n: Int = 200, chunkSize: Int = 137): Transfer {
        val records = TreeSync.records(generation).toList()
        val bytes = (records.joinToString("\n") { GameWire.json.encodeToString(TreeSyncRecord.serializer(), it) } + "\n").toByteArray()
        val chunks = bytes.asList().chunked(chunkSize).map { it.toByteArray() }
        val plan = TreeDownloadPlan(id(n), id(1), id(2), revision, records.size,
            chunks.mapIndexed { index, chunk -> TreeDownloadChunk(index, chunk.size, TreeSync.digest(chunk)) },
            StoredPayload(1, "verified-I14-manifest-input-fixture"))
        return Transfer(plan, chunks.map(::gzip))
    }
    fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()
}

/** Store double only for deterministic adapter scheduling/faults. Never labelled real disk/server. */
internal class TreeStoreFixture : GameplayTreeStore {
    private val f = DownloadedTreeFixture
    override val accountId = f.a
    override val gameId = f.id(1)
    override val playId = f.id(2)
    var current = true
    var saved: CachedGameplayTree? = null
    private var transfer: TreeCacheTransfer? = null
    val chunks = linkedMapOf<Int, StoredPayload>()
    private var cursor: String? = null
    private var sequence = 0
    var failPublication = false
    var appliedUpserts = emptyList<AcceptedRecord>()
    val threads = mutableListOf<Thread>()
    private fun checkCurrent(): Boolean { threads.add(Thread.currentThread()); return current }
    fun seed(generation: DownloadedTree, revision: Long = 10) {
        saved = CachedGameplayTree(TreeCacheVersion(revision, "fixture-${sequence++}"), TreeSync.records(generation).map(TreeDownload::encode).toList())
    }
    override fun invalidate() { current = false }
    override fun isCurrent() = current
    override fun accepted(): CachedGameplayTree? = if (checkCurrent()) saved else null
    override fun version(): TreeCacheVersion? = if (checkCurrent()) saved?.version else null
    override fun transfer(): TreeCacheTransfer? = if (checkCurrent()) transfer else null
    override fun begin(transfer: TreeCacheTransfer) { if (!checkCurrent()) return; if (this.transfer != transfer) chunks.clear(); this.transfer = transfer }
    override fun putChunk(id: String, index: Int, payload: StoredPayload) { if (!checkCurrent() || transfer?.id != id) return; chunks[index] = payload }
    override fun chunk(id: String, index: Int): StoredPayload? = if (checkCurrent() && transfer?.id == id) chunks[index] else null
    override fun restart(id: String) { if (!checkCurrent() || transfer?.id != id) return; transfer = null; chunks.clear() }
    override fun reviewCursor(): String? = if (checkCurrent()) cursor else null
    override fun reviewCursor(nodeId: String?) { if (checkCurrent()) cursor = nodeId }
    override fun publish(id: String, expected: TreeCacheVersion?, revision: Long, records: List<AcceptedRecord>): TreeCacheVersion? {
        if (!checkCurrent() || transfer?.id != id || chunks.size != transfer?.chunkCount || saved?.version != expected) return null
        if (failPublication) throw IOException("fixture publication interruption")
        check(expected == null || revision >= expected.revision)
        val next = TreeCacheVersion(revision, "fixture-${sequence++}")
        saved = CachedGameplayTree(next, records); restart(id); return next
    }
    override fun apply(expected: TreeCacheVersion, revision: Long, upserts: List<AcceptedRecord>, removals: List<RecordKey>): TreeCacheVersion? {
        if (!checkCurrent() || saved?.version != expected) return null
        check(revision > expected.revision)
        val records = saved!!.records.associateByTo(linkedMapOf()) { it.key }
        removals.forEach { records.remove(it) }; upserts.forEach { records[it.key] = it }; appliedUpserts = upserts
        val next = TreeCacheVersion(revision, "fixture-${sequence++}")
        saved = CachedGameplayTree(next, records.values.toList()); return next
    }
}

class TreeDownloadTest {
    private val f = DownloadedTreeFixture
    private val nonWorkerThread = Thread("fixture-ui")
    private fun assertFails(action: () -> Unit) { try { action(); fail("Expected failure") } catch (_: Exception) { } }

    @Test fun automaticallyStagesEveryBoundedChunkAndPublishesWholeRetainedTreeOnlyAfterValidation() {
        val input = f.branched()
        val transfer = f.transfer(input)
        val store = TreeStoreFixture()
        val download = TreeDownload(store, nonWorkerThread)
        val reads = mutableListOf<Int>()
        val staged = download.stage(transfer.plan) { index -> reads += index; transfer.compressed[index].inputStream() }
        assertEquals(transfer.compressed.indices.toList(), reads)
        assertNull(store.accepted())
        assertEquals(input, assertNotNull(staged).generation)
        val result = download.publish(staged, emptyList(), 10) as TreePublication.Published
        assertEquals(input, result.value.generation)
        assertEquals(input, download.accepted()!!.generation)
        assertTrue(input.tree.nodes.keys.all { download.accepted()!!.generation.tree.stateAt(it) != null })
        assertNull(store.transfer())
    }

    @Test fun interruptedTransferResumesOnlyMissingChunksWithoutHarmingOldCompleteGeneration() {
        val input = f.branched()
        val transfer = f.transfer(input)
        val store = TreeStoreFixture().apply { seed(f.generation(f.root()), 9) }
        val old = store.saved
        val download = TreeDownload(store, nonWorkerThread)
        assertFails { download.stage(transfer.plan) { index -> if (index == 2) throw IOException("connection lost") else transfer.compressed[index].inputStream() } }
        assertEquals(old, store.saved)
        assertEquals(setOf(0, 1), store.chunks.keys)
        val resumedReads = mutableListOf<Int>()
        val resumed = TreeDownload(store, nonWorkerThread).stage(transfer.plan) { index ->
            resumedReads += index; transfer.compressed[index].inputStream()
        }
        assertEquals((2 until transfer.compressed.size).toList(), resumedReads)
        assertEquals(input, assertNotNull(resumed).generation)
        assertEquals(old, store.saved)
    }

    @Test fun corruptCompressedDecodedHashLengthAndCoverageNeverPublishOrWipeOldGeneration() {
        val input = f.generation(f.root())
        val transfer = f.transfer(input)
        val store = TreeStoreFixture().apply { seed(input, 9) }
        val old = store.saved
        val download = TreeDownload(store, nonWorkerThread)
        assertFails { download.stage(transfer.plan) { byteArrayOf(1, 2, 3).inputStream() } }
        assertEquals(old, store.saved)
        val wrongDigest = transfer.plan.copy(chunks = transfer.plan.chunks.mapIndexed { i, chunk ->
            if (i == 0) chunk.copy(digest = TreeSync.digest(byteArrayOf(9))) else chunk })
        assertFails { download.stage(wrongDigest) { transfer.compressed[it].inputStream() } }
        val tooShort = transfer.plan.copy(chunks = transfer.plan.chunks.mapIndexed { i, chunk -> if (i == 0) chunk.copy(bytes = 1) else chunk })
        assertFails { download.stage(tooShort) { transfer.compressed[it].inputStream() } }
        val tooLong = transfer.plan.copy(chunks = transfer.plan.chunks.mapIndexed { i, chunk -> if (i == 0) chunk.copy(bytes = chunk.bytes + 1) else chunk })
        assertFails { download.stage(tooLong) { transfer.compressed[it].inputStream() } }
        val incomplete = transfer.plan.copy(recordCount = transfer.plan.recordCount + 1)
        assertFails { download.stage(incomplete) { transfer.compressed[it].inputStream() } }
        assertEquals(old, store.saved)
        assertNotNull(store.transfer())
        assertFails { download.stage(transfer.plan.copy(chunks = listOf(transfer.plan.chunks.first().copy(bytes = TreeDownload.MAX_CHUNK_BYTES + 1)))) { error("must not fetch") } }
        assertFails { download.stage(transfer.plan) { ByteArray(TreeDownload.MAX_CHUNK_BYTES + 1).inputStream() } }
        assertEquals(old, store.saved)
    }

    @Test fun interleavedDeltaCatchesUpBeforeAtomicPublicationAndMissingPrunedSuffixRestarts() {
        val root = f.generation(f.root())
        val first = f.generation(f.tap(root.tree))
        val transfer = f.transfer(root)
        val store = TreeStoreFixture().apply { seed(root) }
        val download = TreeDownload(store, nonWorkerThread)
        val staged = download.stage(transfer.plan) { transfer.compressed[it].inputStream() }
        val delta = f.delta(root, first)
        assertTrue(download.apply(delta) is TreePublication.Published)
        val result = download.publish(staged, listOf(delta), 11) as TreePublication.Published
        assertEquals(11L, result.value.version.revision)
        assertEquals(first, result.value.generation)
        val old = store.saved
        val stale = download.stage(transfer.plan) { transfer.compressed[it].inputStream() }
        assertEquals(TreePublication.RestartRequired, download.publish(stale, emptyList(), 11))
        assertEquals(old, store.saved)
        assertNull(store.transfer())
        val gap = download.stage(transfer.plan) { transfer.compressed[it].inputStream() }
        assertEquals(TreePublication.RestartRequired, download.publish(gap, listOf(delta.copy(baseRevision = 11, revision = 12)), 12))
        assertEquals(old, store.saved)
    }

    @Test fun actualChangedRecordAdapterHandlesReparentingRedoRemovalParentsAndDuplicates() {
        val first = f.tap(f.root())
        val second = f.tap(first, 101, f.b, 12)
        val before = f.generation(second)
        val after = f.generation(f.undo(second))
        val store = TreeStoreFixture().apply { seed(before) }
        val download = TreeDownload(store, nonWorkerThread)
        val delta = f.delta(before, after)
        val published = (download.apply(delta) as TreePublication.Published).value
        assertEquals(after, published.generation)
        assertEquals(first.initial.root.id, published.generation.tree.resolveCursor(first.currentNodeId))
        assertEquals(second.currentNodeId, published.generation.tree.resolveCursor(second.currentNodeId))
        assertFalse(published.generation.tree.stateAt(second.currentNodeId)!!.board.cards.first().tapped)
        assertTrue(store.appliedUpserts.size < TreeSync.records(after).count())
        val version = store.saved!!.version
        download.apply(delta)
        assertEquals(version, store.saved!!.version, "duplicate cannot write/advance twice")
        assertEquals(TreePublication.RestartRequired, download.apply(delta.copy(baseRevision = 12, revision = 13)))
        assertEquals(version, store.saved!!.version)
        assertFails { download.apply(delta.copy(playId = f.id(999))) }
    }

    @Test fun publicationFailureAndObsoleteAccountOrTransferCallbacksNeverReplaceAcceptedTree() {
        val root = f.generation(f.root())
        val transfer = f.transfer(f.branched())
        val store = TreeStoreFixture().apply { seed(root, 9) }
        val download = TreeDownload(store, nonWorkerThread)
        val staged = download.stage(transfer.plan) { transfer.compressed[it].inputStream() }
        val old = store.saved
        store.failPublication = true
        assertFails { download.publish(staged, emptyList(), 10) }
        assertEquals(old, store.saved); assertNotNull(store.transfer())
        store.failPublication = false
        val next = transfer.plan.copy(id = f.id(201), revision = 11)
        download.stage(next) { transfer.compressed[it].inputStream() }
        assertEquals(TreePublication.RestartRequired, download.publish(staged, emptyList(), 10))
        assertEquals(old, store.saved)
        store.invalidate()
        assertEquals(TreePublication.RestartRequired, download.publish(staged, emptyList(), 10))
        assertEquals(old, store.saved)
    }

    @Test fun transferReplacementDuringNetworkReadDiscardsStagingWithoutOverwritingNewTransfer() {
        val store = TreeStoreFixture().apply { seed(f.generation(f.root()), 9) }
        val old = store.saved
        val transfer = f.transfer(f.branched())
        val replacement = TreeCacheTransfer(f.id(999), 12, 1, StoredPayload(1, "new manifest"))
        val download = TreeDownload(store, nonWorkerThread)
        val staged = download.stage(transfer.plan) { index ->
            store.begin(replacement)
            transfer.compressed[index].inputStream()
        }
        assertNull(staged)
        assertEquals(replacement, store.transfer())
        assertTrue(store.chunks.isEmpty())
        assertEquals(old, store.saved)
        assertEquals(TreePublication.RestartRequired, download.publish(staged, emptyList(), 10))
    }

    @Test fun publicationCasRacesRequestRecoveryInsteadOfReportingAnUncommittedTree() {
        val store = TreeStoreFixture().apply { seed(f.generation(f.root())) }
        val racing = object : GameplayTreeStore by store {
            override fun publish(id: String, expected: TreeCacheVersion?, revision: Long, records: List<AcceptedRecord>): TreeCacheVersion? = null
            override fun apply(expected: TreeCacheVersion, revision: Long, upserts: List<AcceptedRecord>, removals: List<RecordKey>): TreeCacheVersion? = null
        }
        val download = TreeDownload(racing, nonWorkerThread)
        val transfer = f.transfer(f.branched())
        val staged = assertNotNull(download.stage(transfer.plan) { transfer.compressed[it].inputStream() })
        val old = store.saved
        assertEquals(TreePublication.RestartRequired, download.publish(staged, emptyList(), 10))
        assertEquals(TreePublication.RestartRequired, download.apply(f.delta(f.generation(f.root()), f.generation(f.tap(f.root())))))
        assertEquals(old, store.saved)
    }

    @Test fun replacementRemovingChunksDuringLazyDecodeDiscardsPartialTreeWithoutThrowing() {
        val store = TreeStoreFixture().apply { seed(f.generation(f.root()), 9) }
        val old = store.saved
        val transfer = f.transfer(f.branched())
        val replacement = TreeCacheTransfer(f.id(999), 12, 1, StoredPayload(1, "new manifest"))
        var reads = 0
        val racing = object : GameplayTreeStore by store {
            override fun chunk(id: String, index: Int): StoredPayload? {
                if (++reads == transfer.plan.chunks.size + 2) store.begin(replacement)
                return store.chunk(id, index)
            }
        }
        val download = TreeDownload(racing, nonWorkerThread)
        assertNull(download.stage(transfer.plan) { transfer.compressed[it].inputStream() })
        assertEquals(replacement, store.transfer())
        assertEquals(old, store.saved)
    }

    @Test fun uiThreadGuardPrecedesAnyDiskDecodeOrReplayWork() {
        val store = TreeStoreFixture()
        val download = TreeDownload(store, Thread.currentThread())
        val input = f.transfer(f.generation(f.root()))
        assertFails { download.accepted() }
        assertFails { download.stage(input.plan) { error("must not fetch on UI") } }
        assertTrue(store.threads.isEmpty())
    }
}
