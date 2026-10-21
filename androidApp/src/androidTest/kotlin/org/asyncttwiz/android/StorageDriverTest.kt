package org.asyncttwiz.android

import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.asyncttwiz.storage.AcceptedRecord
import org.asyncttwiz.storage.AcceptedSnapshot
import org.asyncttwiz.storage.AndroidClientStorage
import org.asyncttwiz.storage.ClientStorage
import org.asyncttwiz.storage.RecordKey
import org.asyncttwiz.storage.StorageOpenFailure
import org.asyncttwiz.storage.StorageOpenResult
import org.asyncttwiz.storage.StoredPayload
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** File-backed production AndroidSqliteDriver/repository, not the host JDBC driver or a test app. */
class StorageDriverTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val directory = File(context.noBackupFilesDir, "storage-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
    private val file = File(directory, "replica.db")
    private val key = RecordKey("fixture", "one")
    private val first = AcceptedRecord(key, StoredPayload(1, """{"value":"first 🧙"}"""))
    private val second = AcceptedRecord(RecordKey("fixture", "two"), StoredPayload(2, "{}"))

    private fun open(): ClientStorage {
        assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
        return (AndroidClientStorage.open(file) as StorageOpenResult.Opened).storage
    }

    private fun retained() {
        val bytes = instrumentation.context.assets.open("1.db").use { it.readBytes() }
        assertEquals("5f88c2a52575c080088c59052091eb8749414a8d0ce25158186e06af1647d252",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        file.writeBytes(bytes)
    }

    // Test-owned offline fixture diagnostics only; no application action endpoint/DB helper.
    private fun <T> inspect(block: (SQLiteDatabase) -> T): T = SQLiteDatabase.openDatabase(
        file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
    ).use(block)
    private fun scalar(sql: String): String = inspect { db ->
        db.rawQuery(sql, null).use { rows -> assertTrue(rows.moveToFirst()); rows.getString(0) }
    }
    private fun execute(sql: String) = inspect { it.execSQL(sql) }

    @After fun removeOnlyThisTestsFiles() {
        directory.listFiles().orEmpty().forEach { assertTrue(it.delete()) }
        assertTrue(directory.delete())
    }

    @Test fun freshCreateCommitExactReopenAndCategorySeparation() {
        val draft = StoredPayload(2, """{"draft":"unfinished Δ"}""")
        open().use {
            assertNull(it.accepted("replica-a"))
            it.commitAccepted("replica-a", null, 9007199254740993, listOf(first, second))
            it.savePersonal("replica-a", "draft", draft)
            it.cachePublic("catalog", StoredPayload(1, "{}"))
        }
        open().use {
            assertEquals(AcceptedSnapshot(9007199254740993, listOf(first, second)), it.accepted("replica-a"))
            assertNull(it.accepted("replica-b"))
            assertNotNull(it.publicCache("catalog"))
            it.evictPublicCache()
            assertNull(it.publicCache("catalog"))
            assertEquals(draft, it.personal("replica-a", "draft"))
            it.commitAccepted("replica-a", 9007199254740993, Long.MAX_VALUE, emptyList(), listOf(second.key))
        }
        open().use { assertEquals(AcceptedSnapshot(Long.MAX_VALUE, listOf(first)), it.accepted("replica-a")) }
        assertEquals("2", scalar("PRAGMA user_version"))
        assertEquals("ok", scalar("PRAGMA integrity_check"))
        println("PASS real Android SQLite ${scalar("SELECT sqlite_version()")} fresh/reopen/exact cursor/category separation")
    }

    @Test fun generatedRecordAndCursorTransactionRollsBackAfterActualSqlError() {
        open().use { it.commitAccepted("a", null, 4, listOf(first)) }
        execute("CREATE TRIGGER fail_two BEFORE INSERT ON accepted_record WHEN NEW.record_id='two' BEGIN SELECT RAISE(ABORT, 'fixture constraint'); END")
        open().use { storage ->
            assertThrows(Exception::class.java) {
                storage.commitAccepted("a", 4, 5, listOf(first.copy(payload = StoredPayload(1, "[]")), second))
            }
            assertEquals(AcceptedSnapshot(4, listOf(first)), storage.accepted("a"))
            assertThrows(Exception::class.java) { storage.commitAccepted("b", null, 1, listOf(second)) }
            assertNull(storage.accepted("b"))
            assertThrows(Exception::class.java) { storage.commitAccepted("a", 4, 6, listOf(second), listOf(key)) }
            assertEquals(AcceptedSnapshot(4, listOf(first)), storage.accepted("a"))
        }
        open().use { assertEquals(AcceptedSnapshot(4, listOf(first)), it.accepted("a")) }
    }

    @Test fun populatedRetainedFixtureMigratesThroughGeneratedSqlAndReopens() {
        retained()
        assertEquals("1", scalar("PRAGMA user_version"))
        repeat(2) {
            open().use { storage ->
                assertEquals(AcceptedSnapshot(9007199254740993, listOf(AcceptedRecord(key,
                    StoredPayload(1, """{"text":"retained 🧙 α","number":"9007199254740993"}""")))), storage.accepted("replica-a"))
                assertEquals(17L, storage.accepted("replica-b")!!.revision)
                assertEquals(StoredPayload(1, """{"text":"unfinished Δ"}"""), storage.personal("replica-a", "draft"))
                assertNull(storage.publicCache("missing"))
            }
        }
        assertEquals("2", scalar("PRAGMA user_version"))
        open().use { it.commitAccepted("replica-a", 9007199254740993, 9007199254740994, listOf(second)) }
        open().use { assertEquals(2, it.accepted("replica-a")!!.records.size) }
    }

    @Test fun realThirdMigrationStatementFailureRollsBackEarlierAltersAndClosesThenRetryWorks() {
        retained()
        execute("CREATE TABLE public_cache (sentinel TEXT NOT NULL)")
        execute("INSERT INTO public_cache VALUES ('preserve me')")
        val before = file.readBytes()
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.MIGRATION_FAILED), AndroidClientStorage.open(file))
        assertArrayEquals(before, file.readBytes())
        assertEquals("1", scalar("PRAGMA user_version"))
        assertEquals("preserve me", scalar("SELECT sentinel FROM public_cache"))
        inspect { db ->
            db.rawQuery("PRAGMA table_info(accepted_record)", null).use { rows ->
                while (rows.moveToNext()) assertNotEquals("payload_version", rows.getString(1))
            }
            // An exclusive transaction is obtainable after refusal: no migration transaction leaked.
            db.beginTransaction()
            try { db.execSQL("DROP TABLE public_cache"); db.setTransactionSuccessful() } finally { db.endTransaction() }
        }
        open().use { assertEquals(9007199254740993, it.accepted("replica-a")!!.revision) }
    }

    @Test fun futureSchemaRefusesWithoutWritableHandleOrByteChanges() {
        retained()
        execute("PRAGMA user_version=900")
        val before = file.readBytes()
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.UNSUPPORTED_VERSION), AndroidClientStorage.open(file))
        assertArrayEquals(before, file.readBytes())
        assertEquals("900", scalar("PRAGMA user_version"))
        assertEquals("2", scalar("SELECT count(*) FROM accepted_record"))
    }

    @Test fun existingUnversionedAndMalformedSchemasAreNeverRecreated() {
        SQLiteDatabase.openDatabase(file.absolutePath, null,
            SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use {
            it.execSQL("CREATE TABLE sentinel (value TEXT)")
            it.execSQL("INSERT INTO sentinel VALUES ('do not erase')")
        }
        val before = file.readBytes()
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.INVALID_SCHEMA), AndroidClientStorage.open(file))
        assertArrayEquals(before, file.readBytes())
        assertEquals("do not erase", scalar("SELECT value FROM sentinel"))
        execute("PRAGMA user_version=2")
        val malformed = file.readBytes()
        assertEquals(StorageOpenResult.Failed(StorageOpenFailure.INVALID_SCHEMA), AndroidClientStorage.open(file))
        assertArrayEquals(malformed, file.readBytes())
    }

    @Test fun corruptFileDoesNotInvokeDestructiveAndroidRecovery() {
        val bytes = "not SQLite; private synthetic sentinel".toByteArray()
        file.writeBytes(bytes)
        val result = AndroidClientStorage.open(file)
        assertTrue(result is StorageOpenResult.Failed)
        assertFalse(result.toString().contains("sentinel"))
        assertArrayEquals(bytes, file.readBytes())
        // Repeating open cannot replace the failed file with an empty schema.
        assertTrue(AndroidClientStorage.open(file) is StorageOpenResult.Failed)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun poolForeignKeysAndClosedHandleAreEnforcedAcrossWorkerThreads() {
        open().close()
        execute("CREATE TRIGGER fk_probe AFTER INSERT ON accepted_cursor WHEN NEW.scope_id='fk-probe' BEGIN INSERT INTO accepted_record(scope_id,record_kind,record_id,payload_json,payload_version) VALUES ('missing-parent','fixture','orphan','{}',1); END")
        val storage = open()
        val pool = Executors.newFixedThreadPool(2)
        try {
            pool.submit {
                storage.commitAccepted("a", null, 1, listOf(first))
                assertThrows(Exception::class.java) { storage.commitAccepted("fk-probe", null, 1, emptyList()) }
                assertNull(storage.accepted("fk-probe"))
            }.get(10, TimeUnit.SECONDS)
            pool.submit {
                storage.commitAccepted("a", 1, 2, listOf(second))
                assertEquals(2, storage.accepted("a")!!.records.size)
                assertThrows(Exception::class.java) { storage.commitAccepted("fk-probe", null, 1, emptyList()) }
                assertNull(storage.accepted("fk-probe"))
            }.get(10, TimeUnit.SECONDS)
            storage.close()
            storage.close()
            assertThrows(IllegalStateException::class.java) { storage.accepted("a") }
        } finally { storage.close(); pool.shutdownNow() }
        open().use { assertEquals(2, it.accepted("a")!!.records.size) }
    }

    @Test fun defaultPathIsPrivateNoBackupAndOpeningOnMainIsRefusedBeforeIo() {
        assertTrue(AndroidClientStorage.databaseFile(context).canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
        instrumentation.runOnMainSync {
            assertThrows(IllegalStateException::class.java) { AndroidClientStorage.open(file) }
        }
        assertFalse(file.exists())
    }
}
