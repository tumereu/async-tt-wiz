package org.asyncttwiz.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import org.asyncttwiz.storage.internaldb.ClientDatabase

/** Opaque versioned wire data, not Java serialization or an authoritative local game. */
data class StoredPayload(val version: Long, val json: String) {
    init { require(version > 0) }
}

data class RecordKey(val kind: String, val id: String) {
    init { require(kind.isNotEmpty() && id.isNotEmpty()) }
}

data class AcceptedRecord(val key: RecordKey, val payload: StoredPayload)
data class AcceptedSnapshot(val revision: Long, val records: List<AcceptedRecord>)

enum class StorageOpenFailure { OPEN_FAILED, UNSUPPORTED_VERSION, INVALID_SCHEMA, MIGRATION_FAILED }

sealed interface StorageOpenResult {
    data class Opened(val storage: ClientStorage) : StorageOpenResult
    /** No writable handle escapes. Retain the file for retry/recovery, never delete it. */
    data class Failed(val reason: StorageOpenFailure) : StorageOpenResult
}

/**
 * Owns one driver. Blocking operations must be dispatched off Android's UI thread by the caller.
 * Synchronization serializes reads, atomic accepted writes and close on this repository only.
 * Account/session fences and complete-baseline staging are supplied by GameplayTreeCache.
 */
class ClientStorage private constructor(private val driver: SqlDriver) : AutoCloseable {
    private val database = ClientDatabase(driver)
    private val lock = Any()
    private var closed = false

    fun accepted(scope: String): AcceptedSnapshot? = access {
        require(scope.isNotEmpty())
        database.transactionWithResult {
            database.replicaQueries.selectCursor(scope).executeAsOneOrNull()?.let { revision ->
                AcceptedSnapshot(revision, database.replicaQueries.selectRecords(scope) { kind, id, version, json ->
                    AcceptedRecord(RecordKey(kind, id), StoredPayload(version, json))
                }.executeAsList())
            }
        }
    }

    /** Cursor + one publication header, without loading every retained event. */
    internal fun acceptedEntry(scope: String, key: RecordKey): Pair<Long, StoredPayload>? = access {
        database.transactionWithResult {
            val revision = database.replicaQueries.selectCursor(scope).executeAsOneOrNull() ?: return@transactionWithResult null
            database.replicaQueries.selectRecord(scope, key.kind, key.id) { version, json -> StoredPayload(version, json) }
                .executeAsOneOrNull()?.let { revision to it }
        }
    }

    /**
     * Persist an already accepted incremental batch and its cursor together. expectedRevision is
     * a LOCAL storage-base check, not a gameplay/transport guard. Null means no existing replica.
     * There is deliberately no independent cursor setter or speculative/outgoing write API.
     */
    fun commitAccepted(
        scope: String,
        expectedRevision: Long?,
        revision: Long,
        upserts: List<AcceptedRecord>,
        removals: List<RecordKey> = emptyList(),
    ) = access {
        require(scope.isNotEmpty() && revision >= 0)
        require(expectedRevision == null || revision > expectedRevision)
        require(upserts.map { it.key }.distinct().size == upserts.size)
        require(removals.distinct().size == removals.size)
        require(upserts.none { it.key in removals })
        database.transaction {
            check(database.replicaQueries.selectCursor(scope).executeAsOneOrNull() == expectedRevision) {
                "Accepted storage base changed"
            }
            database.replicaQueries.insertCursor(scope, revision)
            removals.forEach { database.replicaQueries.removeRecord(scope, it.kind, it.id) }
            upserts.forEach {
                database.replicaQueries.putRecord(scope, it.key.kind, it.key.id, it.payload.version, it.payload.json)
            }
            database.replicaQueries.updateCursor(revision, scope)
        }
    }

    /** Sparse derived-cache upgrade under the caller's publication CAS transaction. */
    internal fun amendAccepted(scope: String, revision: Long, upserts: List<AcceptedRecord>) = access {
        require(upserts.map { it.key }.distinct().size == upserts.size)
        database.transaction {
            check(database.replicaQueries.selectCursor(scope).executeAsOneOrNull() == revision) { "Accepted storage base changed" }
            upserts.forEach { database.replicaQueries.putRecord(scope, it.key.kind, it.key.id, it.payload.version, it.payload.json) }
        }
    }

    /** Replace only this accepted scope, including records absent from a fresh complete baseline.
     * Equal-revision replacement permits repair of derived/corrupt local data. The tree adapter also
     * compares its publication identity, so an equal-revision writer cannot evade its local CAS.
     */
    fun replaceAccepted(scope: String, expectedRevision: Long?, revision: Long, records: List<AcceptedRecord>) = access {
        require(scope.isNotEmpty() && revision >= 0 && (expectedRevision == null || revision >= expectedRevision))
        require(records.map { it.key }.distinct().size == records.size)
        database.transaction {
            check(database.replicaQueries.selectCursor(scope).executeAsOneOrNull() == expectedRevision) {
                "Accepted storage base changed"
            }
            database.replicaQueries.insertCursor(scope, revision)
            database.replicaQueries.removeScopeRecords(scope)
            records.forEach {
                database.replicaQueries.putRecord(scope, it.key.kind, it.key.id, it.payload.version, it.payload.json)
            }
            database.replicaQueries.updateCursor(revision, scope)
        }
    }

    /** Adapter operations (owner/fence checks, records, cursor and staging cleanup) share one commit. */
    internal fun <T> atomic(block: () -> T): T = access { database.transactionWithResult { block() } }

    /** Late owner callbacks must not enter a driver that has already been closed. */
    internal fun <T> atomicIfOpen(block: () -> T): T? = synchronized(lock) {
        if (closed) null else database.transactionWithResult { block() }
    }

    fun personal(scope: String, key: String): StoredPayload? = access {
        database.personalQueries.selectState(scope, key, ::StoredPayload).executeAsOneOrNull()
    }

    fun savePersonal(scope: String, key: String, payload: StoredPayload) = access {
        require(scope.isNotEmpty() && key.isNotEmpty())
        database.personalQueries.putState(scope, key, payload.version, payload.json)
        Unit
    }

    fun removePersonal(scope: String, key: String) = access {
        database.personalQueries.removeState(scope, key)
        Unit
    }

    fun publicCache(key: String): StoredPayload? = access {
        database.cacheQueries.selectEntry(key, ::StoredPayload).executeAsOneOrNull()
    }

    fun cachePublic(key: String, payload: StoredPayload) = access {
        require(key.isNotEmpty())
        database.cacheQueries.putEntry(key, payload.version, payload.json)
        Unit
    }

    fun evictPublicCache() = access {
        database.cacheQueries.evictAll()
        Unit
    }

    /** One installation profile; switching accounts never makes the former replica readable. */
    fun activateAccount(accountId: String) = access {
        require(accountId.isNotEmpty())
        database.transaction {
            val owner = database.personalQueries.selectState("installation", "account-owner", ::StoredPayload).executeAsOneOrNull()
            if (owner != StoredPayload(1, accountId)) {
                clearPrivateData()
                database.personalQueries.putState("installation", "account-owner", 1, accountId)
            }
        }
    }

    /** Logical deletion, not a physical-erasure claim. Public catalog/art cache is independent. */
    fun clearPrivateData() = access {
        database.transaction {
            database.replicaQueries.clearRecords()
            database.replicaQueries.clearCursors()
            database.personalQueries.clearPrivateState()
        }
    }

    private fun <T> access(block: () -> T): T = synchronized(lock) {
        check(!closed) { "Storage is closed" }
        block()
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            driver.close()
        }
    }

    companion object {
        /**
         * The factory must return a raw synchronous SQLite driver: NO automatic create/migrate,
         * downgrade, corruption deletion or nested open transaction. Foreign keys must be enabled
         * for every connection by the adapter (not a one-connection PRAGMA). Ownership transfers here.
         * Platform adapters must also suppress their own destructive error handlers (I01d2).
         */
        fun open(driverFactory: () -> SqlDriver): StorageOpenResult {
            val driver = try { driverFactory() } catch (_: Exception) {
                return StorageOpenResult.Failed(StorageOpenFailure.OPEN_FAILED)
            }
            var failure = StorageOpenFailure.INVALID_SCHEMA
            return try {
                check(driver.currentTransaction() == null)
                val storage = ClientStorage(driver)
                storage.database.transaction {
                    check(driver.longValue("PRAGMA foreign_keys") == 1L)
                    val version = driver.longValue("PRAGMA user_version")
                    when {
                        version > ClientDatabase.Schema.version -> throw OpenRefused(StorageOpenFailure.UNSUPPORTED_VERSION)
                        version == 0L -> {
                            check(driver.longValue("SELECT count(*) FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name != 'android_metadata'") == 0L)
                            ClientDatabase.Schema.create(driver)
                        }
                        version in 1L until ClientDatabase.Schema.version -> {
                            validateColumns(driver, version)
                            failure = StorageOpenFailure.MIGRATION_FAILED
                            ClientDatabase.Schema.migrate(driver, version, ClientDatabase.Schema.version)
                        }
                        version != ClientDatabase.Schema.version -> throw OpenRefused(StorageOpenFailure.UNSUPPORTED_VERSION)
                    }
                    validateColumns(driver, ClientDatabase.Schema.version)
                    check(driver.longValue("SELECT count(*) FROM accepted_cursor WHERE revision < 0") == 0L)
                    check(driver.longValue("SELECT count(*) FROM accepted_record WHERE payload_version <= 0") == 0L)
                    check(driver.longValue("SELECT count(*) FROM personal_state WHERE payload_version <= 0") == 0L)
                    check(driver.longValue("SELECT count(*) FROM public_cache WHERE payload_version <= 0") == 0L)
                    check(driver.textValues("PRAGMA quick_check") == listOf("ok"))
                    check(driver.textValues("PRAGMA foreign_key_check").isEmpty())
                    driver.execute(null, "PRAGMA user_version = ${ClientDatabase.Schema.version}", 0)
                }
                StorageOpenResult.Opened(storage)
            } catch (error: Exception) {
                // Do not return SQL, paths, payloads or credentials through errors/logging.
                try { driver.close() } catch (_: Exception) { /* still no usable handle */ }
                StorageOpenResult.Failed((error as? OpenRefused)?.reason ?: failure)
            }
        }

        private fun validateColumns(driver: SqlDriver, version: Long) {
            val tables = linkedMapOf(
                "accepted_cursor" to listOf("scope_id:TEXT:1:1", "revision:INTEGER:1:0"),
                "accepted_record" to listOf("scope_id:TEXT:1:1", "record_kind:TEXT:1:2", "record_id:TEXT:1:3", "payload_json:TEXT:1:0"),
                "personal_state" to listOf("scope_id:TEXT:1:1", "state_key:TEXT:1:2", "payload_json:TEXT:1:0"),
            )
            if (version >= 2L) {
                listOf("accepted_record", "personal_state").forEach { table ->
                    tables[table] = tables.getValue(table) + "payload_version:INTEGER:1:0"
                }
                tables["public_cache"] = listOf("cache_key:TEXT:1:1", "payload_version:INTEGER:1:0", "payload_json:TEXT:1:0")
            }
            tables.forEach { (table, expected) ->
                val actual = driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
                    val columns = mutableListOf<String>()
                    while (cursor.next().value) {
                        columns += "${cursor.getString(1)}:${cursor.getString(2)}:${cursor.getLong(3)}:${cursor.getLong(5)}"
                    }
                    QueryResult.Value(columns)
                }, 0).value
                check(actual == expected)
            }
        }

        private class OpenRefused(val reason: StorageOpenFailure) : Exception()
    }
}

private fun SqlDriver.longValue(sql: String): Long = executeQuery(null, sql, { cursor ->
    check(cursor.next().value)
    QueryResult.Value(requireNotNull(cursor.getLong(0)))
}, 0).value

private fun SqlDriver.textValues(sql: String): List<String> = executeQuery(null, sql, { cursor ->
    val values = mutableListOf<String>()
    while (cursor.next().value) values += requireNotNull(cursor.getString(0))
    QueryResult.Value(values)
}, 0).value
