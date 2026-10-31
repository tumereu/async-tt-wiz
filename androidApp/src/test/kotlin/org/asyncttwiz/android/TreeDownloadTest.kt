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
    fun root(zone: PlayZone = PlayZone.BATTLEFIELD): GameplayTree {
        val definition = CardDefinition("Downloaded Wizard 🧙", listOf(DefinitionFace(id(11), "Retained face 🧙")))
        val digest = DeckJson.digest(definition)
        val initial = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), listOf(
                PlayCard(id(9), digest, a, id(10), id(11), explicitlyCovered = zone == PlayZone.BATTLEFIELD,
                    placement = if (zone == PlayZone.BATTLEFIELD) PlayPlacement(BoardPosition(0, 0), id(40)) else null),
                PlayCard(id(12), digest, b, id(13), id(11), explicitlyCovered = zone == PlayZone.BATTLEFIELD,
                    placement = if (zone == PlayZone.BATTLEFIELD) PlayPlacement(BoardPosition(0, 0), id(41)) else null)),
                listOf(PlayContainer(id(10), zone, a, listOf(id(9))),
                    PlayContainer(id(13), zone, b, listOf(id(12)))), TurnState(a)),
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
        val chunks = mutableListOf<GameplayChunk>()
        val compressed = mutableListOf<ByteArray>()
        val buffer = ByteArrayOutputStream()
        fun flush() {
            val bytes = buffer.toByteArray()
            chunks += GameplayChunk(chunks.size, bytes.size, TreeSync.digest(bytes))
            compressed += gzip(bytes)
            buffer.reset()
        }
        var count = 0
        for (record in TreeSync.records(generation)) {
            count++
            val line = (GameWire.json.encodeToString(TreeSyncRecord.serializer(), record) + "\n").toByteArray()
            var offset = 0
            while (offset < line.size) {
                val length = minOf(chunkSize - buffer.size(), line.size - offset)
                buffer.write(line, offset, length); offset += length
                if (buffer.size() == chunkSize) flush()
            }
        }
        if (buffer.size() > 0) flush()
        val summary = GameSummary(id(1), "Download", "date", AccountIdentity(a, "A"), AccountIdentity(b, "B"),
            generation.tree.initial.preparationRound, id(90), InvitationState.ACCEPTED, lifecycle = GameLifecycle.ACTIVE,
            revision = revision.toString(), playId = id(2))
        val manifest = GameplayManifest(id(n), id(1), id(2), revision.toString(),
            chunks, emptyList(),
            recordCount = count, head = GameHead(id(1), revision.toString(), summary,
                play = generation.tree.stateAt(generation.tree.currentNodeId))).let { it.copy(digest = GameplaySync.manifestDigest(it)) }
        val plan = TreeDownloadPlan(id(n), id(1), id(2), revision, count,
            manifest.chunks.map { TreeDownloadChunk(it.index, it.bytes, it.digest) },
            StoredPayload(1, GameWire.json.encodeToString(GameplayManifest.serializer(), manifest)))
        return Transfer(plan, compressed)
    }
    fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()
}

/** Store double only for deterministic adapter scheduling/faults. Never labelled real disk/server. */
internal class TreeStoreFixture(override val accountId: String = DownloadedTreeFixture.a) : GameplayTreeStore {
    private val f = DownloadedTreeFixture
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
        generation.tree.stateAt(generation.tree.currentNodeId)
        saved = CachedGameplayTree(TreeCacheVersion(revision, "fixture-${sequence++}"),
            TreeSync.records(generation).map(TreeDownload::encode).toList() +
                TreeAnnotationProjection.from(generation.tree.stateAt(generation.tree.currentNodeId)!!.annotations).records().toList() +
                TreeDownload.endpoint(generation, revision))
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
    override fun amend(expected: TreeCacheVersion, upserts: List<AcceptedRecord>): TreeCacheVersion? {
        if (!checkCurrent() || saved?.version != expected) return null
        val records = saved!!.records.associateByTo(linkedMapOf()) { it.key }
        upserts.forEach { records[it.key] = it }; appliedUpserts = upserts
        val next = TreeCacheVersion(expected.revision, "fixture-${sequence++}")
        saved = CachedGameplayTree(next, records.values.toList()); return next
    }
}

class TreeDownloadTest {
    private val f = DownloadedTreeFixture
    private val nonWorkerThread = Thread("fixture-ui")
    private fun assertFails(action: () -> Unit) { try { action(); fail("Expected failure") } catch (_: Exception) { } }

    private fun annotate(tree: GameplayTree, n: Int, action: PlayAction = PlayAction.EventComment(EventReference(f.id(2), f.id(5)), "mark $n")): GameplayTree =
        assertNotNull(tree.play(f.a, GameCommand(f.id(n), GameScope(f.id(1), playId = f.id(2)),
            GameAction.Play(action), f.id(n + 500000), "1")).tree)

    @Test fun tenThousandAnnotationsRestoreDirectlyAndOnlyChangedProjectionEntitiesAreWritten() {
        fun bounded(value: PublishedDownloadedTree) {
            assertEquals(0, value.generation.tree.work.deltaApplications, "Count actual delta applications, including annotation-only deltas")
            assertEquals(0, value.generation.tree.work.ancestrySteps, "No selected-ancestry walk on restore")
            assertEquals(0, value.generation.tree.work.rootMaterializations)
        }
        val file = java.nio.file.Files.createTempFile(java.nio.file.Path.of("/tmp/opencode"), "annotation-restore-", ".db")
        fun open() = (ClientStorage.open { app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver("jdbc:sqlite:$file",
            java.util.Properties().apply { setProperty("foreign_keys", "true") }) } as StorageOpenResult.Opened).storage
        fun seed(): PlayState = open().use { storage ->
            storage.activateAccount(f.a)
            val store = GameplayTreeCache.bind(storage, f.a, f.id(1), f.id(2))
            var tree = f.root()
            // Release the original tree before cold decoding. No transfer blobs or snapshots per node.
            repeat(9_996) { tree = annotate(tree, 1000 + it) }
            val reference = EventReference(f.id(2), f.id(5))
            val x = PingTarget(PingTargetKind.CARD, f.id(9))
            val y = PingTarget(PingTargetKind.PLAYER, f.b)
            tree = annotate(tree, 11000, PlayAction.Ping(reference, x))
            tree = annotate(tree, 11001, PlayAction.Ping(reference, y))
            tree = annotate(tree, 11002, PlayAction.Unping(reference, x))
            tree = annotate(tree, 11003, PlayAction.Ping(reference, x))
            val generation = f.generation(tree)
            val state = tree.stateAt(tree.currentNodeId)!!
            val projection = TreeAnnotationProjection.from(state.annotations)
            store.begin(TreeCacheTransfer(f.id(200), 10, 1, StoredPayload(1, "fixture")))
            store.putChunk(f.id(200), 0, StoredPayload(1, "fixture"))
            assertNotNull(store.publish(f.id(200), null, 10, TreeSync.records(generation).map(TreeDownload::encode).toList() +
                projection.records().toList() + TreeDownload.endpoint(generation, 10, projection)))
            state
        }
        fun advance(expected: PlayState): PlayState = open().use { storage ->
            val store = GameplayTreeCache.bind(storage, f.a, f.id(1), f.id(2))
            var writes = 0
            val recording = object : GameplayTreeStore by store {
                override fun apply(expected: TreeCacheVersion, revision: Long, upserts: List<AcceptedRecord>, removals: List<RecordKey>): TreeCacheVersion? {
                    writes = upserts.count(TreeAnnotationProjection::owns)
                    return store.apply(expected, revision, upserts, removals)
                }
            }
            val download = TreeDownload(recording, nonWorkerThread)
            val restored = assertNotNull(download.accepted())
            bounded(restored)
            val before = restored.generation
            val state = before.tree.stateAt(before.tree.currentNodeId)!!
            assertEquals(expected, state)
            assertEquals(listOf("2", "3"), EventAnnotations.activePings(state.annotations, state.currentGroup!!.id).map { it.to!!.ordinal })
            val next = annotate(before.tree, 12000)
            val changed = TreeSync.delta(before.tree, next)
            val updated = download.apply(TreeRecordDelta(f.id(1), f.id(2), 10, 11, changed.upserts,
                changed.removals.map { it.kind to it.id })) as TreePublication.Published
            assertEquals(1, writes, "Never rewrite the 10k-entry projection")
            updated.value.generation.tree.stateAt(next.currentNodeId)!!
        }
        try {
            val expected = advance(seed())
            open().use { storage ->
                val store = GameplayTreeCache.bind(storage, f.a, f.id(1), f.id(2))
                val restored = assertNotNull(TreeDownload(store, nonWorkerThread).accepted())
                bounded(restored)
                assertEquals(expected, restored.generation.tree.stateAt(restored.generation.tree.currentNodeId))
            }
        } finally { java.nio.file.Files.deleteIfExists(file) }
    }

    private fun legacy(snapshot: CachedGameplayTree): CachedGameplayTree = snapshot.copy(records = snapshot.records
        .filterNot(TreeAnnotationProjection::owns).map { record ->
            if (record.key.kind != "tree-cache-endpoint") record else {
                val payload = GameWire.json.parseToJsonElement(record.payload.json).jsonObject
                val content = JsonObject(payload.getValue("content").jsonObject - "annotations")
                record.copy(payload = record.payload.copy(json = buildJsonObject {
                    put("content", content); put("digest", TreeSync.digest(DeckJson.canonicalElement(content).toByteArray()))
                }.toString()))
            }
        })

    @Test fun endpointOnlyCacheProjectsOnceThenPersistsAndFencesTheUpgrade() {
        var tree = f.root()
        repeat(12) { tree = annotate(tree, 1000 + it) }
        val store = TreeStoreFixture().apply { seed(f.generation(tree)); saved = legacy(saved!!) }
        val old = store.saved!!
        val first = assertNotNull(TreeDownload(store, nonWorkerThread).accepted())
        assertEquals(12, first.generation.tree.work.deltaApplications)
        assertEquals(12, first.generation.tree.work.ancestrySteps)
        assertEquals(old.version.revision, first.version.revision)
        assertNotEquals(old.version.publicationId, first.version.publicationId)
        assertTrue(store.appliedUpserts.all { it.key.kind == "tree-cache-endpoint" || TreeAnnotationProjection.owns(it) })
        val second = assertNotNull(TreeDownload(store, nonWorkerThread).accepted())
        assertEquals(0, second.generation.tree.work.deltaApplications)
        assertEquals(0, second.generation.tree.work.ancestrySteps)
        assertEquals(tree.stateAt(tree.currentNodeId), second.generation.tree.stateAt(tree.currentNodeId))
        store.saved = old
        val racing = object : GameplayTreeStore by store {
            override fun amend(expected: TreeCacheVersion, upserts: List<AcceptedRecord>): TreeCacheVersion? = null
        }
        assertNull(TreeDownload(racing, nonWorkerThread).accepted())
        assertEquals(old, store.saved)
    }

    @Test fun annotationProjectionRejectsMissingCorruptAndStaleEntitiesAndUpdatesUndoAndBranchSelection() {
        val first = annotate(f.root(), 1000)
        val second = annotate(first, 1001)
        val store = TreeStoreFixture().apply { seed(f.generation(second)) }
        val valid = store.saved!!
        val entry = valid.records.first(TreeAnnotationProjection::owns)
        for (records in listOf(valid.records - entry, valid.records.map { if (it == entry) it.copy(payload = StoredPayload(1, "{}")) else it })) {
            store.saved = valid.copy(records = records)
            assertFails { TreeDownload(store, nonWorkerThread).accepted() }
        }
        store.saved = valid
        var download = TreeDownload(store, nonWorkerThread)
        var before = download.accepted()!!.generation
        val undone = assertNotNull(SelectiveUndo.change(before.tree, f.a, GameCommand(f.id(13000), GameScope(f.id(1), playId = f.id(2)),
            GameAction.Undo, f.id(14), "1")).tree)
        val delta = TreeSync.delta(before.tree, undone)
        assertTrue(download.apply(TreeRecordDelta(f.id(1), f.id(2), 10, 11, delta.upserts, delta.removals.map { it.kind to it.id })) is TreePublication.Published)
        download = TreeDownload(store, nonWorkerThread)
        before = download.accepted()!!.generation
        assertEquals(undone.stateAt(undone.currentNodeId), before.tree.stateAt(before.tree.currentNodeId))
        // A valid old entity substituted into the new projection must fail its revision-bound root.
        val current = store.saved!!
        val removed = valid.records.first { TreeAnnotationProjection.owns(it) && it.key !in current.records.map { record -> record.key } }
        store.saved = current.copy(records = current.records + removed)
        assertFails { TreeDownload(store, nonWorkerThread).accepted() }
        store.saved = current
        val selected = before.tree.activate(before.tree.initial.root.id).tree!!
        val selection = TreeSync.delta(before.tree, selected)
        assertTrue(download.apply(TreeRecordDelta(f.id(1), f.id(2), 11, 12, selection.upserts, selection.removals.map { it.kind to it.id })) is TreePublication.Published)
        val restored = TreeDownload(store, nonWorkerThread).accepted()!!.generation.tree
        assertTrue(restored.stateAt(restored.currentNodeId)!!.annotations.commentsById.isEmpty())
        assertEquals(0, restored.work.deltaApplications)
        assertEquals(0, restored.work.ancestrySteps)
    }

    @Test fun projectionTracksRedoAndNewOffshootWithoutPersistingInactiveAnnotations() {
        val store = TreeStoreFixture().apply { seed(f.generation(annotate(annotate(f.root(), 1000), 1001))) }
        var revision = 10L
        fun accept(change: (GameplayTree) -> GameplayTree): GameplayTree {
            val download = TreeDownload(store, nonWorkerThread)
            val before = download.accepted()!!.generation.tree
            val after = change(before)
            val delta = TreeSync.delta(before, after)
            assertTrue(download.apply(TreeRecordDelta(f.id(1), f.id(2), revision, ++revision, delta.upserts,
                delta.removals.map { it.kind to it.id })) is TreePublication.Published)
            val restored = TreeDownload(store, nonWorkerThread).accepted()!!.generation.tree
            assertEquals(after.stateAt(after.currentNodeId), restored.stateAt(restored.currentNodeId))
            assertEquals(0, restored.work.deltaApplications)
            assertEquals(0, restored.work.ancestrySteps)
            return restored
        }
        fun control(tree: GameplayTree, action: GameAction, n: Int) = assertNotNull(SelectiveUndo.change(tree, f.a,
            GameCommand(f.id(n), GameScope(f.id(1), playId = f.id(2)), action, f.id(n), "1")).tree)
        accept { control(it, GameAction.Undo, 13000) }
        accept { control(it, GameAction.Redo, 13001) }
        val branched = accept { tree ->
            assertNotNull(tree.play(f.a, GameCommand(f.id(13002), GameScope(f.id(1), playId = f.id(2)),
                GameAction.Play(PlayAction.EventComment(EventReference(f.id(2), f.id(5)), "offshoot")), f.id(13002), "1",
                continuation = Continuation(tree.initial.root.id, true))).tree)
        }
        assertEquals(1, branched.stateAt(branched.currentNodeId)!!.annotations.commentsById.size)
        assertEquals(3, branched.nodes.size, "Inactive annotations remain in history, not the materialized projection")
    }

    // SYS-026
    @Test fun tenThousandActionRecoveryAndColdCacheRestoreNeverReplayMutations() {
        var tree = f.root()
        repeat(10_000) { n -> tree = f.tap(tree, 1000 + n, tapped = n % 2 == 0) }
        val transfer = f.transfer(f.generation(tree), chunkSize = 1024 * 1024)
        val file = java.nio.file.Files.createTempFile(java.nio.file.Path.of("/tmp/opencode"), "tree-restore-", ".db")
        fun open() = (ClientStorage.open { app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver("jdbc:sqlite:$file",
            java.util.Properties().apply { setProperty("foreign_keys", "true") }) } as StorageOpenResult.Opened).storage
        try {
            open().use { storage ->
                storage.activateAccount(f.a)
                val store = GameplayTreeCache.bind(storage, f.a, f.id(1), f.id(2))
                val download = TreeDownload(store, nonWorkerThread)
                val staged = assertNotNull(download.stage(transfer.plan) { transfer.compressed[it].inputStream() })
                val published = download.publish(staged, emptyList(), 10) as TreePublication.Published
                assertEquals(0, published.value.generation.tree.work.replayedNodes, "Recovery must use the revision-bound server endpoint")
            }
            open().use { storage ->
                val store = GameplayTreeCache.bind(storage, f.a, f.id(1), f.id(2))
                val restored = assertNotNull(TreeDownload(store, nonWorkerThread).accepted())
                assertEquals(tree.stateAt(tree.currentNodeId), restored.generation.tree.stateAt(tree.currentNodeId))
                assertEquals(0, restored.generation.tree.work.replayedNodes, "Cold cache restore must use its accepted endpoint")
            }
        } finally { java.nio.file.Files.deleteIfExists(file) }
    }

    @Test fun boundedAuthoritativeEndpointRestoresAnnotationsAndBranchesWithoutBoardReplay() {
        val (transfer, expected, branches) = run {
            val tree = org.asyncttwiz.fixtures.ScaleGames.build(10_000)
            Triple(f.transfer(TreeSync.generation(tree), chunkSize = 1024 * 1024), tree.stateAt(tree.currentNodeId)!!, tree.branches)
        }
        val manifest = GameWire.json.decodeFromString(GameplayManifest.serializer(), transfer.plan.transportManifest.json)
        val bounded = manifest.copy(head = manifest.head!!.let { head -> head.copy(boundedEndpoint = true,
            play = head.play!!.copy(annotations = EventAnnotationState())) }).let { it.copy(digest = GameplaySync.manifestDigest(it)) }
        val plan = transfer.plan.copy(transportManifest = StoredPayload(1, GameWire.json.encodeToString(GameplayManifest.serializer(), bounded)))
        val store = TreeStoreFixture()
        fun recover() {
            val download = TreeDownload(store, nonWorkerThread)
            val staged = assertNotNull(download.stage(plan) { transfer.compressed[it].inputStream() })
            assertEquals(expected, staged.generation.tree.stateAt(staged.generation.tree.currentNodeId))
            assertEquals(0, staged.generation.tree.work.replayedNodes)
            assertTrue(download.publish(staged, emptyList(), 10) is TreePublication.Published)
        }
        recover()
        val restored = assertNotNull(TreeDownload(store, nonWorkerThread).accepted())
        assertEquals(expected, restored.generation.tree.stateAt(restored.generation.tree.currentNodeId))
        assertEquals(0, restored.generation.tree.work.replayedNodes)
        assertEquals(0, restored.generation.tree.work.deltaApplications)
        assertEquals(0, restored.generation.tree.work.ancestrySteps)
        assertEquals(branches, restored.generation.tree.branches)
    }

    @Test fun cacheEndpointIntegrityAndRevisionBindingFailClosedAndLegacyCacheNeedsBaseline() {
        val before = f.generation(f.tap(f.root()))
        val after = f.generation(f.tap(before.tree, 101, f.b, 12))
        val store = TreeStoreFixture().apply { seed(before) }
        val download = TreeDownload(store, nonWorkerThread)
        assertTrue(download.apply(f.delta(before, after)) is TreePublication.Published)
        val valid = store.saved!!
        val fresh = TreeDownload(store, nonWorkerThread).accepted()!!
        assertEquals(after.tree.stateAt(after.tree.currentNodeId), fresh.generation.tree.stateAt(after.tree.currentNodeId))
        assertEquals(0, fresh.generation.tree.work.replayedNodes)
        store.saved = valid.copy(version = valid.version.copy(revision = 12))
        assertFails { TreeDownload(store, nonWorkerThread).accepted() }
        val endpoint = valid.records.single { it.key.kind == "tree-cache-endpoint" }
        store.saved = valid.copy(records = valid.records.map { record -> if (record != endpoint) record else {
            val json = GameWire.json.parseToJsonElement(record.payload.json).jsonObject
            record.copy(payload = record.payload.copy(json = JsonObject(json + ("digest" to JsonPrimitive("corrupt"))).toString()))
        } })
        assertFails { TreeDownload(store, nonWorkerThread).accepted() }
        store.saved = valid.copy(records = valid.records - endpoint)
        assertNull(TreeDownload(store, nonWorkerThread).accepted(), "An old cache cannot trigger implicit root replay")
    }

    @Test fun recoveryRejectsManifestEndpointWithCorruptIntegrityOrWrongRevision() {
        val input = f.transfer(f.generation(f.tap(f.root())))
        val manifest = GameWire.json.decodeFromString(GameplayManifest.serializer(), input.plan.transportManifest.json)
        for (bad in listOf(manifest.copy(digest = "corrupt"), manifest.copy(revision = "11").let {
            it.copy(digest = GameplaySync.manifestDigest(it)) })) {
            val plan = input.plan.copy(transportManifest = StoredPayload(1, GameWire.json.encodeToString(GameplayManifest.serializer(), bad)))
            assertFails { TreeDownload(TreeStoreFixture(), nonWorkerThread).stage(plan) { error("Invalid manifest must not fetch chunks") } }
        }
    }

    @Test fun baselineRecoveryAlsoRestoresItsAuthoritativeEndpointWithoutRootReplay() {
        var tree = f.root()
        repeat(360) { n -> tree = f.tap(tree, 1000 + n, tapped = n % 2 == 0) }
        val transfer = f.transfer(f.generation(tree), chunkSize = 1024 * 1024)
        val manifest = GameWire.json.decodeFromString(GameplayManifest.serializer(), transfer.plan.transportManifest.json)
        val replica = GameplayReplica()
        assertTrue(replica.baseline(GameplayBaseline(GameplaySnapshot(manifest.head!!, emptyList(), TreeSync.records(f.generation(tree)).toList()), emptyList())))
        assertEquals(tree.stateAt(tree.currentNodeId), replica.accepted)
        assertEquals(0, replica.tree!!.tree.work.replayedNodes)
        assertEquals(0, replica.tree!!.tree.work.rootMaterializations)
    }

    // SYS-015
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
        assertEquals(input.tree.nodes.size, staged.history.indexedNodes)
        val result = download.publish(staged, emptyList(), 10) as TreePublication.Published
        assertEquals(input, result.value.generation)
        assertSame(staged.history.index, result.value.history.index)
        assertSame(result.value.history.index, download.accepted()!!.history.index)
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
        assertEquals(1, result.value.history.indexedNodes)
        val old = store.saved
        val stale = download.stage(transfer.plan) { transfer.compressed[it].inputStream() }
        assertEquals(TreePublication.RestartRequired, download.publish(stale, emptyList(), 11))
        assertEquals(old, store.saved)
        assertNull(store.transfer())
        val gap = download.stage(transfer.plan) { transfer.compressed[it].inputStream() }
        assertEquals(TreePublication.RestartRequired, download.publish(gap, listOf(delta.copy(baseRevision = 11, revision = 12)), 12))
        assertEquals(old, store.saved)
    }

    @Test fun cachedGenerationKeepsLayoutAndLiveDeltaIndexesOnlyNewRecords() {
        val before = f.tap(f.root())
        val after = f.tap(before, 101, f.b, 12)
        val download = TreeDownload(TreeStoreFixture().apply { seed(f.generation(before)) }, nonWorkerThread)
        val cold = assertNotNull(download.accepted())
        assertEquals(1, cold.history.indexedNodes)
        assertSame(cold.history.index, download.accepted()!!.history.index)
        val published = (download.apply(f.delta(f.generation(before), f.generation(after))) as TreePublication.Published).value
        assertEquals(1, published.history.indexedNodes)
        assertSame(cold.history.index.layout.chips[before.currentNodeId], published.history.index.layout.chips[before.currentNodeId])
        assertSame(published.history.index, download.accepted()!!.history.index)
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
        val next = f.transfer(f.branched(), revision = 11, n = 201)
        download.stage(next.plan) { next.compressed[it].inputStream() }
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
