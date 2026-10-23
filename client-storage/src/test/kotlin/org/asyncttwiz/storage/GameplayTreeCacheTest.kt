package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties

/** Real generated SQLDelight queries against file-backed SQLite, not a store double. Typed
 * replay/gzip adapter fixtures execute in protocol/Android tests, not server/native acceptance.
 */
class GameplayTreeCacheTest {
    @Test fun queuedReviewAfterRebindIsIgnoredAndReopenedTableReadsCurrentTree() {
        initialize()
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            open().use { storage ->
                val old = bind(storage)
                val entered = java.util.concurrent.CountDownLatch(1)
                val resume = java.util.concurrent.CountDownLatch(1)
                val pending = worker.submit {
                    entered.countDown()
                    check(resume.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    old.reviewCursor("obsolete-review")
                }
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                try {
                    val reopened = bind(storage)
                    val version = reopened.version()!!
                    val currentRoot = root.copy(payload = StoredPayload(1, "current-tree"))
                    assertNotNull(reopened.apply(version, 11, listOf(currentRoot), emptyList()))
                    reopened.reviewCursor("current")
                } finally { resume.countDown() }
                pending.get(5, java.util.concurrent.TimeUnit.SECONDS) // formerly the fatal executor exception
                val reopened = bind(storage)
                assertEquals(11, reopened.accepted()!!.version.revision)
                assertEquals("current-tree", reopened.accepted()!!.records.single { it.key == root.key }.payload.json)
                assertEquals("current", reopened.reviewCursor())
            }
        } finally {
            worker.shutdown()
            assertTrue(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
        }
    }

    @Test fun closedStorageAndInactiveAccountBindingIgnoreLateCallbacksWithoutReactivatingOwner() {
        initialize()
        val storage = open()
        val cache = bind(storage)
        val version = cache.version()!!
        storage.activateAccount("b")
        val obsolete = bind(storage)
        assertFalse(obsolete.isCurrent())
        assertEquals(StoredPayload(1, "b"), storage.personal("installation", "account-owner"))
        storage.close()
        assertFalse(cache.isCurrent())
        assertNull(cache.accepted()); assertNull(cache.version()); assertNull(cache.transfer()); assertNull(cache.reviewCursor())
        cache.begin(transfer()); cache.putChunk("transfer", 0, StoredPayload(1, "late"))
        assertNull(cache.chunk("transfer", 0)); cache.restart("transfer"); cache.reviewCursor("late")
        assertNull(cache.publish("transfer", version, 11, listOf(root)))
        assertNull(cache.apply(version, 11, listOf(root), emptyList()))
        assertFalse(bind(storage).isCurrent())
    }

    @Test fun sessionFenceIgnoresStaleCallbackInsideStorageTransactionBeforeDiskPublication() {
        initialize()
        open().use { storage ->
            val cache = bind(storage)
            val before = cache.accepted()!!
            stage(cache, "callback", 11)
            cache.reviewCursor("original")
            var current = true
            cache.guard { current }
            current = false // account/play callback fence flips before account activation/cleanup.
            cache.putChunk("callback", 0, StoredPayload(1, "late"))
            assertNull(cache.publish("callback", before.version, 11, listOf(offshoot)))
            assertNull(cache.apply(before.version, 11, listOf(offshoot), emptyList()))
            cache.reviewCursor("late")
            assertNull(cache.accepted()); assertNull(cache.version()); assertNull(cache.transfer()); assertNull(cache.reviewCursor())
            assertFalse(cache.isCurrent())
        }
        open().use { storage ->
            val cache = bind(storage)
            assertEquals(10, cache.accepted()!!.version.revision)
            assertEquals(setOf(root, offshoot, removed), cache.accepted()!!.records.toSet())
            assertEquals("original", cache.reviewCursor())
            assertEquals("callback", cache.transfer()!!.id)
        }
    }
    @TempDir lateinit var directory: Path
    private val file get() = directory.resolve("tree.db")
    private val root = record("root", "root", "root-data")
    private val offshoot = record("node", "offshoot", "retained-offshoot")
    private val removed = record("removed-parent", "deleted", "root")

    private fun record(kind: String, id: String, value: String) = AcceptedRecord(RecordKey(kind, id), StoredPayload(1, value))
    private fun open() = (ClientStorage.open {
        JdbcSqliteDriver("jdbc:sqlite:$file", Properties().apply { setProperty("foreign_keys", "true") })
    } as StorageOpenResult.Opened).storage
    private fun bind(storage: ClientStorage, play: String = "play") = GameplayTreeCache.bind(storage, "a", "game", play)
    private fun transfer(id: String = "transfer", revision: Long = 10) = TreeCacheTransfer(id, revision, 2, StoredPayload(1, "verified-transport-manifest:$id:$revision"))
    private fun stage(cache: GameplayTreeCache, id: String = "transfer", revision: Long = 10) {
        cache.begin(transfer(id, revision))
        cache.putChunk(id, 0, StoredPayload(1, "chunk-zero"))
        cache.putChunk(id, 1, StoredPayload(1, "chunk-one"))
    }
    private fun execute(sql: String) = DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
        connection.createStatement().use { it.execute(sql) }; Unit
    }
    private fun scalar(sql: String): String = DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
        connection.createStatement().use { it.executeQuery(sql).use { rows -> assertTrue(rows.next()); rows.getString(1) } }
    }
    private fun initialize(): TreeCacheVersion = open().use { storage ->
        storage.activateAccount("a")
        val cache = bind(storage)
        stage(cache)
        requireNotNull(cache.publish("transfer", null, 10, listOf(root, offshoot, removed)))
    }

    @Test fun completeReplacementIsDurableAndDoesNotLeaveAbsentBaselineRecords() {
        initialize()
        open().use { storage ->
            val cache = bind(storage)
            storage.commitAccepted("other-game", null, 40, listOf(offshoot))
            storage.savePersonal("draft", "text", StoredPayload(1, "keep draft"))
            storage.cachePublic("art", StoredPayload(1, "keep art"))
            cache.reviewCursor("offshoot")
            val previous = cache.accepted()!!
            assertEquals(setOf(root, offshoot, removed), previous.records.toSet())
            stage(cache, "fresh", 12)
            assertEquals(previous, cache.accepted(), "staging must never masquerade as the accepted tree")
            cache.publish("fresh", previous.version, 12, listOf(root, removed))
            assertNull(cache.transfer())
            assertEquals("offshoot", cache.reviewCursor()) // personal cursor reconciliation belongs to typed coordinator
        }
        open().use { storage ->
            val cache = bind(storage)
            assertEquals(12, cache.accepted()!!.version.revision)
            assertEquals(setOf(root, removed), cache.accepted()!!.records.toSet())
            assertNotNull(storage.accepted("other-game"))
            assertEquals(StoredPayload(1, "keep draft"), storage.personal("draft", "text"))
            assertEquals(StoredPayload(1, "keep art"), storage.publicCache("art"))
        }
        assertEquals("2", scalar("PRAGMA user_version"), "no schema/version change")
        assertEquals("accepted_cursor,accepted_record,personal_state,public_cache", scalar(
            "SELECT group_concat(name, ',') FROM (SELECT name FROM sqlite_master WHERE type='table' ORDER BY name)"))
    }

    @Test fun incompleteStagingSurvivesRestartAndNeverPublishesEvenAnInitialCursor() {
        open().use { storage ->
            storage.activateAccount("a")
            val cache = bind(storage)
            cache.begin(transfer())
            cache.putChunk("transfer", 0, StoredPayload(1, "chunk-zero"))
            assertNull(cache.publish("transfer", null, 10, listOf(root)))
            assertNull(cache.accepted())
        }
        assertEquals("0", scalar("SELECT count(*) FROM accepted_cursor"))
        open().use { storage ->
            val cache = bind(storage)
            assertEquals(transfer(), cache.transfer())
            assertEquals(StoredPayload(1, "chunk-zero"), cache.chunk("transfer", 0))
            assertNull(cache.chunk("transfer", 1))
            cache.putChunk("transfer", 1, StoredPayload(1, "chunk-one"))
            cache.publish("transfer", null, 10, listOf(root, offshoot))
        }
        open().use { assertEquals(setOf(root, offshoot), bind(it).accepted()!!.records.toSet()) }
    }

    @Test fun realSqlInsertFailureRollsBackReplacementCursorAndStageCleanupAcrossReopen() {
        initialize()
        execute("CREATE TRIGGER fail_node BEFORE INSERT ON accepted_record WHEN NEW.record_id = 'new-node' BEGIN SELECT RAISE(ABORT, 'injected disk write failure'); END")
        open().use { storage ->
            val cache = bind(storage)
            val old = cache.accepted()!!
            stage(cache, "fresh", 11)
            assertThrows(Exception::class.java) { cache.publish("fresh", old.version, 11, listOf(root, record("node", "new-node", "new"))) }
            assertEquals(old, cache.accepted())
            assertEquals(transfer("fresh", 11), cache.transfer())
            assertNotNull(cache.chunk("fresh", 1))
        }
        open().use { storage ->
            val cache = bind(storage)
            assertEquals(10, cache.accepted()!!.version.revision)
            assertEquals(setOf(root, offshoot, removed), cache.accepted()!!.records.toSet())
            assertNotNull(cache.transfer())
        }
        execute("DROP TRIGGER fail_node")
        open().use { storage ->
            val cache = bind(storage)
            cache.publish("fresh", cache.version(), 11, listOf(root))
            assertEquals(listOf(root), cache.accepted()!!.records)
        }
    }

    @Test fun failureAfterAllAcceptedWritesDuringStageCleanupAlsoRollsBackPublication() {
        val original = initialize()
        execute("CREATE TRIGGER fail_cleanup BEFORE DELETE ON personal_state WHEN OLD.state_key = 'transfer' BEGIN SELECT RAISE(ABORT, 'injected cleanup failure'); END")
        open().use { storage ->
            val cache = bind(storage)
            stage(cache, "next", 11)
            assertThrows(Exception::class.java) { cache.publish("next", original, 11, listOf(root)) }
            assertEquals(original, cache.version())
            assertNotNull(cache.chunk("next", 0))
        }
        open().use { storage ->
            val cache = bind(storage)
            assertEquals(original, cache.version())
            assertEquals(setOf(root, offshoot, removed), cache.accepted()!!.records.toSet())
        }
    }

    @Test fun interleavedAcceptedDeltaAndEqualRevisionReplacementCannotEvadePublicationCas() {
        initialize()
        open().use { storage ->
            val cache = bind(storage)
            val old = cache.version()!!
            stage(cache, "late", 10)
            val current = cache.apply(old, 11, listOf(record("node", "new", "new")), emptyList())!!
            assertNull(cache.publish("late", old, 11, listOf(root)))
            assertNull(cache.apply(old, 12, listOf(root), emptyList()))
            assertNull(cache.publish("late", current, 10, listOf(root)))
            val repaired = cache.publish("late", current, 11, cache.accepted()!!.records)!!
            assertNotEquals(current.publicationId, repaired.publicationId)
            stage(cache, "same-revision", 11)
            assertNull(cache.publish("same-revision", current, 11, listOf(root)))
            assertEquals(repaired, cache.version())
        }
    }

    @Test fun changedRecordDeltaIsAtomicDurableAndDoesNotRewriteUntouchedNodes() {
        initialize()
        execute("CREATE TRIGGER no_offshoot_rewrite BEFORE INSERT ON accepted_record WHEN NEW.record_id = 'offshoot' BEGIN SELECT RAISE(ABORT, 'unchanged node rewritten'); END")
        open().use { storage ->
            val cache = bind(storage)
            cache.apply(cache.version()!!, 11, listOf(root.copy(payload = StoredPayload(1, "new-root"))), listOf(removed.key))
        }
        open().use { storage ->
            val cached = bind(storage).accepted()!!
            assertEquals(11, cached.version.revision)
            assertEquals(setOf(root.copy(payload = StoredPayload(1, "new-root")), offshoot), cached.records.toSet())
        }
    }

    @Test fun separateHandleSessionAndIncarnationBindingsFenceOldCallbacksBeforeWrites() {
        initialize()
        open().use { first -> open().use { second ->
            val old = bind(first)
            stage(old, "old", 11)
            val replacement = bind(second)
            assertFalse(old.isCurrent())
            old.putChunk("old", 0, StoredPayload(1, "late"))
            assertNull(old.publish("old", old.version(), 11, listOf(root)))
            assertNotNull(replacement.accepted())
            val freshPlay = bind(first, "fresh-play")
            assertFalse(replacement.isCurrent())
            assertNull(freshPlay.accepted(), "old incarnation must not be readable as the new one")
            assertNull(freshPlay.transfer())
            val oldVersion = freshPlay.version()
            stage(freshPlay, "fresh-play-transfer", 20)
            freshPlay.publish("fresh-play-transfer", oldVersion, 20, listOf(record("root", "fresh-root", "fresh")))
            assertEquals("fresh-root", freshPlay.accepted()!!.records.single().key.id)
        } }
    }

    @Test fun logoutAndAccountAbaClearPrivateAcceptedStagingAndReviewButRetainPublicCache() {
        initialize()
        open().use { storage ->
            val old = bind(storage)
            stage(old, "staged", 11)
            old.reviewCursor("offshoot")
            storage.cachePublic("art", StoredPayload(1, "public"))
            storage.activateAccount("b")
            assertFalse(old.isCurrent())
            assertNull(old.accepted())
            storage.activateAccount("a")
            assertFalse(old.isCurrent(), "same account after switch must not resurrect an old callback")
            val cache = bind(storage)
            assertNull(cache.accepted()); assertNull(cache.transfer()); assertNull(cache.reviewCursor())
            stage(cache)
            cache.publish("transfer", null, 10, listOf(root))
            val version = cache.version()!!
            storage.clearPrivateData()
            assertFalse(cache.isCurrent())
            assertNull(cache.apply(version, 11, listOf(offshoot), emptyList()))
            assertEquals(StoredPayload(1, "public"), storage.publicCache("art"))
        }
        assertEquals("0", scalar("SELECT count(*) FROM accepted_record"))
        assertEquals("0", scalar("SELECT count(*) FROM personal_state"))
    }

    @Test fun transferIdentityAndManifestChangesRestartOnlyIncompleteChunks() {
        initialize()
        open().use { storage ->
            val cache = bind(storage)
            stage(cache, "one", 11)
            cache.begin(transfer("two", 12))
            assertNull(cache.chunk("two", 0))
            cache.putChunk("one", 0, StoredPayload(1, "stale"))
            cache.restart("one")
            assertNull(cache.chunk("one", 0))
            assertEquals("two", cache.transfer()!!.id)
            cache.putChunk("two", 0, StoredPayload(1, "correct"))
            cache.putChunk("two", 0, StoredPayload(1, "correct"))
            assertThrows(IllegalStateException::class.java) { cache.putChunk("two", 0, StoredPayload(1, "conflict")) }
            cache.begin(transfer("two", 12).copy(manifest = StoredPayload(1, "changed manifest")))
            assertNull(cache.chunk("two", 0))
            assertEquals(10, cache.accepted()!!.version.revision)
            cache.invalidate()
            assertFalse(cache.isCurrent())
            cache.reviewCursor("late")
        }
    }
}
