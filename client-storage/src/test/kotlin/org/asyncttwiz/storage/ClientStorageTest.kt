package org.asyncttwiz.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ClientStorageTest {
    @TempDir lateinit var directory: Path
    private val file get() = directory.resolve("replica.db")
    private val key = RecordKey("fixture", "one")
    private val first = AcceptedRecord(key, StoredPayload(1, """{"value":"first 🧙"}"""))
    private val second = AcceptedRecord(RecordKey("fixture", "two"), StoredPayload(2, """{"value":"second"}"""))

    private fun raw(path: Path = file) = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}",
        Properties().apply { setProperty("foreign_keys", "true") })
    private fun open(): ClientStorage = (ClientStorage.open { raw() } as StorageOpenResult.Opened).storage

    private fun retained() {
        val bytes = Files.readAllBytes(Path.of(System.getProperty("storage.fixture")))
        assertEquals(FIXTURE_SHA256, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        Files.write(file, bytes)
    }

    private fun <T> jdbc(block: (java.sql.Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use(block)

    private fun execute(sql: String) = jdbc { connection -> connection.createStatement().use { it.execute(sql) }; Unit }
    private fun scalar(sql: String): String = jdbc { connection ->
        connection.createStatement().use { it.executeQuery(sql).use { rows -> assertTrue(rows.next()); rows.getString(1) } }
    }

    @Test fun freshCreateCommitAndExactReopen() {
        assertEquals("21", System.getProperty("java.specification.version"), "Host SQLite qualification must execute on JVM21")
        open().use { storage ->
            assertNull(storage.accepted("replica-a"))
            storage.commitAccepted("replica-a", null, 0, listOf(first, second))
            assertEquals(AcceptedSnapshot(0, listOf(first, second)), storage.accepted("replica-a"))
            storage.commitAccepted("replica-a", 0, Long.MAX_VALUE, listOf(first.copy(payload = StoredPayload(3, "{}"))), listOf(second.key))
        }
        open().use { storage ->
            assertEquals(AcceptedSnapshot(Long.MAX_VALUE, listOf(first.copy(payload = StoredPayload(3, "{}")))), storage.accepted("replica-a"))
            assertNull(storage.accepted("replica-b"))
        }
        assertEquals("3", scalar("PRAGMA user_version"))
        assertEquals("ok", scalar("PRAGMA integrity_check"))
        println("PASS actual SQLite ${scalar("SELECT sqlite_version()")} file-backed create/commit/reopen, exact signed-64-bit cursor")
    }

    @Test fun incrementalOnlyMigrationDropsTreesAndStagingButRetainsOtherScopes() {
        open().use { storage ->
            storage.commitAccepted("game-tree:game", null, 5, listOf(first))
            storage.commitAccepted("account:profile", null, 2, listOf(second))
            storage.savePersonal("game-tree-staging:game", "chunk:0", StoredPayload(1, "legacy"))
            storage.savePersonal("game-tree-review:game", "cursor", StoredPayload(1, "legacy"))
            storage.savePersonal("installation", "game-tree-fence:game", StoredPayload(1, "legacy"))
            storage.cachePublic("catalog", StoredPayload(1, "keep"))
        }
        execute("PRAGMA user_version = 2")
        open().use { storage ->
            assertNull(storage.accepted("game-tree:game"))
            assertNull(storage.personal("game-tree-staging:game", "chunk:0"))
            assertNull(storage.personal("game-tree-review:game", "cursor"))
            assertNull(storage.personal("installation", "game-tree-fence:game"))
            assertEquals(AcceptedSnapshot(2, listOf(second)), storage.accepted("account:profile"))
            assertEquals(StoredPayload(1, "keep"), storage.publicCache("catalog"))
        }
        assertEquals("3", scalar("PRAGMA user_version"))
    }

    // LOB-007
    @Test fun accountActivationRetainsOnlySameProfileAndLogoutDeletesAllPrivateScopes() {
        open().use { storage ->
            storage.activateAccount("account-a")
            storage.commitAccepted("game-a", null, 3, listOf(first))
            storage.commitAccepted("decks-a", null, 7, listOf(second))
            storage.savePersonal("account:account-a", "shell", StoredPayload(1, "\"GAMES\""))
            storage.cachePublic("public-art", StoredPayload(1, "{}"))
        }
        open().use { storage ->
            storage.activateAccount("account-a")
            assertNotNull(storage.accepted("game-a"))
            assertNotNull(storage.personal("account:account-a", "shell"))
            storage.activateAccount("account-b")
            assertNull(storage.accepted("game-a"))
            assertNull(storage.accepted("decks-a"))
            assertNull(storage.personal("account:account-a", "shell"))
            assertEquals(StoredPayload(1, "account-b"), storage.personal("installation", "account-owner"))
            assertNotNull(storage.publicCache("public-art"))
            storage.commitAccepted("game-b", null, 1, listOf(second))
            storage.clearPrivateData()
        }
        open().use { storage ->
            assertNull(storage.accepted("game-b"))
            assertNull(storage.personal("installation", "account-owner"))
            assertNotNull(storage.publicCache("public-art"))
            storage.activateAccount("account-a")
            assertNull(storage.accepted("game-a"))
        }
        assertEquals("0", scalar("SELECT count(*) FROM accepted_record"))
        assertEquals("0", scalar("SELECT count(*) FROM accepted_cursor"))
        println("PASS same-account retained profile, atomic cross-account private replica/draft deletion and Logout/reopen; public cache retained")
    }

    @Test fun cursorAndAllRecordChangesRollbackAfterRealSqlFailure() {
        open().use { it.commitAccepted("replica-a", null, 4, listOf(first)) }
        execute("CREATE TRIGGER fail_second BEFORE INSERT ON accepted_record WHEN NEW.record_id = 'two' BEGIN SELECT RAISE(ABORT, 'test constraint'); END")
        open().use { storage ->
            assertThrows(Exception::class.java) {
                storage.commitAccepted("replica-a", 4, 5, listOf(first.copy(payload = StoredPayload(1, "{}")), second))
            }
            assertEquals(AcceptedSnapshot(4, listOf(first)), storage.accepted("replica-a"))
        }
        open().use { assertEquals(AcceptedSnapshot(4, listOf(first)), it.accepted("replica-a")) }
        println("PASS first generated upsert rolled back after second SQLite insert failed; old records/cursor survive reopen")
    }

    @Test fun firstBatchFailureDoesNotPublishEvenAnEmptyCursor() {
        open().close()
        execute("CREATE TRIGGER fail_all BEFORE INSERT ON accepted_record BEGIN SELECT RAISE(ABORT, 'test constraint'); END")
        open().use { storage ->
            assertThrows(Exception::class.java) { storage.commitAccepted("replica-a", null, 4, listOf(first)) }
            assertNull(storage.accepted("replica-a"))
        }
        assertEquals("0", scalar("SELECT count(*) FROM accepted_cursor"))
    }

    @Test fun removalAlsoRollsBackAndWrongBaseCannotAdvance() {
        open().use { it.commitAccepted("replica-a", null, 4, listOf(first)) }
        execute("CREATE TRIGGER fail_all BEFORE INSERT ON accepted_record BEGIN SELECT RAISE(ABORT, 'test constraint'); END")
        open().use { storage ->
            assertThrows(Exception::class.java) { storage.commitAccepted("replica-a", 4, 5, listOf(second), listOf(key)) }
            assertThrows(IllegalStateException::class.java) { storage.commitAccepted("replica-a", 3, 6, emptyList()) }
            assertThrows(IllegalArgumentException::class.java) { storage.commitAccepted("replica-a", 4, 4, emptyList()) }
            assertEquals(AcceptedSnapshot(4, listOf(first)), storage.accepted("replica-a"))
        }
    }

    @Test fun cacheEvictionAndPersonalChangesNeverDeleteAcceptedData() {
        val draft = StoredPayload(4, """{"draft":"Δ unsent text, not a request"}""")
        open().use { storage ->
            storage.commitAccepted("replica-a", null, 7, listOf(first))
            storage.commitAccepted("replica-b", null, 10, listOf(second))
            storage.savePersonal("replica-a", "draft", draft)
            storage.cachePublic("art-or-catalog", StoredPayload(1, "{}"))
        }
        open().use { storage ->
            assertNotNull(storage.publicCache("art-or-catalog"))
            storage.evictPublicCache()
            assertNull(storage.publicCache("art-or-catalog"))
            assertEquals(draft, storage.personal("replica-a", "draft"))
            assertNull(storage.personal("replica-b", "draft"))
            assertEquals(AcceptedSnapshot(7, listOf(first)), storage.accepted("replica-a"))
            assertEquals(AcceptedSnapshot(10, listOf(second)), storage.accepted("replica-b"))
            storage.removePersonal("replica-a", "draft")
            assertNull(storage.personal("replica-a", "draft"))
            assertEquals(AcceptedSnapshot(7, listOf(first)), storage.accepted("replica-a"))
        }
        assertEquals("accepted_cursor,accepted_record,personal_state,public_cache", scalar("SELECT group_concat(name, ',') FROM (SELECT name FROM sqlite_master WHERE type='table' ORDER BY name)"))
        println("PASS separate accepted/personal/public-cache tables; no outgoing/prediction/exposure journal")
    }

    @Test fun actualGeneratedForwardMigrationPreservesPopulatedV1AndReopens() {
        retained()
        assertEquals("1", scalar("PRAGMA user_version"))
        repeat(2) {
            open().use { storage ->
                assertEquals(AcceptedSnapshot(9007199254740993, listOf(AcceptedRecord(key,
                    StoredPayload(1, """{"text":"retained 🧙 α","number":"9007199254740993"}""")))), storage.accepted("replica-a"))
                assertEquals(17, storage.accepted("replica-b")!!.revision)
                assertEquals(StoredPayload(1, """{"text":"unfinished Δ"}"""), storage.personal("replica-a", "draft"))
                assertNull(storage.publicCache("missing"))
            }
        }
        assertEquals("3", scalar("PRAGMA user_version"))
        open().use { storage ->
            storage.commitAccepted("replica-a", 9007199254740993, 9007199254740994, listOf(second))
            storage.cachePublic("new", StoredPayload(2, "{}"))
        }
        open().use { assertEquals(2, it.accepted("replica-a")!!.records.size) }
        println("PASS retained populated v1 -> generated 1.sqm -> v2; exact records/cursors/personal state survive reopen and subsequent writes")
    }

    @Test fun failedLaterMigrationStatementRollsBackEarlierDdlAndVersionThenRetryWorks() {
        retained()
        val before = Files.readAllBytes(file)
        var earlierAlterExecuted = false
        var driverClosed = false
        val result = ClientStorage.open {
            val real = raw()
            object : SqlDriver by real {
                override fun execute(identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> {
                    if (sql.startsWith("CREATE TABLE public_cache")) {
                        assertTrue(earlierAlterExecuted)
                        throw java.io.IOException("simulated storage I/O interruption after actual ALTER statements")
                    }
                    val output = real.execute(identifier, sql, parameters, binders)
                    if (sql.startsWith("ALTER TABLE")) earlierAlterExecuted = true
                    return output
                }
                override fun close() { driverClosed = true; real.close() }
            }
        }
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.MIGRATION_FAILED), result)
        assertTrue(earlierAlterExecuted && driverClosed)
        assertEquals("1", scalar("PRAGMA user_version"))
        assertEquals("0", scalar("SELECT count(*) FROM pragma_table_info('accepted_record') WHERE name = 'payload_version'"))
        assertEquals("2", scalar("SELECT count(*) FROM accepted_record"))
        assertArrayEquals(before, Files.readAllBytes(file))
        open().use { assertEquals(9007199254740993, it.accepted("replica-a")!!.revision) }
        println("PASS generated migration interrupted after real earlier ALTERs: no writable repository, driver closed, prior DB byte-identical/version1; normal retry succeeds")
    }

    // SYS-035
    @Test fun realMigrationSqlErrorPreservesOldDataWithoutWipe() {
        retained()
        // A preexisting conflicting table makes the third generated statement fail in SQLite.
        execute("CREATE TABLE public_cache (sentinel TEXT NOT NULL)")
        execute("INSERT INTO public_cache VALUES ('preserve me')")
        val before = Files.readAllBytes(file)
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.MIGRATION_FAILED), ClientStorage.open { raw() })
        assertEquals("1", scalar("PRAGMA user_version"))
        assertEquals("preserve me", scalar("SELECT sentinel FROM public_cache"))
        assertArrayEquals(before, Files.readAllBytes(file))
        assertEquals("0", scalar("SELECT count(*) FROM pragma_table_info('personal_state') WHERE name='payload_version'"))
        println("PASS real SQLite third-statement migration error preserves prior schema/data/version byte-for-byte")
    }

    // SYS-035
    @Test fun newerVersionRefusesWithNoWritableHandleAndPreservesBytes() {
        retained()
        execute("PRAGMA user_version = 900")
        val before = Files.readAllBytes(file)
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.UNSUPPORTED_VERSION), ClientStorage.open { raw() })
        assertArrayEquals(before, Files.readAllBytes(file))
        assertEquals("900", scalar("PRAGMA user_version"))
        assertEquals("2", scalar("SELECT count(*) FROM accepted_record"))
    }

    @Test fun unversionedExistingOrMalformedCurrentSchemaIsNotRecreated() {
        execute("CREATE TABLE existing (sentinel TEXT)")
        execute("INSERT INTO existing VALUES ('do not erase')")
        val before = Files.readAllBytes(file)
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.INVALID_SCHEMA), ClientStorage.open { raw() })
        assertArrayEquals(before, Files.readAllBytes(file))
        assertEquals("do not erase", scalar("SELECT sentinel FROM existing"))
        execute("PRAGMA user_version = 2")
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.INVALID_SCHEMA), ClientStorage.open { raw() })
        assertEquals("do not erase", scalar("SELECT sentinel FROM existing"))
    }

    @Test fun invalidOldSchemaRefusesBeforeAnyMigration() {
        retained()
        execute("DROP TABLE personal_state")
        val before = Files.readAllBytes(file)
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.INVALID_SCHEMA), ClientStorage.open { raw() })
        assertArrayEquals(before, Files.readAllBytes(file))
        assertEquals("1", scalar("PRAGMA user_version"))
        assertEquals("0", scalar("SELECT count(*) FROM pragma_table_info('accepted_record') WHERE name='payload_version'"))
    }

    @Test fun driverWithoutPerConnectionForeignKeysIsNotExposed() {
        val result = ClientStorage.open { JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}") }
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.INVALID_SCHEMA), result)
        assertEquals("0", scalar("PRAGMA user_version"))
        assertEquals("0", scalar("SELECT count(*) FROM sqlite_master"))
    }

    // SYS-035
    @Test fun invalidStoredPayloadVersionAndOrphanRowsRefuseWithoutErasure() {
        open().use { it.commitAccepted("replica-a", null, 1, listOf(first)) }
        execute("UPDATE accepted_record SET payload_version = 0")
        val before = Files.readAllBytes(file)
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.INVALID_SCHEMA), ClientStorage.open { raw() })
        assertArrayEquals(before, Files.readAllBytes(file))
        execute("UPDATE accepted_record SET payload_version = 1")
        execute("DELETE FROM accepted_cursor") // Independent diagnostic connection deliberately has FKs off.
        val orphaned = Files.readAllBytes(file)
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.INVALID_SCHEMA), ClientStorage.open { raw() })
        assertArrayEquals(orphaned, Files.readAllBytes(file))
        assertEquals("1", scalar("SELECT count(*) FROM accepted_record"))
    }

    // SYS-035
    @Test fun corruptFileAndInvalidPathOrFactoryFailureAreExplicitAndNeverDeleted() {
        val corrupt = "not a SQLite file; private sentinel".toByteArray()
        Files.write(file, corrupt)
        assertInstanceOf(StorageOpenResult.Failed::class.java, ClientStorage.open { raw() })
        assertArrayEquals(corrupt, Files.readAllBytes(file))
        assertInstanceOf(StorageOpenResult.Failed::class.java, ClientStorage.open { raw(directory) })
        assertTrue(Files.isDirectory(directory))
        val result = ClientStorage.open { throw IllegalStateException("private sentinel credentials") }
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.OPEN_FAILED), result)
        assertFalse(result.toString().contains("sentinel"))
    }

    @Test fun closeIsIdempotentAndAllOperationsRejectAfterClose() {
        val storage = open()
        storage.close()
        storage.close()
        assertThrows(IllegalStateException::class.java) { storage.accepted("replica-a") }
        assertThrows(IllegalStateException::class.java) { storage.commitAccepted("replica-a", null, 1, listOf(first)) }
        assertThrows(IllegalStateException::class.java) { storage.savePersonal("a", "b", StoredPayload(1, "{}")) }
        assertThrows(IllegalStateException::class.java) { storage.evictPublicCache() }
        open().use { assertNull(it.accepted("replica-a")) }
    }

    @Test fun oneRepositorySerializesAtomicSnapshotReadersAndConcurrentBaseChecks() {
        val pool = Executors.newFixedThreadPool(3)
        try {
            open().use { storage ->
                storage.commitAccepted("replica-a", null, 0, listOf(first))
                val writers = (1..2).map { pool.submit(Callable {
                    runCatching { storage.commitAccepted("replica-a", 0, 1, listOf(second)) }.isSuccess
                }) }
                assertEquals(1, writers.count { it.get() })
            }
        } finally { pool.shutdownNow() }
        open().use { storage ->
            storage.commitAccepted("replica-a", 1, 2, listOf(first.copy(payload = StoredPayload(1, "2"))), listOf(second.key))
            val executor = Executors.newFixedThreadPool(2)
            try {
                val reader = executor.submit(Callable {
                    repeat(100) {
                        val snapshot = storage.accepted("replica-a")!!
                        assertEquals(snapshot.revision.toString(), snapshot.records.single().payload.json)
                    }
                })
                val writer = executor.submit(Callable {
                    for (revision in 3L..50L) storage.commitAccepted("replica-a", revision - 1, revision,
                        listOf(first.copy(payload = StoredPayload(1, revision.toString()))))
                })
                reader.get()
                writer.get()
            } finally { executor.shutdownNow() }
        }
        println("PASS one competing local-base writer wins; concurrent generated snapshot reads never observe record/cursor mismatch")
    }

    companion object {
        private const val FIXTURE_SHA256 = "5f88c2a52575c080088c59052091eb8749414a8d0ce25158186e06af1647d252"
    }
}
